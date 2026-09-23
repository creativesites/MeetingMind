package com.example.ai.faith

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.core.model.BlockSource
import com.example.core.model.NoteBlockType
import com.example.core.model.TranscriptSegment
import com.example.core.scripture.ScriptureDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SermonExtractionTest {

    private val segments = listOf(
        TranscriptSegment(id = "s1", meetingId = "m", speakerName = "Pastor James", startMs = 0, endMs = 20_000,
            text = "Good morning church. Turn with me to Hebrews chapter eleven verse one."),
        TranscriptSegment(id = "s2", meetingId = "m", speakerName = "Pastor James", startMs = 20_000, endMs = 60_000,
            text = "Faith is not a feeling, it is trust in a person. As Romans eight twenty eight says, all things work together."),
        TranscriptSegment(id = "s3", meetingId = "m", speakerName = "Pastor James", startMs = 60_000, endMs = 90_000,
            text = "This week, call someone who is struggling and pray with them.")
    )

    private val good = """
        {"title":"Faith that holds",
         "main_scripture":[{"reference":"Hebrews 11:1","segment_ids":["s1"]},{"reference":"Hezekiah 3:1","segment_ids":["s1"]}],
         "key_message":{"text":"Faith is trust in a person, not a feeling.","segment_ids":["s2"]},
         "key_points":[{"text":"Faith is trust","segment_ids":["s2"]},{"text":"Invented point","segment_ids":["nope"]}],
         "quotes":[{"text":"Faith is not a feeling, it is trust in a person","segment_ids":["s1"]},{"text":"God wants you rich and happy","segment_ids":["s2"]}],
         "application":{"text":"Call someone struggling and pray with them","segment_ids":["s3"]},
         "prayer_points":[],
         "reflection_questions":[{"text":"Who could you call this week?","segment_ids":["s3"]}]}
    """.trimIndent()

    @Test
    fun `only cited, real, verbatim content survives`() {
        val e = SermonExtractionParser.parse("```json\n$good\n```", segments)!!
        assertEquals("Faith that holds", e.title)
        // A book that doesn't exist is dropped; the real one is kept with its citation.
        assertEquals(listOf("Hebrews 11:1"), e.mainScripture.map { it.reference.display() })
        // A point citing a paragraph that doesn't exist is dropped.
        assertEquals(listOf("Faith is trust"), e.keyPoints.map { it.text })
        // A made-up quote is dropped; a real one with the wrong citation is re-anchored.
        assertEquals(1, e.quotes.size)
        assertEquals(listOf("s2"), e.quotes.single().segmentIds)
        assertEquals("s3", e.application!!.segmentIds.single())
    }

    @Test
    fun `garbage from the model is not a sermon`() {
        assertNull(SermonExtractionParser.parse("I think the sermon was about faith.", segments))
    }

    @Test
    fun `every faith prompt carries the faith clause and the fidelity contract`() {
        val prompt = FaithPrompts.sermonPrompt(segments)
        assertTrue(prompt.contains(FaithPrompts.FAITH_CONTRACT.trim()))
        assertTrue(prompt.contains("interpret, explain or apply scripture in your own voice"))
        assertTrue(prompt.contains("[s1] Pastor James:"))
    }

    @Test
    fun `detection pins each reference to its paragraph and moment`() {
        val found = ScriptureDetector.detect(segments)
        assertEquals(listOf("Hebrews 11:1", "Romans 8:28"), found.map { it.reference.display() })
        assertEquals("s2", found[1].segmentId)
        assertTrue(found[1].startMs in 20_000..60_000)
    }

    @Test
    fun `the note has sections in order with evidence behind every item`() {
        val e = SermonExtractionParser.parse(good, segments)!!
        val g = SermonNoteBuilder.build("n", "m", segments, e, ScriptureDetector.detect(segments))
        val headings = g.blocks.filter { it.type == NoteBlockType.HEADING_2 }.map { it.content.text }
        assertEquals(listOf("Scripture", "Main message", "Key points", "Memorable quotes", "Application", "Questions to reflect on", "Scripture references"), headings)
        val scripture = g.blocks.filter { it.type == NoteBlockType.SCRIPTURE }
        assertTrue(scripture.all { it.payload["startMs"] != null && it.payload["meetingId"] == "m" })
        assertEquals(scripture.size, g.refs.size)
        assertTrue(g.blocks.filter { it.source == BlockSource.AI && it.type != NoteBlockType.HEADING_2 }.all { it.sourceSegmentIds.isNotEmpty() })
        assertEquals(NoteBlockType.TRANSCRIPT_EXCERPT, g.blocks.first { it.sectionKey == "quotes" && it.type != NoteBlockType.HEADING_2 }.type)
    }

    @Test
    fun `with no model the note still lists the scripture that was read`() {
        val g = SermonNoteBuilder.build("n", "m", segments, null, ScriptureDetector.detect(segments))
        assertEquals(listOf("Scripture", "Scripture references"), g.blocks.filter { it.type == NoteBlockType.HEADING_2 }.map { it.content.text })
    }

    @Test
    fun `long sermons are read in parts and merged`() = runBlocking {
        val many = (1..60).map { i ->
            TranscriptSegment(id = "p$i", meetingId = "m", startMs = i * 10_000L, endMs = i * 10_000L + 9_000, text = "Point number $i is that grace is enough for every day. ".repeat(4))
        }
        val calls = mutableListOf<String>()
        val model = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
                calls += prompt
                val id = Regex("\\[(p\\d+)]").find(prompt)!!.groupValues[1]
                return AiResult.Success("""{"key_points":[{"text":"Grace from $id","segment_ids":["$id"]}]}""")
            }
        }
        val engine = SermonExtractionEngine(model, contextTokens = 4096)
        val result = engine.extract(many) as AiResult.Success
        assertTrue("expected several parts, got ${calls.size}", calls.size > 1)
        assertTrue(calls.first().contains("part 1 of"))
        assertEquals(calls.size, result.value.keyPoints.size)
    }
}
