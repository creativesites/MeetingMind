package com.craftflowtechnologies.meetingmind.feature.readtome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadToMeFlags
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingStore
import com.craftflowtechnologies.meetingmind.ai.voice.read.profile.LearningRules
import com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadToMeProfileStore
import com.craftflowtechnologies.meetingmind.ai.voice.read.profile.display
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackController
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * What Read to Me has learned and stores, in one place the person controls: the learning switch, each
 * learned default with its reset, the saved audio with a clear button, and the on/off switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadToMeSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val profileStore = remember { ProfileHolder(ReadToMeProfileStore(context)) }
    val store = remember { ReadingStore.get(context) }
    var profile by remember { mutableStateOf(profileStore.store.load()) }
    var bytes by remember { mutableStateOf(store.usedBytes()) }
    var enabled by remember { mutableStateOf(ReadToMeFlags.enabled(context)) }
    var cleared by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MM.colors.surface) {
        Column(
            Modifier.padding(horizontal = MM.space.xl).padding(bottom = MM.space.l).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text("Read to Me settings", style = MM.type.title, color = MM.colors.ink)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Show Read to me in notes", style = MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it; ReadToMeFlags.setEnabled(context, it) })
            }

            Text("Learning from my choices", style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(
                "When you pick the same style, energy, pace or voice for the same kind of note three times in a row, Automatic starts using it. It only watches those choices, never what you skip or stop, and never your notes' words. It stays on this phone.",
                style = MM.type.secondary, color = MM.colors.inkSecondary
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Learn from my choices", style = MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f))
                Switch(checked = profile.adaptive, onCheckedChange = { v -> profile = profileStore.store.update { it.copy(adaptive = v) } })
            }
            if (profile.learned.isEmpty()) {
                Text("Nothing learned yet.", style = MM.type.caption, color = MM.colors.inkMuted)
            } else {
                profile.learned.forEach { (category, values) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category.label, style = MM.type.bodyStrong, color = MM.colors.ink)
                            Text(values.entries.joinToString(", ") { (f, v) -> display(f, v) }, style = MM.type.caption, color = MM.colors.inkMuted)
                        }
                        TextAction("Reset", onClick = { profile = profileStore.store.update { LearningRules.reset(it, category) } })
                    }
                }
                SecondaryButton("Reset everything learned", onClick = { profile = profileStore.store.update { LearningRules.reset(it) } }, modifier = Modifier.fillMaxWidth())
            }

            Text("Saved audio", style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(
                if (bytes == 0L) "No saved readings." else "Saved readings use ${"%.1f".format(bytes / 1_048_576.0)} MB on this phone. They are not backed up, and old ones are removed automatically.",
                style = MM.type.secondary, color = MM.colors.inkSecondary
            )
            if (bytes > 0L) SecondaryButton("Clear Read to Me audio", onClick = {
                if (PlaybackController.state.value.recordingId?.startsWith("read:") == true) PlaybackController.stop()
                store.clearAll(); bytes = store.usedBytes(); cleared = true
            }, modifier = Modifier.fillMaxWidth())
            if (cleared) StatusLine(StatusKind.Info, "Cleared. Readings are made again the next time you listen.")
        }
    }
}

private class ProfileHolder(val store: ReadToMeProfileStore)
