package net.nashat

import org.jlinalg.Matrix
import org.jlinalg.Vector
import org.jlinalg.field_p.FieldP
import org.jlinalg.field_p.FieldPFactoryMap
import kotlin.random.Random

const val PRIME_2_IN_63_PLUS_O = "9223372036854775837"
const val PRIME_2_IN_16_PLUS_1 = 65537.toString()
const val PRIME_2_IN_8_PLUS_1 = 257.toString()
const val PRIME_7 = 7.toString()
const val PRIME_HIGHEST_BACKED_BY_LONG = (3037000500L - 1).toString()
const val PRIME_LOWEST_BACKED_BY_BIG_INT = 3037000537.toString()


fun setFieldPrime(prime: String) {
    pFieldFactory = FieldPFactoryMap.getFactory(prime)
}

var pFieldFactory = FieldPFactoryMap.getFactory("9223372036854775837")

data class CoefVector(val vec: Vector<FieldP>) {

    companion object {
        fun generate(size: Int, rnd: Random, maxCoef: Long) = CoefVector(fromLongs(size) { rnd.nextLong(maxCoef) + 1 })

        fun zero(size: Int) = CoefVector(fromLongs(size) { 0L })

        private fun fromLongs(size: Int, ctor: (Int) -> Long): Vector<FieldP> {
            return Vector(*Array(size) { pFieldFactory[ctor(it)] })
        }
    }
}

data class CoefMatrix(val coefMatrix: Matrix<FieldP>, val origVectors: List<CoefVector>? = null) {
    companion object {
        val EMPTY = CoefMatrix(Matrix(arrayOf(pFieldFactory[0]), 1), emptyList())

        fun generate(vectorCount: Int, coefCount: Int, rnd: Random, maxCoef: Long) =
            fromVectors(List(vectorCount) { CoefVector.generate(coefCount, rnd, maxCoef) })

        fun fromVectors(vecs: List<CoefVector>) = CoefMatrix(Matrix(vecs.map { it.vec }), vecs)
    }

    fun isEmpty() = origVectors?.isEmpty() ?: false
    val rowCount = coefMatrix.rows

    private fun allRows() =
        (1..rowCount).map { coefMatrix.getRow(it) }


    val rank by lazy { coefMatrix.rank() }
    val coefVectors by lazy { origVectors ?: allRows().map { CoefVector(it) } }
    val coefVectorsSet by lazy { coefVectors.toSet() }
}

operator fun CoefVector.times(m: Long) =
    CoefVector(vec.multiply(pFieldFactory[m]))

operator fun CoefVector.plus(otherVec: CoefVector) =
    CoefVector(vec.add(otherVec.vec))


operator fun CoefMatrix.plus(vector: CoefVector) =
    if (isEmpty()) {
        CoefMatrix(Matrix(arrayOf(vector.vec)))
    } else {
        CoefMatrix(coefMatrix.insertRow(coefMatrix.rows + 1, vector.vec))
    }

fun CoefMatrix.doesIncreaseRank(newVector: CoefVector) = this.rank < (this + newVector).rank
