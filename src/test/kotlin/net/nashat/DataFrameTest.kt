@file:ImportDataSchema(
    "Repository",
    "https://raw.githubusercontent.com/Kotlin/dataframe/master/data/jetbrains_repositories.csv",
)

package net.nashat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.DataRow
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.annotations.ImportDataSchema
import org.jetbrains.kotlinx.dataframe.api.JoinType
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.describe
import org.jetbrains.kotlinx.dataframe.api.fillNulls
import org.jetbrains.kotlinx.dataframe.api.getColumn
import org.jetbrains.kotlinx.dataframe.api.getColumnGroup
import org.jetbrains.kotlinx.dataframe.api.inferType
import org.jetbrains.kotlinx.dataframe.api.into
import org.jetbrains.kotlinx.dataframe.api.join
import org.jetbrains.kotlinx.dataframe.api.prev
import org.jetbrains.kotlinx.dataframe.api.rename
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.sortBy
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.api.unfold
import org.jetbrains.kotlinx.dataframe.api.ungroup
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.io.readJson
import org.jetbrains.kotlinx.dataframe.io.writeJson
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
        val res = CoreResult(1, 2, 3, 4, 5, 6, emptyList(), emptyList(), 0)
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

    @Test
    fun structureVsColumnGroupTest() {

        val structRows = listOf(
            Wrapper(10, Struct(1, "s1"), TestEnum.E1),
            Wrapper(20, Struct(2, "s2"), TestEnum.E2),
            Wrapper(30, Struct(3, "s3"), TestEnum.E3),
        )

        val df0 = structRows.toDataFrame()
        val df0_1 = columnOf(*structRows.toTypedArray()).toDataFrame().cast<Wrapper>()
//        val df0_2 = df0.convert { "struct"<Struct>() }.to<Wrapper>()
        val df0_2 = df0_1.unfold { all() }.ungroup { all() }
//        val df0_3 = df0_2.fold()

        val aaa = df0.convert { struct }.with { it.foldToObject().s }

        df0.dump()
        println(df0.describe())
        df0_1.dump()
        println(df0_1.describe())

        val os = ByteArrayOutputStream()

        os.writer().use {
            df0.writeJson(it, true)
        }

        val json = os.toString(Charsets.UTF_8)
        println(json)

        val list = Json.decodeFromString<List<Wrapper>>(json)

        val df1 = DataFrame.readJson(ByteArrayInputStream(os.toByteArray()))
        val df2 = df1.cast<Wrapper>()

        df2.dump()
        println(df2.describe())

        val structCol = df2.getColumn { "struct"<Struct>() }
        val values = structCol.values.toList()

        println(values.first().i)
    }
}

@Serializable
@DataSchema
data class Struct(
    val i: Int,
    val s: String
)

enum class TestEnum {
    E1, E2, E3
}

@Serializable
@DataSchema
data class Wrapper(
    val wi: Int,
    val struct: Struct,
    val en: TestEnum
)

