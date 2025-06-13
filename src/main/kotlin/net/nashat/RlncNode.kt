package net.nashat

import kotlin.random.Random

var origVectorIdGenerator = 0

class RlncNode(index: Int, rnd: Random, config: PotuzSimulationConfig) : AbstractNode(index, rnd, config) {

    val maxThreshold = config.simConfig.filterByMaxCoefficient?.let {
        pFieldFactory[it]
    }
    private val vectorFilter: (CoefVector) -> Boolean = { coefVector ->
        if (maxThreshold != null) {
            val rc = coefVector.vec.all { it <= maxThreshold }
            if (!rc) {
                println("!!!")
            }
            rc
        } else {
            true
        }
    }

    override fun handleRecovered() {}
    override fun makePublisher() {
        currentMartix =
            CoefMatrix.generate(simConfig.numberOfChunks, simConfig.numberOfChunks, rnd, config.maxMultiplier)
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

            simConfig.numberOfChunks -> // we recover original data and may generate a chunk with any random coefs
            {
                origVectorId = origVectorIdGenerator++
                CoefVector.generate(simConfig.numberOfChunks, rnd, config.maxMultiplier)
            }

            else -> // generate a random linear combination of existing chunks
                currentMartix.coefVectors
                    .filter(vectorFilter)
                    .fold(
                        CoefVector.zero(simConfig.numberOfChunks),
                        { v1, v2 -> v1 + v2 * rnd.nextLong(config.maxMultiplier) }
                    )
        }
        val receivePeer: AbstractNode = peers
            .shuffled(rnd)
            .prioritizeReceiveCandidates()
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