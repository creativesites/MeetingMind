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
    val talk = mode == PrayMode.TALK_IT_THROUGH

    fun begin() {
        val setup = PraySetup(
            mode = mode, style = style, about = about,
            requests = ui.requests.filter { it.first in chosen }.map { it.second },
            devotional = if (withDevotional || talk) listOfNotNull(ui.devotional, ui.devotionalPrayer?.let { "Its prayer: $it" }).joinToString("\n").ifBlank { null } else null
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
                        Text("Turn on Internet mode and add your Gemini key in Settings. Your voice goes to Gemini only while you pray here.", fontSize = 13.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
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
            Label("Voice")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreacherStyle.entries.forEach { s -> DarkChip(s.label, voiceStyle == s) { voiceStyle = s } }
            }
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

@Composable
private fun PraySessionContent(viewModel: PrayWithMeViewModel, ui: PrayUi, onClose: () -> Unit, onOpenNote: (String) -> Unit) {
    val level by viewModel.level.collectAsState()
    var typing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val ended = ui.state == LiveVoiceState.ENDED || ui.state == LiveVoiceState.FAILED
    val speaking = ui.state == LiveVoiceState.SPEAKING
    val breathe = rememberInfiniteTransition(label = "breathe")
    val slow by breathe.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "slow")
    val lvl by animateFloatAsState(level, tween(140), label = "lvl")
    val listState = rememberLazyListState()
    LaunchedEffect(ui.lines.size, ui.lines.lastOrNull()?.text?.length) { if (ui.lines.isNotEmpty()) listState.animateScrollToItem(ui.lines.size - 1) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, Color(0xFF1B1530), Color(0xFF2B2140)))).testTag("pray_session")) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White) }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            // The orb: breathes while listening, swells with the voice while it prays.
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                val s = if (ended) 1f else slow + lvl * (if (speaking) 0.35f else 0.2f)
                Box(Modifier.size(210.dp).graphicsLayer { scaleX = s; scaleY = s }
                    .background(Brush.radialGradient(listOf((if (speaking) Gold else Color(0xFF93C5FD)).copy(alpha = 0.45f), Color.Transparent)), CircleShape))
                Box(Modifier.size(118.dp).graphicsLayer { scaleX = 0.96f + lvl * 0.12f; scaleY = 0.96f + lvl * 0.12f }.clip(CircleShape)
                    .background(Brush.linearGradient(if (speaking) listOf(Color(0xFFFFE9A8), Color(0xFFE0A33A)) else listOf(Color(0xFFDBEAFE), Color(0xFF6366F1)))))
            }
            Text(
                when (ui.state) {
                    LiveVoiceState.CONNECTING -> "Getting ready…"
                    LiveVoiceState.LISTENING -> "I'm listening"
                    LiveVoiceState.SPEAKING -> "Praying…"
                    LiveVoiceState.PAUSED -> "Microphone off"
                    LiveVoiceState.ENDED -> "Amen"
                    LiveVoiceState.FAILED -> "The connection stopped"
                },
                fontSize = 20.sp, fontFamily = FontFamily.Serif, color = Color.White, modifier = Modifier.padding(top = 10.dp)
            )
            ui.error?.let { Text(it, fontSize = 13.sp, color = Color(0xFFFCA5A5), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)) }

            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                items(ui.lines) { l ->
                    Text(
                        l.text, fontSize = if (l.mine) 15.sp else 18.sp, lineHeight = if (l.mine) 21.sp else 27.sp,
                        fontFamily = if (l.mine) FontFamily.Default else FontFamily.Serif,
                        color = if (l.mine) Color.White.copy(alpha = 0.6f) else Color.White,
                        textAlign = if (l.mine) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
            }

            if (ended) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ui.lines.isNotEmpty()) {
                        val saved = ui.savedNoteId
                        Surface(onClick = { if (saved != null) onOpenNote(saved) else viewModel.save() }, shape = RoundedCornerShape(50), color = Gold) {
                            Text(if (saved != null) "Open saved prayer" else "Keep this as a note", color = Night, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                        }
                    }
                    Surface(onClick = { viewModel.reset() }, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.12f)) {
                        Text("Pray again", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    }
                }
            } else {
                if (typing) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it }, placeholder = { Text("Type instead…", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Gold, unfocusedBorderColor = Color.White.copy(alpha = 0.25f), cursorColor = Gold),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.say(draft); draft = "" }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Gold) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SessionButton(if (ui.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (ui.muted) "Unmute" else "Mute", Color.White.copy(alpha = 0.12f)) { viewModel.toggleMute() }
                    Surface(onClick = { viewModel.end() }, shape = RoundedCornerShape(50), color = Gold, modifier = Modifier.testTag("pray_amen")) {
                        Text("Amen", fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Night, modifier = Modifier.padding(horizontal = 34.dp, vertical = 16.dp))
                    }
                    SessionButton(Icons.Filled.Keyboard, "Type", Color.White.copy(alpha = 0.12f)) { typing = !typing }
                }
            }
        }
    }
}

@Composable
private fun SessionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(56.dp).clip(CircleShape).background(bg)) { Icon(icon, contentDescription = label, tint = Color.White) }
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
    }
}
