package net.nashat

import org.jetbrains.kotlinx.dataframe.ColumnSelector
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.kandy.dsl.internal.dataframe.DataFramePlotBuilder
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.feature.Position
import org.jetbrains.kotlinx.kandy.letsplot.feature.position
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.vLine
import org.jetbrains.kotlinx.kandy.letsplot.multiplot.facet.facetWrap
import org.jetbrains.kotlinx.kandy.letsplot.multiplot.plotBunch
import org.jetbrains.kotlinx.kandy.letsplot.settings.LineType
import org.jetbrains.kotlinx.kandy.util.color.Color

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

fun <T> DataFrame<T>.myPlotLinesWithEndingVLine(
    xCol: ColumnSelector<T, *>,
    yCol: ColumnSelector<T, *>,
    groupCol: ColumnSelector<T, *>,
    block: DataFramePlotBuilder<T>.() -> Unit
): Plot =
    plot(this) {

        line {
            x(getColumn(xCol))
            y(getColumn(yCol))
            color(getColumn(groupCol))
//            width = 1.2

            withData(this@myPlotLinesWithEndingVLine.selectLastForEachGroup(groupCol)) {
                vLine {
                    xIntercept(getColumn(xCol))
                    type = LineType.DOTTED
//                    width = 1.2
                    color(getColumn(groupCol))
                }
            }
        }
        block(this)
    }


fun <T> DataFrame<T>.myPlotMessageCount(
    groupCol: ColumnSelector<*, *>,
): Plot {

    val msgCount by column<Int>()
    val msgCountType by column<String>()

    val gatheredDf = this
        .cast<ResultEntryExploded>()
        .gather { result.derived.roundDoneMsgCnt and result.derived.roundDupBeforeReadyMsgCnt and result.derived.roundDupAfterReadyMsgCnt }
        .into(msgCountType, msgCount)

    return gatheredDf
        .groupBy { this.getColumn(groupCol) and msgCountType }
        .plot {
            area {
                x(result.derived.relativeRound)
                y(msgCount)
                fillColor(msgCountType)
                position = Position.stack()
            }
            facetWrap(nCol = 1) {
                facet(getColumn(groupCol))
            }
        }
}

fun <T> DataFrame<T>.myPlotGroupDeliveredPartsAndMessageTypeCounts(
    adjustX1: Int = 0,
    adjustX2: Int = 0,
    groupCol: ColumnSelector<*, *>,
) = plotBunch {
    add(
        convert(groupCol).toStr()
            .myPlotLinesWithEndingVLine(
                xCol = { "result"["derived"]["relativeRound"] },
                yCol = { "result"["derived"]["doneMsgFraction"] },
                groupCol = groupCol
            ) {
                vLine {
                    xIntercept.constant(1.0)
                    type = LineType.DOTTED
                    width = 3.0
                    color = Color.BLACK
                }
            }, x = 20 + adjustX1, y = 0, width = 1500 + adjustX2 - adjustX1, height = 400
    )
    add(
        myPlotMessageCount(groupCol), x = 0, y = 400, width = 1615, height = 1000
    )
}