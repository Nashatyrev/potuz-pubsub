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
                    override fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode> =
                        allNodes - sender

                    override fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode) {}
                }
            }

        fun createRandomLimitedReceiveMessage(
            allNodes: List<AbstractNode>,
            bufMsgLimit: Int
        ): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                val receivingNodesMsgCount = mutableMapOf<AbstractNode, Int>()
                object : ReceiveNodeSelector {
                    override fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode> =
                        (allNodes - sender)
                            .filter { it.inboundMessageBuffer.size + (receivingNodesMsgCount[it] ?: 0) < bufMsgLimit }

                    override fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode) {
                        receivingNodesMsgCount.compute(receiver) { _, curVal -> (curVal ?: 0) + 1 }
                    }
                }
            }

        fun createNetworkLimitedReceiveMessage(
            allNodes: List<AbstractNode>,
            network: RandomNetwork,
            bufMsgLimit: Int
        ): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                val receivingNodesMsgCount = mutableMapOf<AbstractNode, Int>()
                val nodeToIndex = allNodes.indices.associateBy { allNodes[it] }
                fun nodePeers(node: AbstractNode) = network.connections[nodeToIndex[node]]!!.map { allNodes[it] }
                object : ReceiveNodeSelector {
                    override fun selectReceivingNodeCandidates(sender: AbstractNode): List<AbstractNode> =
                        nodePeers(sender)
                            .filter { it.inboundMessageBuffer.size + (receivingNodesMsgCount[it] ?: 0) < bufMsgLimit }

                    override fun onReceiverSelected(receiver: AbstractNode, sender: AbstractNode) {
                        receivingNodesMsgCount.compute(receiver) { _, curVal -> (curVal ?: 0) + 1 }
                    }
                }
            }
    }
}


