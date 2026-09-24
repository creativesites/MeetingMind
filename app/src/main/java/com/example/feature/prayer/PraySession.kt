package com.example.feature.prayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.live.LiveVoiceState
import com.example.core.prayer.PrayMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFF07060F)
private val Gold = Color(0xFFF6D365)
private val Amber = Color(0xFFE8A33A)
private val Cyan = Color(0xFF5EE7FF)
private val Indigo = Color(0xFF6366F1)
private val Violet = Color(0xFFA78BFA)
private val Rose = Color(0xFFF472B6)

/** What the room feels like right now: who has the floor, and whether we're singing. */
private enum class Mood { CONNECTING, LISTENING, SPEAKING, SINGING, PAUSED, ENDED }

private fun moodOf(ui: PrayUi) = when {
    ui.state == LiveVoiceState.ENDED || ui.state == LiveVoiceState.FAILED -> Mood.ENDED
    ui.state == LiveVoiceState.CONNECTING -> Mood.CONNECTING
    ui.singing && ui.state == LiveVoiceState.SPEAKING -> Mood.SINGING
    ui.state == LiveVoiceState.SPEAKING -> Mood.SPEAKING
    ui.state == LiveVoiceState.PAUSED -> Mood.PAUSED
    else -> Mood.LISTENING
}

private fun palette(m: Mood): List<Color> = when (m) {
    Mood.CONNECTING -> listOf(Indigo, Violet, Color(0xFF1E1B4B))
    Mood.LISTENING -> listOf(Cyan, Indigo, Color(0xFF0EA5E9))
    Mood.SPEAKING -> listOf(Gold, Amber, Rose)
    Mood.SINGING -> listOf(Violet, Gold, Rose)
    Mood.PAUSED -> listOf(Color(0xFF64748B), Color(0xFF334155), Indigo)
    Mood.ENDED -> listOf(Gold, Violet, Color(0xFF1E1B4B))
}

/**
 * The live conversation (PLAN_V2 F7): a room that breathes with whoever is talking — an aurora,
 * a living orb ringed by the voice, captions like a conversation, quick things to ask, singing,
 * and a composer for when speaking isn't possible.
 */
@Composable
internal fun PraySessionContent(viewModel: PrayWithMeViewModel, ui: PrayUi, onClose: () -> Unit, onOpenNote: (String) -> Unit) {
    val ai by viewModel.level.collectAsState()
    val you by viewModel.userLevel.collectAsState()
    val mood = moodOf(ui)
    val ended = mood == Mood.ENDED
    var captions by rememberSaveable { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ended) { while (!ended) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val elapsed = if (ui.startedAt > 0) ((now - ui.startedAt) / 1000).coerceAtLeast(0) else 0

    Box(Modifier.fillMaxSize().background(Ink).testTag("pray_session")) {
        Aurora(mood, ai, you)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Top: close, what we're doing and for how long, captions.
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (ended) Color.White.copy(alpha = 0.4f) else palette(mood).first()))
                    Spacer(Modifier.width(8.dp))
                    Text("${if (ui.mode == PrayMode.TALK_IT_THROUGH) "Talking it through" else ui.mode.label} · ${"%d:%02d".format(elapsed / 60, elapsed % 60)}",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { captions = !captions }, modifier = Modifier.testTag("pray_captions")) {
                    Icon(if (captions) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionDisabled, contentDescription = if (captions) "Hide words" else "Show words", tint = Color.White.copy(alpha = 0.8f))
                }
            }

            // The orb, smaller once there's conversation to read.
            val compact = captions && ui.lines.size > 1
            val orbSize by animateFloatAsState(if (compact) 150f else 230f, spring(dampingRatio = 0.8f, stiffness = 120f), label = "orb")
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                LivingOrb(mood, ai, you, Modifier.size(orbSize.dp))
                if (mood == Mood.SINGING) Notes(Modifier.size((orbSize + 60).dp))
            }
            AnimatedContent(targetState = statusLine(mood, ui), transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "status",
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { line ->
                Text(line, color = Color.White, fontSize = 21.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("pray_status"))
            }
            if (ui.state == LiveVoiceState.CONNECTING && ui.stage.isNotBlank()) Text(ui.stage, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("pray_stage"))
            ui.error?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFFFCA5A5), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp)) }

            // The conversation, fading out at the top like a page scrolling away.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (captions) Conversation(ui.lines, Modifier.fillMaxSize())
                else Text("Words are hidden. Just pray — the companion is listening.", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp, fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            }

            if (ended) EndedCard(ui, elapsed, onSave = { ui.savedNoteId?.let(onOpenNote) ?: viewModel.save() }, onAgain = { viewModel.reset() }, onClose = onClose)
            else {
                QuickAsks(ui, onSing = { viewModel.sing() }, onAsk = viewModel::ask)
                Composer(ui, you, onSend = viewModel::say, onMute = { viewModel.toggleMute() }, onAmen = { viewModel.end() })
            }
        }
    }
}

