package net.nashat

import org.junit.jupiter.api.Test
import kotlin.random.Random

class MiscTests {

    @Test
    fun testQueueToSequence() {
        val inboundMessageBuffer = ArrayDeque(listOf(1, 1, 3, 7, 9))
        val polled = generateSequence { inboundMessageBuffer.firstOrNull() }
            .takeWhile { it < 9 }
            .take(30)
            .map { inboundMessageBuffer.removeFirst() }
            .toList()

        println("Polled: $polled, queue: $inboundMessageBuffer")

    }

    @Test
    fun testDistribution() {
        val rnd = Random(0)
        val nodeCount = 1000
        val nodeInboundBufferCnt = Array<Int>(nodeCount) { 0 }

        for (round in 0 until 100) {
            for (i in 0 until nodeCount) {
                val receiver = rnd.nextInt(nodeCount)
                nodeInboundBufferCnt[receiver]++
            }

            println("Round $round, receives count: ${nodeInboundBufferCnt.count { it > 0 }}")

            for (i in 0 until nodeCount) {
                val cnt = nodeInboundBufferCnt[i]
                nodeInboundBufferCnt[i] = if (cnt == 0) 0 else cnt - 1
            }
        }
    }
}