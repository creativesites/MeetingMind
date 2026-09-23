package com.example.core.model

import com.example.core.scripture.BibleBooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowsTest {

    @Test
    fun `sermon rows follow the design and drop speakers for one voice`() {
        val rows = Workflows.processingStageRows(RecordingType.SERMON, speakerCount = null).map { it.label }
        assertEquals(
            listOf("Preparing audio", "Transcribing", "Identifying speakers", "Building the transcript", "Themes, passages & scripture references", "Sermon notes"),
            rows
        )
        assertFalse("Identifying speakers" in Workflows.processingStageRows(RecordingType.SERMON, 1).map { it.label })
    }

    @Test
    fun `every engine stage of a run belongs to exactly one row`() {
        RecordingType.entries.forEach { type ->
            val rows = Workflows.processingStageRows(type, null)
            val running = listOf(ProcessingStage.PREPARING_AUDIO, ProcessingStage.TRANSCRIBING, ProcessingStage.DIARIZING, ProcessingStage.CLEANING_TRANSCRIPT, ProcessingStage.ANALYZING, ProcessingStage.SAVING_RESULTS)
            running.forEach { stage -> assertEquals("$type $stage", 1, rows.count { stage in it.stages }) }
        }
    }

    @Test
    fun `faith workflows live in the Faith space and prayers are private`() {
        Workflows.faith.forEach { assertEquals(NotebookSpace.FAITH, Workflows.space(it)) }
        assertTrue(Workflows.isPrivateByDefault(RecordingType.PRAYER))
        assertFalse(Workflows.isPrivateByDefault(RecordingType.SERMON))
        assertEquals(NotebookSpace.WORK, Workflows.space(RecordingType.MEETING))
    }

    @Test
    fun `sermon template keeps prayer private and splits AI from user sections`() {
        val t = Workflows.template(RecordingType.SERMON)
        assertTrue("my_prayer" in t.privateKeys)
        assertFalse("key_points" in t.privateKeys)
        val forRecording = Workflows.startingBlocks(RecordingType.SERMON, "n", forRecording = true).mapNotNull { it.sectionKey }.toSet()
        assertEquals(setOf("my_notes", "my_response", "my_prayer"), forRecording)
    }

    @Test
    fun `sermon vocabulary includes every book of the Bible`() {
        val hints = Workflows.vocabularyHints(RecordingType.SERMON)
        assertEquals(66, BibleBooks.all.size)
        assertTrue(BibleBooks.all.all { it.name in hints })
        assertEquals(1189, BibleBooks.all.sumOf { it.chapterCount })
        assertEquals(176, BibleBooks.byUsfm("PSA")!!.verseCount(119))
    }
}
