package com.craftflowtechnologies.meetingmind.ai.translation

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment

/**
 * Result of translating a meeting transcript, title, and optional summary.
 */
data class TranslatedTranscript(
    val sourceLanguage: String,
    val targetLanguage: String,
    val translatedTitle: String?,
    val translatedSummary: String?,
    val translatedSegments: List<TranslatedSegment>
)

data class TranslatedSegment(
    val segmentId: String,
    val translatedText: String
)

/**
 * Translates transcript segments, titles, and summaries without modifying the verbatim original.
 *
 * Implementations must uphold non-destructive translation: the original verbatim transcript
 * remains untouched and canonical; translations are stored as separate layers alongside.
 */
interface TranslationEngine {
    suspend fun translateTranscript(
        meetingTitle: String,
        summaryText: String?,
        segments: List<TranscriptSegment>,
        sourceLanguage: String,
        targetLanguage: String
    ): AiResult<TranslatedTranscript>
}
