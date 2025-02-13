package net.nashat

import jdk.internal.foreign.abi.Binding.Dup
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

@DataSchema
@Serializable
data class PotuzParams(
    val numberOfChunks: Int,
//    val erasureParams: ErasureParams,
    val rsParams: RSParams? = null,
    val rlncParams: RLNCParams? = null,
    val pPrime: String = PRIME_2_IN_8_PLUS_1
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

@DataSchema
@Serializable
sealed interface ErasureParams {

    @DataSchema
    @Serializable
    data class RS(
        val extensionFactor: Int,
        val isDistinctMeshesPerChunk: Boolean
    ) : ErasureParams

    @DataSchema
    @Serializable
    class RLNC : ErasureParams
}
