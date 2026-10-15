package com.craftflowtechnologies.meetingmind.ai.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import kotlinx.coroutines.flow.first
import okhttp3.Request
import java.util.UUID

/**
 * Where one Gemini request goes and how it is signed.
 *
 * Own key: straight to Google with `?key=`. Managed: to MeetingMind's proxy, which holds the Google key,
 * checks the plan and meters usage, so no key is sent from the phone. [url] and [builder] are the only
 * places a Gemini request's address is made, so the two modes cannot drift apart.
 */
class GeminiRoute(val baseUrl: String, private val key: String?, private val headers: Map<String, String>) {
    val managed: Boolean get() = key == null

    fun url(path: String): String = baseUrl + path + (key?.let { "?key=$it" } ?: "")

    /**
     * A request builder for [path]. With [operation] a managed request carries a fresh operation id, which
     * the proxy charges once however many times the same request is retried.
     */
    fun builder(path: String, operation: Boolean = false): Request.Builder =
        withHeaders(Request.Builder().url(url(path))).also { b ->
            if (operation && managed) b.header("X-Operation-Id", UUID.randomUUID().toString())
        }

    /** Adds the managed sign-in headers (none for an own key). Used for the audio upload, whose URL the proxy hands back. */
    fun withHeaders(b: Request.Builder): Request.Builder = b.also { headers.forEach { (k, v) -> it.header(k, v) } }
}

object GeminiRoutes {
    /**
     * In order: the person's own key (their cost, their choice), then MeetingMind's managed service when this
     * build has one, then the tester key (private builds only). Null when none applies.
     */
    suspend fun resolve(credentials: GeminiCredentialStore, ownBaseUrl: String, managedUrl: String): GeminiRoute? {
        credentials.getUserKey()?.let { return GeminiRoute(ownBaseUrl, it, emptyMap()) }
        managedBase(managedUrl)?.let { base ->
            val headers = mutableMapOf("X-Install-Id" to InstallId.get(credentials.appContext))
            Billing.gateway.appUserId()?.let { headers["X-RC-User"] = it }
            return GeminiRoute(base, null, headers)
        }
        GeminiCredentialStore.systemKey?.let { return GeminiRoute(ownBaseUrl, it, emptyMap()) }
        return null
    }

    /** The proxy's base URL, or null if this build has none. Plain http is refused. */
    fun managedBase(configured: String): String? = configured.trim().takeIf { it.startsWith("https://") }?.trimEnd('/')
}

/** A random id for this install, shared with the DeepSeek allowance so one install is one identity. */
object InstallId {
    private val KEY = stringPreferencesKey("install_id")

    suspend fun get(context: Context): String {
        val store = context.applicationContext.deepSeekStore
        store.data.first()[KEY]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { it[KEY] = id }
        return id
    }
}
