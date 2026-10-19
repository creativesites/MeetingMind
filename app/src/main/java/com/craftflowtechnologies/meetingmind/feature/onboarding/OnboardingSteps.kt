package com.craftflowtechnologies.meetingmind.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** What the person captures and what they want back, both multi-select, plus an optional name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalsStep(
    goals: Set<CaptureGoal>, onGoal: (CaptureGoal) -> Unit,
    outcomes: Set<DesiredOutcome>, onOutcome: (DesiredOutcome) -> Unit,
    showWorkProfile: Boolean, workProfile: com.craftflowtechnologies.meetingmind.core.work.WorkProfile?,
    onWorkProfile: (com.craftflowtechnologies.meetingmind.core.work.WorkProfile) -> Unit,
    name: String, onName: (String) -> Unit,
    /** "What should {companion} call you?" when a companion was picked (ZURI_EXPERIENCE §5.1 step 3). */
    nameTitle: String? = null
) {
    StepTitle("Make it yours", "Pick everything that applies. It sets up your space and what MeetingMind suggests first. You can change it any time.")
    Text("What do you capture?", color = OnNavy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptureGoal.entries.forEach { g -> Chip(g.label, selected = g in goals, tag = "goal_${g.name}") { onGoal(g) } }
    }
    Text("What do you want from recordings?", color = OnNavy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DesiredOutcome.entries.forEach { o -> Chip(o.label, selected = o in outcomes, tag = "outcome_${o.name}") { onOutcome(o) } }
    }
    if (showWorkProfile) {
        Text("What kind of work?", color = OnNavy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
        Text("Sets the words, templates and privacy. Doctors and lawyers get on-device only.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.craftflowtechnologies.meetingmind.core.work.WorkProfile.entries.forEach { p ->
                Chip(p.label, selected = p == workProfile, tag = "work_profile_${p.name}") { onWorkProfile(p) }
            }
        }
        workProfile?.let { Text(it.description, color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
    Text(nameTitle?.let { "$it (optional)" } ?: "What should we call you? (optional)", color = OnNavy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
    OutlinedTextField(
        value = name, onValueChange = onName, singleLine = true,
        placeholder = { Text("Your first name", color = Color.White.copy(alpha = 0.35f)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand.Cyan, unfocusedBorderColor = Color.White.copy(alpha = 0.2f), cursorColor = Brand.Cyan,
            focusedTextColor = OnNavy, unfocusedTextColor = OnNavy
        ),
        shape = RoundedCornerShape(16.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
        modifier = Modifier.fillMaxWidth().testTag("onboarding_name_field")
    )
    Text("Stays on this phone. Used for greetings and so Ask AI can address you.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
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
