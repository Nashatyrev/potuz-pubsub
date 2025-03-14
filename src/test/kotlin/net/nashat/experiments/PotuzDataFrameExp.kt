package net.nashat.experiments

import net.nashat.Erasure
import net.nashat.MeshStrategy
import net.nashat.PotuzIO
import net.nashat.PotuzSimulation
import net.nashat.PotuzSimulationConfig
import net.nashat.ResultEntryExploded
import net.nashat.SimConfig
import net.nashat.chunkDistribution
import net.nashat.config
import net.nashat.core
import net.nashat.deriveExtraResults
import net.nashat.deriveExtraResultsExploded
import net.nashat.derived
import net.nashat.doneMsgFraction
import net.nashat.dump
import net.nashat.erasure
import net.nashat.myPlotGroupDeliveredPartsAndMessageTypeCounts
import net.nashat.numberOfChunks
import net.nashat.peerCount
import net.nashat.relativeRound
import net.nashat.removeNonChangingConfigColumns
import net.nashat.result
import net.nashat.rsIsDistinctMeshes
import net.nashat.rsMeshStrategy
import net.nashat.selectLastForEachGroup
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.describe
import org.jetbrains.kotlinx.dataframe.api.explode
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.first
import org.jetbrains.kotlinx.dataframe.api.flatten
import org.jetbrains.kotlinx.dataframe.api.last
import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.dataframe.api.select
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.api.with
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

//@Disabled
class PotuzDataFrameExp {
    private fun loadTestResult() = PotuzIO().readResultsFromJson(
        this.javaClass.getResource("/result1.json")?.file ?: throw IllegalStateException("Result1 file not found")
    )

    @Test
    fun testLoadResults() {
        val res = loadTestResult()
        println(res.describe())
    }


    @Test
    fun loadFullResultTest() {
        val resDf = PotuzIO().readResultsFromJson("results/result.json")
//            .unfoldPotuzConfig()
//            .convertRawConfigToSimConfig()

        println(resDf)
    }

    @Test
    fun configDataExperiments() {
        val df1 = loadTestResult()
        val df2 = df1.deriveExtraResults()

        println(df2.describe())

        val df3 = df2.convert { result }.with { res ->
                res.last()
            }.filter { config.numberOfChunks == 1 }.select { config.numberOfChunks and result }

        println(df3.describe())

        df3.print(valueLimit = 10000)

//        df2.getColumn {  }
//        df2.filter { result.derived.doneMsgCnt == 1 }
    }

    @Test
    fun tryResultsExplode() {
        val df1 = loadTestResult()
        val df2 = df1.deriveExtraResults()

        val df3 = df2.first().toDataFrame()

        assert(df3.rowsCount() == 1)

        val df4 = df3.explode { result }

        assert(df4.rowsCount() > 1)

        df4.dump()
        println(df4.describe())
    }

    @Test
    fun testExperimentsMergeByTime() {
        val df1 = loadTestResult().deriveExtraResults().removeNonChangingConfigColumns()

        df1.flatten { config }.dump()

        val df3 = df1.filter {
                config.numberOfChunks == 20 && config.peerCount == 10 && config.rsIsDistinctMeshes == true
            }.removeNonChangingConfigColumns()


//        val cfgDf1 = df3.select { config }.cast<PotuzSimulationConfig>()
//        cfgDf1.dump()
//
//        val changingConfigColumns = cfgDf1
//            .describe()
//            .filter { unique > 1 }
//            .values { path }
//            .toList()
//
//        cfgDf1
//            .select(*changingConfigColumns.toTypedArray())
//            .dump()
//
//        val cfgColSelector = changingConfigColumns.map { it.toColumnAccessor() }.toColumnSet()
        val df4 = df3.explode { result }.cast<ResultEntryExploded>()
            .select { config and result.derived.relativeRound and result.derived.doneMsgFraction }

        df4.dump()

        val df5 = df4.selectLastForEachGroup { config.erasure }
        df5.dump()
    }

    @Test
    fun testProcessRunResults() {
        val cfg = SimConfig(
            nodeCount = 1000,
            peerCount = 10,
            numberOfChunks = 10,
            erasure = Erasure.RsX2,
        )

        val res = PotuzSimulation.runAll(
            listOf(cfg), withChunkDistribution = true
        )

        val resDf = res.deriveExtraResults().explode { result }.cast<ResultEntryExploded>()
            .select { result.derived.relativeRound and result.core.chunkDistribution }
            .convert { "chunkDistribution"<List<Int>>() }.with {
                it.withIndex().toDataFrame()
            }.explode { "chunkDistribution"() }

        resDf.dump()
    }

    @Test
    fun `check weird IndexOutBound error inside dataframe`() {
        val commonCfg = SimConfig(
            erasure = Erasure.NoErasure,
            numberOfChunks = 40,
            latencyRounds = 20,
            randomSeed = 1
        )

        val resDf =
            PotuzSimulation.runAll(
                listOf(
                    commonCfg.copy(
                        peerCount = 10,
                        rsMeshStrategy = MeshStrategy.Static
                    ),
                    commonCfg.copy(
                        peerCount = 3,
                        rsMeshStrategy = MeshStrategy.Static
                    ),
                    commonCfg.copy(
                        peerCount = 4,
                        rsMeshStrategy = MeshStrategy.Static
                    ),
                    commonCfg.copy(
                        peerCount = 5,
                        rsMeshStrategy = MeshStrategy.Static
                    ),
                    commonCfg.copy(
                        peerCount = 9,
                        rsMeshStrategy = MeshStrategy.TwoPhaseMesh
                    ),
                )
            )
                .deriveExtraResultsExploded()

        val df1 = resDf.add("peer_count_and_mesh_type") { "" + config.peerCount + "/" + config.rsMeshStrategy }
        df1
            .myPlotGroupDeliveredPartsAndMessageTypeCounts(adjustX2 = 0) {
                "peer_count_and_mesh_type"()
            }
    }
}