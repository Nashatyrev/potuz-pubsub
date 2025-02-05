@file:ImportDataSchema(
    "Repository",
    "https://raw.githubusercontent.com/Kotlin/dataframe/master/data/jetbrains_repositories.csv",
)

package net.nashat

import org.jetbrains.kotlinx.dataframe.annotations.ImportDataSchema

class DataFrameTest {


    fun aaa() {
        val df = Repository.readCSV()
        val col0 = df.fullName
    }

}