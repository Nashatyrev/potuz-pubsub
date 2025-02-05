package net.nashat

import kotlin.random.Random

fun interface ReceiveNodeSelector {
    fun selectReceivingNode(msg: PotuzMessage, sender: PotuzNode): PotuzNode?
}

fun interface ReceiveNodeSelectorStrategy {
    fun createNodeSelector(): ReceiveNodeSelector

    companion object {
        fun createRandom(allNodes: List<PotuzNode>, rnd: Random): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                ReceiveNodeSelector { msg, sender ->
                    (allNodes - sender).random(rnd)
                }
            }

        fun createRandomSingleReceiveMessage(allNodes: List<PotuzNode>, rnd: Random): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                val receivingNodes = mutableSetOf<PotuzNode>()
                ReceiveNodeSelector { msg, sender ->
                    (allNodes - sender - receivingNodes).randomOrNull(rnd)?.also {
                        receivingNodes += it
                    }
                }
            }

        fun createRandomNotReady(allNodes: List<PotuzNode>, rnd: Random): ReceiveNodeSelectorStrategy =
            ReceiveNodeSelectorStrategy {
                val receivingNodes = mutableSetOf<PotuzNode>()
                ReceiveNodeSelector { msg, sender ->
                    val nonReadyNodes = allNodes.filter { !it.isRecovered() }
                    (nonReadyNodes - sender - receivingNodes).randomOrNull(rnd)?.also {
                        receivingNodes += it
                    }
                }
            }
    }
}


