package com.example.ai.modelmanagement

import android.content.Context
import java.io.File

/**
 * Where installed AI models live on disk, independent of any specific model runtime. Nothing
 * outside this class should construct a model file path directly.
 */
interface ModelStorage {
    fun getModelDirectory(modelId: String): File
    fun isInstalled(modelId: String): Boolean
    fun installedSizeBytes(modelId: String): Long
    fun delete(modelId: String): Boolean
}

/** Real implementation: models live under app-private storage, never shared/external storage. */
class LocalModelStorage(private val context: Context) : ModelStorage {

    private val rootDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    override fun getModelDirectory(modelId: String): File =
        File(rootDir, modelId).apply { mkdirs() }

    override fun isInstalled(modelId: String): Boolean =
        File(getModelDirectory(modelId), INSTALLED_MARKER).exists()

    override fun installedSizeBytes(modelId: String): Long =
        getModelDirectory(modelId).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    override fun delete(modelId: String): Boolean =
        getModelDirectory(modelId).deleteRecursively()

    /** Called once a downloaded model file has been verified and is ready to use. */
    fun markInstalled(modelId: String) {
        File(getModelDirectory(modelId), INSTALLED_MARKER).createNewFile()
    }

    private companion object {
        const val INSTALLED_MARKER = ".installed"
    }
}
