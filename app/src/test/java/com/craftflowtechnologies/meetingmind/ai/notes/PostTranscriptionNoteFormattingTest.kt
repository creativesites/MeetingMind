package com.craftflowtechnologies.meetingmind.ai.notes

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.faith.FaithPrompts
import com.craftflowtechnologies.meetingmind.ai.faith.SermonNoteBuilder
import com.craftflowtechnologies.meetingmind.ai.learning.LectureNoteBuilder
import com.craftflowtechnologies.meetingmind.ai.learning.LectureSynthesis
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.ActionItem
import com.craftflowtechnologies.meetingmind.core.model.Decision
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.MeetingSummary
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.NotePolishMode
import com.craftflowtechnologies.meetingmind.core.model.Question
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureDetector
import com.craftflowtechnologies.meetingmind.feature.notes.editor.CalloutKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostTranscriptionNoteFormattingTest {

    private val sermonSegments = listOf(
        TranscriptSegment(
            id = "s1", meetingId = "m1", speakerId = "spk_0", speakerName = "Pastor James",
            startMs = 0, endMs = 15_000,
            text = "Turn with me to Romans chapter eight verse twenty eight."
        ),
        TranscriptSegment(
            id = "s2", meetingId = "m1", speakerId = "spk_0", speakerName = "Pastor James",
            startMs = 15_000, endMs = 45_000,
            text = "God works all things together for good. Hold fast to hope when trials come."
        )
    )

    private val lectureSegments = listOf(
        TranscriptSegment(
            id = "l1", meetingId = "m2", speakerId = "spk_0", speakerName = "Prof. Ada",
            startMs = 0, endMs = 20_000,
            text = "Newton's second law states that force equals mass times acceleration."
        ),
        TranscriptSegment(
            id = "l2", meetingId = "m2", speakerId = "spk_0", speakerName = "Prof. Ada",
            startMs = 20_000, endMs = 50_000,
            text = "Pay attention, this conservation principle will be on the midterm exam."
        )
    )

    private val sampleSummary = MeetingSummary(
        title = "Hope in Trials",
        summary = "An overview of hope in trials and trusting God's purpose.",
        topics = listOf("Hope in adversity", "Trusting purpose"),
        decisions = listOf(Decision("d1", "m1", "Adopt the weekly review cadence")),
        actionItems = listOf(
            ActionItem(
                id = "a1",
                meetingId = "m1",
                task = "Send follow-up notes to the team",
                assigneeName = "Alex",
                deadline = "Friday"
            )
        ),
        questions = listOf(
            Question(id = "q1", meetingId = "m1", text = "How do we apply this in daily life?")
        )
    )

    @Test
    fun `NotePolishMode defaults to DEEP`() {
        assertEquals(NotePolishMode.DEEP, NotePolishMode.fromNameOrDefault(null))
        assertEquals(NotePolishMode.DEEP, NotePolishMode.fromNameOrDefault("UNKNOWN_VALUE"))
        assertEquals(NotePolishMode.OFF, NotePolishMode.fromNameOrDefault("OFF"))
        assertEquals(NotePolishMode.STANDARD, NotePolishMode.fromNameOrDefault("STANDARD"))
    }

    @Test
    fun `NotePolishEngine includes faith contract only for faith workflows and parses rich blocks`() = runBlocking {
        var capturedPrompt = ""
        val model = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
                capturedPrompt = prompt
                return AiResult.Success(
                    """
                    ```markdown
                    ## Main Theme
                    **Hope** anchors us through every trial.

                    ## Sermon Outline
                    1. Remember God's promise
                    2. Encourage one another

                    > Hold fast to hope when trials come.
                    ```
                    """.trimIndent()
                )
            }
        }
        val engine = NotePolishEngine(model)
        val res = engine.polish("n1", "m1", RecordingType.SERMON, sermonSegments, sampleSummary) as AiResult.Success
        assertTrue(capturedPrompt.contains(FaithPrompts.FAITH_CONTRACT.trim()))
        assertTrue(res.value.any { it.type == NoteBlockType.HEADING_2 && it.content.text == "Main Theme" })
        assertTrue(res.value.any { it.type == NoteBlockType.NUMBERED })
        assertTrue(res.value.any { it.type == NoteBlockType.QUOTE })
        assertTrue(res.value.all { it.sectionKey == NotePolishEngine.KEY_POLISHED })

        val lecturePrompt = engine.buildPrompt(RecordingType.LECTURE, lectureSegments, null)
        assertFalse(lecturePrompt.contains(FaithPrompts.FAITH_CONTRACT.trim()))
        assertTrue(lecturePrompt.contains("high-yield study notes"))
    }

    @Test
    fun `SermonNoteBuilder falls back to summary and polished blocks when extraction is null`() {
        val detections = ScriptureDetector.detect(sermonSegments)
        val withSummary = SermonNoteBuilder.build(
            noteId = "n1",
            meetingId = "m1",
            segments = sermonSegments,
            extraction = null,
            detections = detections,
            unavailableReason = "no AI model is available on this phone",
            spoken = sermonSegments,
            speakers = listOf(Speaker("spk_0", "m1", 0, "Pastor James", "Pastor James", "#000000")),
            summary = sampleSummary
        )
        assertTrue(withSummary.blocks.any { it.sectionKey == "key_message" && it.content.text.contains("hope in trials") })
        assertTrue(withSummary.blocks.any { it.sectionKey == "key_points" && it.type == NoteBlockType.NUMBERED })
        assertTrue(withSummary.blocks.any { it.type == NoteBlockType.SCRIPTURE })
        assertTrue(withSummary.blocks.any { it.sectionKey == SermonNoteBuilder.KEY_TRANSCRIPT })
    }

    @Test
    fun `LectureNoteBuilder emits rich callout, math, and transcript blocks`() {
        val synthesis = LectureSynthesis(
            summary = "Introduction to Newtonian mechanics.",
            concepts = listOf(
                LearningConcept(
                    id = "c1",
                    sessionId = "m2",
                    name = "Newton's Second Law",
                    definition = "Force is proportional to acceleration.",
                    formulaLatex = "F = m a",
                    createdAt = 1_000L,
                    updatedAt = 1_000L
                )
            ),
            activities = emptyList(),
            examHints = listOf("Conservation principle on midterm"),
            confusionUnpacks = emptyList()
        )
        val generated = LectureNoteBuilder.build(
            noteId = "n2",
            meetingId = "m2",
            segments = lectureSegments,
            domain = SubjectDomain.GENERAL,
            synthesis = synthesis,
            marks = emptyList(),
            summary = sampleSummary,
            speakers = listOf(Speaker("spk_0", "m2", 0, "Prof. Ada", "Prof. Ada", "#000000"))
        )
        assertTrue(generated.blocks.any { it.type == NoteBlockType.CALLOUT })
        assertTrue(generated.blocks.any { it.type == NoteBlockType.MATH && it.content.text == "F = m a" })
        assertTrue(generated.blocks.any { it.sectionKey == LectureNoteBuilder.KEY_TRANSCRIPT })
        assertTrue(LectureNoteBuilder.KEY_TRANSCRIPT in generated.endKeys)
    }

    @Test
    fun `RecordingNoteBuilder formats meeting summary into structured blocks and transcript`() {
        val generated = RecordingNoteBuilder.build(
            noteId = "n3",
            meetingId = "m1",
            recordingType = RecordingType.MEETING,
            segments = sermonSegments,
            summary = sampleSummary,
            speakers = listOf(Speaker("spk_0", "m1", 0, "Speaker 1", "Alex", "#000000"))
        )
        assertTrue(generated.blocks.any { it.type == NoteBlockType.HEADING_2 && it.content.text == "Summary" })
        assertTrue(generated.blocks.any { it.type == NoteBlockType.CALLOUT && it.payload[NoteBlock.PAYLOAD_CALLOUT_KIND] == "KEY_TAKEAWAY" })
        assertTrue(generated.blocks.any { it.type == NoteBlockType.BULLET && it.content.text == "Adopt the weekly review cadence" })
        assertTrue(generated.blocks.any { it.type == NoteBlockType.CHECKLIST && it.content.text.contains("Send follow-up notes") })
        assertTrue(generated.blocks.any { it.type == NoteBlockType.NUMBERED && it.content.text == "How do we apply this in daily life?" })
        assertTrue(generated.blocks.any { it.sectionKey == RecordingNoteBuilder.KEY_TRANSCRIPT })
    }

    @Test
    fun `NotePolishEngine attaches tap-to-play timestamps to matching polished blocks`() = runBlocking {
        val model = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> =
                AiResult.Success(
                    """
                    ## Sermon Notes
                    > Hold fast to hope when trials come.
                    """.trimIndent()
                )
        }
        val blocks = (NotePolishEngine(model).polish("n1", "m1", RecordingType.SERMON, sermonSegments) as AiResult.Success).value
        val quote = blocks.first { it.type == NoteBlockType.QUOTE }
        assertEquals("15000", quote.payload[NoteBlock.PAYLOAD_START_MS])
        assertEquals(listOf("s2"), quote.sourceSegmentIds)
    }

    @Test
    fun `SubjectDomain detect identifies lecture discipline and CalloutKind supports general kinds`() {
        assertEquals(
            SubjectDomain.PROGRAMMING_CS,
            SubjectDomain.detect("Data Structures & Algorithms", "Big-O complexity, binary trees, and hash tables")
        )
        assertEquals(CalloutKind.KEY_TAKEAWAY, CalloutKind.fromKey("KEY_TAKEAWAY"))
        assertEquals(CalloutKind.APPLICATION, CalloutKind.fromKey("APPLICATION"))
        assertEquals(CalloutKind.DECISION, CalloutKind.fromKey("DECISION"))
    }

    @Test
    fun `RecordingNoteBuilder extracts overview highlights and questions immediately even without AI summary`() {
        val segments = listOf(
            TranscriptSegment("s1", "m9", "spk_0", "Alex", 0L, 6_000L, "We decided today to move the product launch to October fifteenth for extra QA testing."),
            TranscriptSegment("s2", "m9", "spk_1", "Sam", 6_500L, 12_000L, "Can we also verify the offline battery benchmark before Friday evening?"),
            TranscriptSegment("s3", "m9", "spk_0", "Alex", 12_500L, 19_000L, "Yes, the engineering team will finalize all crash recovery checkpoints before release.")
        )
        val generated = RecordingNoteBuilder.build(
            noteId = "n9",
            meetingId = "m9",
            recordingType = RecordingType.MEETING,
            segments = segments,
            summary = null,
            speakers = listOf(Speaker("spk_0", "m9", 0, "Speaker 1", "Alex", "#000000"))
        )
        assertTrue(generated.blocks.any { it.type == NoteBlockType.CALLOUT && it.sectionKey == RecordingNoteBuilder.KEY_SUMMARY })
        assertTrue(generated.blocks.any { it.type == NoteBlockType.BULLET && it.sectionKey == RecordingNoteBuilder.KEY_TOPICS })
        assertTrue(generated.blocks.any { it.type == NoteBlockType.NUMBERED && it.content.text.contains("offline battery benchmark") })
        assertTrue(generated.blocks.any { it.sectionKey == RecordingNoteBuilder.KEY_TRANSCRIPT })
    }
}
