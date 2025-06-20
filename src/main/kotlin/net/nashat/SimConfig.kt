package net.nashat

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

enum class Erasure(val extensionFactor: Int) {
    NoErasure(1),
    RsX2(2),
    RsX3(3),
    RLNC(10000)
}

@DataSchema
data class SimConfig(
    val nodeCount: Int = 1000,
    val peerCount: Int = -1,
    val numberOfChunks: Int = -1,
    val latencyRounds: Int = 0,
    val erasure: Erasure,
    val rsIsDistinctMeshes: Boolean = true,
    val rsChunkSelectionStrategy: ChunkSelectionStrategy = ChunkSelectionStrategy.PreferLater,
    val rsMeshStrategy: MeshStrategy = MeshStrategy.Static,
    val peerSelectionStrategy: PeerSelectionStrategy = PeerSelectionStrategy.LessOutboundThenInboundTraffic,
    val filterByMaxCoefficient: String? = null, // = PRIME_RISTRETTO,
    val limitMaxHops: Int? = null,
    val randomSeed: Long = 0,
) {

    fun withOptimalMeshStrategy() = this.copy(
        rsMeshStrategy =
        if (erasure == Erasure.NoErasure) MeshStrategy.TwoPhaseMesh
        else MeshStrategy.Static
    )
}

data class PotuzSimulationConfig(
    val simConfig: SimConfig,

    val isGodStopMode: Boolean = true,
    val messageBufferSize: Int = UNLIMITED_RECEIVE_BUFFER,
    val maxRoundReceiveMessageCnt: Int = 1,
    val pPrime: String = PRIME_2_IN_8_PLUS_1,
    val maxMultiplier: Long = pPrimeToMaxMultiplier(pPrime),
    val withChunkDistribution: Boolean = false
) {
    companion object {
        private fun pPrimeToMaxMultiplier(pPrime: String) = try {
            pPrime.toLong() - 1
        } catch (e: Exception) {
            Int.MAX_VALUE.toLong()
        }
    }
}
