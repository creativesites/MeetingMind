package com.craftflowtechnologies.meetingmind.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import kotlinx.coroutines.delay

/**
 * The example recording: the transcript arrives line by line, then the summary, the action items
 * and one question to ask. All of it is bundled and worked out in advance, so it runs offline and
 * sends nothing anywhere. It is labelled as an example throughout, and AI-made parts are marked.
 */
@Composable
internal fun SampleStep(kind: SampleKind, onKind: (SampleKind) -> Unit, onViewed: () -> Unit, onRecordInstead: () -> Unit) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        SampleKind.entries.forEach { k -> Chip(k.label, selected = k == kind, tag = "sample_kind_${k.name}") { onKind(k) } }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceBase.copy(alpha = 0.06f)).padding(14.dp).testTag("sample_transcript")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sample.title, color = OnNavy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("EXAMPLE", color = OnNavy, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Brand.Blue, Brand.Violet))).padding(horizontal = 9.dp, vertical = 3.dp))
        }
        Text("${sample.length} · not your recording", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        sample.lines.forEachIndexed { i, line ->
            AnimatedVisibility(visible = i < shown, enter = fadeIn() + slideInVertically { it / 3 }) {
                Column(Modifier.padding(vertical = 5.dp)) {
                    Text("${line.speaker} · ${line.time}", color = Brand.Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(line.text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        if (!done) Text(if (playing) "Transcribing…" else "Paused", color = Color.White.copy(alpha = 0.5f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!done) {
            TextAction(if (playing) "Pause" else "Play", "sample_play_pause") { playing = !playing }
            TextAction("Skip to notes", "sample_skip_to_notes") { shown = sample.lines.size }
        }
        TextAction("Replay", "sample_replay") { shown = 0; asked = false; playing = true }
    }

    AnimatedVisibility(visible = done, enter = fadeIn() + slideInVertically { it / 4 }) {
        Column(Modifier.padding(top = 8.dp)) {
            ResultCard("Summary", "Made by AI from the transcript", "sample_summary") {
                Text(sample.summary, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
            }
            ResultCard("Action items", "Found by AI", "sample_actions") {
                sample.actions.forEach { a ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 2.dp).size(18.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = OnNavy, modifier = Modifier.size(11.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(a, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            ResultCard("Ask about this recording", "Answers come from the transcript", "sample_ask") {
                Chip(sample.question, selected = asked, tag = "sample_question") { asked = true }
                AnimatedVisibility(visible = asked, enter = fadeIn()) {
                    Text(sample.answer, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp).testTag("sample_answer"))
                }
            }
            Text(
                "Rather record your own? Start now",
                color = Brand.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onRecordInstead).padding(vertical = 8.dp).testTag("sample_record_instead")
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
    StepTitle(stringResource(R.string.onboarding_spaces_title), stringResource(R.string.onboarding_spaces_line))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp))
                    .background(SurfaceBase.copy(alpha = if (on) 0.1f else 0.04f))
                    .border(
                        if (on) 1.5.dp else 1.dp,
                        if (on) Brush.linearGradient(Brand.sweep) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))),
                        RoundedCornerShape(18.dp)
                    )
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(space) })
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("space_${space.name}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = if (on) 0.14f else 0.06f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = if (on) Brand.Cyan else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(title), color = OnNavy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(line), color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp, lineHeight = 18.sp)
                }
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(if (on) Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                        .border(if (on) 0.dp else 1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = OnNavy, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
    Text(
        stringResource(R.string.onboarding_spaces_footnote), color = Color.White.copy(alpha = 0.5f), fontSize = 12.5.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
    )
}

/** "What should Zuri call you?", optional, under the companion pick. Stays on the phone. */
@Composable
internal fun CompanionNameField(
    companion: com.craftflowtechnologies.meetingmind.core.companion.CompanionForm?,
    name: String,
    onName: (String) -> Unit
) {
    val title = companion?.let {
        stringResource(R.string.companion_onboarding_step3_title, stringResource(com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster.displayName(it)))
    } ?: stringResource(R.string.onboarding_name_title_plain)
    Text(
        stringResource(R.string.onboarding_name_optional, title), color = OnNavy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
    )
    OutlinedTextField(
        value = name, onValueChange = onName, singleLine = true,
        placeholder = { Text(stringResource(R.string.onboarding_name_placeholder), color = Color.White.copy(alpha = 0.35f)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand.Cyan, unfocusedBorderColor = Color.White.copy(alpha = 0.2f), cursorColor = Brand.Cyan,
            focusedTextColor = OnNavy, unfocusedTextColor = OnNavy
        ),
        shape = RoundedCornerShape(16.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
        modifier = Modifier.fillMaxWidth().testTag("onboarding_name_field")
    )
    Text(stringResource(R.string.onboarding_name_privacy), color = Color.White.copy(alpha = 0.5f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) OnNavy else Color.White.copy(alpha = 0.08f),
        modifier = Modifier.clickable(onClick = onClick).testTag(tag)
    ) {
        Text(label, color = if (selected) Color.Black else Color.White.copy(alpha = 0.9f), fontSize = 13.5.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun TextAction(label: String, tag: String, onClick: () -> Unit) {
    Text(label, color = Brand.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp).testTag(tag))
}

@Composable
private fun ResultCard(title: String, tagLine: String, tag: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceBase.copy(alpha = 0.06f)).padding(14.dp).testTag(tag)) {
        Text(title, color = OnNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(tagLine, color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}
