package net.nashat

import kotlin.random.Random

var origVectorIdGenerator = 0


class RlncNode(index: Int, rnd: Random, params: PotuzParams) : AbstractNode(index, rnd, params) {

    override fun handleRecovered() {}
    override fun makePublisher() {
        currentMartix =
            CoefMatrix.generate(params.numberOfChunks, params.numberOfChunks, rnd, params.maxMultiplier)
        coefDescriptors =
            currentMartix.coefVectors.map { CoefVectorDescriptor(it, emptyList(), origVectorIdGenerator++) }
                .toMutableList()
    }

    override fun generateNewMessageImpl(peers: Collection<AbstractNode>): PotuzMessage? {
        var origVectorId: Int? = null;
        val newChunk = when (currentMartix.coefVectors.size) {
            0 -> return null

            1 -> // no sense to multiply a single vector
                currentMartix.coefVectors.first()

            params.numberOfChunks -> // we recover original data and may generate a chunk with any random coefs
            {
                origVectorId = origVectorIdGenerator++
                CoefVector.generate(params.numberOfChunks, rnd, params.maxMultiplier)
            }

            else -> // generate a random linear combination of existing chunks
                currentMartix.coefVectors.fold(
                    CoefVector.zero(params.numberOfChunks),
                    { v1, v2 -> v1 + v2 * rnd.nextLong(params.maxMultiplier) }
                )
        }
        val receivePeer: AbstractNode = peers
            .shuffled(rnd)
            .prioritizeReceiveCandidates()
//            .sortedBy { seenVectorsByPeer[it]?.rank ?: 0 }
            .firstOrNull { receiver ->
                seenVectorsByPeer[receiver]?.let { seenVectors ->
                    val newVectors = seenVectors + newChunk
                    newVectors.rank > seenVectors.rank
                } ?: true
            } ?: return null
        val descriptor: List<CoefVectorDescriptor> = if (isRecovered()) listOf() else coefDescriptors.toList()
        val newDescriptor =
            if (descriptor.size == 1) descriptor[0] else CoefVectorDescriptor(newChunk, descriptor, origVectorId)

        val msg = PotuzMessage(newChunk, newDescriptor, this, receivePeer)
        addSeenVectorForPeer(msg.to, newChunk)
        return msg
    }
}