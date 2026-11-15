package com.craftflowtechnologies.meetingmind.ai.embeddings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NeuralEmbeddingEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var credentials: GeminiCredentialStore
    private lateinit var engine: NeuralEmbeddingEngine

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().also { it.start() }
        credentials = GeminiCredentialStore(context)
        engine = NeuralEmbeddingEngine(
            context = context,
            credentials = credentials,
            ownBaseUrl = server.url("/").toString().trimEnd('/'),
            managedUrl = ""
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        runBlocking { credentials.clear() }
    }

    @Test
    fun `falls back cleanly to local subword embeddings when unconfigured`() = runBlocking {
        val v1 = engine.embed("quarterly budget review")
        val v2 = engine.embed("budgeting review for Q3")
        val v3 = engine.embed("completely unrelated banana recipe")
        assertEquals(LocalEmbeddingEngine.VECTOR_DIMENSION, v1.size)
        assertTrue(engine.cosineSimilarity(v1, v2) > engine.cosineSimilarity(v1, v3))
    }

    @Test
    fun `batches neural embeddings and hits local cache on repeat calls`() = runBlocking {
        credentials.setApiKey("embed-test-key")
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"embeddings":[{"values":[0.6,0.8,0.0]},{"values":[0.0,0.0,1.0]}]}"""
            )
        )

        val vecs = engine.embedBatch(listOf("ship date on Friday", "coffee machine"), isQuery = false)
        assertEquals(2, vecs.size)
        assertEquals(1, server.requestCount)
        assertEquals(0.6f, vecs[0][0], 0.001f)

        // Second identical request is served completely from in-memory/disk cache with 0 network calls.
        val cached = engine.embedBatch(listOf("ship date on Friday", "coffee machine"), isQuery = false)
        assertEquals(1, server.requestCount)
        assertEquals(1.0f, engine.cosineSimilarity(vecs[0], cached[0]), 0.001f)
    }
}
