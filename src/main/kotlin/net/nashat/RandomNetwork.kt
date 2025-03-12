package net.nashat

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

import org.jgrapht.graph.*
import org.jgrapht.*
import org.jgrapht.generate.*
import org.jgrapht.graph.builder.*
import java.util.concurrent.atomic.AtomicInteger

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

    fun generate() = RandomNetwork(
//        generateConnections()
//        generateUndirectedGraph(totalNodeCount, peerCount)
        if (peerCount == totalNodeCount - 1) generateAllToAll()
        else generateUndirectedGraphJGraphT(totalNodeCount, peerCount)
    )

    private fun generateAllToAll(): Map<Int, Set<Int>> {
        return (0 until totalNodeCount).map { n1 -> n1 to (0 until totalNodeCount).filter { n2 -> n1 != n2 }.toSet() }
            .toMap()
    }

    private fun generateConnections(): Map<Int, Set<Int>> {
        if (peerCount == totalNodeCount - 1) return generateAllToAll()

        val connections = (0 until totalNodeCount).map { it to mutableSetOf<Int>() }.toMap()
        val availableNodes = connections.keys.toMutableSet()
        var availableNodesAsList = availableNodes.toList()
        var noChangeCounter = 0;
        while (availableNodes.size >= 2) {
            val node1 = availableNodesAsList.random(rnd)
            val node2 = availableNodesAsList.random(rnd)
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
            availableNodesAsList = availableNodes.toList()
        }

        val allNodes = connections.keys.toList()
        availableNodes
            .forEach { node ->
                while (connections[node]!!.size < peerCount - 1) {
                    val candidate = allNodes.random(rnd)
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

        /**
         * Generates an undirected graph using the stub matching (configuration model) approach.
         *
         * Each node (represented by an Int from 0 until nodeCount) will have exactly [degree] neighbors.
         *
         * @param nodeCount The total number of nodes in the graph.
         * @param degree The desired degree (number of connections) per node.
         * @return A map where each key is a node and the value is a set of neighboring nodes.
         */
        fun generateUndirectedGraph(nodeCount: Int, degree: Int): Map<Int, Set<Int>> {
            require(degree < nodeCount) { "Degree must be less than the number of nodes (no self-loops allowed)." }
            require(nodeCount * degree % 2 == 0) { "The total degree (nodeCount * degree) must be even." }

            // Create a list of stubs. Each node appears 'degree' times.
            val stubs = mutableListOf<Int>()
            for (node in 0 until nodeCount) {
                repeat(degree) { stubs.add(node) }
            }

            val random = Random(System.currentTimeMillis())

            while (true) {
                stubs.shuffle(random)
                val graph = (0 until nodeCount).associateWith { mutableSetOf<Int>() }.toMutableMap()
                var valid = true

                // Pair stubs consecutively to form edges.
                for (i in stubs.indices step 2) {
                    val u = stubs[i]
                    val v = stubs[i + 1]

                    // Check for self-loops or duplicate edges.
                    if (u == v || graph[u]!!.contains(v)) {
                        valid = false
                        break
                    }
                    // Add the edge in both directions.
                    graph[u]!!.add(v)
                    graph[v]!!.add(u)
                }

                if (valid) {
                    return graph.mapValues { it.value.toSet() }
                }
                // If the pairing wasn't valid, try again.
            }
        }

        /**
         * Generates an undirected regular graph using JGraphT and returns the connections as a map.
         *
         * @param nodeCount Total number of nodes in the graph.
         * @param degree The degree for each node (number of connections).
         * @return A map where each key is a node (Int) and the value is a set of neighboring nodes.
         */
        fun generateUndirectedGraphJGraphT(nodeCount: Int, degree: Int): Map<Int, Set<Int>> {
            require(degree < nodeCount) { "Degree must be less than the number of nodes to avoid self-loops." }
            require(nodeCount * degree % 2 == 0) { "The product of nodeCount and degree must be even." }

            val vertexCounter = AtomicInteger(0)
            val graph: Graph<Int, DefaultEdge> = GraphTypeBuilder
                .undirected<Int, DefaultEdge>()
                .vertexSupplier { vertexCounter.getAndIncrement() }
                .edgeClass(DefaultEdge::class.java)
                .buildGraph()

            // Create an empty simple undirected graph.
//            val graph: Graph<Int, DefaultEdge> = SimpleGraph(DefaultEdge::class.java)

            // Add vertices to the graph.
//            for (node in 0 until nodeCount) {
//                graph.addVertex(node)
//            }

            // Create and use the random regular graph generator.
            val generator = RandomRegularGraphGenerator<Int, DefaultEdge>(nodeCount, degree)
            generator.generateGraph(graph)

            // Convert the graph to a Map<Int, Set<Int>> representation.
            return graph.vertexSet().associateWith { vertex ->
                graph.edgesOf(vertex).map { edge ->
                    // For an undirected edge, determine the opposite vertex.
                    if (graph.getEdgeSource(edge) == vertex)
                        graph.getEdgeTarget(edge)
                    else
                        graph.getEdgeSource(edge)
                }.toSet()
            }
        }


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
