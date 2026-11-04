package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.work.Mark
import com.craftflowtechnologies.meetingmind.core.work.MarkKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningAiAndBuilderTest {

    @Test
    fun lectureNoteBuilderConstructsExamHintsAndConfusionUnpacks() {
        val segments = listOf(
            TranscriptSegment(
                id = "s1",
                startMs = 0,
                endMs = 4000,
                speakerIndex = 0,
                text = "Welcome to calculus. Today we cover limits and continuity."
            ),
            TranscriptSegment(
                id = "s2",
                startMs = 4000,
                endMs = 9000,
                speakerIndex = 0,
                text = "Remember this limit definition because it is always on the midterm exam."
            ),
            TranscriptSegment(
                id = "s3",
                startMs = 9000,
                endMs = 15000,
                speakerIndex = 0,
                text = "Now applying the epsilon delta proof here might feel abstract."
            )
        )
        val marks = listOf(
            Mark(kind = MarkKind.EXAM_HINT, atMs = 6000, text = "Midterm question on limit definition"),
            Mark(kind = MarkKind.CONFUSED, atMs = 11000, text = "Lost on epsilon delta transition")
        )

        val generated = LectureNoteBuilder.build(
            noteId = "note_calc_1",
            meetingId = "meet_calc_1",
            segments = segments,
            domain = SubjectDomain.MATH_STATS,
            synthesis = null,
            marks = marks
        )

        assertTrue(generated.keys.contains("lecture_exam_hints"))
        assertTrue(generated.keys.contains("lecture_confusion_unpacks"))
        assertTrue(generated.keys.contains("definitions_axioms"))

        val examHintBlock = generated.blocks.firstOrNull { it.payload[NoteBlock.PAYLOAD_CALLOUT_KIND] == "EXAM_TIP" }
        assertNotNull(examHintBlock)
        assertTrue(examHintBlock?.content?.text?.contains("Midterm question") == true)
        assertEquals("meet_calc_1", examHintBlock?.payload?.get(NoteBlock.PAYLOAD_MEETING_ID))

        val confusionBlock = generated.blocks.firstOrNull { it.sectionKey == "lecture_confusion_unpacks" && it.type == NoteBlockType.CALLOUT }
        assertNotNull(confusionBlock)
        assertEquals(BlockSource.ENRICHED, confusionBlock?.source)
        assertEquals("0", confusionBlock?.payload?.get(NoteBlock.PAYLOAD_UNPACKED)) // Folded by default!
    }

    @Test
    fun learningAiEngineEvaluatesTeachBackAndExplainsMistakes() = runBlocking {
        val engine = LearningAiEngine(transport = null, clock = { 1_000_000L })

        // 1. Teach-Back evaluation
        val rubric = listOf(
            TeachBackCriterion(claim = "Definition of eigenvalue", required = true),
            TeachBackCriterion(claim = "Characteristic equation det(A - lambda I) = 0", required = true),
            TeachBackCriterion(claim = "Geometric intuition as direction preservation", required = false)
        )
        val concept = LearningConcept(
            id = "c1",
            sessionId = "sess_1",
            name = "Eigenvalues",
            definition = "Scalars lambda such that Av = lambda v",
            state = LearningMasteryState.DEVELOPING,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val studentExplanation = "An eigenvalue is a scalar where matrix multiplication just scales the vector. We calculate it using the characteristic equation det(A - lambda I) = 0."
        val eval = engine.evaluateTeachBack(rubric, studentExplanation, concept)

        assertTrue(eval.scorePercent >= 60)
        assertTrue(eval.overallCoverage == TeachBackCoverage.COVERED || eval.overallCoverage == TeachBackCoverage.PARTLY_COVERED)
        val metCrit = eval.criteriaEvaluations.firstOrNull { it.criterion.claim.contains("Characteristic equation") }
        assertEquals(TeachBackStatus.MET, metCrit?.status)

        // 2. Explain Mistake
        val activity = LearningActivity(
            id = "act_1",
            sessionId = "sess_1",
            conceptId = "c1",
            type = LearningActivityType.RECALL,
            prompt = "What condition ensures non-trivial solutions to (A - lambda I)v = 0?",
            expectedAnswer = "det(A - lambda I) must equal zero.",
            difficulty = "MEDIUM",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val mistake = engine.explainMistake(activity, "v must be the zero vector.")
        assertNotNull(mistake.rootMisconception)
        assertEquals("det(A - lambda I) must equal zero.", mistake.correctMentalModel)
        assertTrue(mistake.stepByStepCorrection.isNotBlank())

        // 3. Enrich Note
        val enriched = engine.enrichNote(
            noteId = "note_1",
            selectionText = "d/dx [sin(x)] = cos(x)",
            kind = EnrichmentKind.UNPACK_STEPS,
            domain = SubjectDomain.MATH_STATS
        )
        assertEquals(EnrichmentKind.UNPACK_STEPS, enriched.kind)
        assertEquals(1, enriched.blocks.size)
        val block = enriched.blocks.first()
        assertEquals(BlockSource.ENRICHED, block.source)
        assertEquals("0", block.payload[NoteBlock.PAYLOAD_UNPACKED]) // Folded by default!
    }
}
