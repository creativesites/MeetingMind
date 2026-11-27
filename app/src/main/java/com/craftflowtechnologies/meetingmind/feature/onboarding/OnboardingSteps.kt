package com.craftflowtechnologies.meetingmind.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay

/**
 * The example recording: the transcript arrives line by line, then the summary, the action items
 * and one question to ask. All of it is bundled and worked out in advance, so it runs offline and
 * sends nothing anywhere. It is labelled as an example throughout, and AI-made parts are marked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SampleStep(kind: SampleKind, onKind: (SampleKind) -> Unit, onViewed: () -> Unit, onRecordInstead: () -> Unit) {
    val c = MM.colors
    val sample = remember(kind) { SampleContent.of(kind) }
    var shown by remember(kind) { mutableIntStateOf(0) }
    var playing by remember(kind) { mutableStateOf(true) }
    var asked by remember(kind) { mutableStateOf(false) }
    val done = shown >= sample.lines.size

    LaunchedEffect(kind, playing, shown) {
        if (playing && shown < sample.lines.size) {
            delay(if (shown == 0) 250L else 380L)
            shown++
        }
    }
    LaunchedEffect(kind, done) { if (done) onViewed() }

    StepTitle("See it work", "Watch an example recording become a transcript and notes. Nothing here is recorded or sent anywhere.")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
        modifier = Modifier.padding(bottom = MM.space.m)
    ) {
        SampleKind.entries.forEach { k ->
            MMChip(k.label, selected = k == kind, onClick = { onKind(k) }, modifier = Modifier.testTag("sample_kind_${k.name}"))
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(MM.radius.card).background(c.surface)
            .border(MMSize.hairline, c.line, MM.radius.card)
            .padding(MM.space.l).testTag("sample_transcript")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sample.title, style = MM.type.heading, color = c.ink, modifier = Modifier.weight(1f))
            Text(
                "EXAMPLE", style = MM.type.overline, color = c.accent,
                modifier = Modifier.clip(MM.radius.pill).background(c.accentWash).padding(horizontal = MM.space.s, vertical = MM.space.xs)
            )
        }
        Text(
            "${sample.length} · not your recording", style = MM.type.caption, color = c.inkMuted,
            modifier = Modifier.padding(top = MM.space.xs, bottom = MM.space.s)
        )
        sample.lines.forEachIndexed { i, line ->
            AnimatedVisibility(visible = i < shown, enter = fadeIn() + slideInVertically { it / 3 }) {
                Column(Modifier.padding(vertical = MM.space.xs + 1.dp)) {
                    Text("${line.speaker} · ${line.time}", style = MM.type.caption, color = c.accent)
                    Text(line.text, style = MM.type.body, color = c.ink)
                }
            }
        }
        if (!done) Text(if (playing) "Transcribing…" else "Paused", style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = MM.space.s))
    }
    Row(Modifier.padding(top = MM.space.xs), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        if (!done) {
            SampleAction(if (playing) "Pause" else "Play", "sample_play_pause") { playing = !playing }
            SampleAction("Skip to notes", "sample_skip_to_notes") { shown = sample.lines.size }
        }
        SampleAction("Replay", "sample_replay") { shown = 0; asked = false; playing = true }
    }

    AnimatedVisibility(visible = done, enter = fadeIn() + slideInVertically { it / 4 }) {
        Column(Modifier.padding(top = MM.space.s)) {
            ResultCard("Summary", "Made by AI from the transcript", "sample_summary") {
                Text(sample.summary, style = MM.type.body, color = c.ink)
            }
            ResultCard("Action items", "Found by AI", "sample_actions") {
                sample.actions.forEach { a ->
                    Row(Modifier.padding(vertical = MM.space.xs), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.padding(top = 2.dp).size(18.dp).clip(CircleShape).background(c.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(11.dp))
                        }
                        Spacer(Modifier.width(MM.space.m))
                        Text(a, style = MM.type.body, color = c.ink)
                    }
                }
            }
            ResultCard("Ask about this recording", "Answers come from the transcript", "sample_ask") {
                MMChip(sample.question, selected = asked, onClick = { asked = true }, modifier = Modifier.testTag("sample_question"))
                AnimatedVisibility(visible = asked, enter = fadeIn()) {
                    Text(sample.answer, style = MM.type.body, color = c.ink, modifier = Modifier.padding(top = MM.space.m).testTag("sample_answer"))
                }
            }
            com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction(
                "Rather record your own? Start now",
                onClick = onRecordInstead,
                modifier = Modifier.padding(top = MM.space.s, bottom = MM.space.m).testTag("sample_record_instead")
            )
        }
    }
}

/**
 * The four spaces, all on by default (founder decision, 2026-11-17). Turning one off removes its home, its bar
 * place and its reminders; the last one can't be turned off. Everything can be changed later in Settings.
 */
