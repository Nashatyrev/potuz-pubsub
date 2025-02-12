package net.nashat

import kotlin.math.min
import kotlin.random.Random

abstract class AbstractNode(
    val index: Int,
    val rnd: Random,
    val params: PotuzParams,
) {

    data class ReceivedMessage(
        val msg: PotuzMessage,
        val hop: Int,
        val isNew: Boolean,
        val isRecovered: Boolean,
    )

    var currentMartix = CoefMatrix.EMPTY
    var coefDescriptors = mutableListOf<CoefVectorDescriptor>()
    val receivedMessages = mutableListOf<ReceivedMessage>()
    val seenVectorsByPeer = mutableMapOf<AbstractNode, CoefMatrix>()

    abstract fun makePublisher();
    abstract fun handleRecovered();
    abstract fun generateNewMessage(peers: Collection<AbstractNode>): PotuzMessage?

    fun getChunksCount() = min(currentMartix.coefVectors.size, params.numberOfChunks)
    fun isRecovered() = getChunksCount() >= params.numberOfChunks
    fun isActive() = getChunksCount() > 0

    protected fun addSeenVectorForPeer(peer: AbstractNode, vec: CoefVector) {
        seenVectorsByPeer.compute(peer) { _, matrix ->
            if (matrix == null)
                CoefMatrix.fromVectors(listOf(vec))
            else
                matrix + vec
        }
    }

    fun doNothing() {}

    fun receive(msg: PotuzMessage, hop: Int) {
        val isNew: Boolean

        if (!isRecovered()) {
            val newMatrix = currentMartix + msg.coefs
            isNew = newMatrix.rank > currentMartix.rank
            if (isNew) {
                currentMartix = newMatrix
                coefDescriptors += msg.descriptor
            } else {
                // place for breakpoint here
                doNothing()
            }
            if (isRecovered()) {
                handleRecovered()
            }
        } else {
            isNew = false
        }
        receivedMessages += ReceivedMessage(msg, hop, isNew, isRecovered())
        addSeenVectorForPeer(msg.from, msg.coefs)
    }

    override fun toString(): String {
        return "$index"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AbstractNode
        return index == other.index
    }

    override fun hashCode(): Int {
        return index
    }
}
