@file:ImportDataSchema(
    "Repository",
    "https://raw.githubusercontent.com/Kotlin/dataframe/master/data/jetbrains_repositories.csv",
)

package net.nashat

import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.annotations.ImportDataSchema
import org.jetbrains.kotlinx.dataframe.api.JoinType
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.fillNulls
import org.jetbrains.kotlinx.dataframe.api.into
import org.jetbrains.kotlinx.dataframe.api.join
import org.jetbrains.kotlinx.dataframe.api.joinWith
import org.jetbrains.kotlinx.dataframe.api.prev
import org.jetbrains.kotlinx.dataframe.api.rename
import org.jetbrains.kotlinx.dataframe.api.sortBy
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.api.withZero
import org.junit.jupiter.api.Test

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
            .fillNulls("data").with { prev()?.get("data") ?: 0}
            .fillNulls("data2").with { prev()?.get("data2") ?: 0}
        println(df3)
    }
}