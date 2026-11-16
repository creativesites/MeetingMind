package com.craftflowtechnologies.meetingmind.ai.notes

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.notes.NoteCitations
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A polish reply wrapped in JSON, with maths and [n] citations, still makes a clean note. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotePolishJsonReplyTest {

    private val segments = listOf(
        TranscriptSegment("l1", "m2", startMs = 0, endMs = 20_000, text = "Today we factor trinomials into two binomials."),
        TranscriptSegment("l2", "m2", startMs = 20_000, endMs = 45_000, text = "Two x squared plus five x plus two is two x plus one times x plus two.")
    )

    private fun model(reply: String, onPrompt: (String) -> Unit = {}) = object : LanguageModel {
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> { onPrompt(prompt); return AiResult.Success(reply) }
    }

    @Test fun `JSON wrapped reply becomes clean blocks with cited moments`() = runBlocking {
        val reply = """{ "notes": "## Overview\nWe factor trinomials [1].\n\n## Worked Example\n${'$'}2x^2 + 5x + 2 = (2x+1)(x+2)${'$'} [2]" }"""
        val blocks = (NotePolishEngine(model(reply)).polish("n", "m2", RecordingType.LECTURE, segments) as AiResult.Success).value
        val all = blocks.joinToString("\n") { it.content.text }
        assertFalse(all, all.contains("{") && all.contains("\"notes\""))
        assertFalse(all, all.contains("\\n") || all.contains("##") || all.contains("[1]") || all.contains("[2]"))
        assertTrue(blocks.any { it.type == NoteBlockType.HEADING_2 && it.content.text == "Overview" })
        val example = blocks.first { it.content.text.startsWith("\$2x^2") }
        assertEquals("\$2x^2 + 5x + 2 = (2x+1)(x+2)\$", example.content.text)
        assertEquals(listOf(20_000L), NoteCitations.of(example).map { it.startMs })
        assertEquals("20000", example.payload[NoteBlock.PAYLOAD_START_MS])
        assertEquals(listOf("l2"), example.sourceSegmentIds)
    }

    @Test fun `prompt numbers the transcript and asks for Markdown maths and chemistry`() = runBlocking {
        var prompt = ""
        NotePolishEngine(model("## Notes\nok") { prompt = it }).polish("n", "m2", RecordingType.LECTURE, segments)
        assertTrue(prompt.contains("[1] Today we factor"))
        assertTrue(prompt.contains("[2] Two x squared"))
        assertTrue(prompt.contains("never wrap it in JSON"))
        assertTrue(prompt.contains("\$...\$") && prompt.contains("\\ce{"))
    }
}
