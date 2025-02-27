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
        val sentRound: Int,
        val receiveRound: Int,
        val isNew: Boolean,
        val isRecovered: Boolean,
    )

    data class BufferedMessage(
        val msg: PotuzMessage,
        val sentRound: Int,
    )

    var currentMartix = CoefMatrix.EMPTY
    var coefDescriptors = mutableListOf<CoefVectorDescriptor>()
    val receivedMessages = mutableListOf<ReceivedMessage>()
    val seenVectorsByPeer = mutableMapOf<AbstractNode, CoefMatrix>()
    val inboundMessageBuffer = ArrayDeque<BufferedMessage>()

    abstract fun makePublisher();
    abstract fun handleRecovered();
    abstract fun generateNewMessage(peers: Collection<AbstractNode>): PotuzMessage?

    fun getChunksCount() = min(currentMartix.coefVectors.size, params.numberOfChunks)
    fun isRecovered() = getChunksCount() >= params.numberOfChunks
    fun isActive() = getChunksCount() > 0
    fun isBufferFull() = inboundMessageBuffer.size == params.messageBufferSize
    fun isCongested() = inboundMessageBuffer.size >= params.messageBufferSize - 1

    protected fun addSeenVectorForPeer(peer: AbstractNode, vec: CoefVector) {
        seenVectorsByPeer.compute(peer) { _, matrix ->
            if (matrix == null)
                CoefMatrix.fromVectors(listOf(vec))
            else
                matrix + vec
        }
    }

    fun doNothing() {}

    fun bufferInboundMessage(msg: PotuzMessage, currentHop: Int) {
        require(!isBufferFull())
        inboundMessageBuffer += BufferedMessage(msg, currentHop)
    }

    fun processSingleBufferedMessage(currentHop: Int) {
        inboundMessageBuffer.removeFirstOrNull()
            ?.also { receive(it, currentHop) }
    }

    protected open fun receive(bufMsg: BufferedMessage, currentHop: Int) {
        val isNew: Boolean

        if (!isRecovered()) {
            if (bufMsg.msg.coefs !in currentMartix.coefVectors) {
                val newMatrix = currentMartix + bufMsg.msg.coefs
                isNew = newMatrix.rank > currentMartix.rank
                if (isNew) {
                    currentMartix = newMatrix
                    coefDescriptors += bufMsg.msg.descriptor

                    if (isRecovered()) {
                        handleRecovered()
                    }
                }
            } else {
                isNew = false
            }
        } else {
            isNew = false
        }
        receivedMessages += ReceivedMessage(bufMsg.msg, bufMsg.sentRound, currentHop, isNew, isRecovered())
        addSeenVectorForPeer(bufMsg.msg.from, bufMsg.msg.coefs)
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
