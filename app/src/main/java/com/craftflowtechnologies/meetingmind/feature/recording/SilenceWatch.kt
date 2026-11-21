package com.craftflowtechnologies.meetingmind.feature.recording

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * Notices a recording that hears nothing: a phone in a pocket, a finger over the mic, a muted
 * headset. A long silence is the one recording problem a person can still fix in the moment, and
 * finding out after an hour is the worst way to learn it.
 *
 * The level is the recorder's peak amplitude, 0 to 1 (see core/audio/AudioRecorder). Speech sits
 * well above [QuietLevel]; a quiet room still flickers above it now and then, which resets the clock.
 */
internal class SilenceWatch(
    private val quietLevel: Float = QuietLevel,
    private val quietForMs: Long = QuietForMs
) {
    private var quietSinceMs: Long? = null

    /** True when it has been near-silent for long enough while recording. A pause clears it. */
    fun update(level: Float, recording: Boolean, nowMs: Long): Boolean {
        if (!recording || level > quietLevel) {
            quietSinceMs = null
            return false
        }
        val since = quietSinceMs ?: nowMs.also { quietSinceMs = it }
        return nowMs - since >= quietForMs
    }

    companion object {
        const val QuietLevel = 0.012f
        const val QuietForMs = 25_000L
        const val SampleEveryMs = 500L
    }
}

/**
 * "It's very quiet. Is the mic covered?" under the timer while the silence lasts. It clears as soon
 * as sound comes back. It's a status line, not a dialog, so it never interrupts a sermon or a meeting.
 */
@Composable
internal fun SilenceCue(recording: Boolean, level: () -> Float, modifier: Modifier = Modifier) {
    val watch = remember { SilenceWatch() }
    val readLevel by rememberUpdatedState(level)
    var silent by remember { mutableStateOf(false) }
    LaunchedEffect(recording) {
        var now = 0L
        silent = watch.update(readLevel(), recording, now)
        while (recording) {
            kotlinx.coroutines.delay(SilenceWatch.SampleEveryMs)
            now += SilenceWatch.SampleEveryMs
            silent = watch.update(readLevel(), recording = true, nowMs = now)
        }
    }
    val motion = MM.motion
    AnimatedVisibility(visible = silent && recording, enter = fadeIn(motion.standard()), exit = fadeOut(motion.quick())) {
        StatusLine(
            StatusKind.Warning,
            stringResource(R.string.recording_silence_cue),
            modifier.padding(top = MM.space.s).semantics { liveRegion = LiveRegionMode.Polite }.testTag("record_silence_cue")
        )
    }
}
