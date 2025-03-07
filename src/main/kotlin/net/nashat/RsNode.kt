package net.nashat

import kotlin.random.Random

class RsNode(
    index: Int,
    rnd: Random,
    config: PotuzSimulationConfig,
    phase0ChunkMeshes: List<RandomNetwork>,
    val phase1ChunkMeshes: List<RandomNetwork> = emptyList()
) :
    AbstractNode(index, rnd, config) {

    val numberOfExtendedChunks = simConfig.numberOfChunks * simConfig.erasure.extensionFactor
    val receivedVectorIdCount = mutableMapOf<Int, Int>()

    var chunkMeshes = phase0ChunkMeshes.map { it.connections[index]!! }

    init {
        require(chunkMeshes.size == numberOfExtendedChunks)
        require(simConfig.rsMeshStrategy == MeshStrategy.Static || phase1ChunkMeshes.isNotEmpty())
    }

    override fun makePublisher() {
        val localRnd = Random(0)
        var localIdGenerator = 0
        currentMartix =
            CoefMatrix.generate(numberOfExtendedChunks, simConfig.numberOfChunks, localRnd, config.maxMultiplier)
        coefDescriptors =
            currentMartix.coefVectors.map { CoefVectorDescriptor(it, emptyList(), localIdGenerator++) }
                .toMutableList()
    }

    override fun handleRecovered() {
        makePublisher()
    }

    private var phase0 = true
    fun maybeSwitchToPhase1Meshes() {
        if (simConfig.rsMeshStrategy == MeshStrategy.Static)
            return
        if (phase0 && getChunksCount() >= simConfig.numberOfChunks / 2) {
            phase0 = false
            chunkMeshes = phase1ChunkMeshes.map { it.connections[index]!! }
        }
    }

    override fun generateNewMessageImpl(peers: Collection<AbstractNode>): PotuzMessage? {
        if (getChunksCount() == 0) return null
        if (isRecovered()) return generateNewMessageWithoutPartialExtensionForPublisher(peers)
        maybeSwitchToPhase1Meshes()

        val peerByIndex = peers.associateBy { it.index }
        val meshNodesCache = mutableMapOf<Int, List<AbstractNode>>()
        fun getMeshNodes(vectorIndex: Int): List<AbstractNode> = meshNodesCache.computeIfAbsent(vectorIndex) {
            val chunkIndex = coefDescriptors[vectorIndex].originalVectorId!!
            val chunkMesh = chunkMeshes[chunkIndex]
            chunkMesh.mapNotNull { peerByIndex[it] }
        }

        // prefer later (and thus more rare) vectors
        val vectorCandidateIndices =
            when (simConfig.rsChunkSelectionStrategy) {
                ChunkSelectionStrategy.PreferLater ->
                    currentMartix.coefVectors.indices
                        .sortedByDescending { coefDescriptors[it].originalVectorId }

                ChunkSelectionStrategy.Random ->
                    currentMartix.coefVectors.indices.shuffled(rnd)

                ChunkSelectionStrategy.PreferRarest ->
                    currentMartix.coefVectors.indices
                        .sortedWith(
                            compareBy<Int> {
                                receivedVectorIdCount[it] ?: 0
                            }.thenByDescending {
                                coefDescriptors[it].originalVectorId
                            }
                        )

                ChunkSelectionStrategy.PreferLaterThenRandom -> {
                    val latestPreferIndexes = currentMartix.coefVectors.indices
                        .sortedByDescending { coefDescriptors[it].originalVectorId }
                    if (coefDescriptors[latestPreferIndexes.first()].originalVectorId == simConfig.numberOfChunks - 1) {
                        // we've got the latest chunk, let's do random now
                        currentMartix.coefVectors.indices.shuffled(rnd)
                    } else {
                        latestPreferIndexes
                    }
                }

                ChunkSelectionStrategy.PreferLaterThenRarest -> {
                    val latestPreferIndexes = currentMartix.coefVectors.indices
                        .sortedByDescending { coefDescriptors[it].originalVectorId }
                    if (coefDescriptors[latestPreferIndexes.first()].originalVectorId == simConfig.numberOfChunks - 1) {
                        // we've got the latest chunk, let's do Rarest now
                        currentMartix.coefVectors.indices
                            .sortedWith(
                                compareBy<Int> {
                                    receivedVectorIdCount[it] ?: 0
                                }.thenByDescending {
                                    coefDescriptors[it].originalVectorId
                                }
                            )
                    } else {
                        latestPreferIndexes
                    }
                }
            }

        data class SendCandidate(
            val vectorIndex: Int,
            val receiver: AbstractNode
        ) {
            val existingVector = currentMartix.coefVectors[vectorIndex]
        }

        return vectorCandidateIndices
            .asSequence() // don't want eager calculation below
            .flatMap { vectorCandidateIndex ->
                getMeshNodes(vectorCandidateIndex)
                    .shuffled(rnd)
                    // prefer peers with less past traffic
                    .sortedBy { seenVectorsByPeer[it]?.rowCount ?: 0 }
                    .map { receiverCandidate ->
                        SendCandidate(vectorCandidateIndex, receiverCandidate)
                    }
            }
            .filter { candidate ->
                !(seenVectorsByPeer[candidate.receiver]?.coefVectorsSet?.contains(candidate.existingVector) ?: false)
            }
            .firstOrNull()
            ?.let { candidate ->
                val msg =
                    PotuzMessage(
                        candidate.existingVector,
                        coefDescriptors[candidate.vectorIndex],
                        this,
                        candidate.receiver
                    )
                addSeenVectorForPeer(msg.to, candidate.existingVector)
                msg
            }
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
                .prioritizeReceiveCandidates()
//                .sortedBy { seenVectorsByPeer[it]?.rowCount ?: 0 }
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

    override fun receive(bufMsg: BufferedMessage, currentHop: Int) {
        super.receive(bufMsg, currentHop)
        receivedVectorIdCount.compute(bufMsg.msg.descriptor.originalVectorId!!) { _, old -> (old ?: 0) + 1 }
    }
}