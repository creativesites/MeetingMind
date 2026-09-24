package com.example.feature.devotional

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
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
    onReadPassage: (ScriptureReference) -> Unit,
    onShare: (com.example.feature.share.ShareRequest) -> Unit = {},
    /** "prayer": pray today's prayer aloud as soon as the page opens (from a story). */
    playOnOpen: String? = null,
    onLive: (com.example.core.prayer.PrayMode) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.ensureToday()
        if (playOnOpen == "prayer") viewModel.listen(com.example.ai.voice.VoiceSection.PRAYER, only = true)
    }
    var showAsk by rememberSaveable { mutableStateOf(false) }
    state.today?.let { t -> LaunchedEffect(t.note.id) { viewModel.opened(t) } }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    DevotionalContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onSettings = { showSettings = true },
        onOpenNote = onOpenNote,
        onReadPassage = onReadPassage,
        onRewrite = { viewModel.refreshWriters(); showAsk = true },
        onSelect = viewModel::select,
        onMakeCurrent = viewModel::makeCurrent,
        onDismissError = viewModel::dismissError,
        onFeedback = { d, v -> viewModel.feedback(d, v) },
        onSaveResponse = { d, t -> viewModel.saveResponse(d, t) },
        onListen = { viewModel.listen() },
        onListenFrom = { section, only -> viewModel.listen(section, only) },
        onLive = onLive,
        onShare = { t -> onShare(shareRequest(t, viewModel.audioPath(t), viewModel.coverPath(t))) }
    )
    if (showAsk) AskDevotionalSheet(
        profile = state.profile,
        writers = state.writers,
        onWrite = { ask -> showAsk = false; viewModel.rewrite(ask) },
        onDismiss = { showAsk = false }
    )
    if (showSettings) DevotionalSettingsSheet(
        profile = state.profile,
        onSave = { p, rewrite -> viewModel.saveProfile(p); if (rewrite) viewModel.rewrite(); showSettings = false },
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
    onListen: () -> Unit = {},
    onListenFrom: (com.example.ai.voice.VoiceSection, Boolean) -> Unit = { _, _ -> },
    onLive: (com.example.core.prayer.PrayMode) -> Unit = {},
    onShare: (DailyDevotional) -> Unit = {},
    onSelect: (DailyDevotional) -> Unit = {},
    onMakeCurrent: (DailyDevotional) -> Unit = {},
    onDismissError: () -> Unit = {},
    /** Verse text is fetched live; screenshots pass false to keep the page deterministic. */
    liveScripture: Boolean = true
) {
    val today = state.today
    LazyColumn(Modifier.fillMaxSize().background(Paper).testTag("devotional_screen")) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                Text("Today's devotional", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                // Always here: a new one, whenever the person wants, by the writer they choose.
                Surface(onClick = onRewrite, enabled = !state.writing, shape = RoundedCornerShape(50), color = Night, modifier = Modifier.testTag("devotional_new")) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.writing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFF6D365))
                        else Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFFF6D365), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.writing) "Writing…" else "New", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Tune, contentDescription = "Devotional settings", tint = Ink) }
            }
        }
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            val fallback = remember(state.date) { com.example.core.share.BackgroundLibrary.forDay(context, state.date.toEpochDay())?.file?.path }
            val cover = today?.let { t -> t.note.metadata[com.example.core.repository.NoteRepository.COVER_KEY]?.let { id -> t.document.attachments.firstOrNull { it.id == id }?.path } } ?: fallback
            Hero(state.date, state.season, today?.devotional, writing = state.writing && today == null, cover = cover) {
                if (today != null && today.devotional.origin != DevotionalOrigin.MINE && today.devotional.origin != DevotionalOrigin.CARE) ListenPill(state.voice, onListen)
            }
        }
        if (state.all.size > 1 && today != null) item { DayList(state.all, today, state.date, onSelect, onMakeCurrent) }
        if (today != null && state.voice.marks.size > 1) item { SectionChips(state.voice, onListenFrom) }
        if (today != null && state.writing) item { RewritingBanner() }
        if (today != null && state.writeError != null && !state.writing) item { ErrorBanner(state.writeError, onRetry = onRewrite, onDismiss = onDismissError) }
        today?.note?.metadata?.get(com.example.core.devotional.META_ASKED)?.let { asked ->
            item { Text("Written for: “$asked”", fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, fontStyle = FontStyle.Italic, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
        }
        when {
            today == null && state.writing -> item { Writing() }
            today == null && state.writeError != null -> item {
                Callout("Couldn't write today's devotional", "${state.writeError}", "Try again") { onRewrite() }
            }
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
                d.prayer?.let { p -> item { PrayerCard(p, praying = state.voice.current == com.example.ai.voice.VoiceSection.PRAYER && state.voice.playing, onPrayWithMe = { onLive(com.example.core.prayer.PrayMode.TOGETHER) }) { onListenFrom(com.example.ai.voice.VoiceSection.PRAYER, true) } } }
                d.motivation?.let { m -> item { WordForToday(m) } }
                d.insight?.let { q -> item { QuoteCard(q.text, listOf(q.author, q.source).filter { it.isNotBlank() }.joinToString(", ")) } }
                d.question?.let { q -> item { QuestionCard(q) } }
                if (d.origin != DevotionalOrigin.CARE) {
                    item { Response(today, onSaveResponse) }
                    item { Feedback(today, onFeedback) }
                }
                if (d.origin != DevotionalOrigin.CARE) item {
                    Surface(onClick = { onLive(com.example.core.prayer.PrayMode.TALK_IT_THROUGH) }, shape = RoundedCornerShape(22.dp), color = Night, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Talk it through", fontSize = 17.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Reflect on this out loud with a thoughtful companion", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color(0xFFF6D365))
                        }
                    }
                }
                item { Footer(today, onOpenNote, onRewrite, onShare) }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** Who wrote a devotional, in a word, for the day's list. */
private fun writerLabel(d: DailyDevotional): String = when (d.devotional.origin) {
    DevotionalOrigin.CLOUD_AI -> "Gemini"
    DevotionalOrigin.DEVICE_AI -> "On phone"
    DevotionalOrigin.CLASSIC -> "Classic"
    DevotionalOrigin.MINE -> "My page"
    DevotionalOrigin.CARE -> "Care"
}

/** Every devotional written today: tap to read one; the day's own is marked, and any can become it. */
@Composable
private fun DayList(all: List<DailyDevotional>, shown: DailyDevotional, date: LocalDate, onSelect: (DailyDevotional) -> Unit, onMakeCurrent: (DailyDevotional) -> Unit) {
    val dayKey = DevotionalNotes.key(com.example.core.devotional.LocalDay.of(date))
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("Today's devotionals · ${all.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(horizontal = 20.dp))
        androidx.compose.foundation.lazy.LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("devotional_day_list")) {
            items(all.size) { i ->
                val d = all[i]
                val on = d.note.id == shown.note.id
                val current = d.note.metadata[DevotionalNotes.META_KEY] == dayKey
                Surface(onClick = { onSelect(d) }, shape = RoundedCornerShape(16.dp), color = if (on) Night else Color.White,
                    border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E1D6)), modifier = Modifier.width(168.dp)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${writerLabel(d)} · ${time.format(java.util.Date(d.note.createdAt))}", fontSize = 11.5.sp, color = if (on) Color(0xFFF6D365) else Gold, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                            if (current) Icon(Icons.Filled.Star, contentDescription = "Today's", tint = if (on) Color(0xFFF6D365) else Gold, modifier = Modifier.size(14.dp))
                        }
                        Text(d.devotional.title, fontSize = 13.5.sp, fontFamily = FontFamily.Serif, color = if (on) Color.White else Ink, maxLines = 2, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
        if (shown.note.metadata[DevotionalNotes.META_KEY] != dayKey) {
            Text("★ Make this today's devotional", fontSize = 13.sp, color = Gold, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(8.dp)).clickable { onMakeCurrent(shown) }.padding(vertical = 4.dp).testTag("devotional_make_current"))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFEF2F2)).padding(14.dp).testTag("devotional_error"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Couldn't write a new one", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF991B1B))
            Text("$message The one you're reading is unchanged.", fontSize = 12.5.sp, lineHeight = 17.sp, color = Color(0xFF7F1D1D))
            Text("Try again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF991B1B), modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(vertical = 2.dp))
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color(0xFF991B1B)) }
    }
}

