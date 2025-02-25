package net.nashat

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.print

fun DataFrame<*>.dump() {
    this.print(rowsLimit = 10000, valueLimit = 10000)
}

