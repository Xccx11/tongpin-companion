package com.aster.companion.domain

data class SemanticVectorCandidate(val id: String, val vector: FloatArray?, val retrievable: Boolean)

object SemanticMemoryRanker {
    fun rank(query: FloatArray, candidates: List<SemanticVectorCandidate>, limit: Int): List<String> =
        candidates.asSequence()
            .filter { it.retrievable && it.vector != null && it.vector.size == query.size }
            .map { it.id to cosine(query, requireNotNull(it.vector)) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        var dot = 0f
        var leftMagnitude = 0f
        var rightMagnitude = 0f
        left.indices.forEach { index ->
            dot += left[index] * right[index]
            leftMagnitude += left[index] * left[index]
            rightMagnitude += right[index] * right[index]
        }
        if (leftMagnitude == 0f || rightMagnitude == 0f) return 0f
        return dot / kotlin.math.sqrt(leftMagnitude * rightMagnitude)
    }
}
