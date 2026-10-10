package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.ActionItem
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.Decision
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.Question
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.feature.notes.editor.BlockEditing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptBlocksTest {

    private fun seg(id: String, start: Long, end: Long, text: String, speakerId: String? = null, name: String? = null,
                    edited: Boolean = false, cleaned: String? = null) =
        TranscriptSegment(id = id, meetingId = "m", speakerId = speakerId, speakerName = name, startMs = start, endMs = end,
            text = text, isUserEdited = edited, cleanedText = cleaned)

    private val speakers = listOf(
        Speaker("s1", "m", 0, "Speaker 1", "Pastor Dan", "#000"),
        Speaker("s2", "m", 1, "Speaker 2", "", "#111")
    )

    private var n = 0
    private fun build(segments: List<TranscriptSegment>, key: String? = null) =
        TranscriptBlocks.build("note", "m", segments, speakers, key) { "b${n++}" }

    @Test
    fun `speaker changes start a new block and use the current speaker name`() {
        val blocks = build(listOf(
            seg("a", 0, 5_000, "Welcome everyone.", "s1", "old name"),
            seg("b", 5_000, 9_000, "Thank you pastor.", "s2"),
            seg("c", 9_000, 12_000, "Let us pray.", "s1")
        ))
        assertEquals(listOf("Pastor Dan", "Speaker 2", "Pastor Dan"), blocks.map { it.payload[NoteBlock.PAYLOAD_SPEAKER] })
        assertTrue(blocks.all { it.type == NoteBlockType.TRANSCRIPT_EXCERPT && it.source == BlockSource.TRANSCRIPT })
    }

    @Test
    fun `fragments of one turn are merged and carry range and segment ids`() {
        val blocks = build(listOf(
            seg("a", 1_000, 4_000, "Grace is a gift. It is free.", "s1"),
            seg("b", 4_000, 8_000, "We cannot earn it. We receive it.", "s1")
        ))
        val b = blocks.single()
        assertEquals("Grace is a gift. It is free. We cannot earn it. We receive it.", b.content.text)
        assertEquals("1000", b.payload[NoteBlock.PAYLOAD_START_MS])
        assertEquals("8000", b.payload[NoteBlock.PAYLOAD_END_MS])
        assertEquals("m", b.payload[NoteBlock.PAYLOAD_MEETING_ID])
        assertEquals(listOf("a", "b"), b.sourceSegmentIds)
    }

    @Test
    fun `a long monologue becomes paragraphs of at most six sentences`() {
        val segs = (0 until 40).map { seg("p$it", it * 10_000L, it * 10_000L + 9_000, "Sentence one of part $it. Sentence two of part $it.", "s1") }
        val blocks = build(segs)
        assertTrue(blocks.size > 5)
        blocks.forEach { b ->
            val sentences = TranscriptBlocks.sentencesOf(b.content.text).size
            assertTrue("$sentences sentences", sentences in 1..TranscriptBlocks.MAX_SENTENCES)
            assertTrue(b.content.text.length <= TranscriptBlocks.MAX_CHARS + 200)
        }
        assertEquals(80, blocks.sumOf { TranscriptBlocks.sentencesOf(it.content.text).size })
        // Time ranges never go backwards.
        assertEquals(blocks.map { it.payload["startMs"]!!.toLong() }, blocks.map { it.payload["startMs"]!!.toLong() }.sorted())
    }

    @Test
    fun `one huge segment is split without losing its id`() {
        val text = (1..14).joinToString(" ") { "Sentence number $it." }
        val blocks = build(listOf(seg("a", 0, 60_000, text, "s1")))
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it.sourceSegmentIds == listOf("a") })
    }

    @Test
    fun `the person's edit wins over cleaned and raw text`() {
        val blocks = build(listOf(
            seg("a", 0, 1000, "raw text here.", "s1", edited = true, cleaned = "stale cleanup."),
            seg("b", 1000, 2000, "raw second.", "s1", cleaned = "Cleaned second.")
        ))
        assertEquals("raw text here. Cleaned second.", blocks.single().content.text)
        assertFalse("stale" in blocks.single().content.text)
    }

    @Test
    fun `unknown speakers are not invented and blank segments are skipped`() {
        val blocks = build(listOf(seg("a", 0, 1000, "Hello there."), seg("b", 1000, 2000, "   ")))
        assertEquals(null, blocks.single().payload[NoteBlock.PAYLOAD_SPEAKER])
        assertEquals(emptyList<NoteBlock>(), build(emptyList()))
    }

    @Test
    fun `segments are ordered by time and tagged with the section key`() {
        val blocks = build(listOf(seg("b", 5000, 6000, "Second.", "s1"), seg("a", 0, 1000, "First.", "s1")), key = "sermon_transcript")
        assertEquals("First. Second.", blocks.single().content.text)
        assertEquals("sermon_transcript", blocks.single().sectionKey)
    }

    @Test
    fun `inserted blocks go after the focused block`() {
        val existing = listOf(
            NoteBlock("x", "note", 0, NoteBlockType.PARAGRAPH, RichText.plain("one")),
            NoteBlock("y", "note", 1, NoteBlockType.PARAGRAPH, RichText.plain("two"))
        )
        val added = build(listOf(seg("a", 0, 1000, "Hello.", "s1")))
        val r = BlockEditing.insertAfter(existing, 0, added)
        assertEquals(listOf("x", added.single().id, "y"), r.blocks.map { it.id })
    }

    @Test
    fun `summary becomes headed sections with a checklist and evidence`() {
        val blocks = SummaryBlocks.build(
            "note", "m", "We reviewed the launch.\n\nBudget is tight.",
            decisions = listOf(Decision("d", "m", "Ship on Friday", sourceSegmentIds = listOf("a"))),
            actionItems = listOf(ActionItem("t", "m", "Send the deck", assigneeName = "Ann", deadline = "Mon", isCompleted = true, sourceSegmentIds = listOf("b"))),
            questions = listOf(Question("q", "m", "Who owns QA?")),
            segmentStarts = mapOf("a" to 3000L)
        ) { "s${n++}" }
        assertEquals(
            listOf("HEADING_2", "PARAGRAPH", "PARAGRAPH", "HEADING_3", "BULLET", "HEADING_3", "CHECKLIST", "HEADING_3", "BULLET"),
            blocks.map { it.type.name }
        )
        assertTrue(blocks.all { it.source == BlockSource.AI })
        assertEquals("Summary", blocks.first().content.text)
        val decision = blocks.first { it.content.text == "Ship on Friday" }
        assertEquals(listOf("a"), decision.sourceSegmentIds)
        assertEquals("3000", decision.payload[NoteBlock.PAYLOAD_START_MS])
        val action = blocks.first { it.type == NoteBlockType.CHECKLIST }
        assertEquals("Send the deck (Ann, due Mon)", action.content.text)
        assertTrue(action.checked)
    }

    @Test
    fun `empty summary sections render nothing`() {
        assertEquals(emptyList<NoteBlock>(), SummaryBlocks.build("note", "m", "  "))
    }
}
