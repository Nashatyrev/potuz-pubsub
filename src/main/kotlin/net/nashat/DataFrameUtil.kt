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

class DataFrameUtil {
    companion object {
    }
}

@DataSchema
data class ResultDerived(
    val relativeRound: Double,
    val doneMsgCnt: Int,
    val doneMsgFraction: Double,
    val roundMsgCnt: Int,
    val roundDoneMsgCnt: Int,
    val roundDupMsgCnt: Int,
)

@DataSchema
data class ResultEntry(
    val config: PotuzSimulationConfig,
    val result: DataFrame<CoreResult>
)

@DataSchema
data class ResultEx(
    val core: CoreResult,
    val derived: ResultDerived
)

@DataSchema
data class ResultEntryEx(
    val config: PotuzSimulationConfig,
    val result: DataFrame<ResultEx>
)

@DataSchema
data class ResultEntryExploded(
    val config: PotuzSimulationConfig,
    val result: ResultEx
)

fun DataFrame<*>.normalizePotuzLoadedResults(): DataFrame<ResultEntry> {
    val df = this.cast<ResultEntry>()
    val col0 = df.getColumn(0).cast<PotuzSimulationConfig>()
    val df3 = df.expandDataColumnToColumnGroup(col0)
    return df3
}

//inline fun <T, reified C> DataFrame<T>.expandDataColumnToColumnGroup(noinline columns: ColumnSelector<T, C>): DataFrame<T> {
//    return expandDataColumnToColumnGroup(this.getColumn(columns))
//}

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
                val numChunks = config.params.numberOfChunks
                val doneMessageCnt = core.totalMsgCnt - core.dupMsgCnt
                val roundMsgCnt = core.totalMsgCnt - (prev()?.core?.totalMsgCnt ?: 0)
                val roundDupMsgCnt = core.dupMsgCnt - (prev()?.core?.dupMsgCnt ?: 0)
                val expectedDoneMessages = (config.nodeCount - 1) * numChunks
                ResultDerived(
                    relativeRound = index().toDouble() / numChunks,
                    doneMsgCnt = doneMessageCnt,
                    doneMsgFraction = doneMessageCnt.toDouble() / expectedDoneMessages,
                    roundMsgCnt = roundMsgCnt,
                    roundDoneMsgCnt = roundMsgCnt - roundDupMsgCnt,
                    roundDupMsgCnt = roundDupMsgCnt
                )
            }
        val df2 = df1.expandDataColumnToColumnGroup(df1.getColumn("derived").cast<ResultDerived>())
        df2.cast<ResultEx>()
    }.cast<ResultEntryEx>()

