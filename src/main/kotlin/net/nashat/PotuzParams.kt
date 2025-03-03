package net.nashat

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

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

@DataSchema
@Serializable
data class PotuzParams(
    val numberOfChunks: Int,
//    val erasureParams: ErasureParams,
    val rsParams: RSParams? = null,
    val rlncParams: RLNCParams? = null,
    val pPrime: String = PRIME_2_IN_8_PLUS_1,
    val chunkSelectionStrategy: ChunkSelectionStrategy = ChunkSelectionStrategy.Random,
    val messageBufferSize: Int = 1,
    val maxRoundReceiveMessageCnt: Int = 1,
    val latencyRounds: Int = 0
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
    val isDistinctMeshesPerChunk: Boolean
)

@DataSchema
@Serializable
data class RLNCParams(val dummy: Int = 0)
