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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Change detection never sends confidential material to a cloud model. A fake transport counts
 * every call and every question about whether it is configured; for a confidential recording both
 * stay at zero even in Internet mode with a key entered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangePrivacyTest {
    private lateinit var f: PulseFixture
    private val context: Context = ApplicationProvider.getApplicationContext()

    private class CountingTransport : GeminiTransport {
        var executed = 0
        var asked = 0
        override suspend fun execute(request: GeminiRequest): AiResult<String> { executed++; return AiResult.Success("{\"replaces\": true}") }
        override fun isConfigured() = true
        override suspend fun refreshConfigured(): Boolean { asked++; return true }
    }

    private object NoLocalModels : ModelStorage {
        override fun getModelDirectory(modelId: String): File = File("/nonexistent/$modelId")
        override fun isInstalled(modelId: String) = false
        override fun installedSizeBytes(modelId: String) = 0L
        override fun delete(modelId: String) = false
    }

    @Before fun setup() {
        f = PulseFixture()
        MeetMindDatabase.setInstanceForTest(f.db)
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Use WordPress for the site")
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); f.db.close() }

    private fun world() {
        f.addMeeting("m2", f.now, listOf("Actually we will not use WordPress for the site."))
        f.addSignal("m2", "sig", ItemKind.DECISION, "We will not use WordPress for the site", listOf(0))
    }

    private fun proposals(transport: CountingTransport) = runBlocking {
        val models = DeviceChangeModels(context, NoLocalModels, transport) { ProcessingProfile.INTERNET }
        ChangeDetector(f.db, models) { f.now }.detect("m2")
    }

    @Test fun aConfidentialProjectNeverReachesTheCloud() {
        runBlocking { f.db.notebookDao().upsert(f.db.notebookDao().getById("nb")!!.copy(propertiesJson = "{\"orgId\":\"org1\",\"confidential\":true}")) }
        world()
        val transport = CountingTransport()
        val found = proposals(transport)
        assertEquals(0, transport.executed)
        assertEquals(0, transport.asked) // the cloud route wasn't even considered
        assertEquals(1, found.size) // the lexical proposal still stands, with no model
    }

    @Test fun aConfidentialPersonNeverReachesTheCloud() {
        runBlocking { f.db.peopleDao().upsert(f.db.peopleDao().getById("ana")!!.copy(confidential = true)) }
        world()
        runBlocking { f.db.peopleDao().link(com.craftflowtechnologies.meetingmind.core.database.NotePersonCrossRef("note_m2", "ana")) }
        val transport = CountingTransport()
        proposals(transport)
        assertEquals(0, transport.executed); assertEquals(0, transport.asked)
    }

    @Test fun ordinaryWorkFollowsTheProcessingMode() {
        world()
        val transport = CountingTransport()
        val found = proposals(transport)
        assertTrue("the fake transport is reachable, so the tests above mean something", transport.executed >= 1)
        assertTrue(found.single().checkedByModel)
    }

    @Test fun offlineModeNeverAsksTheCloudEither() {
        world()
        val transport = CountingTransport()
        runBlocking { ChangeDetector(f.db, DeviceChangeModels(context, NoLocalModels, transport) { ProcessingProfile.OFFLINE }) { f.now }.detect("m2") }
        assertEquals(0, transport.executed); assertEquals(0, transport.asked)
    }
}
