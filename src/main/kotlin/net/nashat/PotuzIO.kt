package net.nashat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.named
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
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
        file: String, res: DataFrame<ResultEntry>
    ) {
        File(file).writer().use { writer ->
            writeResultsToJson(writer, res)
        }
    }

    fun writeResultsToJson(
        out: Appendable, res: DataFrame<ResultEntry>
    ) {
        res.writeJson(out)
    }

    fun readResultsFromJson(file: String): DataFrame<ResultEntry> =
        File(file).inputStream().use { input ->
            readResultsFromJson(input)
        }


    fun readResultsFromJson(input: InputStream): DataFrame<ResultEntry> {
        return DataFrame.readJson(input)
            .cast<ResultEntry>()
            // workaround: not sure how to correctly serialize/deserialize enum values
            .convert { "config"["erasure"]<String>() }.with { Erasure.valueOf(it.toString()) }
    }

    companion object {

    }
}