@Composable
private fun Hero(date: LocalDate, season: LiturgicalDay?, d: Devotional?, writing: Boolean, cover: String? = null, actions: @Composable () -> Unit = {}) {
    val tint = season?.season?.color?.let { Color(it) } ?: Gold
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Night, Color(0xFF2B2140), Color(0xFF3A2A1A))))
    ) {
        if (cover != null) {
            coil.compose.AsyncImage(model = java.io.File(cover), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Night.copy(alpha = 0.85f)))))
        } else Box(Modifier.align(Alignment.TopEnd).size(220.dp).offset(x = 70.dp, y = (-60).dp)
            .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.55f), Color.Transparent)), CircleShape))
        Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 22.dp, top = if (cover != null) 120.dp else 22.dp)) {
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
                    Text(shortLabel(dev), fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
                }
            }
            actions()
        }
    }
}

private fun shortLabel(d: Devotional) = when (d.origin) {
    DevotionalOrigin.CLASSIC -> "Classic · ${d.engine ?: "public domain"}"
    else -> d.label
}

@Composable
private fun ListenPill(voice: VoiceUi, onListen: () -> Unit) {
    val label = when {
        voice.preparing -> "Preparing the voice… ${(voice.progress * 100).toInt()}%"
        voice.playing -> "Pause"
        voice.positionMs > 0 -> "Resume"
        voice.failed -> "Couldn't prepare the voice — try again"
        else -> "Listen"
    }
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onListen, enabled = !voice.preparing, shape = RoundedCornerShape(50), color = Color(0xFFF6D365), modifier = Modifier.testTag("devotional_listen")) {
            Row(Modifier.padding(start = 12.dp, end = 18.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                if (voice.preparing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Night)
                else Icon(if (voice.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Night, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Night)
            }
        }
        if (voice.durationMs > 0) {
            Spacer(Modifier.width(10.dp))
            Text("${com.example.core.common.Formatters.formatDurationHms(voice.positionMs)} / ${com.example.core.common.Formatters.formatDurationHms(voice.durationMs)}",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
    voice.voiceLabel?.takeIf { !voice.preparing }?.let {
        Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(top = 6.dp, start = 4.dp))
    }
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
private fun PrayerCard(text: String, praying: Boolean = false, onPrayWithMe: () -> Unit = {}, onPray: () -> Unit = {}) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 18.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF4DC), Color(0xFFFFFBF2)))).padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PRAYER", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Gold, modifier = Modifier.weight(1f))
            Surface(onClick = onPray, shape = RoundedCornerShape(50), color = if (praying) Gold else Color.White, modifier = Modifier.testTag("pray_aloud")) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (praying) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow, contentDescription = null, tint = if (praying) Color.White else Gold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (praying) "Praying…" else "Pray it aloud", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (praying) Color.White else Gold)
                }
            }
        }
        Surface(onClick = onPrayWithMe, shape = RoundedCornerShape(50), color = Night, modifier = Modifier.padding(top = 12.dp).testTag("pray_with_me")) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color(0xFFF6D365), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pray with me", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
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
    var saved by remember(today.note.id) { mutableStateOf(today.response) }
    // Saves as you pause, and when you leave — nothing to remember to press.
    LaunchedEffect(text) {
        if (text.trim() == saved.trim()) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        onSave(today, text); saved = text
    }
    val latest = androidx.compose.runtime.rememberUpdatedState(text)
    androidx.compose.runtime.DisposableEffect(today.note.id) {
        onDispose { if (latest.value.trim() != saved.trim()) onSave(today, latest.value) }
    }
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 18.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Color.White).border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(24.dp)).padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MY RESPONSE", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Gold, modifier = Modifier.weight(1f))
            if (text.isNotBlank()) Text(if (text.trim() == saved.trim()) "Saved" else "Saving…", fontSize = 11.sp, color = InkMuted)
        }
        androidx.compose.foundation.text.BasicTextField(
            value = text, onValueChange = { text = it },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif, color = Ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Gold),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("devotional_response"),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("What is God stirring in you today? It stays on your phone.", fontSize = 17.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif, color = InkMuted)
                    inner()
                }
            }
        )
    }
}

