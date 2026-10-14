package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Nothing that must stay on the phone reaches a cloud model through the work-memory writers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkModelsPrivacyTest {
    private lateinit var f: PulseFixture
    private val context: Context = ApplicationProvider.getApplicationContext()

    private class Counting : GeminiTransport {
        var executed = 0; var asked = 0
        override suspend fun execute(request: GeminiRequest): AiResult<String> { executed++; return AiResult.Success("{\"executive\":[],\"recommended\":[]}") }
        override fun isConfigured() = true
        override suspend fun refreshConfigured(): Boolean { asked++; return true }
    }

    private object NoLocal : ModelStorage {
        override fun getModelDirectory(modelId: String): File = File("/nonexistent/$modelId")
        override fun isInstalled(modelId: String) = false
        override fun installedSizeBytes(modelId: String) = 0L
        override fun delete(modelId: String) = false
    }

    @Before fun setup() { f = PulseFixture(); MeetMindDatabase.setInstanceForTest(f.db); f.projectWorld() }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); f.db.close() }

    private fun models(t: Counting, profile: ProcessingProfile = ProcessingProfile.INTERNET) = DeviceWorkModels(context, NoLocal, t) { profile }

    @Test fun sensitiveNeverAsksTheCloud() = runBlocking {
        val t = Counting()
        assertNull(models(t).forPack(sensitive = true)) // no local model installed: no prose, not a cloud call
        assertEquals(0, t.executed); assertEquals(0, t.asked)
    }

    @Test fun ordinaryWorkFollowsTheProcessingMode() = runBlocking {
        val t = Counting()
        assertNotNull(models(t).forPack(sensitive = false))
        assertTrue(t.asked >= 1)
        val offline = Counting()
        assertNull(models(offline, ProcessingProfile.OFFLINE).forPack(sensitive = false))
        assertEquals(0, offline.asked)
    }

    @Test fun aConfidentialProjectBriefMakesNoCloudCall() {
        runBlocking { f.db.notebookDao().upsert(f.db.notebookDao().getById("nb")!!.copy(propertiesJson = "{\"orgId\":\"org1\",\"confidential\":true}")) }
        val t = Counting()
        val brief = runBlocking { BriefBuilder(f.db, models(t), DevicePackPrivacy(context)) { f.now }.build(BriefTarget.forProject("nb")) }
        assertTrue(brief.confidential)
        assertEquals(0, t.executed); assertEquals(0, t.asked)
        assertFalse(brief.hasProse)
    }

    @Test fun aConfidentialPersonMakesEveryWriterStayLocal() {
        runBlocking { f.db.peopleDao().upsert(f.db.peopleDao().getById("ana")!!.copy(confidential = true)) }
        val t = Counting()
        val privacy = DevicePackPrivacy(context)
        runBlocking {
            BriefBuilder(f.db, models(t), privacy) { f.now }.build(BriefTarget.forPerson("ana"))
            MemoryRepository(f.db, models(t), privacy, { f.now }).history(ContextType.PERSON, "ana")
            PrepareWriter(f.db, models(t), privacy) { f.now }.write(PackScope.Entity(ContextType.PERSON, "ana"), Prepare(f.db) { f.now }.forEntity(ContextType.PERSON, "ana", f.now))
            com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAsk(f.db, models(t), privacy, { f.now }).ask("deck", com.craftflowtechnologies.meetingmind.ai.assistant.AskScope(ContextType.PERSON, "ana", label = "Ana"))
        }
        assertEquals(0, t.executed); assertEquals(0, t.asked)
    }

    @Test fun anOrdinaryProjectDoesReachTheModelSoTheTestsAboveMeanSomething() {
        val t = Counting()
        runBlocking { BriefBuilder(f.db, models(t), DevicePackPrivacy(context)) { f.now }.build(BriefTarget.forProject("nb")) }
        assertTrue(t.executed >= 1)
    }
}
