package net.nashat

import org.jlinalg.Matrix
import org.jlinalg.Vector
import org.jlinalg.field_p.FieldPFactoryMap
import org.junit.jupiter.api.Test
import kotlin.random.Random


class SomeTests {

    val random = Random(1)
    val maxRandomCoef = 1024L

    @Test
    fun test1() {

        val dataCount = 4
        val initialChunkCount = 8

        val initMatrix = CoefMatrix.generate(initialChunkCount, dataCount, random, maxRandomCoef)

        println("Rank is " + initMatrix.rank)

        val v0_0 = initMatrix.coefVectors[0]
        val v0_1 = initMatrix.coefVectors[1]
        val v0_2 = initMatrix.coefVectors[2]


        val v1_0 = v0_0 * 2 + v0_1 * 7 + v0_2 * 3
        val v1_1 = v0_0 * 13 + v0_1 * 33 + v0_2 * 7

        val m1 = CoefMatrix.fromVectors(listOf(v1_0, v1_1))

        println("m1 rank is " + m1.rank)

        val m2 = CoefMatrix.fromVectors(listOf(v1_0, v1_1, initMatrix.coefVectors[4], initMatrix.coefVectors[5]))

        println("m2 rank is " + m2.rank)

    }

    @Test
    fun jlinalgTest() {
        val rnd = Random(0)
//        val fieldPrime = 3037000500L - 1
//        val fieldPrime = 100000007L
        val fieldPrime = 3037000537
//        val fieldPrime = 111L
//        val fieldPrime = "9223372036854775837"
        val vectorSize = 10
        val vectorCount = vectorSize
        val trials = 10000

        val maxInitialCoef = 128L

        val factory = FieldPFactoryMap.getFactory(fieldPrime)

        fun randomVector() = Vector(* Array(vectorSize) { factory[rnd.nextLong(maxInitialCoef - 1) + 1] })
        fun randomMatrix(size: Int) = Matrix(Array(size) { randomVector() })

        for (i in 0 until trials) {
            val matrix = randomMatrix(vectorCount)
            val rank = matrix.rank()
            if (rank < vectorSize) {
                println("$i: rank = $rank")
            }
            var m1 = matrix
            for (j in 0 until 32) {
                val vectors = (0 until vectorCount).map { m1.getRow(it + 1) }
                val newVectors = vectors.map { it.multiply(factory[rnd.nextInt()]) }
                m1 = Matrix(newVectors)
                val r1 = m1.rank()
                if (r1 != rank) {
                    println("!!!! wrong rank: $r1")
                }
            }
        }
    }
}