package com.craftflowtechnologies.meetingmind.core.circles2

import android.content.Context

/** What the repository needs from push: tell it when Circles matters to this person. */
interface PushRegistrar {
    /** The person opened Circles or is in a circle: make sure this phone's token is registered. */
    suspend fun activate()
    /** The person is in no circle any more: stop pushes to this phone. */
    suspend fun deactivate()
    /** The signed-in account changed: the old registration belongs to someone else. */
    suspend fun onAccountChanged()
}

/** Per-install memory for push registration. */
interface PushTokenStore {
    /** True once Circles has been opened or a circle joined. Until then we never touch the Worker. */
    var wantsPush: Boolean
    /** The token the Worker last accepted for the current account, or null. */
    var registeredToken: String?
    /** The latest token Firebase gave us (may not be registered yet). */
    var latestToken: String?
    /** Whether we already showed the notification permission prompt. */
    var askedPermission: Boolean
}

class InMemoryPushTokenStore : PushTokenStore {
    override var wantsPush = false
    override var registeredToken: String? = null
    override var latestToken: String? = null
    override var askedPermission = false
}

class SharedPrefsPushTokenStore(context: Context) : PushTokenStore {
    private val prefs = context.applicationContext.getSharedPreferences("circles2_push", Context.MODE_PRIVATE)
    override var wantsPush: Boolean
        get() = prefs.getBoolean("wants", false)
        set(v) { prefs.edit().putBoolean("wants", v).apply() }
    override var registeredToken: String?
        get() = prefs.getString("registered", null)
        set(v) { prefs.edit().putString("registered", v).apply() }
    override var latestToken: String?
        get() = prefs.getString("latest", null)
        set(v) { prefs.edit().putString("latest", v).apply() }
    override var askedPermission: Boolean
        get() = prefs.getBoolean("asked", false)
        set(v) { prefs.edit().putBoolean("asked", v).apply() }
}

/**
 * Registers this phone's FCM token with the Worker (`registerToken` / `unregisterToken`).
 *
 * The rules: nothing is sent until the person is in a circle or opens Circles; a refreshed token is
 * registered once; a failed registration is retried next time Circles opens (it is only recorded as
 * registered after the Worker said yes). [tokenSource] returns the current Firebase token or null.
 */
class PushTokenManager(
    private val api: CirclesApi,
    private val store: PushTokenStore,
    private val tokenSource: suspend () -> String?
) : PushRegistrar {

    override suspend fun activate() {
        store.wantsPush = true
        if (!api.isConfigured) return
        val token = tokenSource()?.takeIf { it.isNotBlank() } ?: store.latestToken
        if (token != null) store.latestToken = token
        registerIfNeeded()
    }

    /** From the messaging service: Firebase rotated the token. */
    suspend fun onNewToken(token: String) {
        if (token.isBlank()) return
        store.latestToken = token
        if (store.wantsPush && api.isConfigured) registerIfNeeded()
    }

    override suspend fun deactivate() {
        val registered = store.registeredToken
        store.wantsPush = false
        if (registered != null && api.isConfigured && api.unregisterToken(registered) is CirclesResult.Ok) store.registeredToken = null
    }

    override suspend fun onAccountChanged() {
        // Best effort for the old account (its token is already gone if we were signed out); the new one registers fresh.
        store.registeredToken = null
        if (store.wantsPush) activate()
    }

    private suspend fun registerIfNeeded() {
        val token = store.latestToken ?: return
        if (token == store.registeredToken) return
        if (api.registerToken(token) is CirclesResult.Ok) store.registeredToken = token
    }
}
