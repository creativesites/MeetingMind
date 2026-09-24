package com.example.feature.devotional

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.devotional.DevotionalAsk
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.DevotionalTone
import com.example.core.devotional.DevotionalTopics
import com.example.core.scripture.ScriptureReferenceParser
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary

/**
 * "Write me one": a devotional on demand, shaped by the person's own words — what's on their
 * heart, a passage, topics, a voice and a length — for today only (the profile is untouched).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskDevotionalSheet(profile: DevotionalProfile, onWrite: (DevotionalAsk?) -> Unit, onDismiss: () -> Unit) {
    var about by remember { mutableStateOf("") }
    var passage by remember { mutableStateOf("") }
    var topics by remember { mutableStateOf(emptySet<String>()) }
    var tone by remember { mutableStateOf<DevotionalTone?>(null) }
    var minutes by remember { mutableStateOf<Int?>(null) }
    val parsed = remember(passage) { passage.takeIf { it.isNotBlank() }?.let { ScriptureReferenceParser.parse(it) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding()) {
            Text("Write me one", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("Tell it what you'd like today — or just ask for a different one.", fontSize = 14.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))

            OutlinedTextField(
                value = about, onValueChange = { about = it.take(600) }, minLines = 3,
                label = { Text("What's on your heart?") },
                placeholder = { Text("e.g. I start a new job tomorrow and I'm anxious. Or: forgiving my brother.") },
                modifier = Modifier.fillMaxWidth().testTag("ask_about")
            )
            OutlinedTextField(
                value = passage, onValueChange = { passage = it.replace("\n", "") }, singleLine = true,
                label = { Text("A passage (optional)") }, placeholder = { Text("e.g. Psalm 46 or Phil 4:6-7") },
                supportingText = { if (passage.isNotBlank()) Text(parsed?.display() ?: "That isn't a Bible reference yet.", color = if (parsed != null) Gold else InkMuted) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Label("About")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DevotionalTopics.all.forEach { t -> Choice(t, t in topics) { topics = if (t in topics) topics - t else topics + t } }
            }
            Label("Voice")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DevotionalTone.entries.forEach { t -> Choice(t.label, (tone ?: profile.tone) == t) { tone = t } }
            }
            Label("Length")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 7, 12).forEach { m -> Choice("$m min", (minutes ?: profile.minutes) == m) { minutes = m } }
            }
            Text("Written by AI for today, and labelled as such. Your words go to the AI only when Internet mode is on.", fontSize = 12.sp, lineHeight = 17.sp, color = InkMuted, modifier = Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val custom = DevotionalAsk(about.trim().ifBlank { null }, parsed?.display(), topics, tone, minutes)
                PillButton(if (custom.custom) "Write it" else "Surprise me", filled = true, modifier = Modifier.testTag("ask_write")) { onWrite(custom.takeIf { it.custom }) }
                if (custom.custom) PillButton("Just a different one", filled = false) { onWrite(null) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = if (selected) Gold.copy(alpha = 0.14f) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Gold else Color(0xFFE2E8F0))
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) Color(0xFF7A4E0F) else InkSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}