@Composable
private fun SectionChips(voice: VoiceUi, onListenFrom: (com.example.ai.voice.VoiceSection, Boolean) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp).testTag("voice_sections")
    ) {
        items(voice.marks.size) { i ->
            val (section, at) = voice.marks[i]
            val on = voice.current == section && voice.playing
            Surface(onClick = { onListenFrom(section, false) }, shape = RoundedCornerShape(50), color = if (on) Night else Color.White, border = BorderStroke(1.dp, if (on) Night else Color(0xFFE2E8F0))) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (on) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow, contentDescription = null, tint = if (on) Color(0xFFF6D365) else Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(section.label, fontSize = 13.sp, color = if (on) Color.White else Ink, fontWeight = FontWeight.Medium)
                    Text("  ${com.example.core.common.Formatters.formatDurationHms(at)}", fontSize = 11.sp, color = if (on) Color.White.copy(alpha = 0.6f) else InkMuted)
                }
            }
        }
    }
}

@Composable
private fun RewritingBanner() {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Gold.copy(alpha = 0.12f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Gold)
        Spacer(Modifier.width(12.dp))
        Text("Writing a new one for you… it will appear here.", fontSize = 14.sp, color = Ink)
    }
}

@Composable
private fun Feedback(today: DailyDevotional, onFeedback: (DailyDevotional, String?) -> Unit) {
    val current = today.feedback
    Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(when (current) { "up", "more" -> "Glad it spoke to you — more like this."; "down", "less" -> "Thanks — we'll steer a little differently."; else -> "Did this speak to you?" },
            fontSize = 14.sp, color = InkSecondary, fontWeight = FontWeight.Medium)
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
        onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Gold else Color.White,
        border = BorderStroke(1.dp, if (selected) Gold else Color(0xFFE2E8F0))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, contentDescription = null, tint = if (selected) Color.White else InkSecondary, modifier = Modifier.size(16.dp)) }
            label?.let { Text(it, fontSize = 13.sp, color = if (selected) Color.White else InkSecondary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Footer(today: DailyDevotional, onOpenNote: (String) -> Unit, onRewrite: () -> Unit, onShare: (DailyDevotional) -> Unit) {
    val d = today.devotional
    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
        if (d.origin == DevotionalOrigin.CLASSIC) Text(d.label, fontSize = 12.sp, lineHeight = 17.sp, color = InkMuted)
        today.note.metadata["devotionalFallback"]?.let {
            Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = InkMuted, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 6.dp))
        }
        Surface(onClick = { onShare(today) }, shape = RoundedCornerShape(50), color = Ink, modifier = Modifier.padding(top = 14.dp).testTag("devotional_share")) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
                Text("Share to WhatsApp, Instagram…", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = { onOpenNote(today.note.id) }, shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Open as note", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
                }
            }
            Surface(onClick = onRewrite, shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Write me another", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
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

/** What sharing a devotional sends: its word for today (or opening), titled, with its label. */
internal fun shareRequest(t: DailyDevotional, audioPath: String?, coverPath: String?): com.example.feature.share.ShareRequest {
    val d = t.devotional
    val text = d.keyText?.takeIf { d.origin == DevotionalOrigin.CLASSIC }
        ?: d.motivation
        ?: d.reflection.firstOrNull()?.let { p -> p.split(Regex("(?<=[.!?])\\s+")).fold("") { acc, s -> if (acc.length > 180) acc else "$acc $s" }.trim() }
        ?: d.title
    val ref = listOfNotNull(d.title.takeIf { it != text }, d.scripture.firstOrNull()?.display()).joinToString(" · ").ifBlank { null }
    return com.example.feature.share.ShareRequest(
        content = com.example.core.share.ShareCardContent("Today's devotional", text, ref, d.label.takeIf { d.origin == DevotionalOrigin.CLASSIC }, quoted = d.origin == DevotionalOrigin.CLASSIC),
        theme = listOfNotNull(d.title, d.scripture.firstOrNull()?.display()).joinToString(", "),
        background = coverPath?.let { com.example.core.share.BackgroundSpec.Photo(it) },
        audioPath = audioPath,
        caption = d.title
    )
}
