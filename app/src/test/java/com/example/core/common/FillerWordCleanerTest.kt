package com.example.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FillerWordCleanerTest {

    @Test
    fun `removes a leading hesitation sound and recapitalizes`() {
        assertEquals("So that's the plan.", FillerWordCleaner.clean("Uh, so that's the plan."))
    }

    @Test
    fun `collapses a comma-flanked hesitation without leaving a comma splice`() {
        assertEquals(
            "It's one full train of thought.",
            FillerWordCleaner.clean("It's one full, uh, train of thought.")
        )
    }

    @Test
    fun `cleans a realistic run of dictated speech`() {
        val raw = "Uh, so you know, uh, it's one full, uh, train of thought."
        assertEquals("So you know it's one full train of thought.", FillerWordCleaner.clean(raw))
    }

    @Test
    fun `removes a parenthetical you know but keeps its lexical use`() {
        assertEquals("It's complicated.", FillerWordCleaner.clean("It's, you know, complicated."))
        assertEquals("You know the answer.", FillerWordCleaner.clean("You know the answer."))
    }

    @Test
    fun `preserves backchannel agreement tokens`() {
        assertEquals("Uh-huh, that works.", FillerWordCleaner.clean("Uh-huh, that works."))
    }

    @Test
    fun `does not remove hesitation spellings embedded in real words`() {
        assertEquals("Ahead of the umbrella era.", FillerWordCleaner.clean("Ahead of the umbrella era."))
    }

    @Test
    fun `collapses a stuttered function word`() {
        assertEquals("The plan is ready.", FillerWordCleaner.clean("The the plan is ready."))
        assertEquals("I think we ship.", FillerWordCleaner.clean("I I think we ship."))
    }

    @Test
    fun `leaves grammatically legitimate doubling alone`() {
        assertEquals("I know that that works.", FillerWordCleaner.clean("I know that that works."))
        assertEquals("She had had enough.", FillerWordCleaner.clean("She had had enough."))
        assertEquals("It was very very slow.", FillerWordCleaner.clean("It was very very slow."))
    }

    @Test
    fun `returns the original text rather than blanking a segment that is only filler`() {
        assertEquals("Uh.", FillerWordCleaner.clean("Uh."))
    }

    @Test
    fun `leaves clean text untouched`() {
        val clean = "We agreed to ship the release on Friday."
        assertEquals(clean, FillerWordCleaner.clean(clean))
    }

    @Test
    fun `blank input is returned unchanged`() {
        assertEquals("", FillerWordCleaner.clean(""))
        assertEquals("   ", FillerWordCleaner.clean("   "))
    }

    @Test
    fun `cleanIf respects the toggle`() {
        val raw = "Uh, so that's the plan."
        assertEquals(raw, FillerWordCleaner.cleanIf(enabled = false, text = raw))
        assertEquals("So that's the plan.", FillerWordCleaner.cleanIf(enabled = true, text = raw))
    }
}
