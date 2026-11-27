package com.craftflowtechnologies.meetingmind.feature.learn.explain

import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainRubricTest {

    private val vmax = LearningConcept(
        id = "c-vmax",
        sessionId = "s1",
        name = "Vmax",
        definition = "Vmax is the maximum reaction rate when the enzyme is saturated. Binds the active site.",
        evidence = listOf(
            LearningEvidence(startMs = 754_000L, endMs = 770_000L, quote = "More substrate wins it back, but Vmax stays the same.")
        ),
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `rubric takes definition sentences first, then lecturer quotes, with their evidence`() {
        val rubric = ExplainRubric.build(vmax)

        assertEquals(3, rubric.size)
        assertTrue(rubric[0].claim.startsWith("Vmax is the maximum reaction rate"))
        assertTrue(rubric[1].claim.startsWith("Binds the active site"))
        assertEquals("More substrate wins it back, but Vmax stays the same", rubric[2].claim)
        assertEquals(754_000L, rubric[2].evidence?.startMs)
        assertEquals(754_000L, rubric[0].evidence?.startMs)
    }

    @Test
    fun `rubric is empty when there is nothing to check against`() {
        val empty = vmax.copy(definition = "", evidence = emptyList(), name = "It")
        assertTrue(ExplainRubric.build(empty).isEmpty())
    }

    @Test
    fun `rubric falls back to the concept name when only the name has words`() {
        val nameOnly = vmax.copy(definition = "", evidence = emptyList(), name = "Michaelis constant")
        val rubric = ExplainRubric.build(nameOnly)
        assertEquals(listOf("Michaelis constant"), rubric.map { it.claim })
    }

    @Test
    fun `rubric never has more than four criteria`() {
        val busy = vmax.copy(
            definition = "First idea about enzymes. Second idea about substrates. Third idea about inhibitors.",
            evidence = listOf(
                LearningEvidence(quote = "Fourth point about temperature matters a lot."),
                LearningEvidence(quote = "Fifth point about pressure matters too."),
                LearningEvidence(quote = "Sixth point about acidity matters too.")
            )
        )
        assertEquals(ExplainRubric.MAX_CRITERIA, ExplainRubric.build(busy).size)
    }

    @Test
    fun `key terms drop short and stop words and cap the count`() {
        val terms = ExplainRubric.keyTerms("The enzyme is saturated with substrate, and this would be very fast")
        assertEquals(listOf("enzyme", "saturated", "substrate", "fast"), terms)
    }

    @Test
    fun `coverage is covered when most key terms are heard, partly with some, not yet with none`() {
        val claim = "Vmax is the maximum reaction rate when the enzyme is saturated"
        assertEquals(IdeaCoverage.COVERED, ExplainRubric.coverage(claim, "Vmax is the maximum reaction rate when saturated"))
        assertEquals(IdeaCoverage.PARTLY, ExplainRubric.coverage(claim, "it is the maximum"))
        assertEquals(IdeaCoverage.NOT_YET, ExplainRubric.coverage(claim, "I think the answer is about cells"))
        assertEquals(IdeaCoverage.NOT_YET, ExplainRubric.coverage(claim, ""))
    }

    @Test
    fun `a word matches a key term by its first four letters`() {
        assertEquals(IdeaCoverage.COVERED, ExplainRubric.coverage("Binds the active site", "it binding to the active sites"))
    }

    @Test
    fun `unused term names a word the explanation has not reached`() {
        assertEquals("active", ExplainRubric.unusedTerm("Binds the active site", "it binds the"))
        assertNull(ExplainRubric.unusedTerm("Binds the active site", "binds the active site"))
    }

    @Test
    fun `label is the first clause, cut to a line`() {
        assertEquals("Vmax stays the same", ExplainRubric.label("Vmax stays the same, even with more substrate"))
        assertTrue(ExplainRubric.label("x".repeat(200)).length <= 60)
    }
}
