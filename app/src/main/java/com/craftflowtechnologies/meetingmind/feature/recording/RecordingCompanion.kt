package com.craftflowtechnologies.meetingmind.feature.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.RecordingCompanionPolicy
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.displayName
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible

/**
 * The companion on the recording screen (Z-8, §5.4): Listening above the timer, driven by the
 * live mic level; Quiet for sermons, with a chip that changes this one recording only.
 *
 * Renders nothing when the user chose No companion, presence is off or hidden, or the screen is
 * not a recording the companion belongs on.
 */
@Composable
internal fun RecordingCompanion(type: RecordingType, recording: Boolean, level: () -> Float) {
    val settings by rememberCompanionSettings()
    val visible = rememberCompanionVisible(CompanionPage.RECORDING)
    val form = CompanionRoster.resolve(settings.form)
    if (form == null || !visible) return

    // Per recording: a new recording is a new composition, so the next sermon starts Quiet again.
    var quiet by remember(type, settings.quietSermons) { mutableStateOf(RecordingCompanionPolicy.startsQuiet(type, settings)) }
    val pose = RecordingCompanionPolicy.pose(recording, quiet, LocalConfiguration.current.screenHeightDp)
    val name = settings.displayName(form)

    Column(
        Modifier.heightIn(min = pose.sizeDp.dp).testTag("record_companion"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Companion(
            form = form,
            state = pose.state,
            size = pose.sizeDp.dp,
            variant = pose.variant,
            level = if (pose.levelDriven) level else ZeroLevel,
            contentDescription = if (recording) stringResource(R.string.companion_recording_listening_talkback, name) else null
        )
        if (RecordingCompanionPolicy.showsQuietChip(type, settings)) {
            MMChip(
                label = stringResource(R.string.companion_recording_quiet_chip),
                selected = quiet,
                onClick = { quiet = !quiet },
                modifier = Modifier.padding(top = com.craftflowtechnologies.meetingmind.ui.theme.MM.space.xs).testTag("record_quiet_chip")
            )
        }
    }
}

private val ZeroLevel: () -> Float = { 0f }