private fun statusLine(m: Mood, ui: PrayUi): String = when (m) {
    Mood.CONNECTING -> "Getting ready…"
    Mood.LISTENING -> if (ui.lines.isEmpty()) "Say hello whenever you're ready" else "I'm listening"
    Mood.SPEAKING -> if (ui.mode == PrayMode.TALK_IT_THROUGH) "Speaking…" else "Praying…"
    Mood.SINGING -> "Singing…"
    Mood.PAUSED -> "Microphone off"
    Mood.ENDED -> if (ui.state == LiveVoiceState.FAILED) "The connection stopped" else "Amen"
}

/** Slow light moving behind everything, warmer when the companion speaks, cooler when it listens. */
@Composable
private fun Aurora(mood: Mood, ai: Float, you: Float) {
    val colors = palette(mood)
    val c0 by animateColorAsState(colors[0], tween(1200), label = "a0")
    val c1 by animateColorAsState(colors[1], tween(1200), label = "a1")
    val c2 by animateColorAsState(colors[2], tween(1200), label = "a2")
    val t = rememberInfiniteTransition(label = "aurora")
    val p by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "p")
    val energy by animateFloatAsState(maxOf(ai, you), tween(260), label = "e")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        fun blob(c: Color, cx: Float, cy: Float, r: Float, a: Float) =
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        blob(c0, w * (0.5f + 0.28f * cos(p)), h * (0.22f + 0.06f * sin(p * 2)), w * (0.85f + energy * 0.25f), 0.30f + energy * 0.25f)
        blob(c1, w * (0.25f + 0.2f * sin(p + 1.3f)), h * (0.62f + 0.1f * cos(p)), w * 0.9f, 0.20f)
        blob(c2, w * (0.8f + 0.15f * cos(p * 1.5f + 2f)), h * (0.9f + 0.05f * sin(p)), w * 0.8f, 0.18f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.55f), Ink.copy(alpha = 0.9f)), startY = h * 0.35f))
    }
}

/**
 * The companion: a glowing core that turns slowly, ringed by 72 bars — the voice made visible.
 * Gold when it speaks, cyan when you do; it breathes when it's quiet.
 */
