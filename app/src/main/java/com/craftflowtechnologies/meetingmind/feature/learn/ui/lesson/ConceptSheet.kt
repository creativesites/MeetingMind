package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource
import com.craftflowtechnologies.meetingmind.core.aireport.ReportAiAction
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.ConceptFlags
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learn.ConceptCard
import com.craftflowtechnologies.meetingmind.feature.learn.DiagramSpec
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainMode
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * The concept sheet (design/study/ConceptSheet.dc.html): one idea, the lecturer's own words with a play button, ways
 * to explain it (the result is text, or a drawn diagram), and "Quiz me on this" / "Not right?".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConceptSheet(
    concept: ConceptCard,
    explain: ExplainState,
    onDismiss: () -> Unit,
    onPlayQuote: (Long) -> Unit,
    onExplain: (ExplainMode) -> Unit,
    onQuizMe: () -> Unit,
    onNotRight: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the learner has already changed about this concept (hidden, reported, edited). */
    flags: ConceptFlags = ConceptFlags(),
    onRename: (String) -> Unit = {},
    onToggleHidden: () -> Unit = {},
    onToggleReported: () -> Unit = {},
    /** The AI-written definition to report. Null once the learner has edited the concept. */
    aiSource: (() -> AiReportSource)? = null,
    /** Opens Plans from the allowance panel. Null when this build cannot sell plans. */
    onSeePlans: (() -> Unit)? = null,
    /** "Explain it aloud": opens the speaking round for this idea. Null hides the action. */
    onExplainAloud: (() -> Unit)? = null
) {
    StudyTheme {
        val c = MM.colors
        var notRightOpen by rememberSaveable(concept.id) { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surfaceRaised,
            modifier = modifier
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = MM.space.xl, end = MM.space.xl, bottom = MM.space.xl),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        Text(
                            concept.name,
                            style = MM.type.title,
                            color = c.ink,
                            modifier = Modifier.semantics { heading() }
                        )
                        concept.subtitle?.let { Text(it, style = MM.type.secondary, color = c.inkMuted) }
                    }
                    StarChip(concept.star)
                }
                Text(concept.definition, style = MM.type.body, color = c.ink)
                concept.lecturerQuote?.let { quote ->
                    QuotePanel(quote, concept.quoteStartMs, concept.quoteEndMs, onPlayQuote)
                }
                Text("Explain it another way", style = MM.type.bodyStrong, color = c.ink)
                ExplainModes(selected = explain.mode, onExplain = onExplain)
                ExplainResult(explain, onSeePlans)
                if (flags.hidden || flags.reported) {
                    Text(
                        listOfNotNull(
                            "Hidden from practice".takeIf { flags.hidden },
                            "Reported · questions held back".takeIf { flags.reported }
                        ).joinToString(" · "),
                        style = MM.type.caption,
                        color = c.inkSecondary
                    )
                }
                onExplainAloud?.let { explainAloud ->
                    SecondaryButton(
                        "Explain it aloud",
                        onClick = explainAloud,
                        modifier = Modifier.fillMaxWidth().testTag("concept_explain_aloud"),
                        leadingIcon = Icons.Rounded.Mic
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton("Quiz me on this", onClick = onQuizMe, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(MM.space.s))
                    TextAction(
                        "Not right?",
                        onClick = {
                            notRightOpen = !notRightOpen
                            if (notRightOpen) onNotRight()
                        },
                        modifier = Modifier.testTag("concept_not_right")
                    )
                }
                if (notRightOpen) {
                    NotRightPanel(
                        concept = concept,
                        flags = flags,
                        onRename = onRename,
                        onToggleHidden = onToggleHidden,
                        onToggleReported = onToggleReported,
                        aiSource = aiSource.takeIf { !flags.userEdited }
                    )
                }
            }
        }
    }
}

/**
 * The "Not right?" choices, the studio's concept menu moved onto the sheet: rename, hide from practice, report a
 * problem, and report the AI-written definition. Each one is a toggle or a quiet text action, so it can be undone.
 */
@Composable
private fun NotRightPanel(
    concept: ConceptCard,
    flags: ConceptFlags,
    onRename: (String) -> Unit,
    onToggleHidden: () -> Unit,
    onToggleReported: () -> Unit,
    aiSource: (() -> AiReportSource)?
) {
    val c = MM.colors
    var renaming by rememberSaveable(concept.id) { mutableStateOf(false) }
    var name by rememberSaveable(concept.id) { mutableStateOf(concept.name) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .background(c.surfaceSunk)
            .padding(MM.space.m)
            .testTag("concept_not_right_panel"),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        if (renaming) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name", style = MM.type.caption) },
                textStyle = MM.type.body.copy(color = c.ink),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.line, cursorColor = c.accent),
                modifier = Modifier.fillMaxWidth().testTag("concept_rename_field")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                TextAction("Save", enabled = name.isNotBlank(), onClick = {
                    renaming = false
                    onRename(name.trim())
                })
                TextAction("Cancel", onClick = { renaming = false; name = concept.name })
            }
        } else {
            TextAction("Rename", onClick = { renaming = true }, modifier = Modifier.testTag("concept_rename"))
        }
        TextAction(
            if (flags.hidden) "Show in practice again" else "Hide from practice",
            onClick = onToggleHidden,
            modifier = Modifier.testTag("concept_hide")
        )
        TextAction(
            if (flags.reported) "Undo report" else "Report a problem",
            onClick = onToggleReported,
            modifier = Modifier.testTag("concept_report")
        )
        if (aiSource != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Report this definition", style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.weight(1f))
                ReportAiAction(source = aiSource)
            }
        }
    }
}

