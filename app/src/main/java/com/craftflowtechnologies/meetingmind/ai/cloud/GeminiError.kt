package com.craftflowtechnologies.meetingmind.ai.cloud

import org.json.JSONObject

/** What went wrong with a Gemini request, in terms the app can act on. */
enum class GeminiErrorKind {
    /** No API key is configured. */
    NO_KEY,
    /** The key was rejected (401/403, or "API key not valid"). */
    AUTH,
    /** Billing or prepaid credit problem (402). */
    BILLING,
    /** The plan's quota is used up, or the wait is too long to ride out. */
    QUOTA_EXHAUSTED,
    /** Too many requests right now (429); worth waiting for. */
    RATE_LIMITED,
    /** 5xx: Google's side. */
    SERVER,
    TIMEOUT,
    /** Connection lost, no network, DNS. */
    NETWORK,
    /** 400 and other 4xx: this request is wrong and repeating it won't help. */
    BAD_REQUEST,
    /** 404: usually an unknown or retired model. */
    NOT_FOUND,
    /** The answer wasn't valid JSON, had no audio, or the audio wasn't decodable. */
    MALFORMED,
    /** The audio was empty, or far too short for the text. */
    EMPTY_AUDIO,
    /** The app's own rules (offline, privacy, entitlement) stopped the request before it was sent. */
    POLICY_BLOCKED
}

data class GeminiError(
    val kind: GeminiErrorKind,
    val httpCode: Int? = null,
    val message: String,
    /** How long the server asked us to wait, if it said. */
    val retryAfterMs: Long? = null,
    /** True once automatic retries ran out. */
    val retriesExhausted: Boolean = false
) {
    /** Worth another automatic attempt. */
    val retryable: Boolean
        get() = kind in RETRYABLE

    /** Only the person can fix this: the key, the plan, or waiting a long time. */
    val needsUser: Boolean
        get() = kind in NEEDS_USER

    /** The text older callers showed: "Gemini 429: ...". */
    val legacyMessage: String
        get() = if (httpCode != null) "Gemini $httpCode: ${message.take(200)}" else message

    companion object {
        private val RETRYABLE = setOf(
            GeminiErrorKind.RATE_LIMITED, GeminiErrorKind.SERVER, GeminiErrorKind.TIMEOUT,
            GeminiErrorKind.NETWORK, GeminiErrorKind.MALFORMED, GeminiErrorKind.EMPTY_AUDIO
        )
        private val NEEDS_USER = setOf(
            GeminiErrorKind.NO_KEY, GeminiErrorKind.AUTH, GeminiErrorKind.BILLING, GeminiErrorKind.QUOTA_EXHAUSTED
        )
        /** A rate-limit wait longer than this is treated as an exhausted quota, not something to ride out. */
        const val RIDE_OUT_LIMIT_MS = 60_000L
    }
}

sealed interface GeminiResult<out T> {
    data class Success<T>(val value: T) : GeminiResult<T>
    data class Failure(val error: GeminiError) : GeminiResult<Nothing>
}

object GeminiErrors {

    /** Classifies a non-2xx answer. [retryAfterHeader] is the raw `Retry-After` header, in seconds. */
    fun fromHttp(code: Int, body: String, retryAfterHeader: String? = null): GeminiError {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val message = error?.optString("message")?.takeIf { it.isNotBlank() } ?: "request failed"
        val status = error?.optString("status").orEmpty()
        val retryAfter = retryAfterHeader?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() }
            ?: retryDelayFromDetails(error)
        val lower = message.lowercase()
        val kind = when {
            code == 401 || code == 403 -> GeminiErrorKind.AUTH
            code == 402 -> GeminiErrorKind.BILLING
            code == 400 && ("api key not valid" in lower || "api_key_invalid" in lower || status == "UNAUTHENTICATED") -> GeminiErrorKind.AUTH
            code == 429 || status == "RESOURCE_EXHAUSTED" ->
                if (retryAfter != null && retryAfter > GeminiError.RIDE_OUT_LIMIT_MS) GeminiErrorKind.QUOTA_EXHAUSTED else GeminiErrorKind.RATE_LIMITED
            code == 408 -> GeminiErrorKind.TIMEOUT
            code in 500..599 -> GeminiErrorKind.SERVER
            code == 404 -> GeminiErrorKind.NOT_FOUND
            else -> GeminiErrorKind.BAD_REQUEST
        }
        return GeminiError(kind, code, message, retryAfter)
    }

    /** `error.details[]` item `google.rpc.RetryInfo` has `retryDelay` like "34s" or "34.5s". */
    private fun retryDelayFromDetails(error: JSONObject?): Long? {
        val details = error?.optJSONArray("details") ?: return null
        for (i in 0 until details.length()) {
            val delay = details.optJSONObject(i)?.optString("retryDelay")?.takeIf { it.isNotBlank() } ?: continue
            delay.trim().removeSuffix("s").toDoubleOrNull()?.let { return (it * 1000).toLong() }
        }
        return null
    }
}
