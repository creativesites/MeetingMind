package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.SkyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundComposerTest {

    private val now = 10_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun card(
        id: String,
        session: String,
        dueAgoMs: Long = 0L,
        mastery: LearningMasteryState? = LearningMasteryState.LEARNING
    ): DueCard {
        val activity = LearningActivity(
            id = id,
            sessionId = session,
            conceptId = "concept-$id",
            type = LearningActivityType.RECALL,
            prompt = "Question $id?",
            expectedAnswer = "Answer $id",
            createdAt = now,
            updatedAt = now
        )
        val schedule = ReviewSchedule(
            id = "s-$id", activityId = id, sessionId = session, conceptId = activity.conceptId,
            dueAt = now - dueAgoMs, createdAt = now, updatedAt = now
        )
        return DueCard(
            activity = activity,
            schedule = schedule,
            concept = null,
            sessionId = session,
            sourceLabel = "Course · $session",
            meetingId = null,
            mastery = mastery
        )
    }

    @Test
    fun `a round holds at most eight items`() {
        val due = (1..20).map { card("c$it", "s${it % 3}") }
        assertEquals(RoundComposer.MAX_ITEMS, RoundComposer.compose(due, now).size)
        assertEquals(8, RoundComposer.MAX_ITEMS)
    }

    @Test
    fun `overdue and worth-a-look items come before the rest`() {
        val fresh = card("fresh", "s1", dueAgoMs = 0L, mastery = LearningMasteryState.LEARNING)
        val worth = card("worth", "s2", dueAgoMs = 0L, mastery = LearningMasteryState.NEEDS_REVIEW)
        val overdue = card("overdue", "s3", dueAgoMs = 3 * day, mastery = LearningMasteryState.LEARNING)
        val round = RoundComposer.compose(listOf(fresh, worth, overdue), now).map { it.activity.id }
        assertEquals(setOf("worth", "overdue"), round.take(2).toSet())
        assertEquals("fresh", round.last())
    }

    @Test
    fun `the round mixes lessons instead of taking one lesson first`() {
        val a = (1..5).map { card("a$it", "lessonA", dueAgoMs = it.toLong()) }
        val b = (1..5).map { card("b$it", "lessonB", dueAgoMs = it.toLong()) }
        val round = RoundComposer.compose(a + b, now).map { it.sessionId }
        assertEquals(8, round.size)
        assertEquals(4, round.count { it == "lessonA" })
        assertEquals(4, round.count { it == "lessonB" })
    }

    @Test
    fun `a missed item comes back three places later, once`() {
        val queue: List<PracticeItem> = listOf("a", "b", "c", "d", "e").map { recall(it) }
        val once = RoundComposer.requeue(queue, currentIndex = 0, item = queue[0], alreadyRequeued = false)
        assertEquals(6, once.size)
        assertEquals("a", (once[3] as PracticeItem.Recall).activityId)
        val again = RoundComposer.requeue(once, currentIndex = 1, item = queue[0], alreadyRequeued = true)
        assertEquals(once, again)
    }

    @Test
    fun `summary gives first-try correct, back tomorrow, next step and all-cleared`() {
        val answers = listOf(
            RoundAnswer("a1", "c1", "Inhibition", "s1", firstTryCorrect = true, misses = 0, nextDueAt = now + 2 * day),
            RoundAnswer("a2", "c2", "Km", "s1", firstTryCorrect = false, misses = 2, nextDueAt = now + 10 * 60_000L),
            RoundAnswer("a3", "c2", "Km", "s1", firstTryCorrect = true, misses = 0, nextDueAt = now + 3 * day)
        )
        val result = RoundComposer.summarize(
            answers = answers,
            total = 3,
            newStars = listOf("Inhibition"),
            dueRemaining = 0,
            elapsedMs = 130_000L,
            studiedDaysThisWeek = 2,
            sessionTitles = mapOf("s1" to "Biochem"),
            now = now
        )
        assertEquals(3, result.total)
        assertEquals(2, result.firstTryCorrect)
        assertEquals(listOf("Inhibition"), result.newStars)
        assertEquals(2, result.backTomorrow)
        assertEquals(3, result.minutes)
        assertEquals(2, result.daysThisWeek)
        assertTrue(result.allCleared)
        assertNotNull(result.nextStep)
        val next = result.nextStep!!
        assertEquals("c2", next.conceptId)
        assertEquals("Practise Km", next.title)
        assertEquals(SkyState.Empty, result.sky)
    }

    @Test
    fun `next step is absent when nothing was missed, and not all cleared while items remain`() {
        val result = RoundComposer.summarize(
            answers = listOf(RoundAnswer("a1", "c1", "Km", "s1", true, 0, now + 2 * day)),
            total = 1,
            newStars = emptyList(),
            dueRemaining = 4,
            elapsedMs = 0L,
            studiedDaysThisWeek = 1,
            sessionTitles = emptyMap(),
            now = now
        )
        assertNull(result.nextStep)
        assertFalse(result.allCleared)
        assertEquals(1, result.minutes)
    }

    @Test
    fun `estimate is three quarters of a minute a card, at least one minute`() {
        assertEquals(0, RoundComposer.estimateMinutes(0))
        assertEquals(1, RoundComposer.estimateMinutes(1))
        assertEquals(6, RoundComposer.estimateMinutes(8))
    }

    @Test
    fun `studied days this week are Monday first and only count this week`() {
        val zone = java.time.ZoneId.of("UTC")
        // 2026-11-25 is a Wednesday; the week runs Monday 23 Nov to Sunday 29 Nov.
        val wednesday = java.time.LocalDate.of(2026, 11, 25).atStartOfDay(zone).toInstant().toEpochMilli() + 9 * 3_600_000L
        val monday = java.time.LocalDate.of(2026, 11, 23).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L
        val lastWeek = java.time.LocalDate.of(2026, 11, 16).atStartOfDay(zone).toInstant().toEpochMilli()
        val days = StudyCalendar.studiedDays(listOf(wednesday, monday, monday + 1000, lastWeek), wednesday, zone)
        assertEquals(listOf(true, false, true, false, false, false, false), days)
        assertEquals(2, StudyCalendar.daysThisWeek(listOf(wednesday, monday, lastWeek), wednesday, zone))
        assertEquals(2, StudyCalendar.todayIndex(wednesday, zone))
    }

    private fun recall(id: String) = PracticeItem.Recall(
        activityId = id,
        conceptId = null,
        sourceLabel = "",
        sourceStartMs = null,
        sourceEndMs = null,
        meetingId = null,
        prompt = id,
        answer = id
    )
}