/** Why the idea is in the lesson: its state, with an icon and a word (never colour alone). */
@Composable
private fun StarChip(state: StarState) {
    val c = MM.colors
    val icon = when (state) {
        StarState.SHINING -> Icons.Rounded.Circle
        StarState.GROWING -> Icons.Rounded.Adjust
        StarState.NEW -> Icons.Rounded.RadioButtonUnchecked
        // Worth a look: the gold four-point star drawn below, the same mark as on the sky.
        StarState.WORTH_A_LOOK -> null
    }
    Row(
        Modifier
            .clip(MM.radius.pill)
            .background(c.accentWash)
            .padding(horizontal = MM.space.m, vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(12.dp))
        else FourPointStar(c.gold, 12.dp)
        Text(state.label, style = MM.type.caption, color = c.accent)
    }
}

/** The lecturer's words in a gold panel, with a button that plays them from their moment. */
@Composable
private fun QuotePanel(quote: String, startMs: Long?, endMs: Long?, onPlay: (Long) -> Unit) {
    val c = MM.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .background(c.goldWash)
            .padding(MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        Text("In your lecturer's words", style = MM.type.caption, color = c.goldInk)
        Text("“$quote”", style = MM.type.body.copy(fontStyle = FontStyle.Italic), color = c.ink)
        if (startMs != null) {
            val seconds = if (endMs != null && endMs > startMs) (endMs - startMs + 500L) / 1000L else null
            val time = formatLectureTime(startMs)
            val label = if (seconds != null) "$time · $seconds s" else time
            Row(
                Modifier
                    .heightIn(min = MMSize.minTouch)
                    .clip(MM.radius.pill)
                    .background(c.goldInk)
                    .clickable(role = Role.Button, onClick = { onPlay(startMs) })
                    .semantics { contentDescription = "Play from $time" }
                    .padding(horizontal = MM.space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.onInk, modifier = Modifier.size(14.dp))
                Text(label, style = MM.type.caption.copy(fontFamily = FontFamily.Monospace), color = c.onInk)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExplainModes(selected: ExplainMode?, onExplain: (ExplainMode) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        ExplainMode.entries.forEach { mode ->
            MMChip(label = mode.label, selected = selected == mode, onClick = { onExplain(mode) })
        }
    }
}

@Composable
private fun ExplainResult(explain: ExplainState, onSeePlans: (() -> Unit)?) {
    val c = MM.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        if (explain.loading) {
            if (MM.motion.reduced) StaticSkeleton() else ShimmerSkeleton()
        } else {
            explain.allowance?.let { AllowanceUsedUpPanel(it.resetsAtMs, onSeePlans) }
            explain.error?.let { StatusLine(kind = StatusKind.Error, text = it) }
            explain.diagram?.let { DiagramView(it) }
            explain.text?.let { Text(it, style = MM.type.body, color = c.ink) }
            if (explain.madeOnPhone && (explain.text != null || explain.diagram != null)) {
                Text("Made on this phone", style = MM.type.caption, color = c.inkMuted)
            }
            if ((explain.text != null || explain.diagram != null) && explain.sourceLabel != null) {
                AiBadge(explain.sourceLabel)
            }
        }
    }
}

@Composable
private fun AiBadge(source: String) {
    val c = MM.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text(
            "AI",
            style = MM.type.caption,
            color = c.accent,
            modifier = Modifier.clip(MM.radius.small).background(c.accentWash).padding(horizontal = MM.space.s)
        )
        Text("From $source of your lecture", style = MM.type.caption, color = c.inkMuted)
    }
}

@Composable
private fun StaticSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        listOf(1f, 0.9f, 0.6f).forEach { fraction ->
            Box(Modifier.fillMaxWidth(fraction).height(14.dp).clip(MM.radius.small).background(MM.colors.track))
        }
    }
}

/** The loading skeleton sweeps while the explanation is made. It is only drawn while loading. */
@Composable
private fun ShimmerSkeleton() {
    val c = MM.colors
    val transition = rememberInfiniteTransition(label = "explainShimmer")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing), RepeatMode.Restart),
        label = "explainSweep"
    )
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        listOf(1f, 0.9f, 0.6f).forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(MM.radius.small)
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(c.track, c.lineSoft, c.track),
                                start = Offset(size.width * sweep, 0f),
                                end = Offset(size.width * (sweep + 1f), 0f)
                            )
                        )
                    }
            )
        }
    }
}

