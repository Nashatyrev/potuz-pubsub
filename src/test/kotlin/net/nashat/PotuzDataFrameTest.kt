package net.nashat

import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.describe
import org.jetbrains.kotlinx.dataframe.api.explode
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.first
import org.jetbrains.kotlinx.dataframe.api.flatten
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.inplace
import org.jetbrains.kotlinx.dataframe.api.into
import org.jetbrains.kotlinx.dataframe.api.intoRows
import org.jetbrains.kotlinx.dataframe.api.last
import org.jetbrains.kotlinx.dataframe.api.named
import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.dataframe.api.select
import org.jetbrains.kotlinx.dataframe.api.split
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.api.unfold
import org.jetbrains.kotlinx.dataframe.api.with
import org.junit.jupiter.api.Test

class PotuzDataFrameTest {
    private fun loadTestResult() = PotuzIO().readResultsFromJson(
        this.javaClass.getResource("/result1.json")?.file ?: throw IllegalStateException("Result1 file not found")
    )

    @Test
    fun testLoadResults() {
        val res = loadTestResult()
        println(res.describe())
    }


    @Test
    fun loadFullResultTest() {
        val resDf = PotuzIO()
            .readResultsFromJson("results/result.json")
            .unfoldPotuzConfig()
            .convertRawConfigToSimConfig()

        println(resDf)
    }

    @Test
    fun configDataExperiments() {
        val df1 = loadTestResult().normalizePotuzLoadedResults()
        val df2 = df1.deriveExtraResults()

        println(df2.describe())

        val df3 = df2
            .convert { result }.with { res ->
                res.last()
            }
            .filter { config.numberOfChunks == 1 }
            .select { config.numberOfChunks and result }

        println(df3.describe())

        df3.print(valueLimit = 10000)

//        df2.getColumn {  }
//        df2.filter { result.derived.doneMsgCnt == 1 }
    }

    @Test
    fun tryResultsExplode() {
        val df1 = loadTestResult().normalizePotuzLoadedResults()
        val df2 = df1.deriveExtraResults()

        val df3 = df2.first().toDataFrame()

        assert(df3.rowsCount() == 1)

        val df4 = df3.explode { result }

        assert(df4.rowsCount() > 1)

        df4.dump()
        println(df4.describe())
    }

    @Test
    fun testExperimentsMergeByTimeIsolated() {
        val paramCol = columnOf(1, 1, 1, 1, 1, 2, 2, 2, 2).named("param")
        val timeCol = columnOf(0.0, 0.1, 0.5, 0.8, 1.5, 0.2, 0.5, 0.8, 1.0).named("time")
        val valueCol = columnOf(0, 1, 2, 3, 4, 2, 3, 4, 5).named("value")

        val df = dataFrameOf(paramCol, timeCol, valueCol)
        df.dump()

        val df1 = df.groupBy { paramCol }.into("group")
        df1.dump()
        println(df1.describe())

    }

    @Test
    fun testExperimentsMergeByTime() {
        val df1 = loadTestResult()
            .normalizePotuzLoadedResults()
            .deriveExtraResults()
            .removeNonChangingConfigColumns()

        df1.flatten { config }.dump()

        val df3 = df1
            .filter {
                config.numberOfChunks == 20 &&
                        config.peerCount == 10 &&
                        config.erasure.isDistinctMeshes == true
            }.removeNonChangingConfigColumns()


//        val cfgDf1 = df3.select { config }.cast<PotuzSimulationConfig>()
//        cfgDf1.dump()
//
//        val changingConfigColumns = cfgDf1
//            .describe()
//            .filter { unique > 1 }
//            .values { path }
//            .toList()
//
//        cfgDf1
//            .select(*changingConfigColumns.toTypedArray())
//            .dump()
//
//        val cfgColSelector = changingConfigColumns.map { it.toColumnAccessor() }.toColumnSet()
        val df4 = df3
            .explode { result }
            .cast<ResultEntryExploded>()
            .select { config and result.derived.relativeRound and result.derived.doneMsgFraction }

        df4.dump()

        val df5 = df4.selectLastForEachGroup { config.erasure }
        df5.dump()
    }

    @Test
    fun testProcessRunResults() {
        val cfg = PotuzSimulationConfig(
            params = PotuzParams(
                numberOfChunks = 10,
                rsParams = RSParams(
                    extensionFactor = 1,
                    isDistinctMeshesPerChunk = true,
                    chunkSelectionStrategy = ChunkSelectionStrategy.PreferRarest,
                )
            ),
            peerCount = 10,
            isGodStopMode = true
        )

        val res = PotuzSimulation.runAll(
            listOf(cfg),
            withChunkDistribution = true
        )

        val resDf = res
            .unfoldPotuzConfig()
            .convertRawConfigToSimConfig()
            .deriveExtraResults()
            .explode { result }
            .cast<ResultEntryExploded>()
            .select { result.derived.relativeRound and result.core.chunkDistribution }
//            .split { result.core.chunkDistributiontion }
            .convert { "chunkDistribution"<List<Int>>() }.with {
                it.withIndex().toDataFrame()
            }
            .explode { "chunkDistribution"() }

        resDf.dump()
    }

}