package net.nashat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.print
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

    val configs: List<PotuzSimulationConfig> =
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
                listOf(
                    RSParams(1, false),
                    RSParams(1, true),
                    RSParams(2, false),
                    RSParams(2, true),
                    RSParams(3, true),
                    RLNCParams(),
                ).map { erasureParams ->
                    PotuzParams.create(chunkCount, erasureParams)
                }
            }.map { potuzParams ->
                PotuzSimulationConfig(
                    potuzParams,
                    nodeCount = 1000,
                    peerCount = peerCount,
                    isGodStopMode = true
                )
            }
        }

    val jsonPretty = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    val readyCounter = AtomicInteger()
    val results = configs
        .parallelStream()
        .map { config ->
            val res = PotuzSimulation(config).run()
            println("Complete " + readyCounter.incrementAndGet() + "/" + configs.size)
            config to res
        }
        .toList()
        .toMap()


    results.forEach { (config, result) ->
        println(jsonPretty.encodeToString(config))
        result.print(rowsLimit = Int.MAX_VALUE)
        println()
    }

    PotuzIO().writeResultsToJson("./result.json", results.keys, results.values)

    println("Completed in ${System.currentTimeMillis() - startT} ms")
}

fun noErasureCodingDispersionTest() {
    val cfg = PotuzSimulationConfig(
        params = PotuzParams(
            numberOfChunks = 10,
            chunkSelectionStrategy = ChunkSelectionStrategy.PreferRarest,
            rsParams = RSParams(1, true)
        ),
        peerCount = 10,
        isGodStopMode = true
    )

    val res = PotuzSimulation(cfg, logEveryRound = true).run()
    res.print(rowsLimit = 10000, valueLimit = 10000)
}

fun mainTest() {
    val cfg = PotuzSimulationConfig(
        params = PotuzParams(
            numberOfChunks = 50,
            chunkSelectionStrategy = ChunkSelectionStrategy.Random,
//            rsParams = RSParams(2, true),
            rlncParams = RLNCParams(),
            messageBufferSize = 20
        ),
        peerCount = 40,
        isGodStopMode = true
    )

    PotuzSimulation(cfg, withChunkDistribution = false, logEveryRound = true).run()
}

class PotuzSimulation(
    val config: PotuzSimulationConfig,
    val withChunkDistribution: Boolean = false,
    val logEveryRound: Boolean = false
) {

    val nodes: List<AbstractNode>
    val network: RandomNetwork
    val nodeSelectorStrategy: ReceiveNodeSelectorStrategy
    val rnd = Random(config.randomSeed)

    init {
        setFieldPrime(config.params.pPrime)
        when {
            config.params.rlncParams != null -> {

                nodes = List(config.nodeCount) { index -> PotuzNode(index, rnd, config.params) }
                network = RandomNetwork(config.nodeCount, config.peerCount, rnd)
                nodeSelectorStrategy = ReceiveNodeSelectorStrategy.createNetworkSingleReceiveMessage(nodes, network)
            }

            config.params.rsParams != null -> {
                val extendedChunksCount = config.params.numberOfChunks * config.params.rsParams.extensionFactor
                val chunkMeshes =
                    if (config.params.rsParams.isDistinctMeshesPerChunk)
                        List(extendedChunksCount) { RandomNetwork(config.nodeCount, config.peerCount, rnd) }
                    else {
                        val singleMesh = RandomNetwork(config.nodeCount, config.peerCount, rnd)
                        List(extendedChunksCount) { singleMesh }
                    }

                nodes = List(config.nodeCount) { index -> RsNode(index, rnd, config.params, chunkMeshes) }
                network = RandomNetwork.createAllToAll(config.nodeCount)
                nodeSelectorStrategy = ReceiveNodeSelectorStrategy.createRandomSingleReceiveMessage(nodes)
            }

            else -> throw NotImplementedError()
        }

        nodes.first().makePublisher()
    }

    var round = 0

    fun nextRound(): Int {
        val nodeSelector = nodeSelectorStrategy.createNodeSelector()
        val messages = nodes.shuffled(rnd).map { sender ->
            val receiverCandidates = nodeSelector.selectReceivingNodeCandidates(sender)
            sender.generateNewMessage(receiverCandidates)?.also { msg ->
                nodeSelector.onReceiverSelected(msg.to, msg.from)
                msg.to.bufferInboundMessage(msg, round)
            }
        }
        nodes.forEach { it.processSingleBufferedMessage(round) }
//        for (idx in messages.indices) {
//            val msg = messages[idx]
//            if (msg != null) {
//                val sender = nodes[idx]
//                msg.to.receive(msg, round)
//            }
//        }

        round++
        val msgCount = messages.count { it != null }
        return msgCount

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
    val targetTotalChunksCount = config.nodeCount * config.params.numberOfChunks
    val allReceived get() = receivedNodeCount == config.nodeCount

    fun getCongestedNodeCount() = nodes.count { it.isCongested() }

    fun chunkDistribution() =
        nodes
            .map {
                it.coefDescriptors.flatMap {
                    it.getAllOriginalVectorsRecursively().map { it.originalVectorId!! }
                }.toSet()
            }
            .fold(mutableMapOf<Int, Int>()) { acc, vectorIds ->
                vectorIds.forEach { vectorId ->
                    acc.compute(vectorId) { _, oldCount ->
                        (oldCount ?: 0) + 1
                    }
                }
                acc
            }
            .let { map ->
                List(map.keys.max() + 1) { map[it] ?: 0 }
            }

    fun run(): DataFrame<CoreResult> {
        val rows = mutableListOf<CoreResult>()

//        var duplicateOneConnectionMessagesAccum = 0
        while (true) {
            if (config.isGodStopMode && allReceived) break

            val messageCount = nextRound()

            if (messageCount == 0) {
                break
            }

//            duplicateOneConnectionMessagesAccum += getDuplicateOneConnectionMessagesForRound(round - 1).size

            val chunkDistr = if (withChunkDistribution) chunkDistribution() else emptyList()

            rows += CoreResult(
                receivedNodeCount,
                activeNodeCount,
                totalMessageCount,
                duplicateMessageCount,
                duplicateMessageBeforeRecoverCount,
                getDuplicateOneConnectionMessageCount(),
                chunkDistr,
                getCongestedNodeCount()
            )

            if (logEveryRound) {
                val totalNonDupRequired = (nodes.size - 1) * config.params.numberOfChunks
                val nonDupDone = totalMessageCount - duplicateMessageCount
                println("$round: $nonDupDone/$totalNonDupRequired, ${rows.last()}")
            }
        }

        val dataFrame = rows.toDataFrame()

        return dataFrame
    }

    companion object {
        fun runAll(
            configs: List<PotuzSimulationConfig>, withChunkDistribution: Boolean = false
        ): DataFrame<RawConfigResultEntry> {
            val readyCounter = AtomicInteger()
            val results = configs
                .parallelStream()
                .map { config ->
                    val res = PotuzSimulation(config, withChunkDistribution = withChunkDistribution).run()
                    println("Complete " + readyCounter.incrementAndGet() + "/" + configs.size)
                    RawConfigResultEntry(config, res)
                }
                .toList()
            return results.toDataFrame()
        }
    }
}