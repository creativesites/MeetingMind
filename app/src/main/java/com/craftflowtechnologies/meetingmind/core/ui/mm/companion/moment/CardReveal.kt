package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.CompanionPoses
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Pose
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.drawCompanion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionDrawKit
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.FormDrawer
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.lerpInto
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette

/**
 * The Create card reveal (§5.4): drawn as an overlay at the companion's placement rect on the live preview
 * when "Include {companion}" turns on (or the format changes while it is on). 1.5 s: a dashed ring marks
 * the spot, the companion steps on, plays its mode's Signature once, and **settles into exactly the export
 * pose** (the `renderCompanionBitmap` static pose), so what you see is what you share.
 *
 * ```
 * if (design.includeCompanion) CardReveal(
 *     form, mode = design.companionPose ?: suggested, size = placement.width.toDp(),
 *     playKey = "${card.id}/${design.format}", prayerRequest = source.isPrayerRequest,
 *     announcement = "$companionName added. $modeName pose.", onSettled = { showCaptionOnce() },
 *     modifier = Modifier.offset { placement.topLeft }
 * )
 * ```
 *
 * - A new [playKey] plays the full reveal once (remembered across recreation); a [mode] change alone gets
 *   the 250 ms crossfade plus that mode's Signature (≤ 600 ms), with no ring.
 * - [prayerRequest]: opacity only (no rise, no scale, no Signature), then still (§5.6).
 * - Reduced motion: the export pose with a 150 ms fade.
 * - Any tap on the editor should end it: the host can simply recompose with the same key; the reveal never
 *   blocks input (it has no pointer handling and no semantics besides the announcement).
 */
@Composable
fun CardReveal(
    form: CompanionForm,
    mode: CreateMode,
    size: Dp,
    playKey: Any?,
    modifier: Modifier = Modifier,
    prayerRequest: Boolean = false,
    announcement: String? = null,
    palette: CompanionPalette = companionPalette(MM.colors),
    onSettled: () -> Unit = {}
) {
    val moment = remember(form) {
        ResolvedMoment(MomentSpecs.CardReveal, form, MomentSpace.EVERYDAY, quiet = false, playedMs = MomentSpecs.CardReveal.baseMs)
    }
    val reduced = when (LocalCompanionReducedMotion.current) { true -> true; false -> false; null -> MM.motion.reduced }
    val settled by rememberUpdatedState(onSettled)
    val clock = rememberMomentClock(key = "card/$playKey/$form", playedMs = moment.playedMs, durationScale = MM.motion.durationScale, onEnd = { settled() })
    val exportPose = remember(form, mode) { CompanionPoses.pose(form, mode, 0f, StaticLevel, reduced = true) }
    // A mode change: crossfade from the previous static pose plus the new mode's Signature.
    val modeAnim = remember { Animatable(1f) }
    val previous = remember { Pose().apply { set(exportPose) } }
    val firstMode = remember { booleanArrayOf(true) }
    LaunchedEffect(mode) {
        if (firstMode[0]) { firstMode[0] = false; return@LaunchedEffect }
        if (reduced || prayerRequest) { modeAnim.snapTo(1f); return@LaunchedEffect }
        modeAnim.snapTo(0f)
        modeAnim.animateTo(1f, tween(ModeChangeMs, easing = LinearEasing))
    }
    val fade = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(reduced, playKey) { if (reduced) fade.animateTo(1f, tween(150)) }

    val kit = remember { CompanionDrawKit() }
    val frame = remember { CompanionFrame() }
    val anim = remember { Pose() }
    val out = remember { Pose() }
    val ringColor = MM.colors.inkFaint
    val ring = remember { Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))) }
    val drawer = FormDrawer.of(form)

    Box(modifier.size(size).clearAndSetSemantics { }) {
        Canvas(Modifier.size(size)) {
            val t = moment.baseMsAt(clock.timeMs)
            val static = reduced || prayerRequest
            // The ring marks the spot.
            val ra = if (static) 0f else moment.stage(StageTarget.RING, StageProp.ALPHA, t, 0f)
            if (ra > 0f) drawCircle(ringColor, this.size.minDimension / 2f, alpha = ra, style = ring)
            // The pose: export pose → the mode's Signature (700–1300) → back to the export pose (1300–1500).
            var isExport = static || t <= 700f || t >= 1500f
            val pose = when {
                isExport -> out.set(exportPose)
                t < 1300f -> CompanionPoses.poseInto(out, form, mode, (t - 700f) / 1000f, StaticLevel, reduced = false)
                else -> {
                    CompanionPoses.poseInto(anim, form, mode, 0.6f, StaticLevel, reduced = false)
                    lerpInto(out, anim, exportPose, MomentEasing.Std.transform((t - 1300f) / 200f))
                }
            }
            // After a mode change: blend from the old pose, then the Signature, then settle.
            val mt = modeAnim.value
            if (!static && mt < 1f && clock.atEnd) {
                isExport = false
                val ms = mt * ModeChangeMs
                when {
                    ms < 250f -> lerpInto(out, previous, exportPose, ms / 250f)
                    ms < 250f + 500f -> CompanionPoses.poseInto(out, form, mode, (ms - 250f) / 1000f, StaticLevel, reduced = false)
                    else -> {
                        CompanionPoses.poseInto(anim, form, mode, 0.5f, StaticLevel, reduced = false)
                        lerpInto(out, anim, exportPose, (ms - 750f) / (ModeChangeMs - 750f))
                    }
                }
            } else if (mt >= 1f) previous.set(exportPose)
            val alpha = if (reduced) fade.value else moment.stage(StageTarget.COMPANION, StageProp.ALPHA, t, 1f)
            val dy = if (static) 0f else moment.stage(StageTarget.COMPANION, StageProp.Y_DP, t, 0f) * this.size.height
            val sc = if (static) 1f else moment.stage(StageTarget.COMPANION, StageProp.SCALE, t, 1f)
            frame.pose = pose
            frame.visual = mode
            frame.t = if (isExport) 0f else ((t - 700f) / 1000f).coerceAtLeast(0f)
            frame.level = 0f
            frame.reduced = isExport
            frame.lod = true
            if (alpha > 0f) {
                val pivot = Offset(this.size.width / 2f, this.size.height)
                drawContext.transform.translate(0f, dy)
                drawContext.transform.scale(sc, sc, pivot)
                if (alpha < 1f) {
                    drawIntoLayer(alpha) { drawCompanion(kit, frame, drawer, palette) }
                } else drawCompanion(kit, frame, drawer, palette)
                drawContext.transform.scale(1f / sc, 1f / sc, pivot)
                drawContext.transform.translate(0f, -dy)
            }
        }
        if (announcement != null) PoliteAnnouncement(announcement, "card/$playKey/$mode")
    }
}

/** The mode-change crossfade (250 ms) plus the Signature (≤ 600 ms) and settle. */
private const val ModeChangeMs = 850

/** The level the static export pose uses (matches `renderCompanionBitmap`). */
private const val StaticLevel = 0.55f

/** Draws [block] at [alpha] through an offscreen layer. */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoLayer(alpha: Float, block: () -> Unit) {
    val paint = LayerPaint.get()
    paint.alpha = alpha
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint)
    block()
    drawContext.canvas.restore()
}

private object LayerPaint {
    private val paint = androidx.compose.ui.graphics.Paint()
    fun get() = paint
}
