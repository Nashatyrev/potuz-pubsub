package net.nashat

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class RandomNetwork(
    val totalNodeCount: Int,
    val peerCount: Int,
    val rnd: Random,
    val stopAfterNoChangeCount: Int = 1000
) {

    var missCounter = 0

    fun generateAllToAll(): Map<Int, Set<Int>> {
        return (0 until totalNodeCount).map { n1 -> n1 to (0 until totalNodeCount).filter { n2 -> n1 != n2 }.toSet() }.toMap()
    }

    fun generateConnections(): Map<Int, Set<Int>> {
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
        return connections;
    }

    val connections = generateConnections()
    val allConnections = connections
        .flatMap { (key, value) ->
            value.map { key to it }
        }
    val allDistictConnections = allConnections
        .map { (key, value) -> min(key, value) to max(key, value) }
        .distinct()

    val minPeerConnections = connections.values.map { it.size }.min()
    val maxPeerConnections = connections.values.map { it.size }.max()

    companion object {
        fun createAllToAll(nodeCount: Int): RandomNetwork = RandomNetwork(nodeCount, nodeCount - 1, Random(0))
    }
}