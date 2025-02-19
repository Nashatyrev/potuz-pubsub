package net.nashat

import org.jetbrains.kotlinx.dataframe.ColumnSelector
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.getColumn
import org.jetbrains.kotlinx.kandy.dsl.internal.dataframe.DataFramePlotBuilder
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.vLine
import org.jetbrains.kotlinx.kandy.letsplot.settings.LineType

fun <T> myPlot(
    df: DataFrame<T>,
    xCol: ColumnSelector<T, *>,
            yCol: ColumnSelector<T, *>,
    groupCol: ColumnSelector<T, *>,
    block: DataFramePlotBuilder<T>.() -> Unit
): Plot =
    plot(df) {

        line {
            x(getColumn(xCol))
            y(getColumn(yCol))
            color(getColumn(groupCol))

            withData(df.selectLastForEachGroup(groupCol)) {
                vLine {
                    xIntercept(getColumn(xCol))
                    type = LineType.DOTTED
                    color(getColumn(groupCol))
                }
            }
        }
        block(this)
    }

