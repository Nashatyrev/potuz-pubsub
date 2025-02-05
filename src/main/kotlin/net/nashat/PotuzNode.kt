package net.nashat

import kotlin.random.Random

data class PotuzNode(
    val index: Int,
    private val rnd: Random,
    private val params: PotuzParams,
) {

    data class ReceivedMessage(
        val msg: PotuzMessage,
        val hop: Int,
        val isNew: Boolean,
        val isRecovered: Boolean,
    )

    var currentMartix = CoefMatrix.EMPTY
    val coefDescriptors = mutableListOf<CoefVectorDescriptor>()
    val receivedMessages = mutableListOf<ReceivedMessage>()
    val seenVectorsByPeer = mutableMapOf<PotuzNode, CoefMatrix>()
    var origVectorIdGenerator = 0

    fun makePublisher() {
        currentMartix =
            CoefMatrix.generate(params.numberOfChunks, params.numberOfChunks, rnd, params.maxInitialCoefficient)
    }

    fun generateNewMessage(peers: Collection<PotuzNode>): PotuzMessage? {
        var origVectorId: Int? = null;
        val newChunk = when (currentMartix.coefVectors.size) {
            0 -> return null

            1 -> // no sense to multiply a single vector
                currentMartix.coefVectors[0]

            params.numberOfChunks -> // we recover original data and may generate a chunk with any random coefs
            {
                origVectorId = origVectorIdGenerator++
                CoefVector.generate(params.numberOfChunks, rnd, params.maxInitialCoefficient)
            }

            else -> // generate a random linear combination of existing chunks
                if (params.isPartialExtensionSupported) {
                    currentMartix.coefVectors.fold(
                        CoefVector.zero(params.numberOfChunks),
                        { v1, v2 -> v1 + v2 * rnd.nextLong(params.maxMultiplicator) }
                    )
                } else {
                    currentMartix.coefVectors[rnd.nextInt(getChunksCount())]
                }
        }
        val receivePeer: PotuzNode = peers.shuffled(rnd).first { receiver ->
            seenVectorsByPeer[receiver]?.let { seenVectors ->
                val newVectors = seenVectors + newChunk
                newVectors.rank > seenVectors.rank
            } ?: true
        }
        val descriptor: List<CoefVectorDescriptor> = if (isRecovered()) listOf() else coefDescriptors.toList()
        val newDescriptor =
            if (descriptor.size == 1) descriptor[0] else CoefVectorDescriptor(newChunk, descriptor, origVectorId)

        val msg = PotuzMessage(newChunk, newDescriptor, this, receivePeer)
        addSeenVectorForPeer(msg.from, newChunk)
        return msg
    }

    fun getChunksCount() = currentMartix.coefVectors.size
    fun isRecovered() = getChunksCount() == params.numberOfChunks
    fun isActive() = getChunksCount() > 0

    private fun addSeenVectorForPeer(peer: PotuzNode, vec: CoefVector) {
        seenVectorsByPeer.compute(peer) { _, matrix ->
            if (matrix == null)
                CoefMatrix.fromVectors(listOf(vec))
            else
                matrix + vec
        }
    }

    fun receive(msg: PotuzMessage, hop: Int) {
        val isNew: Boolean

        if (!isRecovered()) {
            val newMatrix = currentMartix + msg.coefs
            isNew = newMatrix.rank > currentMartix.rank
            if (isNew) {
                currentMartix = newMatrix
                coefDescriptors += msg.descriptor
            }
        } else {
            isNew = false
        }
        receivedMessages += ReceivedMessage(msg, hop, isNew, isRecovered())
        addSeenVectorForPeer(msg.from, msg.coefs)
    }

    override fun toString(): String {
        return "$index"
    }
}
