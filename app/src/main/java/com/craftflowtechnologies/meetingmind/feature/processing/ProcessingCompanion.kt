package com.craftflowtechnologies.meetingmind.feature.processing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CaptionKey
import com.craftflowtechnologies.meetingmind.core.companion.CaptionSpec
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.FixAction
import com.craftflowtechnologies.meetingmind.core.companion.OneShotKind
import com.craftflowtechnologies.meetingmind.core.companion.ProcessingCaptions
import com.craftflowtechnologies.meetingmind.core.companion.ProcessingCompanion
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.displayName
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** Whether the companion (and so its StatusLine fix) shows on the processing screen. */
@Composable
internal fun rememberProcessingCompanionShown(): Boolean {
    val settings by rememberCompanionSettings()
    val visible = rememberCompanionVisible(CompanionPage.PROCESSING)
    return visible && CompanionRoster.resolve(settings.form) != null
}

/** Resolves a [CaptionSpec] to text. The caption is a pure function of the stage event. */
@Composable
internal fun captionText(key: CaptionKey?, space: NotebookSpace?): String? {
    val spec = ProcessingCaptions.spec(key, space) ?: return null
    return when (spec) {
        is CaptionSpec.Plain -> stringResource(spec.id)
        is CaptionSpec.Variant -> stringArrayResource(spec.id)[spec.index]
        is CaptionSpec.Counted -> stringResource(spec.id, *spec.args.toTypedArray())
        is CaptionSpec.VariantCounted -> stringArrayResource(spec.id)[spec.index].format(*spec.args.toTypedArray())
    }
}

/**
 * The companion on the processing screen (Z-10, §5.4, §5.6): Thinking with a stage caption,
 * Worried only together with a fix action (the StatusLine), and one Celebrating hop when the job
 * completes. Renders nothing when there is no companion or presence hides it.
 */
@Composable
internal fun ProcessingCompanionBlock(
    state: ProcessingUiState,
    space: NotebookSpace?,
    onRetry: () -> Unit,
    onGetModel: () -> Unit
) {
    val settings by rememberCompanionSettings()
    val form = CompanionRoster.resolve(settings.form)
    if (form == null || !rememberProcessingCompanionShown()) return

    // Once per job: this block lives as long as the job's screen, so the hop plays once and then rests.
    var celebrated by remember { mutableStateOf(false) }
    val failed = state.error != null
    val celebrate = state.isComplete && !celebrated
    LaunchedEffect(celebrate) {
        if (celebrate) {
            kotlinx.coroutines.delay(OneShotKind.CELEBRATING.durationMs)
            celebrated = true
        }
    }
    val view = ProcessingCompanion.view(
        stage = state.stage, failed = failed, modelRequired = state.modelRequired,
        complete = state.isComplete, celebrate = celebrate
    )
    val caption = captionText(view.caption, space)

    Column(
        Modifier.testTag("processing_companion"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        Companion(form = form, state = view.state, size = CompanionDp.dp)
        if (caption != null && !state.isQueued) {
            Text(
                caption, style = MM.type.secondary, color = MM.colors.inkSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = MM.space.l).semantics { liveRegion = LiveRegionMode.Polite }.testTag("processing_caption")
            )
        }
        when (view.fix) {
            FixAction.RETRY -> StatusLine(
                StatusKind.Error, stringResource(R.string.companion_error_processing_failed),
                actionLabel = stringResource(R.string.companion_action_retry), onAction = onRetry
            )
            FixAction.DOWNLOAD_MODELS -> StatusLine(
                StatusKind.Warning,
                stringResource(R.string.companion_error_models_missing, settings.displayName(form)),
                actionLabel = stringResource(R.string.companion_action_download_models), onAction = onGetModel
            )
            else -> Unit
        }
    }
}

private const val CompanionDp = 96
