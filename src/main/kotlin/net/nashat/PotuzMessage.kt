package net.nashat

data class PotuzMessage(
    val coefs: CoefVector,
    val descriptor: CoefVectorDescriptor,
    val from: AbstractNode,
    val to: AbstractNode
)

data class CoefVectorDescriptor(val coefs: CoefVector, val sourceVectors: List<CoefVectorDescriptor>, val originalVectorId: Int?) {
    val isOriginal = sourceVectors.isEmpty()

    fun getAllOriginalVectorsRecursively(): List<CoefVectorDescriptor> {
        return if (isOriginal) {
            listOf(this)
        } else {
            sourceVectors.map { it.getAllOriginalVectorsRecursively() }.flatten()
        }
    }

    fun countTreeNodes(): Int {
        return if (isOriginal) {
            1
        } else {
            1 + sourceVectors.sumOf { it.countTreeNodes() }
        }
    }

    val originalVectors get() = getAllOriginalVectorsRecursively().map { it.coefs }.distinct()
    val originalVectorsCount get() = originalVectors.size
    val originalVectorIds get() = getAllOriginalVectorsRecursively().map { it.originalVectorId!! }.toSet()

    override fun toString(): String {
        return if(isOriginal) "$originalVectorId" else "(" + sourceVectors.joinToString { it.toString() } + ")"
    }

}