@Composable
private fun LivingOrb(mood: Mood, ai: Float, you: Float, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(3_200), RepeatMode.Reverse), label = "breathe")
    val speaking = mood == Mood.SPEAKING || mood == Mood.SINGING
    val level by animateFloatAsState(if (speaking) ai else if (mood == Mood.LISTENING) you else 0f, tween(120), label = "lvl")
    val colors = palette(mood)
    val ringColor by animateColorAsState(if (speaking) colors[0] else if (mood == Mood.LISTENING) Cyan else Color.White.copy(alpha = 0.4f), tween(500), label = "ring")
    val coreA by animateColorAsState(colors[0], tween(900), label = "ca")
    val coreB by animateColorAsState(colors[1], tween(900), label = "cb")

    Canvas(modifier.testTag("pray_orb")) {
        val c = center
        val r = size.minDimension * 0.29f * (if (mood == Mood.ENDED) 1f else breathe + level * 0.10f)
        // Halo.
        drawCircle(Brush.radialGradient(listOf(coreA.copy(alpha = 0.45f + level * 0.3f), Color.Transparent), center = c, radius = r * 2.1f), radius = r * 2.1f, center = c)
        // The ring of the voice.
        val bars = 72
        for (i in 0 until bars) {
            val a = (i.toFloat() / bars) * 2 * PI.toFloat()
            val wobble = 0.5f + 0.5f * sin(a * 3 + phase) * cos(a * 5 - phase * 1.3f)
            val len = r * (0.06f + (0.08f + level * 0.55f) * wobble)
            val start = r * 1.18f
            drawLine(
                ringColor.copy(alpha = 0.35f + 0.6f * wobble * (0.3f + level)),
                Offset(c.x + cos(a) * start, c.y + sin(a) * start),
                Offset(c.x + cos(a) * (start + len), c.y + sin(a) * (start + len)),
                strokeWidth = size.minDimension * 0.009f, cap = StrokeCap.Round
            )
        }
        // The core: a slow-turning gradient with a soft highlight.
        rotate(spin, c) {
            drawCircle(Brush.sweepGradient(listOf(coreA, coreB, colors[2], coreA), center = c), radius = r, center = c)
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = Offset(c.x - r * 0.35f, c.y - r * 0.4f), radius = r * 0.9f), radius = r, center = c)
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.35f)), center = c, radius = r), radius = r, center = c)
    }
}

/** Notes drifting up while singing. */
@Composable
private fun Notes(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "notes")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3_000, easing = LinearEasing)), label = "y")
    Box(modifier) {
        listOf(0f, 0.33f, 0.66f).forEachIndexed { i, off ->
            val k = (y + off) % 1f
            Text(if (i % 2 == 0) "♪" else "♫", color = Gold.copy(alpha = (1f - k) * 0.9f), fontSize = (16 + i * 4).sp,
                modifier = Modifier.align(if (i == 1) Alignment.TopStart else Alignment.TopEnd).graphicsLayer {
                    translationY = size.height * (0.7f - k * 0.7f); translationX = (if (i == 1) 1 else -1) * 30f * sin(k * 6f)
                }.padding(horizontal = (18 + i * 10).dp))
        }
    }
}

@Composable
private fun Conversation(lines: List<PrayLine>, modifier: Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(lines.size, lines.lastOrNull()?.text?.length) { if (lines.isNotEmpty()) state.animateScrollToItem(lines.size - 1) }
    LazyColumn(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade the top edge.
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = size.height * 0.22f), blendMode = BlendMode.DstIn)
            }
            .testTag("pray_lines"),
        state = state, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 40.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(lines) { i, l ->
            val latest = i >= lines.size - 2
            if (l.mine) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    l.text, color = Color.White.copy(alpha = if (latest) 0.95f else 0.6f), fontSize = 15.sp, lineHeight = 21.sp,
                    modifier = Modifier.padding(start = 48.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                        .background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            } else Text(
                l.text, color = Color.White.copy(alpha = if (latest) 1f else 0.5f), fontSize = if (latest) 21.sp else 18.sp, lineHeight = if (latest) 30.sp else 26.sp,
                fontFamily = FontFamily.Serif, modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
            )
        }
    }
}

