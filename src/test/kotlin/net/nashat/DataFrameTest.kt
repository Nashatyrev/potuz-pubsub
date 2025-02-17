@file:ImportDataSchema(
    "Repository",
    "https://raw.githubusercontent.com/Kotlin/dataframe/master/data/jetbrains_repositories.csv",
)

package net.nashat

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
import org.jetbrains.kotlinx.dataframe.api.getColumn
import org.jetbrains.kotlinx.dataframe.api.group
import org.jetbrains.kotlinx.dataframe.api.into
import org.jetbrains.kotlinx.dataframe.api.join
import org.jetbrains.kotlinx.dataframe.api.last
import org.jetbrains.kotlinx.dataframe.api.prev
import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.dataframe.api.rename
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.select
import org.jetbrains.kotlinx.dataframe.api.sortBy
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.values
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DataFrameTest {


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
    fun configDataExperiments() {
        val df1 = loadTestResult().normalizePotuzLoadedResults()
        val df2 = df1.deriveExtraResults()

        println(df2.describe())

        val df3 = df2
            .convert { result }.with { res ->
                res.last()
            }
            .select { config.params.numberOfChunks and result }

        println(df3.describe())

        df3.print(valueLimit = 10000)

//        df2.getColumn {  }
//        df2.filter { result.derived.doneMsgCnt == 1 }
    }

    @Test
    fun tryTwoResultsWithTimings() {
        val df1 = loadTestResult().normalizePotuzLoadedResults()
        val df2 = df1.deriveExtraResults()

        val df3 = df2.rows().first().toDataFrame()
        val df4 = df3.explode { result }

        df4.print(rowsLimit = 10000, valueLimit = 10000)
        println(df4.describe())

    }
}