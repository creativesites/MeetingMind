package com.craftflowtechnologies.meetingmind.feature.today.companion

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNudgesTest {

    private val day = 20_000L
    private val base = HomeNudgeFacts(today = day, companionName = "Zuri")
    private val nothingSeen: (String) -> Boolean = { false }
    private fun seenOf(vararg keys: String): (String) -> Boolean = { it in keys }

    @Test fun `a note that just arrived comes first and opens the recording`() {
        val n = HomeNudges.pick(base.copy(readyMeeting = NudgeItem("m1", "Budget review", "n1"), processing = NudgeItem("m2", "Standup"), processingCount = 1), nothingSeen)!!
        assertEquals(NudgeKind.NOTE_READY, n.kind)
        assertEquals(NudgeAction.OpenRecording("m1"), n.action)
        assertTrue(n.text.contains("Budget review"))
    }

    @Test fun `once the ready note is seen the line moves on`() {
        val facts = base.copy(readyMeeting = NudgeItem("m1", "Budget review"), processing = NudgeItem("m2", "Standup"), processingCount = 1)
        assertEquals(NudgeKind.PROCESSING, HomeNudges.pick(facts, seenOf("ready:m1"))!!.kind)
    }

    @Test fun `a home that can't open recordings opens the note instead`() {
        val facts = base.copy(readyMeeting = NudgeItem("m1", "Lecture 4", "n1"), canOpenRecording = false)
        assertEquals(NudgeAction.OpenNote("n1"), HomeNudges.pick(facts, nothingSeen)!!.action)
    }

    @Test fun `a ready recording with no note and no recording page says nothing about it`() {
        val facts = base.copy(readyMeeting = NudgeItem("m1", "Lecture 4", null), canOpenRecording = false)
        assertNull(HomeNudges.pick(facts, nothingSeen))
    }

    @Test fun `processing stays until it's done and can't be waved away`() {
        val n = HomeNudges.pick(base.copy(processing = NudgeItem("m2", "Standup"), processingCount = 1), nothingSeen)!!
        assertEquals(NudgeKind.PROCESSING, n.kind)
        assertFalse(n.dismissable)
        assertEquals("See progress", n.actionLabel)
    }

    @Test fun `several recordings in progress are counted`() {
        val n = HomeNudges.pick(base.copy(processing = NudgeItem("m2", "Standup"), processingCount = 3), nothingSeen)!!
        assertTrue(n.text.contains("3 recordings"))
    }

    @Test fun `processing without a recording page has no button`() {
        val n = HomeNudges.pick(base.copy(processing = NudgeItem("m2", "Standup"), processingCount = 1, canOpenRecording = false), nothingSeen)!!
        assertNull(n.action)
        assertNull(n.actionLabel)
    }

    @Test fun `someone new is greeted by name with an example for the space`() {
        val n = HomeNudges.pick(base.copy(isNew = true, space = NotebookSpace.FAITH), nothingSeen)!!
        assertEquals(NudgeKind.FIRST_RUN, n.kind)
        assertEquals(NudgeAction.Record, n.action)
        assertTrue(n.text.startsWith("Hi, I'm Zuri"))
        assertTrue(n.text.contains("a sermon"))
        assertTrue(HomeNudges.pick(base.copy(isNew = true, space = NotebookSpace.LEARNING), nothingSeen)!!.text.contains("a lecture"))
        assertTrue(HomeNudges.pick(base.copy(isNew = true, space = NotebookSpace.WORK), nothingSeen)!!.text.contains("a meeting"))
    }

    @Test fun `no first hello where the home greets new people itself or once waved away`() {
        assertNull(HomeNudges.pick(base.copy(isNew = true, firstRun = false), nothingSeen))
        assertNull(HomeNudges.pick(base.copy(isNew = true), seenOf("first")))
    }

    @Test fun `after four quiet days the companion offers the last note`() {
        val facts = base.copy(lastNote = NudgeItem("n9", "Sunday notes"), daysSinceLastNote = 4)
        val n = HomeNudges.pick(facts, nothingSeen)!!
        assertEquals(NudgeKind.WELCOME_BACK, n.kind)
        assertEquals(NudgeAction.OpenNote("n9"), n.action)
        assertNull(HomeNudges.pick(facts.copy(daysSinceLastNote = 3), nothingSeen))
    }

    @Test fun `the welcome back line never says missed or streak`() {
        val n = HomeNudges.pick(base.copy(lastNote = NudgeItem("n9", "Sunday notes"), daysSinceLastNote = 30), nothingSeen)!!
        assertFalse(n.text.contains("miss", ignoreCase = true))
        assertFalse(n.text.contains("streak", ignoreCase = true))
    }

    @Test fun `tips run for the first two weeks in order and skip seen ones`() {
        val facts = base.copy(firstNoteDay = day - 2)
        assertEquals(Tip.YOUTUBE, HomeNudges.pick(facts, nothingSeen)!!.tip)
        assertEquals(Tip.SEARCH, HomeNudges.pick(facts, seenOf("tip:YOUTUBE"))!!.tip)
        assertNull(HomeNudges.pick(base.copy(firstNoteDay = day - HomeNudges.TIP_DAYS), nothingSeen))
    }

    @Test fun `one tip a day stays put and once waved away nothing else shows that day`() {
        val facts = base.copy(firstNoteDay = day - 1, tipShownToday = Tip.SEARCH)
        assertEquals(Tip.SEARCH, HomeNudges.pick(facts, nothingSeen)!!.tip)
        assertNull(HomeNudges.pick(facts, seenOf("tip:SEARCH")))
    }

    @Test fun `the search tip is skipped where there is no search`() {
        val facts = base.copy(firstNoteDay = day, canSearch = false)
        assertEquals(Tip.ASK, HomeNudges.pick(facts, seenOf("tip:YOUTUBE"))!!.tip)
    }

    @Test fun `no tips before the first note`() {
        assertNull(HomeNudges.pick(base.copy(isNew = true, firstRun = false, firstNoteDay = null), nothingSeen))
    }

    @Test fun `long titles are shortened`() {
        val n = HomeNudges.pick(base.copy(readyMeeting = NudgeItem("m1", "A".repeat(80))), nothingSeen)!!
        assertTrue(n.text.contains("…"))
        assertFalse(n.text.contains("A".repeat(41)))
    }
}
