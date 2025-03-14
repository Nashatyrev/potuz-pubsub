package net.nashat

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

@DataSchema
data class CoreResult(
    val doneNodeCnt: Int,
    val activeNodeCnt: Int,
    val totalMsgCnt: Int,
    val dupMsgCnt: Int,
    val dupBeforeDone: Int,
    val dupOnFly: Int,
    val chunkDistribution: List<Int>,
    val chunkCountDistribution: List<Int>,
    val congestedNodeCount: Int
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

@DataSchema
interface ConfigableResultEntry {
    val config: SimConfig
}

@DataSchema
data class ResultEntry(
    override val config: SimConfig,
    val result: DataFrame<CoreResult>
) : ConfigableResultEntry

@DataSchema
data class ResultEx(
    val core: CoreResult,
    val derived: ResultDerived
)

@DataSchema
data class ResultEntryEx(
    override val config: SimConfig,
    val result: DataFrame<ResultEx>
) : ConfigableResultEntry

@DataSchema
data class ResultEntryExploded(
    override val config: SimConfig,
    val result: ResultEx
) : ConfigableResultEntry

