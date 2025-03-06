package net.nashat

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.print
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.Writer
import java.nio.CharBuffer

fun DataFrame<*>.dump() {
    this.print(rowsLimit = 10000, valueLimit = 10000)
}


public class IOBuffer {

    private val baos = ByteArrayOutputStream()

    val writer = baos.bufferedWriter(Charsets.UTF_8)

    val content by lazy {
        writer.close()
        baos.toString(Charsets.UTF_8)
    }

    val inputStream by lazy {
        writer.close()
        ByteArrayInputStream(baos.toByteArray())
    }
}
