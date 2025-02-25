package net.nashat

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

enum class ChunkSelectionStrategy {
    Random, PreferLater, PreferRarest
}

@DataSchema
@Serializable
data class PotuzParams(
    val numberOfChunks: Int,
//    val erasureParams: ErasureParams,
    val rsParams: RSParams? = null,
    val rlncParams: RLNCParams? = null,
    val pPrime: String = PRIME_2_IN_8_PLUS_1,
    val chunkSelectionStrategy: ChunkSelectionStrategy = ChunkSelectionStrategy.Random
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
