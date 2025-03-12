package net.nashat.tests

import net.nashat.RandomNetworkGenerator
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RandomNetworkTest {

    @Test
    fun testReduceTargetPeerCount() {
        val rnd = Random(1)
        val network1 = RandomNetworkGenerator(1000, 10, rnd).generate()
        println("Network1: ${network1.minPeerConnections} - ${network1.maxPeerConnections}")

        val network2 = RandomNetworkGenerator.withReducedPeerCount(network1, 3, rnd)
        println("Network2: ${network2.minPeerConnections} - ${network2.maxPeerConnections}")

        val peerCountDistrib = network2.connections.map { it.value.size }.groupingBy { it }.eachCount()
        println(peerCountDistrib)

        assert(network2.minPeerConnections == 3)
        assert(peerCountDistrib[3]!! > 500)
    }

    @Test
    fun `check random network nodes has enough peers`() {
        val rnd= Random(1)
        for (peerCount in listOf(3, 4, 6, 10, 20)) {
            repeat(30) {
                val network = RandomNetworkGenerator(1000, peerCount, rnd).generate()
                assert(network.minPeerConnections >= peerCount - 1)
                assert(network.maxPeerConnections <= peerCount + 1)
            }
        }
    }

    @Test
    fun `check all-to-all is correctly generated`() {
        val network = RandomNetworkGenerator(1000, 999, Random(1)).generate()
        assert(network.minPeerConnections  == 999)
        assert(network.maxPeerConnections == 999)

    }
}