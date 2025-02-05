package net.nashat

import kotlin.random.Random

fun main() {
    val startT = System.currentTimeMillis()
    for (seed in 0..0) {
        PotuzSimulation(
            isPartialExtensionSupported = false,
            pPrime = PRIME_2_IN_8_PLUS_1,
            rnd = Random(seed),
        ).run()
    }
    println("Completed in ${System.currentTimeMillis() - startT} ms")
}

class PotuzSimulation(
    val numberOfChunks: Int = 20,
    val isPartialExtensionSupported: Boolean = true,
    val nodeCount: Int = 1000,
    val pPrime: String = PRIME_2_IN_63_PLUS_O,
    val rnd: Random = Random(6)
) {

    init {
        setFieldPrime(pPrime)
    }

    val cappedPPrime = try { pPrime.toLong() } catch (e: Exception) { Int.MAX_VALUE.toLong() }
    val params: PotuzParams = PotuzParams(numberOfChunks, isPartialExtensionSupported, cappedPPrime - 1, cappedPPrime - 1)

    var round = 0
    val nodes = List(nodeCount) { index ->
        PotuzNode(index, rnd, params)
    }.also {
        it.first().makePublisher()
    }
    val nodeSelectorStrategy: ReceiveNodeSelectorStrategy =
        ReceiveNodeSelectorStrategy.createRandomSingleReceiveMessage(nodes, rnd)

    fun nextRound() {
        val receivers = nodes.toMutableSet()
        val messages = nodes.map {
            it.generateNewMessage(receivers)?.also { msg ->
                receivers -= msg.to
            }
        }
        for (idx in messages.indices) {
            val msg = messages[idx]
            if (msg != null) {
                val sender = nodes[idx]
                msg.to.receive(msg, round)
            }
        }
        round++
    }

    fun maxTreeNodes() =
        nodes.maxOfOrNull { it.receivedMessages.maxOfOrNull { it.msg.descriptor.countTreeNodes() } ?: 0 } ?: 0


    val allMessages get() = nodes.flatMap { it.receivedMessages }
    val totalMessageCount get() = allMessages.size
    val duplicateMessages get() = allMessages.filter { !it.isNew }
    val duplicateMessagesBeforeRecover get() = allMessages.filter { !it.isNew && !it.isRecovered }
    val duplicateMessageCount get() = allMessages.count { !it.isNew }
    val duplicateMessageBeforeRecoverCount get() = allMessages.count { !it.isNew && !it.isRecovered }
    val receivedNodeCount get() = nodes.count { it.isRecovered() }
    val activeNodeCount get() = nodes.count { it.isActive() }
    val totalChunksCount get() = nodes.sumOf { it.getChunksCount() }
    val targetTotalChunksCount = nodeCount * params.numberOfChunks
    val allReceived get() = receivedNodeCount == nodeCount

    fun run() {
        println("Heey!!!")

        while (!allReceived) {
            nextRound()
            val readyPercents = totalChunksCount * 100 / targetTotalChunksCount
            val relativeHop = round.toDouble() / numberOfChunks
            val relativeHopS = String.format("%.2f", relativeHop)
            println("$round:\t$relativeHopS\t$receivedNodeCount\t$activeNodeCount\t$readyPercents%\t$totalMessageCount\t$duplicateMessageCount\t$duplicateMessageBeforeRecoverCount")
        }

        println("Done!")
    }
}