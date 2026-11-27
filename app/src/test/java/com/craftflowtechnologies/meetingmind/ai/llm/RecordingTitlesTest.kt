package com.craftflowtechnologies.meetingmind.ai.llm

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.common.MeetingTitleGenerator
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.TitleSource
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RecordingTitlesTest {

    private val transcriptTexts = listOf(
        "We will ship the new onboarding flow on Friday.",
        "Sounds good to me."
    )

    private val recordedAt = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(2026, Calendar.OCTOBER, 24, 14, 20, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `a failed model with a real transcript gives a content title, never a date`() {
        val decision = RecordingTitles.resolve(
            existingTitle = "Untitled Meeting",
            existingTitleSource = TitleSource.AI,
            recordingType = RecordingType.GENERAL,
            createdAtMs = recordedAt,
            transcriptTexts = transcriptTexts,
            intelligence = null,
            modelTitle = null,
            locale = Locale.US,
            timeZone = TimeZone.getTimeZone("UTC")
        )

        assertEquals(TitleSource.CONTENT, decision.source)
        val dateFallback = MeetingTitleGenerator.deterministicFallbackTitle(
            RecordingType.GENERAL, recordedAt, Locale.US, TimeZone.getTimeZone("UTC")
        )
        assertNotEquals(dateFallback, decision.title)
        assertTrue("Should be derived from what was said: ${decision.title}", decision.title.contains("ship", ignoreCase = true))
    }

    @Test
    fun `no speech gives a localized date title, disambiguated with the start time`() {
        val zone = TimeZone.getTimeZone("UTC")
        val locale = Locale.FRANCE
        val base = MeetingTitleGenerator.deterministicFallbackTitle(
            RecordingType.VOICE_MEMO, recordedAt, locale, zone
        )

        val first = RecordingTitles.resolve(
            existingTitle = "Voice Memo",
            existingTitleSource = TitleSource.AI,
            recordingType = RecordingType.VOICE_MEMO,
            createdAtMs = recordedAt,
            transcriptTexts = emptyList(),
            locale = locale,
            timeZone = zone
        )
        assertEquals(TitleSource.DATE, first.source)
        assertEquals(base, first.title)

        val second = RecordingTitles.resolve(
            existingTitle = "Voice Memo",
            existingTitleSource = TitleSource.AI,
            recordingType = RecordingType.VOICE_MEMO,
            createdAtMs = recordedAt,
            transcriptTexts = emptyList(),
            siblingTitles = setOf(base),
            locale = locale,
            timeZone = zone
        )
        assertEquals(TitleSource.DATE, second.source)
        assertNotEquals(first.title, second.title)
        assertTrue("Same-day same-type titles must differ, got ${second.title}", second.title.contains("14:20"))
        // French month, not a hardcoded US one.
        assertTrue("Date must be localized, got ${second.title}", second.title.contains("oct", ignoreCase = true))
    }

    @Test
    fun `a user-set title survives a reprocess even when the model offers a better one`() {
        val decision = RecordingTitles.resolve(
            existingTitle = "Grace that holds",
            existingTitleSource = TitleSource.USER,
            recordingType = RecordingType.MEETING,
            createdAtMs = recordedAt,
            transcriptTexts = transcriptTexts,
            modelTitle = "Friday Ship Decision"
        )

        assertEquals("Grace that holds", decision.title)
        assertEquals(TitleSource.USER, decision.source)
    }

    @Test
    fun `a usable imported name survives a reprocess`() {
        val decision = RecordingTitles.resolve(
            existingTitle = "Q3 board planning",
            existingTitleSource = TitleSource.IMPORT,
            recordingType = RecordingType.GENERAL,
            createdAtMs = recordedAt,
            transcriptTexts = transcriptTexts,
            modelTitle = "Friday Ship Decision"
        )

        assertEquals("Q3 board planning", decision.title)
        assertEquals(TitleSource.IMPORT, decision.source)
    }

    @Test
    fun `a model title is used and marked AI when it is real`() {
        val decision = RecordingTitles.resolve(
            existingTitle = "Untitled Meeting",
            existingTitleSource = TitleSource.AI,
            recordingType = RecordingType.GENERAL,
            createdAtMs = recordedAt,
            transcriptTexts = transcriptTexts,
            modelTitle = "Friday Ship Decision"
        )

        assertEquals("Friday Ship Decision", decision.title)
        assertEquals(TitleSource.AI, decision.source)
    }

    @Test
    fun `a model echoing the fallback is not treated as a model title`() {
        val fallback = RecordingTitles.provisionalTitle(RecordingType.GENERAL, recordedAt, transcriptTexts)

        val decision = RecordingTitles.resolve(
            existingTitle = "Untitled Meeting",
            existingTitleSource = TitleSource.AI,
            recordingType = RecordingType.GENERAL,
            createdAtMs = recordedAt,
            transcriptTexts = transcriptTexts,
            intelligence = com.craftflowtechnologies.meetingmind.core.model.MeetingSummary(
                title = fallback,
                summary = "",
                topics = emptyList(),
                decisions = emptyList(),
                actionItems = emptyList(),
                questions = emptyList()
            ),
            synthesisFallbackTitle = fallback
        )

        assertEquals(TitleSource.CONTENT, decision.source)
    }

    @Test
    fun `unparseable synthesis JSON still produces a content title`() = runBlocking {
        // A model that answers every prompt with truncated, unparseable JSON: the synthesis parser
        // can only fall back, so the title must come from real transcript content.
        val garbage = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> =
                AiResult.Success("{\"title\": \"We will ship")
        }
        val engine = RealMeetingIntelligenceEngine(garbage, contextLengthTokens = 4096)
        val transcript = Transcript(
            meetingId = "m1",
            segments = listOf(
                TranscriptSegment(
                    id = "s1",
                    meetingId = "m1",
                    startMs = 0L,
                    endMs = 4000L,
                    text = "We will ship the new onboarding flow on Friday."
                ),
                TranscriptSegment(
                    id = "s2",
                    meetingId = "m1",
                    startMs = 4000L,
                    endMs = 6000L,
                    text = "Sounds good to me."
                )
            ),
            language = "en"
        )

        val result = engine.processMeeting(transcript, "We will ship the new onboarding flow", RecordingType.GENERAL, null)
        assertTrue(result is AiResult.Success)
        val summary = (result as AiResult.Success).value
        assertEquals("We will ship the new onboarding flow", summary.title)

        val decision = RecordingTitles.resolve(
            existingTitle = "Meeting",
            existingTitleSource = TitleSource.AI,
            recordingType = RecordingType.GENERAL,
            createdAtMs = recordedAt,
            transcriptTexts = transcript.segments.map { it.text },
            intelligence = summary,
            synthesisFallbackTitle = "We will ship the new onboarding flow"
        )

        assertEquals(TitleSource.CONTENT, decision.source)
        assertTrue(decision.title.contains("ship", ignoreCase = true))
    }

    @Test
    fun `parseTitleCandidate accepts raw text and a JSON title, and rejects placeholders`() {
        assertEquals("Friday Ship Decision", RecordingTitles.parseTitleCandidate("Friday Ship Decision"))
        assertEquals("Friday Ship Decision", RecordingTitles.parseTitleCandidate("\"Friday Ship Decision.\""))
        assertEquals("Friday Ship Decision", RecordingTitles.parseTitleCandidate("{\"title\": \"Friday Ship Decision\"}"))
        assertNull(RecordingTitles.parseTitleCandidate("Meeting Summary"))
        assertNull(RecordingTitles.parseTitleCandidate("   "))
        assertNull(RecordingTitles.parseTitleCandidate("{"))
    }

    @Test
    fun `the title prompt includes the beginning and end of a long transcript and the type focus`() {
        val longText = "BEGINNING " + "word ".repeat(500) + "ENDING"
        val transcript = Transcript(
            meetingId = "m1",
            segments = listOf(TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0, endMs = 1, text = longText))
        )

        val prompt = RecordingTitles.titlePrompt(transcript, RecordingType.MEETING)

        assertTrue(prompt.contains("BEGINNING"))
        assertTrue(prompt.contains("ENDING"))
        assertTrue(prompt.contains("3 to 8 words"))
        assertTrue(prompt.contains("decisions made", ignoreCase = true))
        assertTrue("Long transcripts must be elided, not passed whole", prompt.contains("transcript continues"))
    }
}
