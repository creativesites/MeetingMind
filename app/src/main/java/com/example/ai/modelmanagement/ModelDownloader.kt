package com.example.ai.modelmanagement

import com.example.ai.common.AiResult
import com.example.core.model.AiModelInfo
import java.io.File

/**
 * Downloads a model into a temporary location. Real implementations must never let a
 * partially-downloaded or corrupt file be treated as installed:
 *
 * ```
 * Download -> temporary file -> ModelVerifier.verify(sha256) -> move into ModelStorage
 * ```
 *
 * No production model source has been selected yet (see docs/AI_ARCHITECTURE.md), so the
 * default implementation is intentionally unimplemented rather than pointing at an invented
 * URL. [ModelRepository] must never treat this returning success as a real install.
 */
interface ModelDownloader {
    suspend fun download(
        model: AiModelInfo,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): AiResult<File>
}

class UnconfiguredModelDownloader : ModelDownloader {
    override suspend fun download(
        model: AiModelInfo,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): AiResult<File> = AiResult.ModelUnavailable(
        modelId = model.id,
        message = "No downloadable model source is configured yet for \"${model.name}\"."
    )
}
