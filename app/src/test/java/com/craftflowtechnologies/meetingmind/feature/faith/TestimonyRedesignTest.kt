package com.craftflowtechnologies.meetingmind.feature.faith

import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteContent
import com.craftflowtechnologies.meetingmind.core.share.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TestimonyRedesignTest {

    private fun sampleNote(
        id: String = "t1",
        title: String = "",
        metadata: Map<String, String> = emptyMap(),
        createdAt: Long = 10_000_000_000L,
        eventDate: Long? = createdAt
    ) = Note(
        id = id,
        title = title,
        workflow = RecordingType.TESTIMONY,
        notebookId = "faith",
        createdAt = createdAt,
        updatedAt = createdAt,
        eventDate = eventDate,
        pinned = false,
        isPrivate = true,
        status = NoteStatus.OPEN,
        answeredAt = null,
        metadata = metadata
    )

    @Test
    fun `NoteContent hasUserContent keeps testimony notes with metadata even before body text`() {
        val draftWithCategory = sampleNote(
            metadata = mapOf(NoteContent.DRAFT to "1", TestimonyMeta.CATEGORY to "healing")
        )
        assertTrue(NoteContent.hasUserContent(draftWithCategory, emptyList()))

        val draftWithVerse = sampleNote(
            metadata = mapOf(NoteContent.DRAFT to "1", TestimonyMeta.VERSE_REF to "Psalm 23:1")
        )
        assertTrue(NoteContent.hasUserContent(draftWithVerse, emptyList()))

        val untouchedBlankDraft = sampleNote(
            metadata = mapOf(NoteContent.DRAFT to "1")
        )
        assertFalse(NoteContent.hasUserContent(untouchedBlankDraft, emptyList()))
    }

    @Test
    fun `TestimonyBeats round-trips through template blocks while preserving audio attachments`() {
        var seq = 0
        val beats = TestimonyBeats(
            situation = "Lost sleep before results",
            prayedFor = "Asked for peace and healing",
            whatHappened = "Report came back completely clear",
            learned = "God's timing holds steady",
            scriptureNote = "Psalm 30:2"
        )
        val audioBlock = NoteBlock(
            id = "aud1",
            noteId = "t1",
            position = 9,
            type = NoteBlockType.AUDIO,
            payload = mapOf(NoteBlock.PAYLOAD_ATTACHMENT_ID to "att1")
        )
        val blocks = TestimonyBeats.toBlocks(
            noteId = "t1",
            existingBlocks = listOf(audioBlock),
            beats = beats,
            idGenerator = { prefix -> "${prefix}_${++seq}" }
        )

        val parsed = TestimonyBeats.fromBlocks(blocks)
        assertEquals(beats.situation, parsed.situation)
        assertEquals(beats.prayedFor, parsed.prayedFor)
        assertEquals(beats.whatHappened, parsed.whatHappened)
        assertEquals(beats.learned, parsed.learned)
        assertEquals(beats.scriptureNote, parsed.scriptureNote)
        assertTrue(blocks.any { it.id == "aud1" && it.type == NoteBlockType.AUDIO })
    }

    @Test
    fun `TestimonyMapper maps category, scripture, Amen count, Circles and prayer duration`() {
        val prayerStart = 1_700_000_000_000L
        val answeredAt = prayerStart + TimeUnit.DAYS.toMillis(21)
        val prayer = sampleNote(id = "p1", title = "Job breakthrough", createdAt = prayerStart).copy(
            workflow = RecordingType.PRAYER_REQUEST,
            status = NoteStatus.ANSWERED,
            answeredAt = answeredAt
        )
        val testimonyNote = sampleNote(
            id = "t2",
            title = "Offer arrived!",
            eventDate = answeredAt,
            metadata = mapOf(
                TestimonyMeta.CATEGORY to "provision",
                TestimonyMeta.VERSE_REF to "Jeremiah 29:11",
                TestimonyMeta.REJOICE_COUNT to "4",
                TestimonyMeta.USER_REJOICED to "1",
                TestimonyMeta.SHARED_CIRCLES to "Grace Cell, Family"
            )
        )
        val bodyBlock = NoteBlock(
            id = "b1",
            noteId = "t2",
            position = 0,
            type = NoteBlockType.PARAGRAPH,
            content = RichText.plain("Received the written offer three weeks after praying."),
            sectionKey = "what_happened"
        )

        val story = TestimonyMapper.fromNote(
            note = testimonyNote,
            blocks = listOf(bodyBlock),
            linkedPrayer = prayer,
            hasAudio = true
        )

        assertEquals(TestimonyCategory.PROVISION, story.category)
        assertEquals("Jeremiah 29:11", story.verseRef)
        assertEquals(4, story.rejoiceCount)
        assertTrue(story.userRejoiced)
        assertEquals(listOf("Grace Cell", "Family"), story.sharedToCircles)
        assertEquals(21, story.prayerDurationDays)
        assertEquals("Answered in 3 weeks", formatDurationLabel(story.prayerDurationDays))
        assertTrue(story.hasAudio)

        val shareText = buildTestimonyShareText(story, includeAppLink = true)
        assertTrue(AppLinks.containsDownloadLink(shareText))
        assertTrue(shareText.contains("Jeremiah 29:11"))

        val card = buildTestimonyShareCardRequest(story)
        assertEquals("TESTIMONY · PROVISION", card.content.eyebrow)
        assertEquals("Jeremiah 29:11", card.content.reference)
    }
}
