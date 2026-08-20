package com.example.ai.modelmanagement

import com.example.ai.common.AiResult
import com.example.core.model.ModelFileSpec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads one model file to a local destination. Real implementations must never let a
 * partially-downloaded or corrupt file be treated as installed — that verification step lives
 * one layer up in [com.example.core.repository.ModelRepository], which only activates a file
 * after [com.example.ai.modelmanagement.ModelVerifier] confirms its checksum.
 *
 * No production model source had been selected when this interface was first introduced (see
 * docs/AI_ARCHITECTURE.md history); [UnconfiguredModelDownloader] remains as the safe default
 * for any model whose manifest entry has no real URL. [OkHttpModelDownloader] is the real,
 * network-capable implementation used once a manifest entry is downloadable.
 */
interface ModelDownloader {
    /**
     * Downloads [file] to [destination]. Streams directly to disk, reports progress via
     * [onProgress], resumes from [destination]'s existing bytes when the server supports HTTP
     * Range requests, and retries transient failures a bounded number of times. Cancelling the
     * calling coroutine cancels the in-flight HTTP call.
     */
    suspend fun download(
        file: ModelFileSpec,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): AiResult<File>
}

class UnconfiguredModelDownloader : ModelDownloader {
    override suspend fun download(
        file: ModelFileSpec,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): AiResult<File> = AiResult.ModelUnavailable(
        modelId = file.fileName,
        message = "No downloadable source is configured yet for \"${file.fileName}\"."
    )
}

/**
 * Real HTTPS streaming downloader. Writes directly into [destination] (the caller is
 * responsible for using a `.part`-suffixed temporary path and only renaming it into place after
 * [com.example.ai.modelmanagement.ModelVerifier] confirms the checksum — this class never marks
 * anything "installed").
 */
class OkHttpModelDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val maxAttempts: Int = 3
) : ModelDownloader {

    override suspend fun download(
        file: ModelFileSpec,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): AiResult<File> {
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                downloadOnce(file, destination, onProgress)
                return AiResult.Success(destination)
            } catch (e: java.util.concurrent.CancellationException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                // Keep partial bytes on disk so the next attempt (or the next call to
                // download(), e.g. after the user retries) can resume via HTTP Range instead
                // of starting over — "resume if practical" per docs/AI_ARCHITECTURE.md.
                if (attempt < maxAttempts - 1) {
                    // Slow/intermittent connections (this app's primary target) fail in bursts;
                    // retrying instantly just re-fails against the same congestion. Back off
                    // exponentially (1s, 2s, 4s, ...) so a later attempt has a real chance.
                    delay(INITIAL_BACKOFF_MS * (1L shl attempt))
                }
            }
        }
        return AiResult.Failed(
            message = "Failed to download \"${file.fileName}\" after $maxAttempts attempts: ${lastError?.message}",
            cause = lastError
        )
    }

    private suspend fun downloadOnce(
        file: ModelFileSpec,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ) {
        val existingBytes = if (destination.exists()) destination.length() else 0L
        val requestBuilder = Request.Builder().url(file.downloadUrl)
        if (existingBytes in 1 until file.sizeBytes) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        } else if (existingBytes >= file.sizeBytes) {
            // Already fully present from a prior attempt; nothing to do.
            return
        }

        val call = client.newCall(requestBuilder.build())
        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code} downloading ${file.fileName}")
                }
                val resumed = resp.code == 206
                val body = resp.body ?: throw IOException("Empty response body for ${file.fileName}")

                destination.parentFile?.mkdirs()
                var bytesWritten = if (resumed) existingBytes else 0L
                if (!resumed && destination.exists()) {
                    destination.delete()
                }

                java.io.FileOutputStream(destination, resumed).sink().buffer().use { sink ->
                    body.source().use { source ->
                        val buffer = okio.Buffer()
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer, CHUNK_SIZE_BYTES)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            bytesWritten += read
                            onProgress(bytesWritten, file.sizeBytes)
                        }
                        sink.flush()
                    }
                }
            }
        } finally {
            call.cancel()
        }
    }

    private companion object {
        const val CHUNK_SIZE_BYTES = 64L * 1024L
        const val INITIAL_BACKOFF_MS = 1000L
    }
}
