package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A hint must never contain the answer (design spec §13 #5). */
@RunWith(RobolectricTestRunner::class)
class HintSafetyTest {

    @Test
    fun `hint guard spots a quoted answer, case-insensitively`() {
        assertTrue(hintRevealsAnswer("Think about it: Competitive Inhibition raises Km", "competitive inhibition"))
        assertFalse(hintRevealsAnswer("Which site does the inhibitor bind?", "competitive inhibition"))
        // Very short answers can't be told apart from ordinary words, so they are not flagged.
        assertFalse(hintRevealsAnswer("Consider the axis", "x"))
    }

    @Test
    fun `offline hints never contain the expected answer`() = runBlocking {
        val answer = "The apparent Km increases while Vmax stays the same"
        val hints = LearningAiEngine().generateHintLadder(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            prompt = "What does a competitive inhibitor do to Km and Vmax?",
            expectedAnswer = answer,
            domain = SubjectDomain.GENERAL
        )
        assertEquals(3, hints.size)
        hints.forEach { hint ->
            assertFalse("Hint leaks the answer: $hint", hint.contains(answer.take(40).trim(), ignoreCase = true))
            assertFalse(hintRevealsAnswer(hint, answer))
        }
    }
}
