package com.example.feature.devotional

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.DevotionalSource
import com.example.core.devotional.DevotionalTone
import com.example.core.devotional.DevotionalTopics
import com.example.core.devotional.Tradition
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary

/** Everything about the daily devotional, on one sheet. Nothing here is required. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DevotionalSettingsSheet(profile: DevotionalProfile, onSave: (DevotionalProfile, rewriteToday: Boolean) -> Unit, onDismiss: () -> Unit) {
    var p by remember { mutableStateOf(profile) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding()) {
            Text("Your daily devotional", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("Make it yours. Everything is optional.", fontSize = 14.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

            ToggleRow("Every morning", "Have it ready and send a gentle notification", p.enabled) { p = p.copy(enabled = it) }
            if (p.enabled) {
                Label("Arrives at")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5 * 60, 5 * 60 + 30, 6 * 60, 6 * 60 + 30, 7 * 60, 8 * 60, 9 * 60).forEach { m ->
                        Chip("%d:%02d".format(m / 60, m % 60), p.deliveryMinutes == m) { p = p.copy(deliveryMinutes = m) }
                    }
                }
            }

            Label("Where it comes from")
            DevotionalSource.entries.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    RadioButton(selected = p.source == s, onClick = { p = p.copy(source = s) }, colors = RadioButtonDefaults.colors(selectedColor = Gold))
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(s.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text(s.description, fontSize = 12.5.sp, lineHeight = 17.sp, color = InkSecondary)
                    }
                }
            }

            if (p.source == DevotionalSource.AI || p.source == DevotionalSource.MIX) {
                Label("Voice")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevotionalTone.entries.forEach { t -> Chip(t.label, p.tone == t) { p = p.copy(tone = t) } }
                }
                Label("Length")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 7, 12).forEach { m -> Chip("$m min", p.minutes == m) { p = p.copy(minutes = m) } }
                }
                Label("What you'd like to grow in")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevotionalTopics.all.forEach { t ->
                        Chip(t, t in p.topics) { p = p.copy(topics = if (t in p.topics) p.topics - t else p.topics + t) }
                    }
                }
                Label("A season you're in")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevotionalTopics.seasons.forEach { s -> Chip(s, p.season == s) { p = p.copy(season = if (p.season == s) null else s) } }
                }
                Label("About you (optional)")
                OutlinedTextField(
                    value = p.aboutMe, onValueChange = { p = p.copy(aboutMe = it.take(400)) }, minLines = 2,
                    placeholder = { Text("e.g. Nurse on night shifts, mum of two, learning to rest") }, modifier = Modifier.fillMaxWidth()
                )
                Label("Include")
                ToggleRow("A prayer", null, p.includePrayer) { p = p.copy(includePrayer = it) }
                ToggleRow("A word for today", "One line of encouragement to carry with you", p.includeMotivation) { p = p.copy(includeMotivation = it) }
                ToggleRow("A quote", "From the great Christian writers and hymns", p.includeInsight) { p = p.copy(includeInsight = it) }
                ToggleRow("A question to sit with", null, p.includeQuestion) { p = p.copy(includeQuestion = it) }
                Label("Privacy")
                ToggleRow(
                    "Let my prayer requests and journal shape it",
                    "When written by Gemini, a few of their lines are sent with the request. Written on your phone, they never leave it.",
                    p.sharePrivateWithCloud
                ) { p = p.copy(sharePrivateWithCloud = it) }
            }

            VoiceSection(p.voice) { p = p.copy(voice = it) }

            Label("Tradition")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tradition.entries.forEach { t -> Chip(t.label, p.tradition == t) { p = p.copy(tradition = t) } }
            }

            Spacer(Modifier.height(20.dp))
            val changedContent = p.copy(enabled = profile.enabled, deliveryMinutes = profile.deliveryMinutes, lessOf = profile.lessOf, moreOf = profile.moreOf) != profile
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Save", filled = true) { onSave(p, false) }
                if (changedContent) PillButton("Save and rewrite today's", filled = false) { onSave(p, true) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Gold.copy(alpha = 0.14f) else Color.White,
        border = BorderStroke(1.dp, if (selected) Gold else Color(0xFFE2E8F0))
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) Color(0xFF7A4E0F) else InkSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, fontSize = 12.5.sp, lineHeight = 17.sp, color = InkSecondary) }
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Gold))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSection(voice: com.example.ai.voice.VoiceSettings, onChange: (com.example.ai.voice.VoiceSettings) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var previewing by remember { mutableStateOf(false) }
    var previewFailed by remember { mutableStateOf(false) }
    Label("Read aloud by")
    com.example.ai.voice.PreacherStyle.entries.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = voice.style == s, onClick = { onChange(voice.copy(style = s)) }, colors = RadioButtonDefaults.colors(selectedColor = Gold))
            Column(Modifier.padding(top = 10.dp)) {
                Text(s.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text(s.line, fontSize = 12.5.sp, color = InkSecondary)
            }
        }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.example.ai.voice.VoiceGender.entries.forEach { g -> Chip(g.label, voice.gender == g) { onChange(voice.copy(gender = g)) } }
    }
    Label("Pace")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.85f to "Slower", 1.0f to "Natural", 1.15f to "Brisk").forEach { (r, l) -> Chip(l, kotlin.math.abs(voice.rate - r) < 0.01f) { onChange(voice.copy(rate = r)) } }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(if (previewing) "Preparing…" else "Hear it", filled = false) {
            if (previewing) return@PillButton
            previewing = true; previewFailed = false
            scope.launch {
                val file = runCatching { com.example.core.devotional.DevotionalVoice(context).preview(voice) }.getOrNull()
                previewing = false
                if (file != null) com.example.core.audio.PlaybackController.play(context, "preview:${voice.voiceName}", "Voice preview", file) else previewFailed = true
            }
        }
        if (previewFailed) Text("  No voice is available right now.", fontSize = 12.sp, color = InkMuted)
    }
    Text("Gemini's voices need Internet mode and your key; otherwise your phone's own voice reads it.", fontSize = 12.sp, lineHeight = 17.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
    ToggleRow("Pray the prayer aloud", null, voice.speakPrayer) { onChange(voice.copy(speakPrayer = it)) }
    ToggleRow("Have the voice ready each morning", "Records it with the devotional, so Listen starts instantly", voice.autoVoice) { onChange(voice.copy(autoVoice = it)) }
}
