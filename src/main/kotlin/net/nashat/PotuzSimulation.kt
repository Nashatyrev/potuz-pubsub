package net.nashat

import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

fun main() {
//    runAll()
//    noErasureCodingDispersionTest()
    mainTest()
}

fun runAll() {
    val startT = System.currentTimeMillis()

    val configs =
        listOf(
            2,
            6,
            10,
            20,
            40,
            100
        ).flatMap { peerCount ->
            listOf(
                1,
                2,
                4,
                10,
                20,
                40,
                100
            ).flatMap { chunkCount ->
                Erasure.values().map { erasure ->
                    SimConfig(
                        nodeCount = 1000,
                        peerCount = peerCount,
                        numberOfChunks = chunkCount,
                        erasure = erasure
                    )
                }
            }
        }

    val jsonPretty = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    val result = PotuzSimulation.runAll(configs)

    PotuzIO().writeResultsToJson("./result.json", result)

    println("Completed in ${System.currentTimeMillis() - startT} ms")
}

fun mainTest() {
    val commonCfg = SimConfig(
        erasure = Erasure.NoErasure,
        numberOfChunks = 40,
        latencyRounds = 20,
        randomSeed = 1
    )

    val resDf =
        PotuzSimulation.runAll(
            listOf(
                commonCfg.copy(
                    peerCount = 10,
                    rsMeshStrategy = MeshStrategy.Static
                ),
                commonCfg.copy(
                    peerCount = 3,
                    rsMeshStrategy = MeshStrategy.Static
                ),
                commonCfg.copy(
                    peerCount = 4,
                    rsMeshStrategy = MeshStrategy.Static
                ),
                commonCfg.copy(
                    peerCount = 5,
                    rsMeshStrategy = MeshStrategy.Static
                ),
                commonCfg.copy(
                    peerCount = 10,
                    rsMeshStrategy = MeshStrategy.TwoPhaseMesh
                ),
            )
        )
            .deriveExtraResultsExploded()

    val df1 = resDf.add("peer_count_and_mesh_type") { "" + config.peerCount + "/" + config.rsMeshStrategy }
    df1
        .myPlotGroupDeliveredPartsAndMessageTypeCounts(adjustX2 = 0) {
            "peer_count_and_mesh_type"()
        }
}

