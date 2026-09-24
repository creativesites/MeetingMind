package com.example.feature.devotional

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.devotional.DailyDevotional
import com.example.core.devotional.Devotional
import com.example.core.devotional.DevotionalNotes
import com.example.core.devotional.DevotionalOrigin
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.LiturgicalDay
import com.example.core.model.NoteBlockType
import com.example.core.scripture.ScriptureReference
import com.example.feature.scripture.ScriptureCard
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val Gold = Color(0xFFB7791F)
private val Paper = Color(0xFFFCFAF6)
private val Night = Color(0xFF1B1530)

/** Today's devotional (PLAN_V2 F2). */
@Composable
fun DevotionalScreen(
    viewModel: DevotionalViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onReadPassage: (ScriptureReference) -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureToday() }
    state.today?.let { t -> LaunchedEffect(t.note.id) { viewModel.opened(t) } }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    DevotionalContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onSettings = { showSettings = true },
        onOpenNote = onOpenNote,
        onReadPassage = onReadPassage,
        onRewrite = viewModel::rewrite,
        onFeedback = { d, v -> viewModel.feedback(d, v) },
        onSaveResponse = { d, t -> viewModel.saveResponse(d, t) }
    )
    if (showSettings) DevotionalSettingsSheet(
        profile = state.profile,
        onSave = { p, rewrite -> viewModel.saveProfile(p, rewrite); showSettings = false },
        onDismiss = { showSettings = false }
    )
}

