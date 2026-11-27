package com.craftflowtechnologies.meetingmind.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [AppPreferencesState.userName] is the one piece of Phase 15 §5's "User Identity" requirement
 * that's just a stored preference (the onboarding UI collecting it is exercised separately) — the
 * behavior worth pinning down here is that "the user typed nothing" and "the user has no name set"
 * are the same state, never two different ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesManagerTest {

    @Test
    fun `userName defaults to null before anything is set`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UserPreferencesManager(context)

        assertNull(manager.preferencesFlow.first().userName)
    }

    @Test
    fun `setUserName persists a real name, trimmed`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UserPreferencesManager(context)

        manager.setUserName("  Winston  ")

        assertEquals("Winston", manager.preferencesFlow.first().userName)
    }

    @Test
    fun `setUserName with a blank string clears it back to null rather than storing empty`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UserPreferencesManager(context)
        manager.setUserName("Winston")

        manager.setUserName("   ")

        assertNull(manager.preferencesFlow.first().userName)
    }

    @Test
    fun `processing defaults to the private profile`() {
        // Asserted on the state's own default rather than on a freshly-read DataStore, because
        // every test in this class shares one DataStore file and would otherwise see whatever a
        // previously-run test wrote.
        assertEquals(
            com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.OFFLINE,
            AppPreferencesState().processingProfile
        )
    }

    @Test
    fun `the processing profile round-trips when the user chooses Internet`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UserPreferencesManager(context)

        manager.setProcessingProfile(com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.INTERNET)

        assertEquals(
            com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.INTERNET,
            manager.preferencesFlow.first().processingProfile
        )
    }

    @Test
    fun `an unreadable stored profile falls back to OFFLINE, never to a cloud profile`() {
        // A corrupted or future-version preference must not be able to start uploading recordings.
        assertEquals(
            com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.OFFLINE,
            com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.fromNameOrDefault("SOMETHING_ELSE")
        )
        assertEquals(
            com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.OFFLINE,
            com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.fromNameOrDefault(null)
        )
    }

    @Test
    fun `app lock defaults off and is persisted once set`() = runBlocking {
        assertEquals(false, AppPreferencesState().appLockEnabled)
        val manager = UserPreferencesManager(ApplicationProvider.getApplicationContext())
        manager.setAppLockEnabled(true)
        assertEquals(true, manager.preferencesFlow.first().appLockEnabled)
        manager.setAppLockEnabled(false)
        assertEquals(false, manager.preferencesFlow.first().appLockEnabled)
    }

    @Test
    fun `selectedLanguage defaults to English and switches ASR model when an African language is set`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UserPreferencesManager(context)
        assertEquals("en", AppPreferencesState().selectedLanguage)

        manager.setSelectedLanguage("sna")
        val snaPrefs = manager.preferencesFlow.first()
        assertEquals("sna", snaPrefs.selectedLanguage)
        assertEquals(com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog.omnilingualAsr300mInt8.id, snaPrefs.selectedAsrModelId)

        manager.setSelectedLanguage("bem")
        val bemPrefs = manager.preferencesFlow.first()
        assertEquals("bem", bemPrefs.selectedLanguage)
        assertEquals(com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog.omnilingualAsr300mInt8.id, bemPrefs.selectedAsrModelId)

        // Switching to 1B model should be preserved when switching between African languages
        manager.setSelectedAsrModel(com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog.omnilingualAsr1bInt8.id)
        manager.setSelectedLanguage("nya")
        val nyaPrefs = manager.preferencesFlow.first()
        assertEquals("nya", nyaPrefs.selectedLanguage)
        assertEquals(com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog.omnilingualAsr1bInt8.id, nyaPrefs.selectedAsrModelId)

        // Whisper preferred language auto-selects Whisper Turbo
        manager.setSelectedLanguage("ja")
        val jaPrefs = manager.preferencesFlow.first()
        assertEquals("ja", jaPrefs.selectedLanguage)
        assertEquals(com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog.whisperTurboInt8.id, jaPrefs.selectedAsrModelId)

        // Multilingual response, summary, translation, and auto-detect settings
        manager.setAiResponseLanguage("fr")
        manager.setSummaryLanguage("de")
        manager.setTranslationTargetLanguage("es")
        manager.setAutoDetectLanguage(true)
        val multiPrefs = manager.preferencesFlow.first()
        assertEquals("fr", multiPrefs.aiResponseLanguage)
        assertEquals("de", multiPrefs.summaryLanguage)
        assertEquals("es", multiPrefs.translationTargetLanguage)
        assertTrue(multiPrefs.autoDetectLanguage)
    }

    @Test
    fun `app language preference field round-trips correctly`() = runBlocking {
        val manager = UserPreferencesManager(ApplicationProvider.getApplicationContext())
        assertNull(AppPreferencesState().appLanguage)

        manager.setAppLanguage("sw")
        val prefs = manager.preferencesFlow.first()
        assertEquals("sw", prefs.appLanguage)

        manager.setAppLanguage(null)
        val resetPrefs = manager.preferencesFlow.first()
        assertNull(resetPrefs.appLanguage)
    }
}
