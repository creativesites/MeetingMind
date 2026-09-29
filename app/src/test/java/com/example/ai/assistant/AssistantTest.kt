package com.example.ai.assistant

import com.example.ai.cloud.GeminiRequest
import com.example.ai.cloud.GeminiTransport
import com.example.ai.common.AiResult
import com.example.ai.notes.CitedItem
import com.example.ai.notes.NoteAiApply
import com.example.ai.notes.NoteAiOutcome
import com.example.ai.notes.SectionDraft
import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.RichText
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantTest {
    @Test fun parsesFencedJsonUnknownToolsAndProse() {
        val t = AssistantProtocol.parse("```json\n{\"say\":\"Done\",\"calls\":[{\"tool\":\"get_verses\",\"args\":{\"reference\":\"John 15:5\"}},{\"tool\":\"hack\",\"args\":{}}]}\n```")
        assertEquals("Done", t.say)
        assertEquals(AssistantTool.GET_VERSES, t.calls.single().tool)
        assertEquals("John 15:5", t.calls.single().str("reference"))
        assertEquals(listOf("hack"), t.unknownTools)
        assertEquals("Just talking.", AssistantProtocol.parse("Just talking.").say)
    }

    @Test fun guardRemovesVoiceOfGodButKeepsReports() {
        assertEquals("Take it slowly.", FaithGuard.clean("God is telling you to rest. Take it slowly."))
        val kept = "Your note says God told him to wait."
        assertEquals(kept, FaithGuard.clean(kept))
    }

    @Test fun promptAssetsLoadWithRequiredSections() {
        listOf("faith_assistant", "notes_assistant", "sermon_study", "ask_everything").forEach { id ->
            val p = com.example.ai.faith.Prompts.get(id)
            com.example.ai.faith.PromptAsset.REQUIRED_SECTIONS.forEach { p.section(it) }
        }
    }

    private class Host(override val scope: AssistantScope) : AssistantHost {
        val written = mutableListOf<String>()
        override suspend fun outline() = "b1 | paragraph | hello"
        override suspend fun read(call: ToolCall) = ToolResult("Read ${call.str("reference")}", "John 15:5 (NIV): I am the vine")
        override suspend fun write(call: ToolCall): Pair<ToolResult, AssistantAction> {
            written += call.tool.id
            return ToolResult("Added", "ok") to AssistantAction("a1", "Added a block")
        }
    }

    private class Script(val replies: List<String>) : GeminiTransport {
        val prompts = mutableListOf<GeminiRequest>()
        override suspend fun execute(request: GeminiRequest): AiResult<String> { prompts += request; return AiResult.Success(replies[prompts.size - 1]) }
        override fun isConfigured() = true
    }

    @Test fun readsThenWritesThenAnswers() = runBlocking {
        val script = Script(listOf(
            "{\"say\":\"\",\"calls\":[{\"tool\":\"get_verses\",\"args\":{\"reference\":\"John 15:5\"}}]}",
            "{\"say\":\"I added the verse.\",\"calls\":[{\"tool\":\"insert_scripture\",\"args\":{\"reference\":\"John 15:5\"}}]}"
        ))
        val host = Host(AssistantScope("Vine", true, "Sermon", noteId = "n", meetingId = "m", sermon = true))
        val reply = AssistantEngine(script, host).reply(emptyList(), "add the key verse")
        assertEquals("I added the verse.", reply.text)
        assertEquals(listOf("insert_scripture"), host.written)
        assertEquals(1, reply.actions.size)
        assertTrue(script.prompts[1].prompt.contains("I am the vine"))
        // A sermon note carries the faith contract and the sermon study guidance.
        assertTrue(script.prompts[0].systemInstruction.contains("Never claim to speak for God"))
        assertTrue(script.prompts[0].systemInstruction.contains("Useful study moves"))
    }

    @Test fun plainNotesDoNotGetFaithContractOrRecordingTools() {
        val sys = AssistantEngine(Script(emptyList()), Host(AssistantScope("Standup", false, "Meeting", noteId = "n"))).systemPrompt()
        assertFalse(sys.contains("Never claim to speak for God, and never"))
        assertFalse(sys.contains("- read_transcript"))
        assertTrue(sys.contains("insert_blocks"))
    }

    @Test fun serviceFailureComesBackAsAnErrorMessage() = runBlocking {
        val failing = object : GeminiTransport {
            override suspend fun execute(request: GeminiRequest): AiResult<String> = AiResult.Failed("Network down")
            override fun isConfigured() = true
        }
        val r = AssistantEngine(failing, Host(AssistantScope("x", false, "Note", noteId = "n"))).reply(emptyList(), "hi")
        assertTrue(r.error)
        assertTrue(r.text.contains("Network down"))
    }

    // ---- organise keeps what was generated before

    private fun b(id: String, text: String, type: NoteBlockType = NoteBlockType.PARAGRAPH, key: String? = null, src: BlockSource = BlockSource.USER) =
        NoteBlock(id, "n", 0, type, RichText.plain(text), source = src, sectionKey = key)

    @Test fun organisingKeepsAnEarlierSummaryAndActionsWhole() {
        val blocks = listOf(
            b("h", "Summary", NoteBlockType.HEADING_2, NoteAiApply.SUMMARY_KEY, BlockSource.AI),
            b("s1", "Point one", NoteBlockType.BULLET, NoteAiApply.SUMMARY_KEY, BlockSource.AI),
            b("s2", "Point two", NoteBlockType.BULLET, NoteAiApply.SUMMARY_KEY, BlockSource.AI),
            b("p1", "My own line"),
            b("ah", "Action items", NoteBlockType.HEADING_2, NoteAiApply.ACTIONS_KEY, BlockSource.AI),
            b("a1", "Call Mary", NoteBlockType.CHECKLIST, NoteAiApply.ACTIONS_KEY, BlockSource.AI)
        )
        // Even if the model cited the summary's own bullets, they are not moved or rewritten.
        val result = NoteAiOutcome.Sections(listOf(SectionDraft("k", "Key points", listOf(CitedItem("My own line", listOf("p1", "s1"))))), emptyList())
        val out = NoteAiApply.organized(blocks, "n", result)
        assertEquals(listOf("Summary", "Point one", "Point two"), out.take(3).map { it.content.text })
        assertTrue(out.any { it.id == "ah" } && out.any { it.id == "a1" })
        assertEquals(1, out.count { it.content.text == "Point one" })
        assertTrue(out.indexOfFirst { it.content.text == "Key points" } > 2)
    }

    // ---- house rule: no sparkles

    @Test fun noSparklesIconAnywhere() {
        val src = File("src/main/java")
        val hits = src.walkTopDown().filter { it.extension == "kt" && it.readText().contains("AutoAwesome") }.map { it.name }.toList()
        assertTrue("Sparkles icon still used in $hits", hits.isEmpty())
    }
}
