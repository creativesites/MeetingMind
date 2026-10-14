package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Registry of all integration providers in MeetingMind.
 * Provides access to available providers, their status, and their capabilities.
 *
 * The preferences the providers consult are collected once into [snapshot] and read from memory,
 * so the getters never block on DataStore, which keeps them safe to call from the UI thread.
 */
class IntegrationRegistry(
    private val context: Context,
    private val preferences: IntegrationPreferences = IntegrationPreferences(context),
    private val userPrefs: UserPreferencesManager = UserPreferencesManager(context)
) {
    /** The values the registry reads, cached from DataStore. Defaults match the stored defaults. */
    private data class Snapshot(
        val enabledProviderIds: Set<String>,
        val confidentialOptIn: Boolean,
        val profile: WorkProfile
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val snapshot: StateFlow<Snapshot> = combine(
        preferences.state,
        userPrefs.workSettings
    ) { prefsState, workSettings ->
        Snapshot(
            enabledProviderIds = prefsState.enabledProviderIds,
            confidentialOptIn = prefsState.confidentialEmailOptIn,
            profile = workSettings.profile
        )
    }
        // On a read error the last good value stays in place.
        .catch { }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            Snapshot(
                enabledProviderIds = IntegrationPreferencesState().enabledProviderIds,
                confidentialOptIn = false,
                profile = WorkProfile.CLIENT_WORK
            )
        )

    private val deviceCalendar = DeviceCalendarProvider(
        context = context,
        // Default on, respects integration preferences
        enabledChecker = { DeviceCalendarProvider.ID in snapshot.value.enabledProviderIds }
    )

    private val shareSheet = ShareSheetOutputChannel()
    private val safStorage = SafStorageProvider()

    private val googleEmail = GoogleEmailProvider(
        workProfileProvider = { snapshot.value.profile },
        explicitOptInProvider = { snapshot.value.confidentialOptIn },
        enabledChecker = { GoogleEmailProvider.ID in snapshot.value.enabledProviderIds }
    )

    private val microsoftEmail = MicrosoftEmailProvider(
        workProfileProvider = { snapshot.value.profile },
        explicitOptInProvider = { snapshot.value.confidentialOptIn },
        enabledChecker = { MicrosoftEmailProvider.ID in snapshot.value.enabledProviderIds }
    )

    // Hidden for MVP unless FEATURE_STUB_INTEGRATIONS is on (S-5).
    private val comingLater: List<StubIntegrationProvider> =
        if (BuildConfig.FEATURE_STUB_INTEGRATIONS) StubIntegrationProvider.comingLaterProviders() else emptyList()

    /** Primary active calendar provider. */
    val calendarProvider: CalendarProvider get() = deviceCalendar

    /** Primary active output channel. */
    val outputChannel: OutputChannel get() = shareSheet

    /** Primary active storage provider. */
    val storageProvider: StorageProvider get() = safStorage

    /** Email providers. */
    val emailProviders: List<EmailProvider> get() = listOf(googleEmail, microsoftEmail)

    /** Returns all available and stub providers. */
    fun allProviders(): List<IntegrationProvider> {
        val list = mutableListOf<IntegrationProvider>(
            deviceCalendar,
            shareSheet,
            safStorage
        )
        if (BuildConfig.FEATURE_INTEGRATIONS_EMAIL) {
            list.add(googleEmail)
            list.add(microsoftEmail)
        }
        list.addAll(comingLater)
        return list
    }

    /** Find a provider by unique ID. */
    fun findProvider(id: String): IntegrationProvider? {
        return allProviders().firstOrNull { it.id == id }
    }

    /** Query providers supporting a specific capability. */
    fun providersWithCapability(capability: Capability): List<IntegrationProvider> {
        return allProviders().filter { capability in it.capabilities }
    }

    /** Stream of providers paired with their live user preference states. */
    val providersFlow: Flow<List<IntegrationProvider>> = combine(
        preferences.state,
        userPrefs.workSettings
    ) { _, _ ->
        allProviders()
    }

    /** Stops the background collection of preferences. Call when the owner is cleared. */
    fun close() {
        scope.cancel()
    }
}
