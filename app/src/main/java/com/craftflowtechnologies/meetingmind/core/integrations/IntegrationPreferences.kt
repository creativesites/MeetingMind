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
    val confidentialEmailOptIn: Boolean = false,
    val pinnedVaultTreeUri: String? = null,
    val pinnedVaultName: String? = null
)

/**
 * Manages local persistence for integration toggles, user interest taps ("Notify me"),
 * confidential profile opt-in states, and pinned vault tree URIs.
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
        val pinnedUri = prefs[KEY_PINNED_VAULT_TREE_URI]
        val pinnedName = prefs[KEY_PINNED_VAULT_NAME]

        IntegrationPreferencesState(
            enabledProviderIds = enabled,
            notifyMeProviderIds = notifyMe,
            confidentialEmailOptIn = confidentialOptIn,
            pinnedVaultTreeUri = pinnedUri,
            pinnedVaultName = pinnedName
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

    suspend fun setPinnedVault(treeUri: String?, name: String?) {
        context.integrationsDataStore.edit { prefs ->
            if (treeUri != null) {
                prefs[KEY_PINNED_VAULT_TREE_URI] = treeUri
            } else {
                prefs.remove(KEY_PINNED_VAULT_TREE_URI)
            }
            if (name != null) {
                prefs[KEY_PINNED_VAULT_NAME] = name
            } else {
                prefs.remove(KEY_PINNED_VAULT_NAME)
            }
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
        private val KEY_PINNED_VAULT_TREE_URI = androidx.datastore.preferences.core.stringPreferencesKey("pinned_vault_tree_uri")
        private val KEY_PINNED_VAULT_NAME = androidx.datastore.preferences.core.stringPreferencesKey("pinned_vault_name")
    }
}
