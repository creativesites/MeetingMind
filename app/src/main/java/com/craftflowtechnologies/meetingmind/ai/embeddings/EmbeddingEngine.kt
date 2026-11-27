package com.craftflowtechnologies.meetingmind.ai.embeddings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

interface EmbeddingEngine {
    suspend fun embed(text: String): FloatArray
    suspend fun embedQuery(text: String): FloatArray = embed(text)
    suspend fun embedBatch(texts: List<String>, isQuery: Boolean = false): List<FloatArray> =
        texts.map { if (isQuery) embedQuery(it) else embed(it) }
    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float
}

/**
 * Deterministic zero-network embedding projection combining word unigrams, signed feature
 * hashing, adjacent bigrams, and character 3-gram subwords within a fixed 64-d unit sphere.
 */
class LocalEmbeddingEngine : EmbeddingEngine {

    companion object {
        const val VECTOR_DIMENSION = 64

        fun cosine(vecA: FloatArray, vecB: FloatArray): Float {
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

        fun normalize(vector: FloatArray): FloatArray {
            var sumSquares = 0f
            for (v in vector) sumSquares += v * v
            val magnitude = sqrt(sumSquares)
            if (magnitude > 0f) {
                for (i in vector.indices) vector[i] /= magnitude
            }
            return vector
        }
    }

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val vector = FloatArray(VECTOR_DIMENSION) { 0f }
        if (text.isBlank()) return@withContext vector

        val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        if (words.isEmpty()) return@withContext vector

        fun project(feature: String, weight: Float) {
            val h = feature.hashCode()
            val idx1 = (h and 0x7FFFFFFF) % VECTOR_DIMENSION
            val sign1 = if (((h ushr 16) and 1) == 0) 1f else -1f
            val h2 = h * 31 + feature.length
            val idx2 = (h2 and 0x7FFFFFFF) % VECTOR_DIMENSION
            val sign2 = if (((h2 ushr 12) and 1) == 0) 1f else -1f
            vector[idx1] += sign1 * weight
            vector[idx2] += sign2 * weight * 0.5f
        }

        for (i in words.indices) {
            val word = words[i]
            val baseWeight = 1.0f / sqrt(word.length.toFloat() + 1f)
            project("w:$word", baseWeight)

            // Character 3-grams give robust subword overlap for inflections & stems.
            if (word.length >= 4) {
                val padded = "^$word$"
                val gramWeight = baseWeight * 0.35f
                for (g in 0..padded.length - 3) {
                    project("c:${padded.substring(g, g + 3)}", gramWeight)
                }
            }

            // Word bigrams preserve multi-word concepts ("action item", "budget cut").
            if (i + 1 < words.size) {
                project("b:${word}_${words[i + 1]}", 0.55f)
            }
        }

        normalize(vector)
    }

    override fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float = cosine(vecA, vecB)
}
