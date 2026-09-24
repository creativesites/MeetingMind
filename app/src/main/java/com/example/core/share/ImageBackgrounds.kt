package com.example.core.share

import android.content.Context
import com.example.ai.cloud.GeminiCredentialStore
import com.example.ai.cloud.GeminiInteractions
import com.example.ai.common.AiResult
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.ProcessingProfile
import kotlinx.coroutines.flow.first
import java.io.File

/** Looks for a generated background (PLAN_V2 F4). */
enum class ImageStyle(val label: String, val prompt: String) {
    LANDSCAPE("Landscape", "a serene natural landscape photograph, soft morning light, gentle depth of field"),
    LIGHT("Abstract light", "abstract soft light and bokeh, luminous gradients, calm and hopeful"),
    WATERCOLOUR("Watercolour", "a delicate watercolour painting with soft washes and paper texture"),
    MINIMAL("Minimal", "a minimal, uncluttered composition with a single subtle subject and lots of negative space")
}

/**
 * Asks Gemini's image model for a background that suits a verse or thought. Images never contain
 * text or people's faces — the words are drawn by the app, correctly, on top.
 */
class ImageBackgrounds(private val context: Context) {
    companion object {
        const val MODEL = "gemini-3.1-flash-lite-image"

        fun prompt(theme: String, style: ImageStyle): String =
            "Create a background image for a devotional card. Theme: ${theme.take(300)}. Style: ${style.prompt}. " +
                "No text, no letters, no words, no watermarks, no people's faces. Leave calm space in the middle for overlaid text. " +
                "Reverent, beautiful, not kitsch."
    }

    /** Whether generating is possible now (Internet mode and a key). */
    suspend fun available(): Boolean =
        UserPreferencesManager(context).preferencesFlow.first().processingProfile == ProcessingProfile.INTERNET &&
            GeminiInteractions(GeminiCredentialStore(context)).isConfigured()

    suspend fun generate(theme: String, style: ImageStyle, format: ShareFormat, target: File? = null): AiResult<File> {
        if (!available()) return AiResult.ModelUnavailable(MODEL, "Turn on Internet mode with your Gemini key to make pictures.")
        val result = GeminiInteractions(GeminiCredentialStore(context)).media(GeminiInteractions.imageRequest(MODEL, prompt(theme, style), format.aspect), "image")
        val media = (result as? AiResult.Success)?.value ?: return result as AiResult<File>
        val ext = if (media.mimeType.contains("jpeg") || media.mimeType.contains("jpg")) "jpg" else "png"
        val file = target ?: File(File(context.cacheDir, "shares").apply { mkdirs() }, "bg_${System.currentTimeMillis()}.$ext")
        file.parentFile?.mkdirs()
        file.writeBytes(media.bytes)
        return AiResult.Success(file)
    }
}
