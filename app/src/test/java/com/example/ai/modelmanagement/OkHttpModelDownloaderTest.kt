package com.example.ai.modelmanagement

import com.example.ai.common.AiResult
import com.example.core.model.ModelFileSpec
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/**
 * Exercises the real [OkHttpModelDownloader] against a local [MockWebServer] — real HTTP over a
 * real socket, real streaming-to-disk code, just not the live internet (which would make this
 * test flaky/slow and is inappropriate for a unit test run in CI). This is not a mock of the
 * class under test; MockWebServer only replaces where the bytes come from.
 */
class OkHttpModelDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val downloader = OkHttpModelDownloader()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `downloads a file and its bytes match exactly`() = runBlocking {
        val content = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))

        val spec = ModelFileSpec(
            fileName = "test.bin",
            downloadUrl = server.url("/test.bin").toString(),
            sha256 = sha256Of(content),
            sizeBytes = content.size.toLong()
        )
        val destination = tempFolder.root.resolve("test.bin.part")

        val progressUpdates = mutableListOf<Long>()
        val result = downloader.download(spec, destination) { bytesDownloaded, _ -> progressUpdates.add(bytesDownloaded) }

        assertTrue(result is AiResult.Success)
        assertEquals(content.size.toLong(), destination.length())
        assertTrue(content.contentEquals(destination.readBytes()))
        assertTrue("progress callback should have fired", progressUpdates.isNotEmpty())
        assertEquals(content.size.toLong(), progressUpdates.last())
    }

    @Test
    fun `resumes from an existing partial file via Range request`() = runBlocking {
        val fullContent = ByteArray(100_000) { (it % 197).toByte() }
        val alreadyHave = fullContent.copyOfRange(0, 40_000)
        val remaining = fullContent.copyOfRange(40_000, fullContent.size)

        val destination = tempFolder.root.resolve("resume.bin.part")
        destination.writeBytes(alreadyHave)

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes 40000-99999/100000")
                .setBody(okio.Buffer().write(remaining))
        )

        val spec = ModelFileSpec(
            fileName = "resume.bin",
            downloadUrl = server.url("/resume.bin").toString(),
            sha256 = sha256Of(fullContent),
            sizeBytes = fullContent.size.toLong()
        )

        val result = downloader.download(spec, destination)

        assertTrue(result is AiResult.Success)
        assertEquals(fullContent.size.toLong(), destination.length())
        assertTrue(fullContent.contentEquals(destination.readBytes()))

        val recordedRequest = server.takeRequest()
        assertEquals("bytes=40000-", recordedRequest.getHeader("Range"))
    }

    @Test
    fun `retries after a transient server error and eventually succeeds`() = runBlocking {
        val content = ByteArray(1_000) { it.toByte() }
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))

        val spec = ModelFileSpec(
            fileName = "flaky.bin",
            downloadUrl = server.url("/flaky.bin").toString(),
            sha256 = sha256Of(content),
            sizeBytes = content.size.toLong()
        )
        val destination = tempFolder.root.resolve("flaky.bin.part")

        val result = downloader.download(spec, destination)

        assertTrue(result is AiResult.Success)
        assertEquals(2, server.requestCount)
        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun `already fully-present file is left untouched without a network call`() = runBlocking {
        val content = ByteArray(500) { it.toByte() }
        val destination = tempFolder.root.resolve("done.bin.part")
        destination.writeBytes(content)

        val spec = ModelFileSpec(
            fileName = "done.bin",
            downloadUrl = server.url("/done.bin").toString(),
            sha256 = sha256Of(content),
            sizeBytes = content.size.toLong()
        )

        val result = downloader.download(spec, destination)

        assertTrue(result is AiResult.Success)
        assertEquals(0, server.requestCount)
    }
}
