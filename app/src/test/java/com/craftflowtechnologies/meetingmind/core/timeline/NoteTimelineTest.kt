package com.craftflowtechnologies.meetingmind.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec §11.4: to-note and back-to-source conversions, zero-length parts, reorder, equal local times. */
class NoteTimelineTest {
    private val min = 60_000L
    private val part1 = SourceSpan("p1", 20 * min)
    private val part2 = SourceSpan("p2", 15 * min)

    @Test
    fun localTimeInPartTwoLandsAfterPartOne() {
        // Spec example: Part 1 = 20:00, Part 2 = 15:00, mark at local 05:00 in Part 2 is note-wide 25:00.
        assertEquals(25 * min, NoteTimeline.toNoteTime(listOf(part1, part2), SourcePosition("p2", 5 * min)))
    }

    @Test
    fun partOneIsUnchanged() {
        assertEquals(5 * min, NoteTimeline.toNoteTime(listOf(part1, part2), SourcePosition("p1", 5 * min)))
    }

    @Test
    fun noteTimeMapsBackToTheSourceAndItsLocalTime() {
        assertEquals(SourcePosition("p2", 5 * min), NoteTimeline.toSource(listOf(part1, part2), 25 * min))
        assertEquals(SourcePosition("p1", 19 * min), NoteTimeline.toSource(listOf(part1, part2), 19 * min))
    }

    @Test
    fun theBoundaryBelongsToThePartThatStartsThere() {
        assertEquals(SourcePosition("p2", 0), NoteTimeline.toSource(listOf(part1, part2), 20 * min))
    }

    @Test
    fun theVeryEndOfTheNoteMapsToTheEndOfItsLastPart() {
        assertEquals(SourcePosition("p2", 15 * min), NoteTimeline.toSource(listOf(part1, part2), 35 * min))
    }

    @Test
    fun reorderingChangesNoteTimeButNotLocalTime() {
        val reordered = listOf(part2, part1)
        assertEquals(5 * min, NoteTimeline.toNoteTime(reordered, SourcePosition("p2", 5 * min)))
        assertEquals(15 * min + 5 * min, NoteTimeline.toNoteTime(reordered, SourcePosition("p1", 5 * min)))
    }

    @Test
    fun aZeroLengthPartOwnsNoTime() {
        val sources = listOf(part1, SourceSpan("empty", 0), part2)
        assertEquals(SourcePosition("p2", 0), NoteTimeline.toSource(sources, 20 * min))
        assertEquals(20 * min, NoteTimeline.toNoteTime(sources, SourcePosition("empty", 0)))
    }

    @Test
    fun timesOutsideTheNoteAreNull() {
        assertNull(NoteTimeline.toSource(listOf(part1, part2), -1))
        assertNull(NoteTimeline.toSource(listOf(part1, part2), 36 * min))
        assertNull(NoteTimeline.toNoteTime(listOf(part1), SourcePosition("missing", 1)))
    }

    @Test
    fun theSameLocalTimeInTwoPartsIsTwoDifferentNoteTimes() {
        val sources = listOf(part1, part2)
        assertEquals(5 * min, NoteTimeline.toNoteTime(sources, SourcePosition("p1", 5 * min)))
        assertEquals(25 * min, NoteTimeline.toNoteTime(sources, SourcePosition("p2", 5 * min)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPartListedTwiceIsRejected() {
        NoteTimeline.offsets(listOf(part1, part1))
    }
}
