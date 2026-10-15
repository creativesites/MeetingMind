package com.craftflowtechnologies.meetingmind.core.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.billingStore: DataStore<Preferences> by preferencesDataStore(name = "meetmind_billing")

/**
 * The last entitlement the store confirmed, kept so the app knows the plan offline. It is read through
 * [Entitlements.effective], so an old value cannot keep paid access alive indefinitely.
 */
class EntitlementCache(private val context: Context) {
    private val key = stringPreferencesKey("entitlements")

    val flow = context.billingStore.data.map { decode(it[key]) }

    suspend fun read(): Entitlements = flow.first()

    suspend fun write(value: Entitlements) {
        context.billingStore.edit { it[key] = encode(value) }
    }

    suspend fun clear() {
        context.billingStore.edit { it.remove(key) }
    }

    companion object {
        fun encode(e: Entitlements): String = JSONObject()
            .put("plan", e.plan.name)
            .put("state", e.state.name)
            .put("expiresAtMs", e.expiresAtMs ?: JSONObject.NULL)
            .put("willRenew", e.willRenew)
            .put("verifiedAtMs", e.verifiedAtMs)
            .toString()

        /** An unreadable value is free, never an error and never paid. */
        fun decode(raw: String?): Entitlements {
            if (raw.isNullOrBlank()) return Entitlements()
            return runCatching {
                val o = JSONObject(raw)
                Entitlements(
                    plan = Plan.entries.firstOrNull { it.name == o.optString("plan") } ?: Plan.FREE,
                    state = EntitlementState.entries.firstOrNull { it.name == o.optString("state") } ?: EntitlementState.NONE,
                    expiresAtMs = if (o.isNull("expiresAtMs")) null else o.getLong("expiresAtMs"),
                    willRenew = o.optBoolean("willRenew", false),
                    verifiedAtMs = o.optLong("verifiedAtMs", 0)
                )
            }.getOrDefault(Entitlements())
        }
    }
}
