package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.personal.RoutineTimeWindow
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.CompanionTilt
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.CompanionTiltEffect
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.today.home.rememberHomeReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = 6.2831855f

/**
 * Multi-plane 3D parallax Sanctuary diorama for Personal Home.
 *
 * Uses [CompanionTilt] (30Hz ref-counted gyroscope) to shift background sky, arched sanctuary window,
 * floating light dust motes, and the companion across depth planes without recomposing on every frame.
 * All colours derive strictly from [MM.colors] so light and dark themes stay harmonious and guard-clean.
 */
@Composable
fun SanctuaryDioramaCard(
    window: RoutineTimeWindow,
    greeting: String,
    subtitle: String,
    intentionText: String,
    onTapIntention: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = rememberHomeReducedMotion()
    CompanionTiltEffect(active = !reduced)
    val c = MM.colors
    val infinite = rememberInfiniteTransition(label = "sanctuary_diorama")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "sanctuary_drift"
    )
    val time = if (reduced) 0.25f else drift

    val skyTop = when (window) {
        RoutineTimeWindow.MORNING -> c.goldWash
        RoutineTimeWindow.AFTERNOON -> c.accentWash
        else -> c.surfaceSunk
    }
    val skyBottom = c.surface
    val orbColor = when (window) {
        RoutineTimeWindow.MORNING -> c.gold
        RoutineTimeWindow.AFTERNOON -> c.speaker3
        else -> c.accent
    }
    val companionState = when (window) {
        RoutineTimeWindow.EVENING -> CompanionState.COZY
        else -> CompanionState.IDLE
    }

    Surface(
        shape = MM.radius.card,
        color = c.surface,
        border = if (c.isDark) null else BorderStroke(1.dp, c.line),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Multi-plane parallax backdrop drawn in draw phase only.
            Canvas(Modifier.matchParentSize()) {
                val tx = if (reduced) 0f else CompanionTilt.x
                val ty = if (reduced) 0f else CompanionTilt.y
                drawSanctuaryBackdrop(
                    t = time,
                    tiltX = tx,
                    tiltY = ty,
                    skyTop = skyTop,
                    skyBottom = skyBottom,
                    orbColor = orbColor,
                    hillColor = c.speaker3,
                    windowStroke = c.line
                )
            }

            Column(Modifier.fillMaxWidth().padding(MM.space.l)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            window.label.uppercase(),
                            style = MM.type.overline,
                            color = orbColor
                        )
                        Spacer(Modifier.height(MM.space.xs))
                        Text(greeting, style = MM.type.title, color = c.ink)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(subtitle, style = MM.type.secondary, color = c.inkSecondary)
                    }
                    Spacer(Modifier.width(MM.space.m))
                    ZuriSlot(
                        page = CompanionPage.HOME,
                        state = companionState,
                        size = 76.dp
                    )
                }

                Spacer(Modifier.height(MM.space.m))

                Surface(
                    onClick = onTapIntention,
                    shape = MM.radius.small,
                    color = c.surfaceRaised.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, c.lineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s)) {
                        Text("TODAY'S INTENTION", style = MM.type.overline, color = c.inkMuted)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(
                            intentionText.ifBlank { "Tap to set a calm intention for today…" },
                            style = if (intentionText.isBlank()) MM.type.secondary else MM.type.bodyStrong,
                            color = if (intentionText.isBlank()) c.inkMuted else c.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSanctuaryBackdrop(
    t: Float,
    tiltX: Float,
    tiltY: Float,
    skyTop: Color,
    skyBottom: Color,
    orbColor: Color,
    hillColor: Color,
    windowStroke: Color
) {
    val w = size.width
    val h = size.height
    val a = t * TWO_PI
    drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)))

    // Far depth plane: celestial sun / lantern glow drifting with tilt
    val sunCenter = Offset(
        x = w * 0.78f + tiltX * 16f,
        y = h * 0.28f + tiltY * 10f + sin(a) * 5f
    )
    drawCircle(
        Brush.radialGradient(
            listOf(orbColor.copy(alpha = 0.28f), orbColor.copy(alpha = 0f)),
            center = sunCenter,
            radius = w * 0.44f
        ),
        radius = w * 0.44f,
        center = sunCenter
    )

    // Mid plane: arched window silhouette with gentle parallax
    val left = w * 0.62f + tiltX * 9f
    val right = w * 0.94f + tiltX * 9f
    val top = h * 0.10f + tiltY * 6f
    val radius = ((right - left) / 2f).coerceAtLeast(1f)
    val arch = Path().apply {
        moveTo(left, h)
        lineTo(left, top + radius)
        arcTo(Rect(left, top, right, top + 2f * radius), 180f, 180f, false)
        lineTo(right, h)
        close()
    }
    drawPath(arch, orbColor.copy(alpha = 0.06f))
    drawPath(arch, windowStroke.copy(alpha = 0.5f), style = Stroke(width = 1.2.dp.toPx()))

    // Near plane: soft rolling horizon + floating dust motes
    val horizon = Path().apply {
        moveTo(0f, h)
        val steps = 20
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val y = h * 0.88f - 10f * sin(f * TWO_PI * 0.8f + a * 0.5f) + tiltY * 4f
            lineTo(w * f, y)
        }
        lineTo(w, h)
        close()
    }
    drawPath(horizon, hillColor.copy(alpha = 0.09f))

    // Foreground motes
    for (i in 0..5) {
        val px = w * (0.14f + i * 0.15f) + cos(a + i * 1.3f) * 8f - tiltX * 14f
        val py = h * (0.22f + (i % 3) * 0.22f) + sin(a + i * 1.9f) * 6f - tiltY * 10f
        drawCircle(orbColor.copy(alpha = 0.24f), radius = (2.2f + (i % 2)).dp.toPx(), center = Offset(px, py))
    }
}

