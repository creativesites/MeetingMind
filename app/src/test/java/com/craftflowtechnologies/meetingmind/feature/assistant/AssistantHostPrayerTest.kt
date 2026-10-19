package com.craftflowtechnologies.meetingmind.feature.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantScope
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantTool
import com.craftflowtechnologies.meetingmind.ai.assistant.ToolCall
import com.craftflowtechnologies.meetingmind.core.database.DatabaseGuard
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/** Priority 4 (prayer management), exercised against the real NoteRepository/Room, not a fake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantHostPrayerTest {
    private lateinit var context: Context
    private lateinit var host: AppAssistantHost

    private fun call(tool: AssistantTool, args: JSONObject = JSONObject()) = ToolCall(tool, args)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        MeetMindDatabase.forget()
        context.deleteDatabase(DatabaseGuard.NAME)
        host = AppAssistantHost(context, AssistantScope("Faith", faith = true, kindLabel = "Faith"), editor = null)
    }

    @After
    fun tearDown() {
        MeetMindDatabase.forget()
        context.deleteDatabase(DatabaseGuard.NAME)
    }

    @Test fun `creates a prayer request, lists it, then marks it answered`() = runBlocking {
        val created = host.write(call(AssistantTool.CREATE_PRAYER_REQUEST, JSONObject().put("title", "Mom's surgery")))
        assertTrue(created.first.ok)
        val requestId = created.second.openNoteId!!

        val listed = host.read(call(AssistantTool.LIST_PRAYER_REQUESTS))
        assertTrue(listed.ok)
        assertTrue(listed.text.contains("Mom's surgery"))

        val answered = host.write(call(AssistantTool.MARK_PRAYER_ANSWERED, JSONObject().put("request_id", requestId)))
        assertTrue(answered.first.ok)

        val listedAfter = host.read(call(AssistantTool.LIST_PRAYER_REQUESTS))
        assertFalse("an answered request no longer shows as open", listedAfter.text.contains("Mom's surgery"))
    }

    @Test fun `marking an unknown id fails instead of silently doing nothing`() = runBlocking {
        val result = host.write(call(AssistantTool.MARK_PRAYER_ANSWERED, JSONObject().put("request_id", "nope")))
        assertFalse(result.first.ok)
    }

    @Test fun `marking the same request answered twice fails the second time`() = runBlocking {
        val created = host.write(call(AssistantTool.CREATE_PRAYER_REQUEST, JSONObject().put("title", "A new job")))
        val requestId = created.second.openNoteId!!
        assertTrue(host.write(call(AssistantTool.MARK_PRAYER_ANSWERED, JSONObject().put("request_id", requestId))).first.ok)
        assertFalse(host.write(call(AssistantTool.MARK_PRAYER_ANSWERED, JSONObject().put("request_id", requestId))).first.ok)
    }

    @Test fun `undo on mark-answered puts the request back on the open list`() = runBlocking {
        val created = host.write(call(AssistantTool.CREATE_PRAYER_REQUEST, JSONObject().put("title", "Safe travels")))
        val requestId = created.second.openNoteId!!
        val (_, action) = host.write(call(AssistantTool.MARK_PRAYER_ANSWERED, JSONObject().put("request_id", requestId)))
        assertTrue(host.undo(action.id))
        assertTrue(host.read(call(AssistantTool.LIST_PRAYER_REQUESTS)).text.contains("Safe travels"))
    }
}
