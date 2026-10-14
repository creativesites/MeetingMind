package com.craftflowtechnologies.meetingmind.core.companion

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage

/**
 * Processing captions from real pipeline stages (§5.4). Pure: no timers, so a caption can only
 * change when the stage (or its real detail) changes. A missing detail drops its clause; no number
 * is ever invented.
 */
sealed interface CaptionSpec {
    /** A single string, no arguments. */
    data class Plain(@StringRes val id: Int) : CaptionSpec
    /** One variant out of a string-array (Everyday, Faith, Work, Study), no arguments. */
    data class Variant(@ArrayRes val id: Int, val index: Int) : CaptionSpec
    /** A format string whose positional arguments are real numbers. */
    data class Counted(@StringRes val id: Int, val args: List<Int>) : CaptionSpec
    /** A variant out of a string-array whose item takes the numbers as arguments. */
    data class VariantCounted(@ArrayRes val id: Int, val index: Int, val args: List<Int>) : CaptionSpec
}

object ProcessingCaptions {

    /** Index into the variant arrays: 0 Everyday, 1 Faith, 2 Work, 3 Study. */
    fun variantIndex(space: NotebookSpace?): Int = when (space) {
        NotebookSpace.FAITH -> 1
        NotebookSpace.WORK -> 2
        NotebookSpace.LEARNING -> 3
        NotebookSpace.PERSONAL, null -> 0
    }

    /** Null means "no caption for this stage" (idle, completed, failed). */
    fun spec(key: CaptionKey?, space: NotebookSpace?): CaptionSpec? {
        key ?: return null
        val i = variantIndex(space)
        val d = key.detail
        return when (key.stage) {
            ProcessingStage.PREPARING_AUDIO -> CaptionSpec.Plain(R.string.companion_processing_preparing_audio)
            ProcessingStage.DETECTING_SPEECH -> CaptionSpec.Variant(R.array.companion_processing_detecting_speech, i)
            ProcessingStage.TRANSCRIBING -> {
                val done = d?.doneMinutes
                val total = d?.totalMinutes
                if (done != null && total != null) CaptionSpec.Counted(R.string.companion_processing_transcribing, listOf(done, total))
                else CaptionSpec.Plain(R.string.companion_processing_transcribing_plain)
            }
            ProcessingStage.DIARIZING -> when {
                i == WorkIndex -> CaptionSpec.Variant(R.array.companion_processing_diarizing, i)
                d?.voices != null -> CaptionSpec.VariantCounted(R.array.companion_processing_diarizing, i, listOf(d.voices))
                else -> CaptionSpec.Plain(R.string.companion_processing_diarizing_plain)
            }
            ProcessingStage.CLEANING_TRANSCRIPT -> CaptionSpec.Plain(R.string.companion_processing_cleaning_transcript)
            ProcessingStage.ANALYZING -> CaptionSpec.Variant(R.array.companion_processing_analyzing, i)
            ProcessingStage.SAVING_RESULTS -> CaptionSpec.Plain(R.string.companion_processing_saving_results)
            ProcessingStage.CANCELLED -> CaptionSpec.Plain(R.string.companion_processing_cancelled)
            ProcessingStage.IDLE, ProcessingStage.COMPLETED, ProcessingStage.FAILED -> null
        }
    }

    private const val WorkIndex = 2
}

/** What the companion shows on the processing screen. Worried always carries its [fix]. */
data class ProcessingCompanionView(
    val state: CompanionState,
    val caption: CaptionKey?,
    val fix: FixAction?
) {
    init { require(state != CompanionState.WORRIED || fix != null) { "Worried needs a fix" } }
}

object ProcessingCompanion {

    /**
     * @param celebrate true only for the first frame(s) of a finished job; the screen clears it
     * after [OneShotKind.CELEBRATING] so a job celebrates once.
     */
    fun view(
        stage: ProcessingStage?,
        failed: Boolean,
        modelRequired: Boolean,
        complete: Boolean,
        celebrate: Boolean,
        detail: StageDetail? = null
    ): ProcessingCompanionView = when {
        modelRequired -> ProcessingCompanionView(CompanionState.WORRIED, null, FixAction.DOWNLOAD_MODELS)
        failed -> ProcessingCompanionView(CompanionState.WORRIED, null, FixAction.RETRY)
        complete -> ProcessingCompanionView(if (celebrate) CompanionState.CELEBRATING else CompanionState.IDLE, null, null)
        else -> ProcessingCompanionView(CompanionState.THINKING, stage?.let { CaptionKey(it, detail) }, null)
    }
}
