package net.nashat

import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.named
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.io.readJson
import org.jetbrains.kotlinx.dataframe.io.writeJson
import java.io.File
import java.io.InputStream

class PotuzIO {
    val jsonFile = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    fun writeResultsToJson(
        file: String,
        configs: Collection<PotuzSimulationConfig>,
        results: Collection<DataFrame<CoreResult>>
    ) {
        File(file).writer().use { writer ->
            writeResultsToJson(writer, configs, results)
        }
    }

    fun writeResultsToJson(
        out: Appendable,
        configs: Collection<PotuzSimulationConfig>,
        results: Collection<DataFrame<CoreResult>>
    ) {
        require(configs.size == results.size)

        val configColumn = columnOf(*configs.toTypedArray()).named("config")
        val resultColumn = columnOf(*results.toTypedArray()).named("result")

        val allDf = dataFrameOf(configColumn, resultColumn)
            .convert(configColumn).with { jsonFile.encodeToString(it) }

        allDf.writeJson(out, prettyPrint = true)
    }

    fun readResultsFromJson(file: String): DataFrame<Any?> =
        File(file).inputStream().use { input ->
            readResultsFromJson(input)
        }


    fun readResultsFromJson(input: InputStream): DataFrame<Any?> {
        return DataFrame.readJson(input)
            .convert("config").with { jsonFile.decodeFromString<PotuzSimulationConfig>(it.toString()) }
    }
}