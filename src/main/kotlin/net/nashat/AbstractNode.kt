package net.nashat

import kotlin.math.min
import kotlin.random.Random

abstract class AbstractNode(
    val index: Int,
    val rnd: Random,
    val config: PotuzSimulationConfig,
) {

    data class ReceivedMessage(
        val msg: PotuzMessage,
        val sentRound: Int,
        val receiveRound: Int,
        val isNew: Boolean,
        val isRecovered: Boolean,
    )

    data class SentMessage(
        val msg: PotuzMessage,
        val sentRound: Int,
    )

    data class BufferedMessage(
        val msg: PotuzMessage,
        val sentRound: Int,
    )

    data class CoefsAndDescriptor(val coefs: CoefVector, val descriptor: CoefVectorDescriptor)

    var currentMartix = CoefMatrix.EMPTY
    var coefDescriptors = mutableListOf<CoefVectorDescriptor>()
    val coefsAndDescriptors get() = coefDescriptors.map { CoefsAndDescriptor(it.coefs, it) }
    val receivedMessages = mutableListOf<ReceivedMessage>()
    val receivedMessagesByPeer =
        mutableMapOf<AbstractNode, MutableList<ReceivedMessage>>()
    val sentMessages = mutableListOf<SentMessage>()
    val sentMessagesByPeer =
        mutableMapOf<AbstractNode, MutableList<SentMessage>>()
    val seenVectorsByPeer = mutableMapOf<AbstractNode, CoefMatrix>()
    val inboundMessageBuffer = ArrayDeque<BufferedMessage>()

    protected val simConfig get() = config.simConfig


    abstract fun makePublisher();
    abstract fun handleRecovered();

    fun getChunksCount() = min(currentMartix.coefVectors.size, simConfig.numberOfChunks)
    fun isRecovered() = getChunksCount() >= simConfig.numberOfChunks
    fun isActive() = getChunksCount() > 0
    fun isBufferFull() = inboundMessageBuffer.size == config.messageBufferSize
    fun isCongested() = inboundMessageBuffer.size >= config.messageBufferSize
    fun getOriginalVectorIds() =
        coefDescriptors
            .map { it.originalVectorIds }
            .reduceOrNull { acc, ints -> acc + ints }
            ?: emptySet()

    protected fun addSeenVectorForPeer(peer: AbstractNode, vec: CoefVector) {
        seenVectorsByPeer.compute(peer) { _, matrix ->
            if (matrix == null)
                CoefMatrix.fromVectors(listOf(vec))
            else
                matrix + vec
        }
    }

    protected fun recordReceivedMessage(receivedMsg: ReceivedMessage) {
        receivedMessages += receivedMsg
        receivedMessagesByPeer.computeIfAbsent(receivedMsg.msg.from) { mutableListOf() } += receivedMsg
        addSeenVectorForPeer(receivedMsg.msg.from, receivedMsg.msg.coefs)
    }

    protected fun recordSentMessage(sentMsg: SentMessage) {
        sentMessages += sentMsg
        sentMessagesByPeer.computeIfAbsent(sentMsg.msg.to) { mutableListOf() } += sentMsg
    }

    fun doNothing() {}

    fun bufferInboundMessage(msg: PotuzMessage, currentHop: Int) {
        require(!isBufferFull())
        inboundMessageBuffer += BufferedMessage(msg, currentHop)
    }

    fun processBufferedMessages(currentRound: Int) {
        generateSequence { inboundMessageBuffer.firstOrNull() }
            .takeWhile { currentRound - it.sentRound >= simConfig.latencyRounds }
            .take(config.maxRoundReceiveMessageCnt)
            .map { inboundMessageBuffer.removeFirst() }
            .forEach {
                receive(it, currentRound)
            }
    }

    fun generateNewMessage(peers: Collection<AbstractNode>, currentRound: Int): PotuzMessage? {
        return generateNewMessageImpl(peers)?.also {
            recordSentMessage(SentMessage(it, currentRound))
        }
    }

    protected abstract fun generateNewMessageImpl(peers: Collection<AbstractNode>): PotuzMessage?

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
        recordReceivedMessage(
            ReceivedMessage(
                msg = bufMsg.msg,
                sentRound = bufMsg.sentRound,
                receiveRound = currentHop,
                isNew = isNew,
                isRecovered = isRecovered()
            )
        )
    }

    protected fun List<AbstractNode>.prioritizeReceiveCandidates() =
        prioritizeReceivePeerCandidates(this)

    private fun prioritizeReceivePeerCandidates(peers: List<AbstractNode>): List<AbstractNode> {
        return when (simConfig.peerSelectionStrategy) {
            PeerSelectionStrategy.Random -> peers
            PeerSelectionStrategy.LessOutboundThenInboundTraffic -> {
                peers.sortedWith(
                    compareBy<AbstractNode> { sentMessagesByPeer[it]?.size ?: 0 }
                        .thenBy { receivedMessagesByPeer[it]?.size ?: 0 }
                )
            }
        }
    }

    override fun toString() = "$index"
    override fun equals(other: Any?) = index == (other as? AbstractNode)?.index
    override fun hashCode() = index
}