/** One-tap things to ask for, so nobody has to find the words. */
@Composable
private fun QuickAsks(ui: PrayUi, onSing: () -> Unit, onAsk: (String) -> Unit) {
    val asks = buildList<Pair<String, () -> Unit>> {
        add("♪  Sing with me" to onSing)
        if (ui.mode == PrayMode.TALK_IT_THROUGH) {
            add("Ask me a question" to { onAsk("Please ask me one good question about today's reading.") })
            add("Pray about this" to { onAsk("Let's pray about what we've talked about. Please lead a short prayer.") })
        } else {
            add("Pray for me now" to { onAsk("Please pray for me now, about what I've shared.") })
            add("A verse for this" to { onAsk("Please share one Bible verse that speaks to this, with its reference, and say briefly why.") })
        }
        add("Quiet moment" to { onAsk("Let's have a moment of quiet. Please say one short, calm sentence and then stay silent until I speak.") })
        add("Slower, please" to { onAsk("Please speak more slowly and leave more space for me.") })
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp).testTag("pray_quick")) {
        items(asks.size) { i ->
            val (label, action) = asks[i]
            Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .clickable(enabled = ui.state != LiveVoiceState.CONNECTING, onClick = action).padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

/**
 * Mic, a message box that grows with what's typed, and Amen — which becomes Send while typing.
 * The mic ring shows your own voice arriving, so you know you're heard.
 */
@Composable
private fun Composer(ui: PrayUi, you: Float, onSend: (String) -> Unit, onMute: () -> Unit, onAmen: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val ring by animateFloatAsState(if (ui.muted) 0f else you, tween(90), label = "ring")
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size((46 + ring * 18).dp).clip(CircleShape).background(Cyan.copy(alpha = if (ui.muted) 0f else 0.10f + ring * 0.35f)))
            Surface(onClick = onMute, shape = CircleShape, color = if (ui.muted) Color(0xFFEF4444).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f), modifier = Modifier.size(46.dp).testTag("pray_mute")) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (ui.muted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = if (ui.muted) "Unmute" else "Mute", tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = draft, onValueChange = { draft = it.take(1000) },
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(Gold), maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) { onSend(draft); draft = "" } }),
            modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("pray_input"),
            decorationBox = { inner ->
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draft.isEmpty()) Text("Type a prayer…", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp, maxLines = 1)
                    inner()
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        AnimatedContent(targetState = draft.isNotBlank(), transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "action") { typing ->
            if (typing) Surface(onClick = { onSend(draft); draft = "" }, shape = CircleShape, color = Gold, modifier = Modifier.size(46.dp).testTag("pray_send")) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Ink, modifier = Modifier.size(20.dp)) }
            } else Surface(onClick = onAmen, shape = RoundedCornerShape(50), color = Color.Transparent, modifier = Modifier.heightIn(min = 46.dp).testTag("pray_amen")) {
                Box(Modifier.background(Brush.horizontalGradient(listOf(Gold, Amber))).padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Amen", color = Ink, fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EndedCard(ui: PrayUi, elapsed: Long, onSave: () -> Unit, onAgain: () -> Unit, onClose: () -> Unit) {
    AnimatedVisibility(visible = true, enter = fadeIn(tween(500)) + slideInVertically { it / 3 }) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Brush.linearGradient(listOf(Gold.copy(alpha = 0.6f), Violet.copy(alpha = 0.4f))), RoundedCornerShape(28.dp)).padding(20.dp)
                .testTag("pray_ended"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (ui.state == LiveVoiceState.FAILED) "We got cut off" else "Go in peace", color = Color.White, fontSize = 22.sp, fontFamily = FontFamily.Serif)
            Text("${elapsed / 60} min ${elapsed % 60} s together" + if (ui.lines.isNotEmpty()) " · ${ui.lines.size} ${if (ui.lines.size == 1) "moment" else "moments"}" else "", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.lines.isNotEmpty()) Surface(onClick = onSave, shape = RoundedCornerShape(50), color = Gold) {
                    Text(if (ui.savedNoteId != null) "Open saved prayer" else "Keep as a note", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp))
                }
                Surface(onClick = onAgain, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.12f)) {
                    Text(if (ui.state == LiveVoiceState.FAILED) "Try again" else "Pray again", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp))
                }
            }
            Text("Close", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp))
        }
    }
}