/**
 * A small diagram: rounded boxes joined by arrows with labels, drawn with Compose. Two or fewer rows of boxes lay
 * out top to bottom; more than four boxes go into two columns. It draws in once on show (instant when motion is off).
 */
@Composable
internal fun DiagramView(spec: DiagramSpec, modifier: Modifier = Modifier) {
    val c = MM.colors
    val ink = c.ink
    val accent = c.accent
    val wash = c.accentWash
    val muted = c.inkMuted
    val labelStyle: TextStyle = MM.type.secondary
    val measurer = rememberTextMeasurer()
    val drawProgress = remember(spec) { Animatable(0f) }
    val enter = MM.motion.enter<Float>()
    LaunchedEffect(spec) { drawProgress.animateTo(1f, enter) }

    val n = spec.nodes.size
    if (n == 0) return
    val twoColumns = n > 4
    val rows = if (twoColumns) (n + 1) / 2 else n
    val nodeHeight: Dp = 52.dp
    val rowGap: Dp = 40.dp
    val height: Dp = nodeHeight * rows + rowGap * (rows - 1)
    val description = buildString {
        append("Diagram. Boxes: ").append(spec.nodes.joinToString(", ")).append(".")
        spec.edges.filter { it.from in 0 until n && it.to in 0 until n }.forEach { e ->
            append(" ${spec.nodes[e.from]} to ${spec.nodes[e.to]}")
            e.label?.let { append(", $it") }
            append(".")
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
            val p = drawProgress.value
            val w = size.width
            val boxH = nodeHeight.toPx()
            val gap = rowGap.toPx()
            val boxW = if (twoColumns) w * 0.42f else min(w * 0.8f, 280.dp.toPx())
            val pad = 4.dp.toPx()
            val stroke = 1.5.dp.toPx()

            fun centerOf(i: Int): Offset =
                if (twoColumns) Offset(if (i % 2 == 0) w * 0.25f else w * 0.75f, boxH / 2 + (i / 2) * (boxH + gap))
                else Offset(w / 2, boxH / 2 + i * (boxH + gap))

            // Where a ray from a box's centre leaves its outline.
            fun edgePoint(center: Offset, ux: Float, uy: Float): Offset {
                val tx = if (abs(ux) < 1e-4f) Float.MAX_VALUE else (boxW / 2) / abs(ux)
                val ty = if (abs(uy) < 1e-4f) Float.MAX_VALUE else (boxH / 2) / abs(uy)
                val t = min(tx, ty)
                return Offset(center.x + ux * t, center.y + uy * t)
            }

            spec.edges.forEach { e ->
                if (e.from !in 0 until n || e.to !in 0 until n || e.from == e.to) return@forEach
                val a = centerOf(e.from)
                val b = centerOf(e.to)
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = hypot(dx, dy)
                if (len <= 0f) return@forEach
                val ux = dx / len
                val uy = dy / len
                val start = edgePoint(a, ux, uy).let { Offset(it.x + ux * pad, it.y + uy * pad) }
                val end = edgePoint(b, -ux, -uy).let { Offset(it.x - ux * pad, it.y - uy * pad) }
                val tip = Offset(start.x + (end.x - start.x) * p, start.y + (end.y - start.y) * p)
                drawLine(accent, start, tip, strokeWidth = stroke, cap = StrokeCap.Round)
                if (p >= 0.95f) {
                    val head = 9.dp.toPx()
                    val baseX = end.x - ux * head
                    val baseY = end.y - uy * head
                    val path = Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(baseX - uy * head * 0.6f, baseY + ux * head * 0.6f)
                        lineTo(baseX + uy * head * 0.6f, baseY - ux * head * 0.6f)
                        close()
                    }
                    drawPath(path, accent)
                }
                e.label?.let { label ->
                    val layout = measurer.measure(
                        text = label,
                        style = labelStyle.copy(color = muted),
                        constraints = Constraints(maxWidth = boxW.toInt().coerceAtLeast(1))
                    )
                    val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                    drawText(
                        layout,
                        topLeft = Offset(mid.x + 6.dp.toPx(), mid.y - layout.size.height / 2f),
                        alpha = p
                    )
                }
            }

            spec.nodes.forEachIndexed { i, label ->
                val center = centerOf(i)
                val topLeft = Offset(center.x - boxW / 2, center.y - boxH / 2)
                val corner = CornerRadius(8.dp.toPx())
                drawRoundRect(wash, topLeft, Size(boxW, boxH), corner, alpha = p)
                drawRoundRect(accent, topLeft, Size(boxW, boxH), corner, Stroke(width = stroke), alpha = p)
                val layout = measurer.measure(
                    text = label,
                    style = labelStyle.copy(color = ink),
                    constraints = Constraints(maxWidth = (boxW - 16.dp.toPx()).toInt().coerceAtLeast(1)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                drawText(
                    layout,
                    topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
                    alpha = p
                )
            }
        }
        spec.caption?.let { Text(it, style = MM.type.caption, color = c.inkMuted) }
    }
}
