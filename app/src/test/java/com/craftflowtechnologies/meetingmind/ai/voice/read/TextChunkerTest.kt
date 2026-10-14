package com.craftflowtechnologies.meetingmind.ai.voice.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TextChunkerTest {

    private fun letters(s: String) = s.filter { !it.isWhitespace() }

    @Test fun `short text is one piece, whitespace normalised`() {
        assertEquals(listOf("Hello there. How are you?"), TextChunker.split("  Hello   there.\n\nHow are you?  ", 100))
    }

    @Test fun `empty and blank text give no pieces`() {
        assertTrue(TextChunker.split("", 10).isEmpty())
        assertTrue(TextChunker.split(" \n\t ", 10).isEmpty())
    }

    @Test fun `splits at the last sentence end that fits`() {
        val text = "One two three. Four five six. Seven eight nine."
        val pieces = TextChunker.split(text, 32)
        assertEquals(listOf("One two three. Four five six.", "Seven eight nine."), pieces)
    }

    @Test fun `falls back to a clause mark, then a space`() {
        assertEquals(listOf("alpha beta gamma,", "delta epsilon"), TextChunker.split("alpha beta gamma, delta epsilon", 20))
        assertEquals(listOf("alpha beta", "gamma delta"), TextChunker.split("alpha beta gamma delta", 12))
    }

    @Test fun `a token longer than the limit is cut inside itself and nothing is lost`() {
        val token = "x".repeat(25)
        val pieces = TextChunker.split(token, 10)
        assertEquals(listOf(10, 10, 5), pieces.map { it.length })
        assertEquals(token, pieces.joinToString(""))
    }

    @Test fun `a surrogate pair is never cut in half`() {
        val emoji = "😀" // one character, two UTF-16 units
        val text = emoji.repeat(10)
        val pieces = TextChunker.split(text, 5)
        assertEquals(text, pieces.joinToString(""))
        pieces.forEach { p -> assertTrue(p.codePointCount(0, p.length) * 2 == p.length) }
    }

    @Test fun `no piece is longer than the limit`() {
        val text = (1..400).joinToString(" ") { "word$it" } + "."
        TextChunker.split(text, 120).forEach { assertTrue(it.length <= 120) }
    }

    @Test fun `order and content are preserved for random text`() {
        val rnd = Random(42)
        val words = listOf("grace", "peace.", "Lord,", "faith;", "and", "the", "word", "John", "3:16", "—", "ok!", "really?")
        repeat(300) {
            val text = (1..rnd.nextInt(200) + 1).joinToString(if (rnd.nextBoolean()) " " else "\n ") { words[rnd.nextInt(words.size)] }
            val max = rnd.nextInt(60) + 8
            val pieces = TextChunker.split(text, max)
            assertEquals(letters(text), letters(pieces.joinToString("")))
            pieces.forEach { assertTrue(it.length <= max && it.isNotBlank()) }
        }
    }

    @Test fun `repeated text keeps every repetition`() {
        val text = "Amen. ".repeat(50).trim()
        val pieces = TextChunker.split(text, 30)
        assertEquals(50, pieces.joinToString(" ").split("Amen.").size - 1)
    }
}
