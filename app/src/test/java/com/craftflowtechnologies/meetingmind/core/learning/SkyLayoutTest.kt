package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SkyLayoutTest {

    private val now = 1_000_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun concept(id: String, name: String, relationships: List<String> = emptyList()) = LearningConcept(
        id = id,
        sessionId = "sess",
        name = name,
        definition = "d",
        relationships = relationships,
        createdAt = now,
        updatedAt = now
    )

    private fun attempt(conceptId: String, correct: Boolean, daysAgo: Long) = ActivityAttempt(
        id = "a-$conceptId-$daysAgo-$correct",
        activityId = "act-$conceptId",
        sessionId = "sess",
        conceptId = conceptId,
        userResponse = "",
        isCorrect = correct,
        createdAt = now - daysAgo * day
    )

    private fun ids(n: Int) = List(n) { "concept_$it" }

    @Test
    fun `position depends only on the concept id, not on the other concepts`() {
        val one = SkyLayout.build(listOf(concept("c1", "Alpha")), emptyMap(), emptyMap(), now)
        val withOthers = SkyLayout.build(
            listOf(concept("c9", "Zulu"), concept("c1", "Alpha"), concept("c4", "Mid")),
            emptyMap(), emptyMap(), now
        )
        val a = one.stars.single { it.conceptId == "c1" }
        val b = withOthers.stars.single { it.conceptId == "c1" }
        assertEquals(a.x, b.x, 0.0001f)
        assertEquals(a.y, b.y, 0.0001f)
    }

    @Test
    fun `same input always produces the same sky, in any input order`() {
        val list = ids(10).mapIndexed { i, id -> concept(id, "Idea $i") }
        val first = SkyLayout.build(list, emptyMap(), emptyMap(), now)
        val second = SkyLayout.build(list.reversed(), emptyMap(), emptyMap(), now)
        assertEquals(first.stars.map { it.conceptId to (it.x to it.y) }.toMap(), second.stars.map { it.conceptId to (it.x to it.y) }.toMap())
    }

    @Test
    fun `stars do not overlap`() {
        val list = ids(24).mapIndexed { i, id -> concept(id, "Idea $i") }
        val sky = SkyLayout.build(list, emptyMap(), emptyMap(), now)
        for (i in sky.stars.indices) {
            for (j in i + 1 until sky.stars.size) {
                val a = sky.stars[i]
                val b = sky.stars[j]
                val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                assertTrue("stars ${a.conceptId} and ${b.conceptId} overlap at $d", d >= SkyLayout.MIN_SEPARATION - 1e-4f)
            }
        }
        sky.stars.forEach { s ->
            assertTrue(s.x in 0f..1f && s.y in 0f..1f)
        }
    }

    @Test
    fun `links come from relationships by name, once per pair, within the sky`() {
        val concepts = listOf(
            concept("c1", "Enzyme", relationships = listOf("Substrate", "Missing")),
            concept("c2", "Substrate", relationships = listOf("enzyme")),
            concept("c3", "Inhibitor", relationships = listOf("Enzyme"))
        )
        val sky = SkyLayout.build(concepts, emptyMap(), emptyMap(), now)
        assertEquals(2, sky.links.size)
        assertTrue(sky.links.any { (it.fromId == "c1" && it.toId == "c2") || (it.fromId == "c2" && it.toId == "c1") })
        assertTrue(sky.links.any { (it.fromId == "c3" && it.toId == "c1") || (it.fromId == "c1" && it.toId == "c3") })
    }

    @Test
    fun `mastery maps to star states`() {
        assertEquals(StarState.SHINING, SkyLayout.starStateFor(LearningMasteryState.STRONG))
        assertEquals(StarState.GROWING, SkyLayout.starStateFor(LearningMasteryState.DEVELOPING))
        assertEquals(StarState.GROWING, SkyLayout.starStateFor(LearningMasteryState.LEARNING))
        assertEquals(StarState.NEW, SkyLayout.starStateFor(LearningMasteryState.NEW))
        assertEquals(StarState.WORTH_A_LOOK, SkyLayout.starStateFor(LearningMasteryState.NEEDS_REVIEW))
    }

    @Test
    fun `a concept with no attempts is New, and a missed latest attempt is worth a look`() {
        val concepts = listOf(concept("fresh", "Fresh"), concept("missed", "Missed"))
        val attempts = mapOf("missed" to listOf(attempt("missed", true, 2), attempt("missed", false, 0)))
        val schedules = mapOf(
            "missed" to ReviewSchedule(
                id = "s", activityId = "act-missed", sessionId = "sess", conceptId = "missed",
                dueAt = now + day, createdAt = now, updatedAt = now
            )
        )
        val sky = SkyLayout.build(concepts, attempts, schedules, now)
        assertEquals(StarState.NEW, sky.stars.single { it.conceptId == "fresh" }.star)
        assertEquals(StarState.WORTH_A_LOOK, sky.stars.single { it.conceptId == "missed" }.star)
        assertEquals(2, sky.totalIdeas)
        assertNotEquals(StarState.SHINING, sky.stars.single { it.conceptId == "fresh" }.star)
    }
}