/**
 * 3D Perspective Bound Journal Volume cover that tilts subtly with the device gyroscope.
 */
@Composable
fun JournalVolume3DCard(
    title: String,
    subtitle: String,
    entryCount: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = rememberHomeReducedMotion()
    val density = LocalDensity.current.density
    val c = MM.colors
    val rotY = if (reduced) 0f else CompanionTilt.x * 8f
    val rotX = if (reduced) 0f else -CompanionTilt.y * 6f

    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = c.surfaceRaised,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
            .width(156.dp)
            .height(196.dp)
            .graphicsLayer {
                rotationY = rotY
                rotationX = rotX
                cameraDistance = 12f * density
            }
    ) {
        Box(Modifier.fillMaxSize()) {
            // Book spine band
            Box(
                Modifier
                    .width(12.dp)
                    .height(196.dp)
                    .background(accent.copy(alpha = 0.28f))
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = MM.space.l, end = MM.space.m, top = MM.space.m, bottom = MM.space.m)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$entryCount", style = MM.type.caption, color = accent)
                }
                Spacer(Modifier.weight(1f))
                Text(title, style = MM.type.heading, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(MM.space.xs))
                Text(subtitle, style = MM.type.caption, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 3D Perspective Time Capsule card with gyroscope tilt and locked/unlocked seal presentation.
 */
@Composable
fun TimeCapsule3DCard(
    title: String,
    sealLabel: String,
    isUnlocked: Boolean,
    preview: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = rememberHomeReducedMotion()
    val density = LocalDensity.current.density
    val c = MM.colors
    val tint = if (isUnlocked) c.gold else c.accent
    val rotY = if (reduced) 0f else CompanionTilt.x * 6f

    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = c.surfaceRaised,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotY
                cameraDistance = 14f * density
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(MM.space.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    contentDescription = if (isUnlocked) "Unlocked time capsule" else "Sealed time capsule",
                    tint = tint
                )
            }
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isUnlocked) "READY TO UNSEAL" else "SEALED UNTIL ${sealLabel.uppercase()}",
                    style = MM.type.overline,
                    color = tint
                )
                Spacer(Modifier.height(MM.space.xs))
                Text(title, style = MM.type.heading, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (isUnlocked) preview.ifBlank { "Tap to unseal your past words" } else "Kept safe in your vault until $sealLabel",
                    style = MM.type.secondary,
                    color = c.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Interactive 3D Breathing Pacer sphere synced with the user's companion for Routine & Mindfulness steps.
 * Cycles calmly through Inhale (4s) -> Hold (4s) -> Exhale (6s).
 */
@Composable
fun Breathing3DPacer(
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val reduced = rememberHomeReducedMotion()
    val c = MM.colors
    val infinite = rememberInfiniteTransition(label = "breathing_pacer")
    val cycle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 14f, // 4s inhale + 4s hold + 6s exhale
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "breath_cycle"
    )
    val t = if (!isRunning || reduced) 4f else cycle
    val phaseIndex = when {
        t < 4f -> 0
        t < 8f -> 1
        else -> 2
    }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(phaseIndex, isRunning, reduced) {
        if (isRunning && !reduced) {
            val type = if (phaseIndex == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            runCatching { haptic.performHapticFeedback(type) }
        }
    }
    val (phaseLabel, scale) = when (phaseIndex) {
        0 -> "Breathe in…" to (0.68f + 0.32f * (t / 4f))
        1 -> "Hold softly…" to 1.0f
        else -> "Exhale slowly…" to (1.0f - 0.32f * ((t - 8f) / 6f))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(210.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = (size.minDimension / 2f) * scale
                drawCircle(
                    Brush.radialGradient(
                        listOf(c.accent.copy(alpha = 0.24f), c.accent.copy(alpha = 0.02f)),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
                drawCircle(
                    color = c.accent.copy(alpha = 0.36f),
                    radius = r * 0.78f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            ZuriSlot(
                page = CompanionPage.HOME,
                state = CompanionState.BREATHING,
                level = { ((scale - 0.68f) / 0.32f).coerceIn(0f, 1f) },
                size = (84 * (0.88f + 0.16f * scale)).dp
            )
        }
        Spacer(Modifier.height(MM.space.s))
        Text(phaseLabel, style = MM.type.heading, color = c.ink)
        Text("4s in · 4s hold · 6s out", style = MM.type.caption, color = c.inkMuted)
    }
}

/**
 * Evolving 3D Parallax Journey Landscape & Garden diorama for active Guided Journeys.
 *
 * Uses [CompanionTilt] gyroscope depth layers: sky gradient, distant ridge, botanical blooms that
 * appear as milestones are completed, stepping-stone trail, and the user's companion striding at
 * the active stepping stone. Non-competitive, calm progression.
 */
@Composable
fun JourneyPathVisualizer(
    completedSteps: Int,
    totalSteps: Int,
    landscapeTheme: String = "QUIET_GARDEN",
    modifier: Modifier = Modifier
) {
    val reduced = rememberHomeReducedMotion()
    CompanionTiltEffect(active = !reduced)
    val c = MM.colors
    val steps = totalSteps.coerceIn(1, 14)
    val done = completedSteps.coerceIn(0, steps)
    val primaryHue = when (landscapeTheme) {
        "DAWN_TRAIL" -> c.gold
        "COASTAL_LIGHT" -> c.accent
        "STARLIT_PEAK" -> c.speaker2
        else -> c.speaker3
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(MM.radius.small)
            .background(c.surfaceSunk)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tx = if (reduced) 0f else CompanionTilt.x
            val ty = if (reduced) 0f else CompanionTilt.y

            // Sky wash
            drawRect(Brush.verticalGradient(listOf(primaryHue.copy(alpha = 0.14f), c.surfaceSunk)))

            // Far horizon ridge (slow parallax)
            val farRidge = Path().apply {
                moveTo(0f, h)
                for (i in 0..16) {
                    val f = i / 16f
                    val y = h * 0.56f - 14f * sin(f * TWO_PI * 0.65f + 0.4f) + ty * 3f
                    lineTo(w * f + tx * 4f, y)
                }
                lineTo(w, h)
                close()
            }
            drawPath(farRidge, primaryHue.copy(alpha = 0.10f))

            // Near garden hill (medium parallax)
            val nearHill = Path().apply {
                moveTo(0f, h)
                for (i in 0..18) {
                    val f = i / 18f
                    val y = h * 0.72f - 10f * sin(f * TWO_PI * 1.1f + 1.2f) + ty * 5f
                    lineTo(w * f + tx * 8f, y)
                }
                lineTo(w, h)
                close()
            }
            drawPath(nearHill, primaryHue.copy(alpha = 0.16f))

            // Stepping-stone trail
            val padX = 24.dp.toPx()
            val points = (0 until steps).map { i ->
                val f = if (steps == 1) 0.5f else i / (steps - 1f)
                val x = padX + f * (w - padX * 2f) + tx * 6f
                val y = h * 0.64f + sin(f * TWO_PI * 1.15f) * (h * 0.18f) + ty * 4f
                Offset(x, y)
            }
            val trailPath = Path()
            points.forEachIndexed { idx, pt ->
                if (idx == 0) trailPath.moveTo(pt.x, pt.y) else trailPath.lineTo(pt.x, pt.y)
            }
            drawPath(trailPath, c.line, style = Stroke(width = 3.dp.toPx()))
            if (done > 1) {
                val litPath = Path()
                points.take(done).forEachIndexed { idx, pt ->
                    if (idx == 0) litPath.moveTo(pt.x, pt.y) else litPath.lineTo(pt.x, pt.y)
                }
                drawPath(litPath, primaryHue, style = Stroke(width = 3.2.dp.toPx()))
            }

            // Nodes + evolving botanical blooms on completed milestones
            points.forEachIndexed { idx, pt ->
                val isReached = idx < done
                val isNext = idx == done
                if (isReached) {
                    // Small bloom beside each completed stone
                    val bloomCenter = Offset(pt.x, pt.y - 12.dp.toPx())
                    drawCircle(primaryHue.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = bloomCenter)
                    drawCircle(c.gold.copy(alpha = 0.85f), radius = 2.5.dp.toPx(), center = bloomCenter)
                }
                val nodeColor = when {
                    isReached -> primaryHue
                    isNext -> c.accent
                    else -> c.track
                }
                val r = if (isNext) 6.5.dp.toPx() else 4.dp.toPx()
                drawCircle(nodeColor, radius = r, center = pt)
                if (isNext) {
                    drawCircle(c.accent.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = pt)
                }
            }
        }

        // Companion striding at the top-end corner of the landscape
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(MM.space.s)
        ) {
            ZuriSlot(
                page = CompanionPage.HOME,
                state = if (done >= steps) CompanionState.PROUD else CompanionState.STRIDING,
                size = 52.dp
            )
        }
    }
}

/**
 * Interactive 3D "Letting Go" / Vent & Release Embers experience.
 *
 * When a user wants to vent a heavy thought and release it without keeping it in their journal,
 * tapping Release lifts the thought into warm glowing embers/lanterns that drift upward and shift
 * with the phone's gyroscope tilt, while the companion holds a calm Cozy/Nostalgic presence.
 */
@Composable
fun LettingGoEmbersStage(
    isReleasing: Boolean,
    modifier: Modifier = Modifier
) {
    val reduced = rememberHomeReducedMotion()
    CompanionTiltEffect(active = !reduced && isReleasing)
    val c = MM.colors
    val infinite = rememberInfiniteTransition(label = "letting_go_embers")
    val rise by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Restart),
        label = "ember_rise"
    )
    val phase = if (reduced) 0.4f else rise

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(MM.radius.card)
            .background(c.surfaceSunk),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tx = if (reduced) 0f else CompanionTilt.x
            drawRect(Brush.verticalGradient(listOf(c.surfaceSunk, c.goldWash)))
            if (isReleasing) {
                for (i in 0..13) {
                    val p = (phase + i * 0.083f) % 1f
                    val x = w * (0.12f + ((i * 37) % 76) / 100f) + sin(p * TWO_PI + i) * 14f + tx * 18f
                    val y = h * (1.05f - p * 1.15f)
                    val alpha = (1f - p).coerceIn(0f, 1f) * 0.78f
                    val radius = (3f + (i % 3) * 1.6f).dp.toPx() * (0.6f + 0.4f * (1f - p))
                    val emberColor = if (i % 2 == 0) c.gold else c.accent
                    drawCircle(emberColor.copy(alpha = alpha * 0.35f), radius = radius * 2.2f, center = Offset(x, y))
                    drawCircle(emberColor.copy(alpha = alpha), radius = radius, center = Offset(x, y))
                }
            }
        }
        ZuriSlot(
            page = CompanionPage.HOME,
            state = if (isReleasing) CompanionState.NOSTALGIC else CompanionState.COZY,
            size = 76.dp
        )
    }
}

