package net.nashat.tests

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.gather
import org.jetbrains.kotlinx.dataframe.api.into
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.dsl.*
import org.jetbrains.kotlinx.statistics.kandy.layers.boxplot
import org.junit.jupiter.api.Test

class KandyTest {

    @Test
    fun `check boxplot doesnt throw exception`() {
        val dataset = dataFrameOf(
            "expr0" to listOf(
                850, 740, 900, 1070, 930, 850, 950, 980, 980,
                880, 1000, 980, 930, 650, 760, 810, 1000, 1000, 960, 960
            ),
            "expr1" to listOf(
                960, 940, 960, 940, 880, 800, 850, 880, 900, 840, 830,
                790, 810, 880, 880, 830, 800, 790, 760, 800
            ),
            "expr2" to listOf(
                880, 880, 880, 860, 720, 720, 620, 860, 970, 950,
                880, 910, 850, 870, 840, 840, 850, 840, 840, 840
            ),
            "expr3" to listOf(
                890, 810, 810, 820, 800, 770, 760, 740, 750,
                760, 910, 920, 890, 860, 880, 720, 840, 850, 850, 780
            ),
            "expr4" to listOf(
                890, 840, 780, 810, 760, 810, 790, 810, 820,
                850, 870, 870, 810, 740, 810, 940, 950, 800, 810, 870
            )
        ).gather("expr0", "expr1", "expr2", "expr3", "expr4").into("expr", "value")

        dataset.plot {
            boxplot("expr", "value")
        }
    }
}