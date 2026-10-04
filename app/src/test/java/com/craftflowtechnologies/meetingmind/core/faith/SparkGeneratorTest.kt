package com.craftflowtechnologies.meetingmind.core.faith

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkGeneratorTest {

    @Test
    fun offlineCatalogHasRichDiversity() {
        val catalog = SparkGenerator.curatedCatalog
        assertTrue("Catalog should have at least 20 pieces", catalog.size >= 20)

        val vibes = catalog.map { it.vibe }.toSet()
        assertTrue("Should contain DEEP", vibes.contains(SparkVibe.DEEP))
        assertTrue("Should contain FUNNY", vibes.contains(SparkVibe.FUNNY))
        assertTrue("Should contain FIRE", vibes.contains(SparkVibe.FIRE))
        assertTrue("Should contain REAL", vibes.contains(SparkVibe.REAL))
        assertTrue("Should contain CALM", vibes.contains(SparkVibe.CALM))
        assertTrue("Should contain GRATEFUL", vibes.contains(SparkVibe.GRATEFUL))
    }

    @Test
    fun sparkPieceUndoPreservesHistory() {
        val initial = SparkPiece(
            text = "Initial punchline",
            vibe = SparkVibe.FUNNY,
            format = SparkFormat.ONE_LINER
        )

        assertFalse(initial.canUndo())

        val step1 = initial.withTextUpdate("First remix punchline")
        assertTrue(step1.canUndo())
        assertEquals(1, step1.history.size)
        assertEquals("Initial punchline", step1.history[0])

        val step2 = step1.withTextUpdate("Second remix punchline")
        assertTrue(step2.canUndo())
        assertEquals(2, step2.history.size)

        val undone1 = step2.undo()
        assertEquals("First remix punchline", undone1.text)
        assertEquals(1, undone1.history.size)

        val undone2 = undone1.undo()
        assertEquals("Initial punchline", undone2.text)
        assertFalse(undone2.canUndo())
    }

    @Test
    fun toShareRequestFormatsCorrectly() {
        val pieceWithVerse = SparkPiece(
            text = "Discomfort today is interest on freedom.",
            verseRef = "Joshua 1:9",
            verseText = "Be strong and courageous.",
            vibe = SparkVibe.FIRE,
            format = SparkFormat.ONE_LINER
        )

        val req = pieceWithVerse.toShareRequest()
        assertEquals("FIRE & DRIVE", req.content.eyebrow)
        assertEquals("Discomfort today is interest on freedom.", req.content.text)
        assertEquals("Joshua 1:9", req.content.reference)
        assertTrue(req.caption?.contains("Joshua 1:9") == true)
        assertTrue(req.caption?.contains("Be strong and courageous.") == true)

        val pieceNoVerse = SparkPiece(
            text = "Clean funny thought without scripture.",
            vibe = SparkVibe.FUNNY,
            format = SparkFormat.ONE_LINER
        )
        val reqNoVerse = pieceNoVerse.toShareRequest()
        assertNull(reqNoVerse.content.reference)
        assertNull(reqNoVerse.content.attribution)
        assertEquals("Clean funny thought without scripture.", reqNoVerse.caption)
    }

    @Test
    fun remixActionsWorkPredictably() {
        val piece = SparkPiece(
            text = "Maybe you should try to be brave today.",
            verseRef = "Joshua 1:9",
            vibe = SparkVibe.FIRE,
            format = SparkFormat.ONE_LINER
        )

        // Text update test
        val bolder = piece.withTextUpdate("Stop waiting: you must be brave today.")
        assertEquals("Stop waiting: you must be brave today.", bolder.text)
        assertTrue(bolder.canUndo())

        // Verse strip test
        val stripped = piece.copy(verseRef = null, verseText = null)
        assertNull(stripped.verseRef)
        assertNull(stripped.verseText)
    }
}