@Composable
fun DevotionalContent(
    state: DevotionalUiState,
    onNavigateBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenNote: (String) -> Unit,
    onReadPassage: (ScriptureReference) -> Unit,
    onRewrite: () -> Unit,
    onFeedback: (DailyDevotional, String?) -> Unit,
    onSaveResponse: (DailyDevotional, String) -> Unit,
    /** Verse text is fetched live; screenshots pass false to keep the page deterministic. */
    liveScripture: Boolean = true
) {
    val today = state.today
    LazyColumn(Modifier.fillMaxSize().background(Paper).testTag("devotional_screen")) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                Text("Today's devotional", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Tune, contentDescription = "Devotional settings", tint = Ink) }
            }
        }
        item { Hero(state.date, state.season, today?.devotional, writing = state.writing && today == null) }
        when {
            today == null && state.writing -> item { Writing() }
            today == null -> item {
                Intro(enabled = state.profile.enabled, onSettings = onSettings, onRewrite = onRewrite)
            }
            today.devotional.origin == DevotionalOrigin.MINE -> item {
                Callout("Your page is ready", "Today's passage is at the top. Read it slowly, then write what stands out.", "Open my page") { onOpenNote(today.note.id) }
            }
            else -> {
                val d = today.devotional
                if (d.origin == DevotionalOrigin.CARE) item { CareCard(d) }
                item { Section("Scripture") }
                d.keyText?.let { k -> item { KeyText(k) } }
                d.scripture.forEach { ref ->
                    item {
                        if (liveScripture) ScriptureCard(reference = ref, heardAtMs = null, onOpen = { onReadPassage(ref) }, onPlay = null, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        else StaticReference(ref)
                    }
                }
                if (d.origin != DevotionalOrigin.CARE && d.reflection.isNotEmpty()) {
                    item { Section("Reflection") }
                    d.reflection.forEach { p -> item { Paragraph(p) } }
                }
                if (d.application.isNotEmpty()) {
                    item { Section("Today I will") }
                    val checks = today.document.blocks.filter { it.sectionKey == DevotionalNotes.S_APPLICATION && it.type == NoteBlockType.CHECKLIST }.sortedBy { it.position }
                    d.application.forEachIndexed { i, a -> item { CheckRow(a, checks.getOrNull(i)?.checked == true) } }
                }
                d.prayer?.let { p -> item { PrayerCard(p) } }
                d.motivation?.let { m -> item { WordForToday(m) } }
                d.insight?.let { q -> item { QuoteCard(q.text, listOf(q.author, q.source).filter { it.isNotBlank() }.joinToString(", ")) } }
                d.question?.let { q -> item { QuestionCard(q) } }
                if (d.origin != DevotionalOrigin.CARE) {
                    item { Response(today, onSaveResponse) }
                    item { Feedback(today, onFeedback) }
                }
                item { Footer(today, onOpenNote, onRewrite) }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
private fun Hero(date: LocalDate, season: LiturgicalDay?, d: Devotional?, writing: Boolean) {
    val tint = season?.season?.color?.let { Color(it) } ?: Gold
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Night, Color(0xFF2B2140), Color(0xFF3A2A1A))))
    ) {
        Box(Modifier.align(Alignment.TopEnd).size(220.dp).offset(x = 70.dp, y = (-60).dp)
            .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.55f), Color.Transparent)), CircleShape))
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date.format(DATE), fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Medium)
                season?.let {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.35f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                        Text(it.describe(), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                d?.title ?: if (writing) "Preparing today's word…" else "A word for today",
                fontSize = 28.sp, lineHeight = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Color.White,
                modifier = Modifier.padding(top = 14.dp)
            )
            d?.scripture?.firstOrNull()?.let {
                Text(it.display(), fontSize = 14.sp, color = Color(0xFFF6D365), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }
            d?.let { dev ->
                Row(Modifier.padding(top = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dev.origin == DevotionalOrigin.CLOUD_AI || dev.origin == DevotionalOrigin.DEVICE_AI) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFFF6D365), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(shortLabel(dev), fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun shortLabel(d: Devotional) = when (d.origin) {
    DevotionalOrigin.CLASSIC -> "Classic · ${d.engine ?: "public domain"}"
    else -> d.label
}

@Composable
private fun Writing() {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Gold)
        Spacer(Modifier.width(12.dp))
        Text("Writing today's devotional — a moment of quiet while it comes together.", fontSize = 14.sp, color = InkSecondary, lineHeight = 20.sp)
    }
}

@Composable
private fun Intro(enabled: Boolean, onSettings: () -> Unit, onRewrite: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text(
            if (enabled) "Today's devotional isn't here yet." else "A devotional every morning, shaped by your week — or a classic, or your own page.",
            fontSize = 15.sp, lineHeight = 22.sp, color = InkSecondary
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Make it mine", filled = true, onClick = onSettings)
            PillButton("Read today's", filled = false, onClick = onRewrite)
        }
    }
}

@Composable
internal fun PillButton(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50), color = if (filled) Ink else Color.White,
        border = if (filled) null else BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = modifier
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else Ink, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Gold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun KeyText(text: String) {
    Text(text, fontSize = 19.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Ink,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
}

@Composable
private fun StaticReference(ref: ScriptureReference) {
    Text(ref.display(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp))
}

@Composable
private fun Paragraph(text: String) {
    Text(text, fontSize = 17.sp, lineHeight = 28.sp, fontFamily = FontFamily.Serif, color = Color(0xFF2A2A2A),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 7.dp))
}

@Composable
private fun CheckRow(text: String, checked: Boolean) {
    Row(Modifier.padding(horizontal = 22.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 15.5.sp, lineHeight = 22.sp, color = Ink)
    }
}

@Composable
private fun PrayerCard(text: String) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 18.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF4DC), Color(0xFFFFFBF2)))).padding(22.dp)
    ) {
        Text("PRAYER", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Gold)
        Text(text, fontSize = 17.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Color(0xFF3A2A1A), modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun WordForToday(text: String) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))).padding(22.dp)
    ) {
        Text("A WORD FOR TODAY", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
        Text(text, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun QuoteCard(text: String, by: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(22.dp)) {
        Icon(Icons.Filled.FormatQuote, contentDescription = null, tint = Gold, modifier = Modifier.size(30.dp))
        Text(text, fontSize = 18.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.padding(top = 4.dp))
        if (by.isNotBlank()) Text("— $by", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun QuestionCard(text: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFEFF6F1)).padding(22.dp)) {
        Text("A QUESTION TO SIT WITH", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2F7D5B))
        Text(text, fontSize = 18.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun CareCard(d: Devotional) {
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFF1F2)).padding(22.dp)) {
        Text("You're not alone", fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Color(0xFF9F1239))
        d.reflection.forEach { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Ink, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
private fun Callout(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(22.dp)) {
        Text(title, fontSize = 19.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = InkSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        PillButton(action, filled = true, onClick = onClick)
    }
}

@Composable
private fun Response(today: DailyDevotional, onSave: (DailyDevotional, String) -> Unit) {
    var text by remember(today.note.id) { mutableStateOf(today.response) }
    val dirty = text.trim() != today.response.trim()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
        Text("MY RESPONSE", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Gold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it }, minLines = 3,
            placeholder = { Text("What is God stirring in you? This stays on your phone.") },
            modifier = Modifier.fillMaxWidth().testTag("devotional_response")
        )
        if (dirty) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onSave(today, text) }) { Text("Save", color = Accent, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Feedback(today: DailyDevotional, onFeedback: (DailyDevotional, String?) -> Unit) {
    val current = today.feedback
    Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text("Did this speak to you?", fontSize = 14.sp, color = InkSecondary, fontWeight = FontWeight.Medium)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackChip(if (current == "up") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, null, current == "up") { onFeedback(today, if (current == "up") null else "up") }
            FeedbackChip(if (current == "down") Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, null, current == "down") { onFeedback(today, if (current == "down") null else "down") }
            FeedbackChip(null, "More like this", current == "more") { onFeedback(today, if (current == "more") null else "more") }
            FeedbackChip(null, "Less", current == "less") { onFeedback(today, if (current == "less") null else "less") }
        }
    }
}

@Composable
private fun FeedbackChip(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Gold.copy(alpha = 0.16f) else Color.White,
        border = BorderStroke(1.dp, if (selected) Gold else Color(0xFFE2E8F0))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, contentDescription = null, tint = if (selected) Gold else InkSecondary, modifier = Modifier.size(16.dp)) }
            label?.let { Text(it, fontSize = 13.sp, color = if (selected) Gold else InkSecondary, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun Footer(today: DailyDevotional, onOpenNote: (String) -> Unit, onRewrite: () -> Unit) {
    val d = today.devotional
    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
        val line = when (d.origin) {
            DevotionalOrigin.CLOUD_AI, DevotionalOrigin.DEVICE_AI ->
                "${d.label}${d.engine?.let { " by $it" } ?: ""}. Bible verses are shown from the Bible itself, never written by AI. Weigh every word against Scripture."
            DevotionalOrigin.CLASSIC -> d.label
            else -> d.label
        }
        Text(line, fontSize = 12.sp, lineHeight = 17.sp, color = InkMuted)
        today.note.metadata["devotionalFallback"]?.let {
            Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = InkMuted, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 6.dp))
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = { onOpenNote(today.note.id) }, shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Open as note", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
                }
            }
            Surface(onClick = onRewrite, shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("A different one", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** For callers outside this screen: a compact "today's devotional" line. */
fun DevotionalProfile.summary(): String = buildString {
    append(source.label)
    append(" · ${minutes} min · ${tone.label}")
}
