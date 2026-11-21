package com.craftflowtechnologies.meetingmind.feature.processing

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * "What's happening?": one plain sentence for the stage the work is in, and one for where it runs.
 * A first recording is the moment people wonder what the app is doing with their audio. This
 * answers it without a wall of text: closed by default, one tap to open.
 */
internal object ProcessingExplainer {

    /** The sentence for a stage, or null when the screen already says it (done, failed, stopped). */
    @StringRes
    fun stageSentence(stage: ProcessingStage?): Int? = when (stage) {
        null, ProcessingStage.IDLE, ProcessingStage.PREPARING_AUDIO -> R.string.processing_explain_preparing
        ProcessingStage.DETECTING_SPEECH -> R.string.processing_explain_detecting
        ProcessingStage.TRANSCRIBING -> R.string.processing_explain_transcribing
        ProcessingStage.DIARIZING -> R.string.processing_explain_speakers
        ProcessingStage.CLEANING_TRANSCRIPT -> R.string.processing_explain_cleaning
        ProcessingStage.ANALYZING -> R.string.processing_explain_analyzing
        ProcessingStage.SAVING_RESULTS -> R.string.processing_explain_saving
        ProcessingStage.COMPLETED, ProcessingStage.FAILED, ProcessingStage.CANCELLED -> null
    }

    /** Where the work runs, in words that match docs/DATA_SAFETY.md. */
    @StringRes
    fun whereSentence(profile: ProcessingProfile): Int =
        if (profile == ProcessingProfile.OFFLINE) R.string.processing_explain_where_device else R.string.processing_explain_where_cloud
}

@Composable
internal fun WhatsHappening(stage: ProcessingStage?, profile: ProcessingProfile, modifier: Modifier = Modifier) {
    val sentence = ProcessingExplainer.stageSentence(stage) ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    val motion = MM.motion
    Column(modifier.fillMaxWidth().testTag("processing_explainer"), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        TextAction(
            stringResource(if (open) R.string.processing_explain_hide else R.string.processing_explain_open),
            onClick = { open = !open },
            modifier = Modifier.testTag("processing_explainer_toggle")
        )
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(motion.standard()) + expandVertically(motion.standard()),
            exit = fadeOut(motion.quick()) + shrinkVertically(motion.quick())
        ) {
            Column(
                Modifier.padding(horizontal = MM.space.s).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Text(stringResource(sentence), style = MM.type.body, color = MM.colors.ink, modifier = Modifier.testTag("processing_explainer_stage"))
                Text(stringResource(ProcessingExplainer.whereSentence(profile)), style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
        }
    }
}
