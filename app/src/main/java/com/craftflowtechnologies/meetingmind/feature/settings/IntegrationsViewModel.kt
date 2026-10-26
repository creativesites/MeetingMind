package com.craftflowtechnologies.meetingmind.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationPreferences
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationProvider
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationRegistry
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.FeatureGate
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationCredentialStore

data class IntegrationsUiState(
    val providers: List<IntegrationProvider> = emptyList(),
    val enabledProviderIds: Set<String> = emptySet(),
    val notifyMeProviderIds: Set<String> = emptySet(),
    val confidentialOptIn: Boolean = false,
    val currentWorkProfile: WorkProfile = WorkProfile.CLIENT_WORK,
    val isEmailFeatureEnabled: Boolean = BuildConfig.FEATURE_INTEGRATIONS_EMAIL,
    val canUseConnectedIntegrations: Boolean = false,
    val pinnedVaultName: String? = null,
    val pinnedVaultTreeUri: String? = null
)

class IntegrationsViewModel(application: Application) : AndroidViewModel(application) {
    private val registry = IntegrationRegistry(application)
    private val preferences = IntegrationPreferences(application)
    private val userPrefs = UserPreferencesManager(application)
    val credentialStore = IntegrationCredentialStore(application)

    val uiState: StateFlow<IntegrationsUiState> = combine(
        preferences.state,
        userPrefs.workSettings
    ) { prefsState, workSettings ->
        IntegrationsUiState(
            providers = registry.allProviders(),
            enabledProviderIds = prefsState.enabledProviderIds,
            notifyMeProviderIds = prefsState.notifyMeProviderIds,
            confidentialOptIn = prefsState.confidentialEmailOptIn,
            currentWorkProfile = workSettings.profile,
            canUseConnectedIntegrations = FeatureGate.can(Capability.CONNECTED_INTEGRATIONS),
            pinnedVaultName = prefsState.pinnedVaultName,
            pinnedVaultTreeUri = prefsState.pinnedVaultTreeUri
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        IntegrationsUiState(
            providers = registry.allProviders(),
            canUseConnectedIntegrations = FeatureGate.can(Capability.CONNECTED_INTEGRATIONS)
        )
    )

    override fun onCleared() {
        registry.close()
        super.onCleared()
    }

    fun toggleProvider(providerId: String, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setProviderEnabled(providerId, enabled)
        }
    }

    fun saveCredential(providerId: String, secret: String) {
        viewModelScope.launch {
            credentialStore.saveCredential(providerId, secret)
            preferences.setProviderEnabled(providerId, true)
        }
    }

    fun disconnectProvider(providerId: String) {
        viewModelScope.launch {
            credentialStore.removeCredential(providerId)
            preferences.setProviderEnabled(providerId, false)
        }
    }

    fun setPinnedVault(treeUri: String?, name: String?) {
        viewModelScope.launch {
            preferences.setPinnedVault(treeUri, name)
            if (treeUri != null) {
                preferences.setProviderEnabled(com.craftflowtechnologies.meetingmind.core.integrations.SafStorageProvider.ID, true)
            }
        }
    }

    fun toggleNotifyMe(providerId: String) {
        viewModelScope.launch {
            preferences.toggleNotifyMe(providerId)
        }
    }

    fun setConfidentialOptIn(optIn: Boolean) {
        viewModelScope.launch {
            preferences.setConfidentialEmailOptIn(optIn)
        }
    }
}
