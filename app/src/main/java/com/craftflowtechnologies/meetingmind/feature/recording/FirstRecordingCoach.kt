package com.craftflowtechnologies.meetingmind.feature.recording

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** The three things a first recording needs to know, in the order they matter. */
internal enum class CoachTip(@StringRes val text: Int) {
    /** Before anything else: where the phone goes decides how good the notes are. */
    PLACEMENT(R.string.first_recording_tip_placement),
    /** Once it is running: the buttons below mark a moment worth keeping. */
    MARK(R.string.first_recording_tip_mark),
    /** Once there is something worth keeping, or on a pause: how to end, and that notes follow. */
    FINISH(R.string.first_recording_tip_finish)
}

/**
 * Which tip to show at a point in the first recording. Pure, so the timing is tested, not
 * guessed. The coach keeps to one line in one slot and never covers the controls.
 */
internal object FirstRecordingCoachPolicy {
    const val MarkAtMs = 20_000L
    const val FinishAtMs = 120_000L

    fun tipAt(recordedMs: Long, paused: Boolean): CoachTip = when {
        recordedMs >= FinishAtMs -> CoachTip.FINISH
        paused && recordedMs >= MarkAtMs -> CoachTip.FINISH
        recordedMs >= MarkAtMs -> CoachTip.MARK
        else -> CoachTip.PLACEMENT
    }

    /** Seeing the last tip counts as having been coached: the next recording starts clean. */
    fun completesCoaching(tip: CoachTip): Boolean = tip == CoachTip.FINISH
}

/** Whether this person has been coached through a recording. One answer for the whole app. */
internal object FirstRecordingCoachStore {
    private const val File = "meetmind_first_recording_coach"
    private const val Key = "done"

    fun isDone(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(File, Context.MODE_PRIVATE).getBoolean(Key, false)

    fun markDone(context: Context) {
        context.applicationContext.getSharedPreferences(File, Context.MODE_PRIVATE).edit().putBoolean(Key, true).apply()
    }
}

/**
 * The first-recording coach: one quiet line under the timer that changes as the recording goes
 * on, with "Got it" to end it. Shown on a person's first recording only. It cross-fades between
 * tips, or swaps them at once when motion is reduced.
 */
@Composable
internal fun FirstRecordingCoach(recordedMs: Long, paused: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(!FirstRecordingCoachStore.isDone(context)) }
    if (!visible) return
    val tip = FirstRecordingCoachPolicy.tipAt(recordedMs, paused)
    // Reaching the last tip completes coaching even if they never tap Got it; this recording still shows it.
    if (FirstRecordingCoachPolicy.completesCoaching(tip)) {
        androidx.compose.runtime.LaunchedEffect(Unit) { FirstRecordingCoachStore.markDone(context) }
    }
    val motion = MM.motion
    Row(
        modifier.padding(top = MM.space.s).testTag("first_recording_coach"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = tip,
            transitionSpec = { fadeIn(motion.standard()) togetherWith fadeOut(motion.quick()) },
            label = "coach_tip",
            modifier = Modifier.weight(1f, fill = false)
        ) { shown ->
            Text(
                stringResource(shown.text),
                style = MM.type.secondary, color = MM.colors.inkSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("first_recording_tip_${shown.name.lowercase()}")
            )
        }
        TextAction(
            stringResource(R.string.first_recording_got_it),
            onClick = { FirstRecordingCoachStore.markDone(context); visible = false },
            modifier = Modifier.testTag("first_recording_got_it")
        )
    }
}
