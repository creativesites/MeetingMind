package com.craftflowtechnologies.meetingmind.ai.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * What the managed service says the person has used this period. Seconds of cloud transcription and a
 * count of AI actions, kept apart because one is not the other. Only what the server reported is
 * shown; nothing is estimated on the phone.
 */
data class UsageState(
    val known: Boolean = false,
    val plan: String = "free",
    val audioUsedSeconds: Long = 0,
    val audioLimitSeconds: Long = 0,
    val actionsUsed: Long = 0,
    val actionsLimit: Long = 0,
    val periodEndMs: Long = 0,
    val founderUntilMs: Long? = null,
    /** The service refused the last request because the allowance is used up (or the plan has none). */
    val exhausted: Boolean = false
) {
    /** The fullest meter that exists for this plan; a meter with no allowance at all is not counted. */
    val fraction: Double
        get() = listOf(audioUsedSeconds to audioLimitSeconds, actionsUsed to actionsLimit)
            .filter { it.second > 0 }
            .maxOfOrNull { (used, limit) -> used.toDouble() / limit }
            ?: 0.0

    val nearLimit: Boolean get() = known && !exhausted && fraction >= NEAR_LIMIT
    val atLimit: Boolean get() = exhausted || (known && fraction >= 1.0)

    /** "12 of 100 min cloud transcription · 4 of 300 AI actions", or empty when there is nothing to say. */
    fun summary(): String = listOfNotNull(
        if (audioLimitSeconds > 0) "${audioUsedSeconds / 60} of ${audioLimitSeconds / 60} min cloud transcription" else null,
        if (actionsLimit > 0) "$actionsUsed of $actionsLimit AI actions" else null
    ).joinToString(" · ")

    companion object {
        const val NEAR_LIMIT = 0.8

        /** Reads the service's usage headers; null when the response carried none (for example Google itself). */
        fun fromHeaders(code: Int, get: (String) -> String?): UsageState? {
            val end = get("X-Usage-Period-End")?.toLongOrNull() ?: return null
            fun n(name: String) = get(name)?.toLongOrNull() ?: 0L
            return UsageState(
                known = true,
                plan = get("X-Plan") ?: "free",
                audioUsedSeconds = n("X-Usage-Audio-Used"),
                audioLimitSeconds = n("X-Usage-Audio-Limit"),
                actionsUsed = n("X-Usage-Actions-Used"),
                actionsLimit = n("X-Usage-Actions-Limit"),
                periodEndMs = end,
                founderUntilMs = get("X-Founder-Until")?.toLongOrNull(),
                exhausted = (code == 402 || code == 429) && get("X-Allowance") == "exhausted"
            )
        }
    }
}

object UsageMeter {
    private val _state = MutableStateFlow(UsageState())
    val state: StateFlow<UsageState> = _state.asStateFlow()

    /** Called with every reply; a reply that is not from the managed service changes nothing. */
    fun observe(code: Int, headers: okhttp3.Headers) {
        UsageState.fromHeaders(code) { headers[it] }?.let { _state.value = it }
    }

    /** Asks the service where the person stands, for Settings. Quietly does nothing without a managed route or a connection. */
    suspend fun refresh(context: Context, managedUrl: String = com.craftflowtechnologies.meetingmind.BuildConfig.GEMINI_PROXY_URL) {
        val base = GeminiRoutes.managedBase(managedUrl) ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val route = GeminiRoute(base, null, mapOf("X-Install-Id" to InstallId.get(context)) +
                    (com.craftflowtechnologies.meetingmind.core.billing.Billing.gateway.appUserId()?.let { mapOf("X-RC-User" to it) } ?: emptyMap()))
                val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
                client.newCall(route.builder("/usage").get().build()).execute().use { observe(it.code, it.headers) }
            }
        }
    }
}
