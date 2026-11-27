package com.craftflowtechnologies.meetingmind.ai.cloud

/**
 * Lightweight, privacy-safe telemetry ring buffer for AI requests (zero prompts or transcript text).
 * Tracks provider, model, stream vs batch mode, time-to-first-token (TTFT), total duration, and
 * prompt/cached/output token counts for quality & latency diagnostics.
 */
data class AiCallMetric(
    val timestampMs: Long,
    val provider: String,
    val modelId: String,
    val streamed: Boolean,
    val ttftMs: Long?,
    val durationMs: Long,
    val promptTokens: Int = 0,
    val cachedTokens: Int = 0,
    val outputTokens: Int = 0,
    val success: Boolean
)

object AiTelemetry {
    private const val MAX_EVENTS = 200
    private val events = ArrayDeque<AiCallMetric>()

    fun record(metric: AiCallMetric) {
        synchronized(this) {
            events.addLast(metric)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
        val ttftPart = metric.ttftMs?.let { " ttft=${it}ms" }.orEmpty()
        val cachePart = if (metric.cachedTokens > 0) " cached=${metric.cachedTokens}" else ""
        GeminiLog.add(
            "Telemetry [${metric.provider}/${metric.modelId}] ${if (metric.streamed) "stream" else "sync"} " +
                "${if (metric.success) "OK" else "FAIL"} in ${metric.durationMs}ms$ttftPart" +
                if (metric.promptTokens > 0) " in=${metric.promptTokens} out=${metric.outputTokens}$cachePart" else ""
        )
    }

    fun recent(): List<AiCallMetric> = synchronized(this) { events.toList().asReversed() }

    fun averageTtftMs(): Long? = synchronized(this) {
        val vals = events.mapNotNull { it.ttftMs }
        if (vals.isEmpty()) null else vals.sum() / vals.size
    }

    fun clear() = synchronized(this) { events.clear() }
}
