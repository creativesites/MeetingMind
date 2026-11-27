package com.craftflowtechnologies.meetingmind.ai.embeddings

import android.content.Context
import android.util.Log
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiHttpTransport
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRoute
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRoutes
import com.craftflowtechnologies.meetingmind.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Neural semantic embedding engine backed by Gemini `gemini-embedding-001` (256-dim truncated
 * MRL vectors with `RETRIEVAL_QUERY` / `RETRIEVAL_DOCUMENT` task types), cached in an LRU memory
 * map + compact disk cache, with automatic offline fallback to [LocalEmbeddingEngine].
 */
class NeuralEmbeddingEngine(
    private val context: Context,
    private val credentials: GeminiCredentialStore = GeminiCredentialStore(context),
    private val fallback: EmbeddingEngine = LocalEmbeddingEngine(),
    private val ownBaseUrl: String = GeminiHttpTransport.DEFAULT_BASE_URL,
    private val managedUrl: String = BuildConfig.GEMINI_PROXY_URL,
    private val client: OkHttpClient = Net.base.newBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
) : EmbeddingEngine {

    private val cacheLock = Any()
    private val memoryCache: MutableMap<String, FloatArray> = Collections.synchronizedMap(
        object : LinkedHashMap<String, FloatArray>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
                size > MAX_MEMORY_ENTRIES
        }
    )

    @Volatile
    private var diskLoaded = false

    override suspend fun embed(text: String): FloatArray = embedSingle(text, isQuery = false)

    override suspend fun embedQuery(text: String): FloatArray = embedSingle(text, isQuery = true)

    override suspend fun embedBatch(texts: List<String>, isQuery: Boolean): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        ensureDiskLoaded()

        val taskType = if (isQuery) TASK_QUERY else TASK_DOCUMENT
        val results = arrayOfNulls<FloatArray>(texts.size)
        val missingIndices = mutableListOf<Int>()
        val missingKeys = mutableListOf<String>()

        for (i in texts.indices) {
            val clean = texts[i].trim()
            if (clean.isEmpty()) {
                results[i] = FloatArray(NEURAL_DIMENSION)
                continue
            }
            val key = cacheKey(taskType, clean)
            val cached = synchronized(cacheLock) { memoryCache[key] }
            if (cached != null) {
                results[i] = cached
            } else {
                missingIndices += i
                missingKeys += key
            }
        }

        if (missingIndices.isEmpty()) {
            return results.map { it ?: FloatArray(NEURAL_DIMENSION) }
        }

        val route = GeminiRoutes.resolve(credentials, ownBaseUrl, managedUrl)
        if (route != null) {
            val fetched = withContext(Dispatchers.IO) {
                fetchBatch(route, missingIndices.map { texts[it].trim() }, taskType)
            }
            if (fetched != null && fetched.size == missingIndices.size) {
                synchronized(cacheLock) {
                    missingIndices.forEachIndexed { offset, idx ->
                        val vec = fetched[offset]
                        results[idx] = vec
                        memoryCache[missingKeys[offset]] = vec
                    }
                }
                persistDiskCache()
                return results.map { it ?: FloatArray(NEURAL_DIMENSION) }
            }
        }

        // Graceful offline/error fallback — pad or keep fallback consistently for every entry in this batch.
        return texts.map { fallback.embed(it) }
    }

    private suspend fun embedSingle(text: String, isQuery: Boolean): FloatArray {
        val clean = text.trim()
        if (clean.isEmpty()) return FloatArray(fallbackSizeOrDefault())
        ensureDiskLoaded()
        val taskType = if (isQuery) TASK_QUERY else TASK_DOCUMENT
        val key = cacheKey(taskType, clean)
        synchronized(cacheLock) { memoryCache[key] }?.let { return it }

        val route = GeminiRoutes.resolve(credentials, ownBaseUrl, managedUrl)
        if (route != null) {
            val fetched = withContext(Dispatchers.IO) { fetchBatch(route, listOf(clean), taskType) }?.firstOrNull()
            if (fetched != null && fetched.isNotEmpty()) {
                synchronized(cacheLock) { memoryCache[key] = fetched }
                persistDiskCache()
                return fetched
            }
        }
        return if (isQuery) fallback.embedQuery(clean) else fallback.embed(clean)
    }

    override fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        return LocalEmbeddingEngine.cosine(vecA, vecB)
    }

    private fun fetchBatch(route: GeminiRoute, texts: List<String>, taskType: String): List<FloatArray>? {
        val out = ArrayList<FloatArray>(texts.size)
        for (chunkStart in texts.indices step MAX_BATCH_CHUNK) {
            val chunk = texts.subList(chunkStart, minOf(chunkStart + MAX_BATCH_CHUNK, texts.size))
            val requests = JSONArray()
            chunk.forEach { contentText ->
                requests.put(
                    JSONObject()
                        .put("model", "models/$MODEL_ID")
                        .put("taskType", taskType)
                        .put("outputDimensionality", NEURAL_DIMENSION)
                        .put(
                            "content",
                            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", contentText.take(MAX_EMBED_CHARS))))
                        )
                )
            }
            val payload = JSONObject().put("requests", requests).toString()
            val req = route.builder("/v1beta/models/$MODEL_ID:batchEmbedContents", operation = false)
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            val ok = runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "batchEmbedContents HTTP ${resp.code}")
                        return@use false
                    }
                    val body = resp.body?.string() ?: return@use false
                    val arr = JSONObject(body).optJSONArray("embeddings") ?: return@use false
                    if (arr.length() != chunk.size) return@use false
                    for (i in 0 until arr.length()) {
                        val values = arr.optJSONObject(i)?.optJSONArray("values") ?: return@use false
                        val vec = FloatArray(values.length()) { j -> values.optDouble(j, 0.0).toFloat() }
                        out += LocalEmbeddingEngine.normalize(vec)
                    }
                    true
                }
            }.getOrElse {
                Log.w(TAG, "batchEmbedContents failed: ${it.message}")
                false
            }
            if (!ok) return null
        }
        return out
    }

    private fun cacheKey(taskType: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$MODEL_ID|$NEURAL_DIMENSION|$taskType|$text".toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun fallbackSizeOrDefault(): Int = LocalEmbeddingEngine.VECTOR_DIMENSION

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE)

    private fun ensureDiskLoaded() {
        if (diskLoaded) return
        synchronized(cacheLock) {
            if (diskLoaded) return
            diskLoaded = true
            runCatching {
                val f = cacheFile()
                if (!f.exists()) return@runCatching
                val obj = JSONObject(f.readText())
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = obj.optJSONArray(k) ?: continue
                    val vec = FloatArray(arr.length()) { i -> arr.optDouble(i, 0.0).toFloat() }
                    if (vec.isNotEmpty()) memoryCache[k] = vec
                }
            }
        }
    }

    private fun persistDiskCache() {
        runCatching {
            val snapshot = synchronized(cacheLock) {
                memoryCache.entries.toList().takeLast(MAX_DISK_ENTRIES)
            }
            val obj = JSONObject()
            snapshot.forEach { (k, vec) ->
                val arr = JSONArray()
                vec.forEach { v -> arr.put(v.toDouble()) }
                obj.put(k, arr)
            }
            cacheFile().writeText(obj.toString())
        }
    }

    companion object {
        private const val TAG = "NeuralEmbeddingEngine"
        const val MODEL_ID = "gemini-embedding-001"
        const val NEURAL_DIMENSION = 256
        private const val TASK_QUERY = "RETRIEVAL_QUERY"
        private const val TASK_DOCUMENT = "RETRIEVAL_DOCUMENT"
        private const val MAX_BATCH_CHUNK = 64
        private const val MAX_EMBED_CHARS = 4_000
        private const val MAX_MEMORY_ENTRIES = 512
        private const val MAX_DISK_ENTRIES = 400
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val CACHE_FILE = "neural_embeddings_v1.json"
    }
}
