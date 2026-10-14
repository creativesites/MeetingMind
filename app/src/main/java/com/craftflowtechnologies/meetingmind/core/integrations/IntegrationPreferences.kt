package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.integrationsDataStore: DataStore<Preferences> by preferencesDataStore(name = "meetmind_integrations")

data class IntegrationPreferencesState(
    val enabledProviderIds: Set<String> = setOf(
        DeviceCalendarProvider.ID,
        ShareSheetOutputChannel.ID,
        SafStorageProvider.ID
    ),
    val notifyMeProviderIds: Set<String> = emptySet(),
    val confidentialEmailOptIn: Boolean = false
)

/**
 * Manages local persistence for integration toggles, user interest taps ("Notify me"),
 * and confidential profile opt-in states.
 */
class IntegrationPreferences(private val context: Context) {

    val state: Flow<IntegrationPreferencesState> = context.integrationsDataStore.data.map { prefs ->
        val enabled = prefs[KEY_ENABLED_PROVIDERS] ?: setOf(
            DeviceCalendarProvider.ID,
            ShareSheetOutputChannel.ID,
            SafStorageProvider.ID
        )
        val notifyMe = prefs[KEY_NOTIFY_ME_PROVIDERS] ?: emptySet()
        val confidentialOptIn = prefs[KEY_CONFIDENTIAL_OPT_IN] ?: false

        IntegrationPreferencesState(
            enabledProviderIds = enabled,
            notifyMeProviderIds = notifyMe,
            confidentialEmailOptIn = confidentialOptIn
        )
    }

    suspend fun isProviderEnabled(providerId: String): Boolean {
        val current = state.first()
        return providerId in current.enabledProviderIds
    }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        context.integrationsDataStore.edit { prefs ->
            val current = prefs[KEY_ENABLED_PROVIDERS]?.toMutableSet() ?: mutableSetOf(
                DeviceCalendarProvider.ID,
                ShareSheetOutputChannel.ID,
                SafStorageProvider.ID
            )
            if (enabled) {
                current.add(providerId)
            } else {
                current.remove(providerId)
            }
            prefs[KEY_ENABLED_PROVIDERS] = current
        }
    }

    suspend fun toggleNotifyMe(providerId: String): Boolean {
        var isNowRegistered = false
        context.integrationsDataStore.edit { prefs ->
            val current = prefs[KEY_NOTIFY_ME_PROVIDERS]?.toMutableSet() ?: mutableSetOf()
            if (providerId in current) {
                current.remove(providerId)
                isNowRegistered = false
            } else {
                current.add(providerId)
                isNowRegistered = true
            }
            prefs[KEY_NOTIFY_ME_PROVIDERS] = current
        }
        return isNowRegistered
    }

    suspend fun setConfidentialEmailOptIn(optIn: Boolean) {
        context.integrationsDataStore.edit { prefs ->
            prefs[KEY_CONFIDENTIAL_OPT_IN] = optIn
        }
    }

    companion object {
        private val KEY_ENABLED_PROVIDERS = stringSetPreferencesKey("enabled_providers")
        private val KEY_NOTIFY_ME_PROVIDERS = stringSetPreferencesKey("notify_me_providers")
        private val KEY_CONFIDENTIAL_OPT_IN = booleanPreferencesKey("confidential_email_opt_in")
    }
}
