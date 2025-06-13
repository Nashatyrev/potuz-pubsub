package net.nashat

import kotlin.random.Random

var origVectorIdGenerator = 0

class RlncNode(index: Int, rnd: Random, config: PotuzSimulationConfig) : AbstractNode(index, rnd, config) {

    private val maxCoefThreshold = config.simConfig.filterByMaxCoefficient?.let {
        pFieldFactory[it]
    }

    private val maxCoeffVectorFilter: (CoefVector) -> Boolean = { coefVector ->
        if (maxCoefThreshold != null) {
            coefVector.vec.all { it <= maxCoefThreshold }
        } else {
            true
        }
    }

    val maxHopThreshold = config.simConfig.limitMaxHops
    private val maxHopVectorFilter: (CoefVectorDescriptor) -> Boolean = { coefVectorDescr ->
        if (maxHopThreshold != null) {
            val rc = coefVectorDescr.treeDepth < maxHopThreshold
            if (!rc) {
//                println("!!!!")
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
        val (newChunk, descriptors) = when (currentMartix.coefVectors.size) {
            0 -> return null

            1 -> // no sense to multiply a single vector
                currentMartix.coefVectors.first() to coefDescriptors

            simConfig.numberOfChunks -> // we recover original data and may generate a chunk with any random coefs
            {
                origVectorId = origVectorIdGenerator++
                CoefVector.generate(
                    simConfig.numberOfChunks,
                    rnd,
                    config.maxMultiplier
                ) to emptyList<CoefVectorDescriptor>()

            }

            else -> { // generate a random linear combination of existing chunks
                val descr = coefDescriptors
                    .filter { maxHopVectorFilter(it) }
                    .filter { maxCoeffVectorFilter(it.coefs) }
                val newChunk = descr
                    .map { it.coefs }
                    .fold(
                        CoefVector.zero(simConfig.numberOfChunks),
                        { v1, v2 -> v1 + v2 * rnd.nextLong(config.maxMultiplier) }
                    )
                newChunk to descr
            }
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
        val newDescriptor =
            if (descriptors.size == 1) descriptors[0] else CoefVectorDescriptor(newChunk, descriptors, origVectorId)

        val msg = PotuzMessage(newChunk, newDescriptor, this, receivePeer)
        addSeenVectorForPeer(msg.to, newChunk)
        return msg
    }
}