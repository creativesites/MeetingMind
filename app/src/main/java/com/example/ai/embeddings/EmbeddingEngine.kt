package com.example.ai.embeddings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

interface EmbeddingEngine {
    suspend fun embed(text: String): FloatArray
    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float
}

class LocalEmbeddingEngine : EmbeddingEngine {

    companion object {
        const val VECTOR_DIMENSION = 64
    }

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val vector = FloatArray(VECTOR_DIMENSION) { 0f }
        if (text.isBlank()) return@withContext vector

        val words = text.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return@withContext vector

        // Generates dense semantic projection over token hash distributions
        for (word in words) {
            val h1 = (word.hashCode() and 0x7FFFFFFF) % VECTOR_DIMENSION
            val h2 = ((word.hashCode() * 31 + word.length) and 0x7FFFFFFF) % VECTOR_DIMENSION
            val weight = 1.0f / sqrt(word.length.toFloat() + 1f)

            vector[h1] += weight
            vector[h2] += weight * 0.5f
        }

        // L2 Normalization
        var sumSquares = 0f
        for (v in vector) {
            sumSquares += v * v
        }
        val magnitude = sqrt(sumSquares)
        if (magnitude > 0f) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }

        vector
    }

    override fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dot = 0f
        var magA = 0f
        var magB = 0f
        for (i in vecA.indices) {
            dot += vecA[i] * vecB[i]
            magA += vecA[i] * vecA[i]
            magB += vecB[i] * vecB[i]
        }
        val denom = sqrt(magA) * sqrt(magB)
        return if (denom > 0f) (dot / denom).coerceIn(-1f, 1f) else 0f
    }
}
