package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine

/**
 * Registry of all integration providers in MeetingMind.
 * Provides access to available providers, their status, and their capabilities.
 */
class IntegrationRegistry(
    private val context: Context,
    private val preferences: IntegrationPreferences = IntegrationPreferences(context),
    private val userPrefs: UserPreferencesManager = UserPreferencesManager(context)
) {
    private val deviceCalendar = DeviceCalendarProvider(
        context = context,
        enabledChecker = {
            // Default on, respects integration preferences
            runBlockingCatching { preferences.isProviderEnabled(DeviceCalendarProvider.ID) } ?: true
        }
    )

    private val shareSheet = ShareSheetOutputChannel()
    private val safStorage = SafStorageProvider()

    private val googleEmail = GoogleEmailProvider(
        workProfileProvider = {
            runBlockingCatching { userPrefs.workSettings.firstOrNull()?.profile } ?: WorkProfile.CLIENT_WORK
        },
        explicitOptInProvider = {
            runBlockingCatching { preferences.state.firstOrNull()?.confidentialEmailOptIn } ?: false
        },
        enabledChecker = {
            runBlockingCatching { preferences.isProviderEnabled(GoogleEmailProvider.ID) } ?: false
        }
    )

    private val microsoftEmail = MicrosoftEmailProvider(
        workProfileProvider = {
            runBlockingCatching { userPrefs.workSettings.firstOrNull()?.profile } ?: WorkProfile.CLIENT_WORK
        },
        explicitOptInProvider = {
            runBlockingCatching { preferences.state.firstOrNull()?.confidentialEmailOptIn } ?: false
        },
        enabledChecker = {
            runBlockingCatching { preferences.isProviderEnabled(MicrosoftEmailProvider.ID) } ?: false
        }
    )

    private val comingLater = StubIntegrationProvider.comingLaterProviders()

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
        if (com.craftflowtechnologies.meetingmind.BuildConfig.FEATURE_INTEGRATIONS_EMAIL) {
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
    ) { prefsState, workSettings ->
        allProviders()
    }

    private fun <T> runBlockingCatching(block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.runBlocking { block() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun <T> Flow<T>.firstOrNull(): T? {
        return try {
            this.first()
        } catch (_: Exception) {
            null
        }
    }
}