class PotuzSimulation(
    val config: PotuzSimulationConfig,
    val logEveryRound: Boolean = false
) {

    val nodes: List<AbstractNode>
    val network: RandomNetwork
    val nodeSelectorStrategy: ReceiveNodeSelectorStrategy
    val rnd = Random(config.simConfig.randomSeed)

    private val simConfig get() = config.simConfig
    private val withChunkDistribution get() = config.withChunkDistribution

    init {
        setFieldPrime(config.pPrime)
        when (config.simConfig.erasure) {
            Erasure.RLNC -> {

                nodes = List(simConfig.nodeCount) { index -> RlncNode(index, rnd, config) }
                network = RandomNetworkGenerator(simConfig.nodeCount, simConfig.peerCount, rnd).generate()
                nodeSelectorStrategy = ReceiveNodeSelectorStrategy.createNetworkLimitedReceiveMessage(
                    nodes,
                    network,
                    config.messageBufferSize
                )
            }

            else -> {
                val extendedChunksCount = simConfig.numberOfChunks * simConfig.erasure.extensionFactor
                val phase0ChunkMeshes: List<RandomNetwork>
                val phase1ChunkMeshes: List<RandomNetwork>
                if (simConfig.rsIsDistinctMeshes) {
                    phase0ChunkMeshes = List(extendedChunksCount) {
                        RandomNetworkGenerator(
                            simConfig.nodeCount,
                            simConfig.peerCount,
                            rnd
                        ).generate()
                    }
                    phase1ChunkMeshes =
                        if (simConfig.rsMeshStrategy == MeshStrategy.TwoPhaseMesh)
                            phase0ChunkMeshes.map { RandomNetworkGenerator.withReducedPeerCount(it, 3, rnd) }
                        else
                            emptyList()

                } else {
                    val singleMesh =
                        RandomNetworkGenerator(simConfig.nodeCount, simConfig.peerCount, rnd).generate()
                    phase0ChunkMeshes = List(extendedChunksCount) { singleMesh }
                    if (simConfig.rsMeshStrategy == MeshStrategy.TwoPhaseMesh) {
                        val phase1SingleMesh =
                            RandomNetworkGenerator.withReducedPeerCount(singleMesh, 3, rnd)
                        phase1ChunkMeshes = List(extendedChunksCount) { phase1SingleMesh }
                    } else {
                        phase1ChunkMeshes = emptyList()
                    }
                }

                nodes = List(simConfig.nodeCount) { index ->
                    RsNode(
                        index,
                        rnd,
                        config,
                        phase0ChunkMeshes,
                        phase1ChunkMeshes
                    )
                }
                network = RandomNetworkGenerator.createAllToAll(simConfig.nodeCount)
                nodeSelectorStrategy = ReceiveNodeSelectorStrategy.createRandomLimitedReceiveMessage(
                    nodes,
                    config.messageBufferSize
                )
            }
        }

        nodes.first().makePublisher()
    }

    var currentRound = 0

    fun produceOutboundMessages(): List<PotuzMessage> {
        val nodeSelector = nodeSelectorStrategy.createNodeSelector()
        return nodes
            .shuffled(rnd)
            .mapNotNull { sender ->
                val receiverCandidates = nodeSelector.selectReceivingNodeCandidates(sender)
                sender.generateNewMessage(receiverCandidates, currentRound)?.also { msg ->
                    nodeSelector.onReceiverSelected(msg.to, msg.from)
                }
            }
    }

    fun dispatchOutboundMessages(messages: List<PotuzMessage>) {
        messages.forEach { message ->
            message.to.bufferInboundMessage(message, currentRound)
        }
    }

    fun processBufferedMessages() {
        nodes.forEach { it.processBufferedMessages(currentRound) }
    }

    fun turnToNextRound() {
        currentRound++
    }

    fun maxTreeNodes() =
        nodes.maxOfOrNull { it.receivedMessages.maxOfOrNull { it.msg.descriptor.countTreeNodes() } ?: 0 } ?: 0


    val allReceivedMessages get() = nodes.flatMap { it.receivedMessages }
    val totalMessageCount get() = allReceivedMessages.size
    val duplicateMessages get() = allReceivedMessages.filter { !it.isNew }
    val duplicateMessagesBeforeRecover get() = allReceivedMessages.filter { !it.isNew && !it.isRecovered }
    val duplicateMessageCount get() = allReceivedMessages.count { !it.isNew }
    val duplicateMessageBeforeRecoverCount get() = allReceivedMessages.count { !it.isNew && !it.isRecovered }
    val messagesByConnection
        get() = allReceivedMessages
            .groupBy { setOf(it.msg.from, it.msg.to) }

    fun getAllReceivedMessagesInRound(round: Int) =
        nodes.flatMap { it.receivedMessages.filter { it.receiveRound == round } }

    fun getDuplicateOneConnectionMessageCount() =
        allReceivedMessages
            .groupingBy { setOf(it.msg.from, it.msg.to) to it.msg.coefs }
            .eachCount()
            .count { (_, cnt) -> cnt > 1 }

    val receivedNodeCount get() = nodes.count { it.isRecovered() }
    val activeNodeCount get() = nodes.count { it.isActive() }
    val totalChunksCount get() = nodes.sumOf { it.getChunksCount() }
    val targetTotalChunksCount = simConfig.nodeCount * simConfig.numberOfChunks
    val allReceived get() = receivedNodeCount == simConfig.nodeCount

    fun chunkDistribution() =
        nodes
            .flatMap { it.getOriginalVectorIds() }
            .groupingBy { it }
            .eachCount()
            .let { map ->
                List(map.keys.max() + 1) { map[it] ?: 0 }
            }

    fun chunkCountDistribution() =
        nodes
            .map { it.getChunksCount() }
            .groupingBy { it }
            .eachCount()
            .let { map ->
                List(map.keys.max() + 1) { map[it] ?: 0 }
            }


    fun run(): DataFrame<CoreResult> {
        val rows = mutableListOf<CoreResult>()

//        var duplicateOneConnectionMessagesAccum = 0
        while (true) {
            if (config.isGodStopMode && allReceived) break

            val outboundMessages = produceOutboundMessages()
            if (outboundMessages.isEmpty()) {
                break
            }

            dispatchOutboundMessages(outboundMessages)

            val congestedNodeCount = nodes.count { it.isCongested() }

            processBufferedMessages()
            turnToNextRound()

//            duplicateOneConnectionMessagesAccum += getDuplicateOneConnectionMessagesForRound(round - 1).size

            val chunkDistr = if (withChunkDistribution) chunkDistribution() else emptyList()

            rows += CoreResult(
                receivedNodeCount,
                activeNodeCount,
                totalMessageCount,
                duplicateMessageCount,
                duplicateMessageBeforeRecoverCount,
//                getDuplicateOneConnectionMessageCount(), // optimization needed
                0,
                chunkDistr,
                chunkCountDistribution(),
                congestedNodeCount
            )

            if (logEveryRound) {
                val totalNonDupRequired = (nodes.size - 1) * simConfig.numberOfChunks
                val nonDupDone = totalMessageCount - duplicateMessageCount
                println("$currentRound: $nonDupDone/$totalNonDupRequired, ${rows.last()}")
            }
        }

        val dataFrame = rows.toDataFrame()

        return dataFrame
    }

    companion object {
        fun runAll(
            configs: List<SimConfig>, withChunkDistribution: Boolean = false
        ): DataFrame<ResultEntry> =
            runAllExt(configs.map { PotuzSimulationConfig(it, withChunkDistribution = withChunkDistribution) })

        fun runAllExt(
            configs: List<PotuzSimulationConfig>
        ): DataFrame<ResultEntry> {
            val readyCounter = AtomicInteger()
            val results = configs
                .parallelStream()
                .map { config ->
                    val res = try {
                        PotuzSimulation(config).run()
                    } catch (e: Exception) {
                        System.err.println("Error for config: $config")
                        throw e
                    }
                    println("Complete " + readyCounter.incrementAndGet() + "/" + configs.size)
                    ResultEntry(config.simConfig, res)
                }
                .toList()
            return results.toDataFrame()
        }
    }
}