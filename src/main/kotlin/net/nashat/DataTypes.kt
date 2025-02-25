package net.nashat

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.DataRow
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

@Serializable
@DataSchema
data class PotuzSimulationConfig(
    val params: PotuzParams,
    val nodeCount: Int = 1000,
    val peerCount: Int,
    val isGodStopMode: Boolean,
    val randomSeed: Long = 0,
)

@DataSchema
data class CoreResult(
    val doneNodeCnt: Int,
    val activeNodeCnt: Int,
    val totalMsgCnt: Int,
    val dupMsgCnt: Int,
    val dupBeforeDone: Int,
    val dupOneConn: Int,
    val chunkDistribution: List<Int>
)

@DataSchema
data class ResultDerived(
    val relativeRound: Double,
    val doneMsgCnt: Int,
    val doneMsgFraction: Double,
    val roundMsgCnt: Int,
    val roundDoneMsgCnt: Int,
    val roundDupAfterReadyMsgCnt: Int,
    val roundDupBeforeReadyMsgCnt: Int,
)

enum class Erasure(val isDistinctMeshes: Boolean, val extensionFactor: Int) {
    NoErasure(true, 1),
    RsX2(true, 2),
    RsX3(true, 3),
    RLNC(true, 10000),
    NoErasureOneMesh(false,1),
    RsX2OneMesh(false, 2),
    RsX3OneMesh(false, 3);

    companion object {
        fun fromColumns(rsExtensionFactor: Int?, rsIsDistinctMeshes: Boolean?, rlncDummy: Any?): Erasure =
            when {
                rlncDummy != null -> Erasure.RLNC
                rsExtensionFactor!! == 1 && rsIsDistinctMeshes!! -> Erasure.NoErasure
                rsExtensionFactor == 1 && !(rsIsDistinctMeshes!!) -> Erasure.NoErasureOneMesh
                rsExtensionFactor == 2 && rsIsDistinctMeshes!! -> Erasure.RsX2
                rsExtensionFactor == 2 && !(rsIsDistinctMeshes!!) -> Erasure.RsX2OneMesh
                rsExtensionFactor == 3 && rsIsDistinctMeshes!! -> Erasure.RsX3
                rsExtensionFactor == 3 && !(rsIsDistinctMeshes!!) -> Erasure.RsX3OneMesh
                else -> throw IllegalArgumentException("Invalid column configuration")
            }
    }
}

@DataSchema
data class RawConfigResultEntry(
    val config: PotuzSimulationConfig,
    val result: DataFrame<CoreResult>
) {
    companion object {
        fun createRawResultDataFrame(
            configs: Collection<PotuzSimulationConfig>,
            results: Collection<DataFrame<CoreResult>>
        ): DataFrame<RawConfigResultEntry> {
            require(configs.size == results.size)
            return configs.zip(results) { config, resultFrame ->
                RawConfigResultEntry(config, resultFrame)
            }.toDataFrame()
        }
    }
}

@DataSchema
data class SimConfig(
    val erasure: Erasure,
    val numberOfChunks: Int,
    val chunkSelectionStrategy: ChunkSelectionStrategy,
    val nodeCount: Int = 1000,
    val peerCount: Int,
    val isGodStopMode: Boolean,
    val randomSeed: Long = 0,
) {
    companion object {
        fun fromPotuzSimulationConfig(cfg: PotuzSimulationConfig) =
            SimConfig(
                Erasure.fromColumns(cfg.params.rsParams?.extensionFactor, cfg.params.rsParams?.isDistinctMeshesPerChunk, cfg.params.rlncParams?.dummy),
                cfg.params.numberOfChunks,
                cfg.params.chunkSelectionStrategy,
                cfg.nodeCount,
                cfg.peerCount,
                cfg.isGodStopMode,
                cfg.randomSeed
            )
        fun fromPotuzSimulationConfigRow(cfg: DataRow<PotuzSimulationConfig>) =
            SimConfig(
                Erasure.fromColumns(cfg.params.rsParams.extensionFactor, cfg.params.rsParams.isDistinctMeshesPerChunk, cfg.params.rlncParams.dummy),
                cfg.params.numberOfChunks,
                cfg.params.chunkSelectionStrategy,
                cfg.nodeCount,
                cfg.peerCount,
                cfg.isGodStopMode,
                cfg.randomSeed
            )
    }
}

@DataSchema
data class ResultEntry(
    val config: SimConfig,
    val result: DataFrame<CoreResult>
)

@DataSchema
data class ResultEx(
    val core: CoreResult,
    val derived: ResultDerived
)

@DataSchema
data class ResultEntryEx(
    val config: SimConfig,
    val result: DataFrame<ResultEx>
)

@DataSchema
data class ResultEntryExploded(
    val config: SimConfig,
    val result: ResultEx
)

