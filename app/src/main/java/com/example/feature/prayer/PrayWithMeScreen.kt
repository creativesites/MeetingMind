package com.example.feature.prayer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.live.LiveVoiceState
import com.example.ai.voice.PreacherStyle
import com.example.ai.voice.VoiceGender
import com.example.core.prayer.PrayMode
import com.example.core.prayer.PraySetup
import com.example.core.prayer.PrayerStyle

private val Night = Color(0xFF0E0B1A)
private val Gold = Color(0xFFF6D365)

/** "Pray with me" and "Talk it through" (PLAN_V2 F7), with Gemini Live. */
@Composable
fun PrayWithMeScreen(viewModel: PrayWithMeViewModel, startMode: PrayMode?, onNavigateBack: () -> Unit, onOpenNote: (String) -> Unit, onOpenSettings: () -> Unit) {
    val ui by viewModel.ui.collectAsState()
    if (!ui.started) PraySetupContent(ui, startMode, onNavigateBack, onOpenSettings) { setup, voice -> viewModel.start(setup, voice) }
    else PraySessionContent(viewModel, ui, onClose = { viewModel.end(); onNavigateBack() }, onOpenNote = onOpenNote)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PraySetupContent(ui: PrayUi, startMode: PrayMode?, onNavigateBack: () -> Unit, onOpenSettings: () -> Unit, onStart: (PraySetup, String) -> Unit) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(startMode ?: PrayMode.TOGETHER) }
    var style by rememberSaveable { mutableStateOf(PrayerStyle.CONVERSATIONAL) }
    var about by rememberSaveable { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var withDevotional by rememberSaveable { mutableStateOf(startMode == PrayMode.TALK_IT_THROUGH) }
    var voiceStyle by rememberSaveable { mutableStateOf(PreacherStyle.GENTLE_FRIEND) }
    var gender by rememberSaveable { mutableStateOf(VoiceGender.FEMALE) }
    var worship by rememberSaveable { mutableStateOf<String?>(null) }
    val talk = mode == PrayMode.TALK_IT_THROUGH

    fun begin() {
        val setup = PraySetup(
            mode = mode, style = style, about = about,
            requests = ui.requests.filter { it.first in chosen }.map { it.second },
            devotional = if (withDevotional || talk) listOfNotNull(ui.devotional, ui.devotionalPrayer?.let { "Its prayer: $it" }).joinToString("\n").ifBlank { null } else null,
            worship = if (talk) null else worship,
            persona = voiceStyle.style
        )
        onStart(setup, if (gender == VoiceGender.MALE) voiceStyle.male else voiceStyle.female)
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) begin() }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, Color(0xFF221A3A), Color(0xFF3A2A1A)))).testTag("pray_setup")) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Text(if (talk) "Talk it through" else "Pray with me", fontSize = 32.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                if (talk) "Reflect on today's devotional out loud, with a thoughtful companion." else "Someone to pray with — out loud, at your pace, in the way you pray.",
                fontSize = 15.sp, lineHeight = 22.sp, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
            if (ui.available == false) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.08f)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("This needs Gemini", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Add your Gemini API key in Settings. Your voice goes to Gemini only while you pray here — recordings can stay on your phone.", fontSize = 13.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
                        Surface(onClick = onOpenSettings, shape = RoundedCornerShape(50), color = Gold, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Open Settings", color = Night, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            if (!talk) {
                Label("How would you like to pray?")
                PrayMode.prayerModes.forEach { m -> ModeRow(m, mode == m) { mode = m } }
                Label("Style")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrayerStyle.entries.forEach { s -> DarkChip(s.label, style == s) { style = s } }
                }
            }
            Label(if (talk) "Anything you want to start with? (optional)" else "What's on your heart? (optional)")
            OutlinedTextField(
                value = about, onValueChange = { about = it.take(800) }, minLines = 3,
                placeholder = { Text(if (talk) "e.g. I keep thinking about the part on rest…" else "e.g. My mum's surgery on Friday, and peace for my exams", color = Color.White.copy(alpha = 0.4f)) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Gold, unfocusedBorderColor = Color.White.copy(alpha = 0.25f), cursorColor = Gold),
                modifier = Modifier.fillMaxWidth().testTag("pray_about")
            )
            if (!talk && ui.requests.isNotEmpty()) {
                Label("From your prayer list")
                ui.requests.take(12).forEach { (id, title) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { chosen = if (id in chosen) chosen - id else chosen + id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = id in chosen, onCheckedChange = { chosen = if (it) chosen + id else chosen - id }, colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Color.White.copy(alpha = 0.5f), checkmarkColor = Night))
                        Text(title, fontSize = 15.sp, color = Color.White)
                    }
                }
            }
            if (!talk && ui.devotional != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { withDevotional = !withDevotional }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withDevotional, onCheckedChange = { withDevotional = it }, colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Color.White.copy(alpha = 0.5f), checkmarkColor = Night))
                    Text("Pray from today's devotional", fontSize = 15.sp, color = Color.White)
                }
            }
            if (!talk) {
                Label("Begin with worship? (optional)")
                Text("Sing a hymn together first, or start your own song and it follows you.", fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("pray_worship")) {
                    DarkChip("No, straight to prayer", worship == null) { worship = null }
                    DarkChip("♪ " + com.example.core.prayer.WorshipSongs.THEIR_OWN, worship == com.example.core.prayer.WorshipSongs.THEIR_OWN) { worship = com.example.core.prayer.WorshipSongs.THEIR_OWN }
                    com.example.core.prayer.WorshipSongs.hymns.forEach { h -> DarkChip(h, worship == h) { worship = h } }
                }
            }
            Label("Voice")
            PreacherStyle.entries.groupBy { it.tradition }.forEach { (tradition, styles) ->
                Text(tradition, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    styles.forEach { s -> DarkChip(s.label, voiceStyle == s) { voiceStyle = s } }
                }
            }
            Text(voiceStyle.line, fontSize = 13.sp, color = Gold.copy(alpha = 0.85f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceGender.entries.forEach { g -> DarkChip(g.label, gender == g) { gender = g } }
            }
            Text("Your voice is sent to Gemini only during this conversation. Nothing is kept unless you save it at the end.", fontSize = 12.sp, lineHeight = 17.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 18.dp, bottom = 20.dp))
        }
        Surface(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                else mic.launch(Manifest.permission.RECORD_AUDIO)
            },
            enabled = ui.available == true, shape = RoundedCornerShape(50), color = if (ui.available == true) Gold else Color.White.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp).testTag("pray_begin")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (talk) "Begin" else "Begin praying", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (ui.available == true) Night else Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun ModeRow(mode: PrayMode, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp), color = if (selected) Gold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (selected) Gold else Color.White.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mode.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(mode.line, fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f))
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Gold)
        }
    }
}

@Composable
private fun DarkChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Gold else Color.White.copy(alpha = 0.08f)) {
        Text(label, fontSize = 13.sp, color = if (selected) Night else Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gold.copy(alpha = 0.9f), modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}
