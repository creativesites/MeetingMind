package com.craftflowtechnologies.meetingmind.feature.personal.seeds

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.LEARNING
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeedSortTest {

    private val all = NotebookSpace.entries.toSet()
    private val noLearning = setOf(WORK, FAITH, PERSONAL)

    private fun move(space: NotebookSpace) = Suggestion.MoveTo(space)

    @Test fun `work words move a note to Work, in any case`() {
        val words = listOf(
            "meeting", "client", "budget", "project", "deadline", "team", "invoice", "quarter",
            "hiring", "report", "boss", "colleague"
        )
        words.forEach { word ->
            assertEquals("$word", move(WORK), SeedSort.suggest("Notes on the $word", all))
            assertEquals("${word.uppercase()}", move(WORK), SeedSort.suggest("NOTES ON THE ${word.uppercase()}", all))
        }
    }

    @Test fun `quarter codes Q1 to Q4 are Work`() {
        listOf("Q1", "q2", "Q3", "q4").forEach { q ->
            assertEquals(q, move(WORK), SeedSort.suggest("Plan for $q", all))
        }
    }

    @Test fun `faith words move a note to Faith`() {
        val words = listOf("pray", "prayer", "psalm", "verse", "church", "sermon", "god", "bible", "scripture", "worship")
        words.forEach { word ->
            assertEquals(word, move(FAITH), SeedSort.suggest("Thinking about $word today", all))
        }
    }

    @Test fun `learning words move a note to Learning when the person uses Learning`() {
        val words = listOf("lecture", "chapter", "exam", "study", "course", "assignment", "revise")
        words.forEach { word ->
            assertEquals(word, move(LEARNING), SeedSort.suggest("The $word on Monday", all))
        }
    }

    @Test fun `learning is never suggested when the person does not use it`() {
        assertNull(SeedSort.suggest("Lecture notes on chapter 4", noLearning))
    }

    @Test fun `short task words make a task`() {
        listOf("Call the dentist", "Buy milk", "Book the flight", "Pay the water bill", "Email Sam back",
            "Remind me about the bins", "Fix the back gate", "Pick up the parcel", "Renew the passport",
            "Send the photos").forEach { text ->
            assertEquals(text, Suggestion.MakeTask, SeedSort.suggest(text, all))
        }
    }

    @Test fun `a space rule beats the task rule`() {
        assertEquals(move(WORK), SeedSort.suggest("Call the client about the budget", all))
    }

    @Test fun `more keyword hits win, and ties go to Work`() {
        assertEquals(move(FAITH), SeedSort.suggest("pray at church before the sermon meeting", all))
        assertEquals(move(WORK), SeedSort.suggest("meeting in church", all))
    }

    @Test fun `words only match as whole words`() {
        assertNull(SeedSort.suggest("Teamwork and godly habits", all))
        assertNull(SeedSort.suggest("The bookshelf is full", all))
        assertNull(SeedSort.suggest("Prayers are answered", all))
    }

    @Test fun `a space the person does not use is never suggested`() {
        assertNull(SeedSort.suggest("Client budget for the quarter", setOf(FAITH, PERSONAL)))
        assertNull(SeedSort.suggest("Psalm reading", setOf(WORK, PERSONAL)))
    }

    @Test fun `Personal is never suggested as a space`() {
        assertNull(SeedSort.suggest("A thought about the weekend", setOf(PERSONAL)))
        assertEquals(Suggestion.MakeTask, SeedSort.suggest("Call Mum", setOf(PERSONAL)))
    }

    @Test fun `long text never becomes a task`() {
        val fourteen = (1..14).joinToString(" ") { if (it == 1) "call" else "word$it" }
        val fifteen = (1..15).joinToString(" ") { if (it == 1) "call" else "word$it" }
        assertEquals(Suggestion.MakeTask, SeedSort.suggest(fourteen, all))
        assertNull(SeedSort.suggest(fifteen, all))
    }

    @Test fun `blank or unmatched text gives no suggestion`() {
        assertNull(SeedSort.suggest("", all))
        assertNull(SeedSort.suggest("   ", all))
        assertNull(SeedSort.suggest("Something about the sky", all))
    }
}