@Composable
internal fun SpacesStep(
    spaces: Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>,
    onToggle: (com.craftflowtechnologies.meetingmind.core.model.NotebookSpace) -> Unit
) {
    val c = MM.colors
    StepTitle(stringResource(R.string.onboarding_spaces_title), stringResource(R.string.onboarding_spaces_line))
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        OnboardingSpaces.forEach { space ->
            val on = space in spaces
            val (icon, title, line) = when (space) {
                com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK ->
                    Triple(Icons.Filled.Work, R.string.onboarding_space_work, R.string.onboarding_space_work_line)
                com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.LEARNING ->
                    Triple(Icons.Filled.School, R.string.onboarding_space_learning, R.string.onboarding_space_learning_line)
                com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH ->
                    Triple(Icons.Filled.AutoStories, R.string.onboarding_space_faith, R.string.onboarding_space_faith_line)
                com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL ->
                    Triple(Icons.Filled.Person, R.string.onboarding_space_personal, R.string.onboarding_space_personal_line)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .choiceCard(c, MM.radius.card, on, MMSize.hairline * 4)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(space) })
                    .padding(horizontal = MM.space.l, vertical = MM.space.m)
                    .testTag("space_${space.name}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTile(icon, on = on)
                Column(Modifier.weight(1f).padding(horizontal = MM.space.m)) {
                    Text(stringResource(title), style = MM.type.heading, color = c.ink)
                    Text(stringResource(line), style = MM.type.secondary, color = c.inkSecondary)
                }
                Box(
                    Modifier.size(MMSize.icon).clip(CircleShape)
                        .background(if (on) c.accent else c.surface)
                        .border(if (on) 0.dp else 1.5.dp, if (on) c.accent else c.inkFaint, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
    Text(
        stringResource(R.string.onboarding_spaces_footnote), style = MM.type.caption, color = c.inkMuted,
        modifier = Modifier.padding(top = MM.space.l, bottom = MM.space.l)
    )
}

/** "What should Zuri call you?", optional, under the companion pick. Stays on the phone. */
@Composable
internal fun CompanionNameField(
    companion: com.craftflowtechnologies.meetingmind.core.companion.CompanionForm?,
    name: String,
    onName: (String) -> Unit
) {
    val c = MM.colors
    val title = companion?.let {
        stringResource(R.string.companion_onboarding_step3_title, stringResource(com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster.displayName(it)))
    } ?: stringResource(R.string.onboarding_name_title_plain)
    Text(
        stringResource(R.string.onboarding_name_optional, title), style = MM.type.heading, color = c.ink,
        modifier = Modifier.padding(top = MM.space.xl, bottom = MM.space.m)
    )
    OutlinedTextField(
        value = name, onValueChange = onName, singleLine = true,
        placeholder = { Text(stringResource(R.string.onboarding_name_placeholder), style = MM.type.heading, color = c.inkMuted) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.line, cursorColor = c.accent,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface
        ),
        shape = MM.radius.card, textStyle = MM.type.heading,
        modifier = Modifier.fillMaxWidth().testTag("onboarding_name_field")
    )
    Text(
        stringResource(R.string.onboarding_name_privacy), style = MM.type.caption, color = c.inkMuted,
        modifier = Modifier.padding(top = MM.space.s, bottom = MM.space.l)
    )
}

/** A text action under the example (Play, Skip, Replay): the accent, with a full touch target. */
@Composable
private fun SampleAction(label: String, tag: String, onClick: () -> Unit) {
    com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction(label, onClick = onClick, modifier = Modifier.testTag(tag))
}

/** One of the example's results. AI-made parts say so under their title. */
@Composable
private fun ResultCard(title: String, tagLine: String, tag: String, content: @Composable () -> Unit) {
    val c = MM.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = MM.space.s).clip(MM.radius.card).background(c.surface)
            .border(MMSize.hairline, c.line, MM.radius.card)
            .padding(MM.space.l).testTag(tag)
    ) {
        Text(title, style = MM.type.heading, color = c.ink)
        Row(Modifier.padding(top = 2.dp, bottom = MM.space.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, contentDescription = null, tint = c.accent, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(MM.space.xs))
            Text(tagLine, style = MM.type.caption, color = c.inkMuted)
        }
        content()
    }
}
