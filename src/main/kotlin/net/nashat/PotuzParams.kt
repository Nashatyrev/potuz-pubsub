package net.nashat

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

@DataSchema
@Serializable
data class PotuzParams(
    val numberOfChunks: Int,
    val erasureParams: ErasureParams,
    val pPrime: String = PRIME_2_IN_8_PLUS_1
) {
    @Transient
    val maxMultiplier = try {
        pPrime.toLong() - 1
    } catch (e: Exception) {
        Int.MAX_VALUE.toLong()
    }
}
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
