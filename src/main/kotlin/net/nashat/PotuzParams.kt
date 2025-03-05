package net.nashat

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

const val UNLIMITED_RECEIVE_BUFFER = 1_000_000

/**
 * The strategy to select a chunk from existing to propagate
 * Applicable to either No Erasure or RS erasure (not applicable to RLNC)
 */
enum class ChunkSelectionStrategy {
    /**
     * Just select a random chunk from existing
     */
    Random,

    /**
     * Always prefer chunks with larger indexes
     */
    PreferLater,

    /**
     * Select the less seen chunk locally, then by the largest chunk index
     */
    PreferRarest,

    /**
     * Selects the chunk with larger index, when the last chunk appears selects randomly
     */
    PreferLaterThenRandom,

    /**
     * Selects the chunk with larger index, when the last chunk appears switch to PreferRarest
     */
    PreferLaterThenRarest
}

enum class PeerSelectionStrategy {

    Random,

    LessOutboundThenInboundTraffic
}

enum class MeshStrategy {

    /**
     * Regular mesh which remains static for the whole simulation
     */
    Static,

    /**
     * Increased mesh in the beginning, reduced mesh when around 50% chunks are disseminated
     */
    TwoPhaseMesh
}

@DataSchema
@Serializable
data class PotuzParams(
    val numberOfChunks: Int,
    val rsParams: RSParams? = null,
    val rlncParams: RLNCParams? = null,
    val pPrime: String = PRIME_2_IN_8_PLUS_1,
    val messageBufferSize: Int = UNLIMITED_RECEIVE_BUFFER,
    val maxRoundReceiveMessageCnt: Int = 1,
    val latencyRounds: Int = 0,
    val peerSelectionStrategy: PeerSelectionStrategy = PeerSelectionStrategy.LessOutboundThenInboundTraffic,
) {
    @Transient
    val maxMultiplier = try {
        pPrime.toLong() - 1
    } catch (e: Exception) {
        Int.MAX_VALUE.toLong()
    }

    companion object {
        fun create(numberOfChunks: Int, erasureParams: Any): PotuzParams {
            when (erasureParams) {
                is RSParams -> return PotuzParams(numberOfChunks, rsParams = erasureParams)
                is RLNCParams -> return PotuzParams(numberOfChunks, rlncParams = erasureParams)
                else -> throw IllegalArgumentException()
            }
        }
    }
}

@DataSchema
@Serializable
data class RSParams(
    val extensionFactor: Int,
    val isDistinctMeshesPerChunk: Boolean = true,
    val chunkSelectionStrategy: ChunkSelectionStrategy = ChunkSelectionStrategy.PreferLater,
    val meshStrategy: MeshStrategy = MeshStrategy.Static
)

@DataSchema
@Serializable
data class RLNCParams(val dummy: Int = 0)
