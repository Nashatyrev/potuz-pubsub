package net.nashat.tests

import net.nashat.Erasure
import net.nashat.IOBuffer
import net.nashat.PotuzIO
import net.nashat.PotuzSimulation
import net.nashat.PotuzSimulationConfig
import net.nashat.ResultEntry
import net.nashat.ResultEntryExploded
import net.nashat.SimConfig
import net.nashat.config
import net.nashat.deriveExtraResults
import net.nashat.derived
import net.nashat.erasure
import net.nashat.relativeRound
import net.nashat.result
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.explode
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.last
import org.junit.jupiter.api.Test

class PotuzDataFrameTest {

    @Test
    fun `check run save load roundtrip`() {
        val cfg0 = PotuzSimulationConfig(
            SimConfig(
                nodeCount = 1000,
                peerCount = 10,
                numberOfChunks = 10,
                erasure = Erasure.RsX2,
            )
        )
        val cfg1 = PotuzSimulationConfig(
            SimConfig(
                nodeCount = 1000,
                peerCount = 10,
                numberOfChunks = 10,
                erasure = Erasure.RLNC,
            )
        )

        fun checkDataFrame(df: DataFrame<ResultEntry>) {
            assert(df.rowsCount() == 2)
            val erasures = df.get { config.erasure }.values().toList()
            assert(erasures == listOf(Erasure.RsX2, Erasure.RLNC))

            val df1 = df
                .deriveExtraResults()
                .explode { result }
                .cast<ResultEntryExploded>()

            assert(df1.rowsCount() > 10)

            val dfRs = df1.filter { config.erasure == Erasure.RsX2 }
            val dfRlnc = df1.filter { config.erasure == Erasure.RLNC }

            assert(dfRs.rowsCount() + dfRlnc.rowsCount() == df1.rowsCount())

            val rsTime = dfRs.last().get { result.derived.relativeRound }
            val rlncTime = dfRlnc.last().get { result.derived.relativeRound }

            assert(rsTime > 1.1)
            assert(rlncTime > 1.1)
            assert(rlncTime < rsTime)
        }

        val res: DataFrame<ResultEntry> = PotuzSimulation.runAll(
            listOf(cfg0, cfg1),
            withChunkDistribution = true
        )

        checkDataFrame(res)

        val ioBuffer0 = IOBuffer()
        PotuzIO().writeResultsToJson(ioBuffer0.writer, res)

//        println(ioBuffer.content)

        val readDf = PotuzIO().readResultsFromJson(ioBuffer0.inputStream)

        checkDataFrame(readDf)

        val ioBuffer1 = IOBuffer()
        PotuzIO().writeResultsToJson(ioBuffer1.writer, readDf)

        assert(ioBuffer0.content == ioBuffer1.content)
    }

}