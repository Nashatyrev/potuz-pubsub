package net.nashat

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class RandomNetwork(
    val connections: Map<Int, Set<Int>>
) {
    val allConnections = connections
        .flatMap { (key, value) ->
            value.map { key to it }
        }
    val allDistictConnections = allConnections
        .map { (key, value) -> min(key, value) to max(key, value) }
        .distinct()

    val nodes = allDistictConnections
        .flatMap { listOf(it.first, it.second) }
        .distinct()
        .sorted()

    val minPeerConnections = connections.values.map { it.size }.min()
    val maxPeerConnections = connections.values.map { it.size }.max()

    val peerCountDistribution by lazy {
        connections.values.map { it.size }.groupingBy { it }.eachCount()
    }

    init {
        require(nodes == (0 until nodes.size).toList()) {
            "Some nodes missing: size = ${nodes.size}"
        }
    }
}

class RandomNetworkGenerator(
    val totalNodeCount: Int,
    val peerCount: Int,
    val rnd: Random,
    val stopAfterNoChangeCount: Int = 1000
) {

    var missCounter = 0

    fun generate() = RandomNetwork(generateConnections())

    private fun generateAllToAll(): Map<Int, Set<Int>> {
        return (0 until totalNodeCount).map { n1 -> n1 to (0 until totalNodeCount).filter { n2 -> n1 != n2 }.toSet() }
            .toMap()
    }

    private fun generateConnections(): Map<Int, Set<Int>> {
        if (peerCount == totalNodeCount - 1) return generateAllToAll()

        val connections = (0 until totalNodeCount).map { it to mutableSetOf<Int>() }.toMap()
        val availableNodes = connections.keys.toMutableSet()
        var noChangeCounter = 0;
        while (availableNodes.size >= 2) {
            val node1 = availableNodes.random(rnd)
            val node2 = availableNodes.random(rnd)
            if (node1 == node2) continue

            val connections1 = connections[node1]!!
            val connections2 = connections[node2]!!
            val changed1 = connections1.add(node2)
            if (connections1.size >= peerCount) {
                availableNodes -= node1
            }
            val changed2 = connections2.add(node1)
            if (connections2.size >= peerCount) {
                availableNodes -= node2
            }

            if (!changed1 && !changed2) {
                if (noChangeCounter >= stopAfterNoChangeCount) {
                    break
                }
                noChangeCounter++
                missCounter++
            } else {
                noChangeCounter = 0
            }
        }

        availableNodes
            .forEach { node ->
                while (connections[node]!!.size < peerCount - 1) {
                    val candidate = connections.keys.random(rnd)
                    if (node == candidate)
                        continue
                    val candidateConnections = connections[candidate]!!
                    if (candidateConnections.size > peerCount)
                        continue
                    if (node in candidateConnections)
                        continue

                    connections[node]!! += candidate
                    candidateConnections += node
                }
            }

        return connections;
    }

    companion object {
        fun createAllToAll(nodeCount: Int): RandomNetwork =
            RandomNetworkGenerator(nodeCount, nodeCount - 1, Random(0)).generate()

        fun withReducedPeerCount(network: RandomNetwork, newMinPeerCount: Int, rnd: Random): RandomNetwork {
            require(network.minPeerConnections > newMinPeerCount)
            val newConnections = network.connections
                .mapValues { (_, connections) -> connections.toMutableSet() }
                .toMutableMap()
            val reduceCandidates = network.nodes.toMutableSet()
            while (reduceCandidates.isNotEmpty()) {
                val nodeCandidate = reduceCandidates.random(rnd)

                if (newConnections[nodeCandidate]!!.size == newMinPeerCount) {
                    reduceCandidates -= nodeCandidate
                    continue
                }

                val peeCandidates = newConnections[nodeCandidate]!!
                    .filter { peerCandidate -> newConnections[peerCandidate]!!.size > newMinPeerCount }

                if (peeCandidates.isEmpty()) {
                    reduceCandidates -= nodeCandidate
                    continue
                }

                val peerCandidate = peeCandidates.random(rnd)

                newConnections[nodeCandidate]!!.remove(peerCandidate)
                newConnections[peerCandidate]!!.remove(nodeCandidate)
            }

            return RandomNetwork(newConnections)
        }
    }
}
