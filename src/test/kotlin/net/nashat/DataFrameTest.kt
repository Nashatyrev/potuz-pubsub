@file:ImportDataSchema(
    "Repository",
    "https://raw.githubusercontent.com/Kotlin/dataframe/master/data/jetbrains_repositories.csv",
)

package net.nashat

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.annotations.ImportDataSchema
import org.jetbrains.kotlinx.dataframe.api.JoinType
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.describe
import org.jetbrains.kotlinx.dataframe.api.explode
import org.jetbrains.kotlinx.dataframe.api.fillNulls
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.first
import org.jetbrains.kotlinx.dataframe.api.flatten
import org.jetbrains.kotlinx.dataframe.api.fullJoin
import org.jetbrains.kotlinx.dataframe.api.getColumn
import org.jetbrains.kotlinx.dataframe.api.group
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.into
import org.jetbrains.kotlinx.dataframe.api.join
import org.jetbrains.kotlinx.dataframe.api.last
import org.jetbrains.kotlinx.dataframe.api.name
import org.jetbrains.kotlinx.dataframe.api.named
import org.jetbrains.kotlinx.dataframe.api.path
import org.jetbrains.kotlinx.dataframe.api.prev
import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.dataframe.api.rename
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.select
import org.jetbrains.kotlinx.dataframe.api.sortBy
import org.jetbrains.kotlinx.dataframe.api.toColumnAccessor
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.api.toList
import org.jetbrains.kotlinx.dataframe.api.unique
import org.jetbrains.kotlinx.dataframe.api.values
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.columns.toColumnSet
import org.jetbrains.kotlinx.dataframe.values
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DataFrameTest {

    fun DataFrame<*>.dump() {
        this.print(rowsLimit = 10000, valueLimit = 10000)
    }

    @Test
    fun diffTest() {
        val nums by columnOf(1, 4, 4, 10)
        val df = dataFrameOf(nums)

        val df1 = df.add("diff") {
            this[nums] - (prev()?.get(nums) ?: 0)
        }
        println(df1)
    }

    @Test
    fun testJsonSerialize() {
        val buf = ByteArrayOutputStream()
        val cfg1 = PotuzSimulationConfig(
            params = PotuzParams(10, rlncParams = RLNCParams()),
            peerCount = 40,
            isGodStopMode = true
        )
        val cfg2 = PotuzSimulationConfig(
            params = PotuzParams(20, rsParams = RSParams(2, true)),
            peerCount = 50,
            isGodStopMode = false
        )
        val res = CoreResult(1, 2, 3, 4, 5, 6)
        val df = listOf(res).toDataFrame()

        val writer = buf.writer()
        PotuzIO().writeResultsToJson(
            writer,
            listOf(cfg1, cfg2), listOf(df, df)
        )
        writer.flush()

        println(buf.toString(Charsets.UTF_8))

        val fromJson = PotuzIO().readResultsFromJson(ByteArrayInputStream(buf.toByteArray()))

        println(fromJson)
    }

    @Test
    fun mergeTimesTest() {

        @DataSchema
        data class TimedData(
            val time: Double,
            val data: Int
        )

        val df1 = listOf(
            TimedData(0.0, 100),
            TimedData(2.0, 200),
            TimedData(4.0, 300),
        ).toDataFrame()

        val df2 = listOf(
            TimedData(1.0, 50),
            TimedData(3.0, 150),
            TimedData(4.0, 250),
        ).toDataFrame()
            .rename("data").into("data2")

        val df3 = df1
            .join(df2, JoinType.Full)
            .sortBy("time")
            .fillNulls("data").with { prev()?.get("data") ?: 0 }
            .fillNulls("data2").with { prev()?.get("data2") ?: 0 }
        println(df3)
    }

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
        val resDf = PotuzIO().readResultsFromJson("results/result.json").normalizePotuzLoadedResults()
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

        df1.flatten{ config }.dump()

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
}