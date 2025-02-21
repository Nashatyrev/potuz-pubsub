package net.nashat

import org.jetbrains.kotlinx.dataframe.ColumnSelector
import org.jetbrains.kotlinx.dataframe.ColumnsSelector
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.aggregate
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.describe
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.getColumn
import org.jetbrains.kotlinx.dataframe.api.group
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.into
import org.jetbrains.kotlinx.dataframe.api.last
import org.jetbrains.kotlinx.dataframe.api.map
import org.jetbrains.kotlinx.dataframe.api.path
import org.jetbrains.kotlinx.dataframe.api.prev
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.replace
import org.jetbrains.kotlinx.dataframe.api.select
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.api.ungroup
import org.jetbrains.kotlinx.dataframe.api.unique
import org.jetbrains.kotlinx.dataframe.api.values
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.columns.ColumnReference
import org.jetbrains.kotlinx.dataframe.values

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

//fun DataFrame<*>.replaceErasureConfigWithEnum(): DataFrame<*> {
//    val df = cast<ResultEntry>()
//
//    return df.merge {
//        config.params.rsParams and config.params.rlncParams
//    }.by {
//        Erasure.fromColumns(
//            config.params.rsParams.extensionFactor,
//            config.params.rsParams.isDistinctMeshesPerChunk,
//            config.params.rlncParams.dummy
//        )
//    }
////        .into(pathOf("config", "params", "erasure")) // doesn't work for some reason
//        .into(df.getColumn { config.params.rlncParams }.path())
//        .rename { "config"()["params"]["rlncParams"] }.into("erasure")
//
//}
@DataSchema
data class SimConfig(
    val erasure: Erasure,
    val numberOfChunks: Int,
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

fun DataFrame<*>.normalizePotuzLoadedResults(): DataFrame<ResultEntry> {
    val df = this.cast<ResultEntry>()
    val col0 = df.getColumn(0).cast<PotuzSimulationConfig>()
    val colConfig = col0.map { SimConfig.fromPotuzSimulationConfig(it) }
    val colDf = colConfig.values.toDataFrame().group { all() }.into("config")
    val df3 = df.replace(col0).with(colDf.getColumn(0))
    return df3
}

inline fun <T, reified C> DataFrame<T>.expandDataColumnToColumnGroup(column: ColumnReference<C>): DataFrame<T> {
    return this.replace(column).with { col ->
        val colDf = col.values.toDataFrame()
        val colDfGrouped = colDf.group { all() }.into(col.name())
        val newCol = colDfGrouped.getColumn(0)
        newCol.cast<C>()
    }
}

fun <T> DataFrame<T>.removeNonChangingConfigColumns(configColumn: ColumnSelector<T, *> = { "config"() }): DataFrame<T> {
    val cfgDf = this.select(configColumn)
    val nonChangingConfigColumns = cfgDf
        .describe()
        .filter { unique == 1 }
        .values { path }
        .toList()
    return this.remove(*nonChangingConfigColumns.toTypedArray())
}

fun <T> DataFrame<T>.selectLastForEachGroup(groupBy: ColumnsSelector<T, *>): DataFrame<T> =
    this.groupBy(cols = groupBy).aggregate { last() }
        .select("aggregated")
        .ungroup { all() }

fun DataFrame<ResultEntry>.deriveExtraResults(): DataFrame<ResultEntryEx> =
    this.convert { result }.with { coreResult ->
        val df1 = coreResult
            .group { all() }.into("core")
            .cast<ResultEx>()
            .add("derived") {
                val numChunks = config.numberOfChunks
                val doneMessageCnt = core.totalMsgCnt - core.dupMsgCnt
                val roundMsgCnt = core.totalMsgCnt - (prev()?.core?.totalMsgCnt ?: 0)
                val roundDupMsgCnt = core.dupMsgCnt - (prev()?.core?.dupMsgCnt ?: 0)
                val roundDupBeforeReadyMsgCnt = core.dupBeforeDone - (prev()?.core?.dupBeforeDone ?: 0)
                val expectedDoneMessages = (config.nodeCount - 1) * numChunks
                ResultDerived(
                    relativeRound = index().toDouble() / numChunks,
                    doneMsgCnt = doneMessageCnt,
                    doneMsgFraction = doneMessageCnt.toDouble() / expectedDoneMessages,
                    roundMsgCnt = roundMsgCnt,
                    roundDoneMsgCnt = roundMsgCnt - roundDupMsgCnt,
                    roundDupAfterReadyMsgCnt = roundDupMsgCnt - roundDupBeforeReadyMsgCnt,
                    roundDupBeforeReadyMsgCnt = roundDupBeforeReadyMsgCnt
                )
            }
        val df2 = df1.expandDataColumnToColumnGroup(df1.getColumn("derived").cast<ResultDerived>())
        df2.cast<ResultEx>()
    }.cast<ResultEntryEx>()

