package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingCaptionsTest {

    private fun spec(stage: ProcessingStage, space: NotebookSpace? = null, detail: StageDetail? = null) =
        ProcessingCaptions.spec(CaptionKey(stage, detail), space)

    @Test fun `each space picks its own variant`() {
        assertEquals(CaptionSpec.Variant(R.array.companion_processing_analyzing, 0), spec(ProcessingStage.ANALYZING, NotebookSpace.PERSONAL))
        assertEquals(CaptionSpec.Variant(R.array.companion_processing_analyzing, 1), spec(ProcessingStage.ANALYZING, NotebookSpace.FAITH))
        assertEquals(CaptionSpec.Variant(R.array.companion_processing_analyzing, 2), spec(ProcessingStage.ANALYZING, NotebookSpace.WORK))
        assertEquals(CaptionSpec.Variant(R.array.companion_processing_analyzing, 3), spec(ProcessingStage.ANALYZING, NotebookSpace.LEARNING))
        assertEquals(CaptionSpec.Variant(R.array.companion_processing_detecting_speech, 0), spec(ProcessingStage.DETECTING_SPEECH, null))
    }

    @Test fun `no number when there is no detail`() {
        assertEquals(CaptionSpec.Plain(R.string.companion_processing_transcribing_plain), spec(ProcessingStage.TRANSCRIBING))
        // One half of the pair is not enough either.
        assertEquals(
            CaptionSpec.Plain(R.string.companion_processing_transcribing_plain),
            spec(ProcessingStage.TRANSCRIBING, detail = StageDetail(doneMinutes = 3))
        )
        assertEquals(CaptionSpec.Plain(R.string.companion_processing_diarizing_plain), spec(ProcessingStage.DIARIZING))
    }

    @Test fun `real detail fills the clause`() {
        assertEquals(
            CaptionSpec.Counted(R.string.companion_processing_transcribing, listOf(4, 31)),
            spec(ProcessingStage.TRANSCRIBING, detail = StageDetail(doneMinutes = 4, totalMinutes = 31))
        )
        assertEquals(
            CaptionSpec.VariantCounted(R.array.companion_processing_diarizing, 0, listOf(3)),
            spec(ProcessingStage.DIARIZING, detail = StageDetail(voices = 3))
        )
    }

    @Test fun `work diarizing never carries a number`() {
        assertEquals(
            CaptionSpec.Variant(R.array.companion_processing_diarizing, 2),
            spec(ProcessingStage.DIARIZING, NotebookSpace.WORK, StageDetail(voices = 4))
        )
    }

    @Test fun `terminal and idle stages have no caption`() {
        listOf(ProcessingStage.IDLE, ProcessingStage.COMPLETED, ProcessingStage.FAILED).forEach { assertNull(spec(it)) }
        assertNull(ProcessingCaptions.spec(null, null))
        assertEquals(CaptionSpec.Plain(R.string.companion_processing_cancelled), spec(ProcessingStage.CANCELLED))
    }

    @Test fun `the caption depends only on the stage event, so identical events give identical captions`() {
        val a = spec(ProcessingStage.SAVING_RESULTS, NotebookSpace.FAITH)
        val b = spec(ProcessingStage.SAVING_RESULTS, NotebookSpace.FAITH)
        assertEquals(a, b)
    }

    @Test fun `every working stage has a caption`() {
        ProcessingStage.entries.filter { it != ProcessingStage.IDLE && it != ProcessingStage.COMPLETED && it != ProcessingStage.FAILED }
            .forEach { assertTrue("$it", spec(it) != null) }
    }

    @Test fun `thinking while working, worried only with a fix, celebrating once`() {
        val working = ProcessingCompanion.view(ProcessingStage.ANALYZING, false, false, false, false)
        assertEquals(CompanionState.THINKING, working.state)
        assertEquals(CaptionKey(ProcessingStage.ANALYZING), working.caption)

        val failed = ProcessingCompanion.view(ProcessingStage.FAILED, true, false, false, false)
        assertEquals(CompanionState.WORRIED, failed.state)
        assertEquals(FixAction.RETRY, failed.fix)

        val noModel = ProcessingCompanion.view(null, false, true, false, false)
        assertEquals(FixAction.DOWNLOAD_MODELS, noModel.fix)

        assertEquals(CompanionState.CELEBRATING, ProcessingCompanion.view(ProcessingStage.COMPLETED, false, false, true, true).state)
        assertEquals(CompanionState.IDLE, ProcessingCompanion.view(ProcessingStage.COMPLETED, false, false, true, false).state)
    }
}
