package net.nashat

import kotlin.random.Random

class RsNode(index: Int, rnd: Random, params: PotuzParams, chunkMeshes: List<RandomNetwork>) :
    AbstractNode(index, rnd, params) {


    val rsParams = params.rsParams!!
    val numberOfExtendedChunks = params.numberOfChunks * rsParams.extensionFactor
    val chunkMeshes = chunkMeshes.map { it.connections[index]!! }

    init {
        require(chunkMeshes.size == numberOfExtendedChunks)
        require(params.rsParams != null)
    }

    override fun makePublisher() {
        val localRnd = Random(0)
        var localIdGenerator = 0
        currentMartix =
            CoefMatrix.generate(numberOfExtendedChunks, params.numberOfChunks, localRnd, params.maxMultiplier)
        coefDescriptors =
            currentMartix.coefVectors.map { CoefVectorDescriptor(it, emptyList(), localIdGenerator++) }
                .toMutableList()
    }

    override fun handleRecovered() {
        makePublisher()
    }

    override fun generateNewMessage(peers: Collection<AbstractNode>): PotuzMessage? {
        if (getChunksCount() == 0) return null
        if (isRecovered()) return generateNewMessageWithoutPartialExtensionForPublisher(peers)

        fun getMeshNodes(vectorIndex: Int): List<AbstractNode> {
            val chunkIndex = coefDescriptors[vectorIndex].originalVectorId!!
            val chunkMesh = chunkMeshes[chunkIndex]
            return peers.filter { it.index in chunkMesh }
        }

        // prefer later (and thus more rare) vectors
        val vectorCandidateIndices = currentMartix.coefVectors.indices
            .sortedByDescending { coefDescriptors[it].originalVectorId }

        for (existingVectorIdx in vectorCandidateIndices) {
            val existingVector = currentMartix.coefVectors[existingVectorIdx]

            val meshNodes = getMeshNodes(existingVectorIdx)
            // prefer peers with less past traffic
            val receiveCandidates = meshNodes
                .shuffled(rnd)
                .sortedBy { seenVectorsByPeer[it]?.rowCount ?: 0 }

            val maybeReceiver = receiveCandidates.firstOrNull { receiver ->
                !(seenVectorsByPeer[receiver]?.coefVectors?.contains(existingVector) ?: false)
            }
            if (maybeReceiver != null) {
                val msg =
                    PotuzMessage(
                        existingVector,
                        coefDescriptors[existingVectorIdx],
                        this,
                        maybeReceiver
                    )
                addSeenVectorForPeer(msg.to, existingVector)
                return msg
            }
        }
        return null
    }

    private var publisherIndexCounter = 0
    fun generateNewMessageWithoutPartialExtensionForPublisher(peers: Collection<AbstractNode>): PotuzMessage? {
        fun getMeshNodes(vectorIndex: Int): List<AbstractNode> {
            val chunkIndex = coefDescriptors[vectorIndex].originalVectorId!!
            val chunkMesh = chunkMeshes[chunkIndex]
            return peers.filter { it.index in chunkMesh }
        }

        repeat(currentMartix.coefVectors.size) {
            val existingVectorIdx = publisherIndexCounter % currentMartix.coefVectors.size
            publisherIndexCounter++
            val existingVector = currentMartix.coefVectors[existingVectorIdx]
            val receiveCandidates = getMeshNodes(existingVectorIdx)
                .shuffled(rnd)
                .sortedBy { seenVectorsByPeer[it]?.rowCount ?: 0 }
            for (receiveCandidate in receiveCandidates) {
                if (!(seenVectorsByPeer[receiveCandidate]?.coefVectors?.contains(existingVector) ?: false)) {
                    val msg =
                        PotuzMessage(
                            existingVector,
                            coefDescriptors[existingVectorIdx],
                            this,
                            receiveCandidate
                        )
                    addSeenVectorForPeer(msg.to, existingVector)
                    return msg
                }
            }
        }
        return null
    }
}