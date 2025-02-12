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

        fun createRandomSingleReceiveMessage(allNodes: List<AbstractNode>, rnd: Random): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                val receivingNodes = mutableSetOf<AbstractNode>()
                object : ReceiveNodeSelector {
                    override fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode> = allNodes - sender - receivingNodes
                    override fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode) {
                        receivingNodes += receiver
                    }
                }
            }

        fun createNetworkSingleReceiveMessage(allNodes: List<AbstractNode>, network: RandomNetwork): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                val nodeToIndex = allNodes.indices.associateBy { allNodes[it] }
                val receivingNodes = mutableSetOf<AbstractNode>()
                fun nodePeers(node: AbstractNode) = network.connections[nodeToIndex[node]]!!.map { allNodes[it] }
                object : ReceiveNodeSelector {
                    override fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode> =
                        nodePeers(sender) - receivingNodes
                    override fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode) {
                        receivingNodes += receiver
                    }
                }
            }
    }
}


