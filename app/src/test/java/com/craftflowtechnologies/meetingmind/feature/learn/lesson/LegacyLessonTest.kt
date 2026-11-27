package com.craftflowtechnologies.meetingmind.feature.learn.lesson

import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.feature.learn.StressReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A lesson laid out from the concepts the old studio saved: no AI, the learner's own words. */
class LegacyLessonTest {

    private fun concept(
        id: String,
        name: String,
        definition: String = "$name definition.",
        emphasis: String? = null,
        startMs: Long? = null,
        dismissed: Boolean = false
    ) = LearningConcept(
        id = id,
        sessionId = "s1",
        name = name,
        definition = definition,
        emphasis = emphasis,
        evidence = if (startMs == null) emptyList() else listOf(LearningEvidence(startMs = startMs, endMs = startMs + 9_000, quote = "$name, said slowly")),
        isDismissed = dismissed,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun recall(conceptId: String, prompt: String, answer: String) = LearningActivity(
        id = "a-$conceptId",
        sessionId = "s1",
        conceptId = conceptId,
        type = LearningActivityType.RECALL,
        prompt = prompt,
        expectedAnswer = answer,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `no concepts means no lesson to lay out`() {
        assertNull(LegacyLesson.kitFrom("s1", emptyList(), emptyList(), 1L))
        assertNull(LegacyLesson.kitFrom("s1", listOf(concept("c1", "Km", dismissed = true)), emptyList(), 1L))
    }

    @Test
    fun `each live concept becomes a section in lecture order, with its quick check`() {
        val kit = LegacyLesson.kitFrom(
            "s1",
            listOf(concept("c2", "Vmax", startMs = 60_000), concept("c1", "Km", startMs = 14_000)),
            listOf(recall("c1", "What does a low Km tell you?", "A tight grip on the substrate.")),
            createdAt = 5L
        )!!

        assertEquals(listOf("Km", "Vmax"), kit.sections.map { it.title })
        assertEquals(listOf(1, 2), kit.sections.map { it.index })
        assertEquals(14_000L, kit.sections.first().startMs)
        assertEquals("What does a low Km tell you?", kit.sections.first().quickCheck?.prompt)
        assertNull(kit.sections.last().quickCheck)
        assertEquals(LegacyLesson.MODEL_ID, kit.modelId)
        assertEquals(5L, kit.createdAt)
    }

    @Test
    fun `a hidden concept leaves the lesson`() {
        val kit = LegacyLesson.kitFrom("s1", listOf(concept("c1", "Km"), concept("c2", "Hidden", dismissed = true)), emptyList(), 1L)!!
        assertEquals(listOf("Km"), kit.sections.map { it.title })
    }

    @Test
    fun `an emphasised concept with a moment becomes a stressed moment`() {
        val kit = LegacyLesson.kitFrom(
            "s1",
            listOf(concept("c1", "Km", emphasis = "will be on the exam", startMs = 41_000), concept("c2", "Vmax")),
            emptyList(),
            1L
        )!!

        assertEquals(1, kit.stressed.size)
        val moment = kit.stressed.single()
        assertEquals(41_000L, moment.startMs)
        assertEquals(StressReason.NAMED_EXAM, moment.reason)
        assertEquals("c1", moment.conceptId)
        assertTrue(moment.quote.contains("Km"))
    }

    @Test
    fun `the summary is the first definitions, never blank`() {
        val kit = LegacyLesson.kitFrom("s1", listOf(concept("c1", "Km", definition = "The half-speed point.")), emptyList(), 1L)!!
        assertEquals("The half-speed point.", kit.summary)
    }
}
