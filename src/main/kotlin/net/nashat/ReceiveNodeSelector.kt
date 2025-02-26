package net.nashat

import kotlin.random.Random

interface ReceiveNodeSelector {

    fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode>

    fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode)
}

fun interface ReceiveNodeSelectorStrategy {
    fun createNodeSelector(): ReceiveNodeSelector

    companion object {
        fun createRandom(allNodes: List<AbstractNode>, rnd: Random): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                object : ReceiveNodeSelector {
                    override fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode> = allNodes - sender
                    override fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode) {}
                }
            }

        fun createRandomSingleReceiveMessage(allNodes: List<AbstractNode>): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                object : ReceiveNodeSelector {
                    override fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode> = (allNodes - sender).filter { !it.isBufferFull() }
                    override fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode) {}
                }
            }

        fun createNetworkSingleReceiveMessage(allNodes: List<AbstractNode>, network: RandomNetwork): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                val nodeToIndex = allNodes.indices.associateBy { allNodes[it] }
                fun nodePeers(node: AbstractNode) = network.connections[nodeToIndex[node]]!!.map { allNodes[it] }
                object : ReceiveNodeSelector {
                    override fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode> =
                        nodePeers(sender).filter { !it.isBufferFull() }
                    override fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode) {}
                }
            }
    }
}


