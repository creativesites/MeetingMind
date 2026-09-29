package com.example.ai.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Where MeetingMind's Gemini API key lives: **on the device, entered by the user.**
 *
 * It is deliberately not a `BuildConfig` field fed from `local.properties`, which is the usual
 * shortcut. A key compiled into an APK is recoverable from that APK with `strings` — a signed
 * release published anywhere someone else can download it is a published key, and MeetingMind's
 * releases are public. Keeping it here means the repository, the build and the artifact all stay
 * free of it, and the key exists only in the DataStore of the phone whose owner typed it.
 *
 * This is the development arrangement, not the end state. Production sends requests through
 * MeetingMind's own authenticated backend, which holds the credential server-side and can enforce
 * quotas and rate limits — see `docs/FUTURE_BACKEND.md`. That change replaces
 * [GeminiHttpTransport]; nothing above the [GeminiTransport] interface moves.
 *
 * DataStore is app-private storage, readable only by this app on a non-rooted device. It is not
 * hardware-backed: a key here is about as protected as a saved password in any ordinary app, which
 * is the right level for a personal development key and is not the right level for anyone else's.
 */
class GeminiCredentialStore(private val context: Context) {

    internal val appContext: Context get() = context.applicationContext


    /** The key the person entered in Settings, if any. */
    val userKeyFlow: Flow<String?> = context.geminiDataStore.data.map { preferences ->
        preferences[API_KEY]?.takeIf { it.isNotBlank() }
    }

    /**
     * The key requests use: the person's own when they've entered one, otherwise the tester key
     * built into this build (`SYSTEM_GEMINI_API_KEY` at build time), so testers can use Internet
     * mode without setting anything up. Null when there is neither.
     */
    val apiKeyFlow: Flow<String?> = userKeyFlow.map { it ?: systemKey }

    suspend fun getApiKey(): String? = apiKeyFlow.first()

    suspend fun getUserKey(): String? = userKeyFlow.first()

    /** Whether requests are running on the built-in tester key rather than the person's own. */
    suspend fun usingSystemKey(): Boolean = getUserKey() == null && systemKey != null

    /** Stores a key, trimmed. A blank value clears it rather than storing an empty string. */
    suspend fun setApiKey(key: String) {
        context.geminiDataStore.edit { preferences ->
            val trimmed = key.trim()
            if (trimmed.isEmpty()) preferences.remove(API_KEY) else preferences[API_KEY] = trimmed
        }
    }

    suspend fun clear() {
        context.geminiDataStore.edit { it.remove(API_KEY) }
    }

    /**
     * A redacted form for display, so the settings screen can show that a key is set without
     * putting it back on screen where it can be shoulder-surfed or screenshotted.
     */
    fun redact(key: String?): String? {
        if (key.isNullOrBlank()) return null
        return if (key.length <= VISIBLE_SUFFIX) "•".repeat(key.length)
        else "•".repeat(8) + key.takeLast(VISIBLE_SUFFIX)
    }

    companion object {
        private val API_KEY = stringPreferencesKey("gemini_api_key")

        /** The tester key compiled into this build, or null when none was supplied. */
        val systemKey: String? = com.example.BuildConfig.SYSTEM_GEMINI_API_KEY.trim().takeIf { it.isNotEmpty() }
        private const val VISIBLE_SUFFIX = 4
    }
}

/** Separate from the app's main preferences file so a credential is never swept up in a settings
 * export, backup or debug dump of ordinary user preferences. */
private val Context.geminiDataStore by preferencesDataStore(name = "meetmind_credentials")
