package net.nashat

data class PotuzParams(
    val numberOfChunks: Int,
    val isPartialExtensionSupported: Boolean,
    val maxInitialCoefficient: Long,
    val maxMultiplicator: Long
)