/**
 * 3D Perspective Rotatable Challenge / Journey Completion Crest for the Memory Vault.
 * Tilts dynamically with [CompanionTilt] and displays a subtle specular rim highlight.
 */
@Composable
fun Keepsake3DCrestCard(
    title: String,
    kindLabel: String,
    stepsKept: Int,
    onOpenAnthology: (() -> Unit)?,
    onRestartRefresher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = rememberHomeReducedMotion()
    val density = LocalDensity.current.density
    val c = MM.colors
    val rotY = if (reduced) 0f else CompanionTilt.x * 9f
    val rotX = if (reduced) 0f else -CompanionTilt.y * 6f

    Surface(
        shape = MM.radius.card,
        color = c.surfaceRaised,
        border = BorderStroke(1.dp, c.gold.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotY
                rotationX = rotX
                cameraDistance = 14f * density
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(MM.space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZuriSlot(
                    page = CompanionPage.HOME,
                    state = CompanionState.PROUD,
                    size = 56.dp
                )
                Spacer(Modifier.width(MM.space.m))
                Column(Modifier.weight(1f)) {
                    Text("COMPLETED $kindLabel", style = MM.type.overline, color = c.gold)
                    Spacer(Modifier.height(MM.space.xs))
                    Text(title, style = MM.type.heading, color = c.ink)
                    Text("$stepsKept steps kept in your own words", style = MM.type.caption, color = c.inkSecondary)
                }
            }
            Spacer(Modifier.height(MM.space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                if (onOpenAnthology != null) {
                    com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton(
                        text = "Read Anthology",
                        onClick = onOpenAnthology
                    )
                }
                com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction(
                    text = "7-day refresher",
                    onClick = onRestartRefresher
                )
            }
        }
    }
}
