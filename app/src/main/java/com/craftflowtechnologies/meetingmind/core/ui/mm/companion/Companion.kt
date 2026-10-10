package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.craftflowtechnologies.meetingmind.core.companion.CompanionFlags
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionTier
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionDrawKit
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.FormDrawer
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionAssets
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionRenderer
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionRendererSelector
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.LocalCompanionRiveBudget
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.RendererChoice
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.RendererInputs
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.RiveCompanion
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Overrides the system animator scale for companions below: true = static poses, false = animate. */
val LocalCompanionReducedMotion = staticCompositionLocalOf<Boolean?> { null }

/** True forces the Canvas renderer below (screenshot tests, card export previews). */
val LocalCompanionForceCanvas = staticCompositionLocalOf { false }

/** The tier for a drawn size (§4.2). */
fun CompanionTier.Companion.forSize(size: Dp): CompanionTier = forSizeDp(size.value)

/** Below this the companion drops to LOD-0: silhouette and eyes (§2.4). */
val CompanionLodCutoff = 32.dp

/**
 * Draws one companion form in one state (§10.2). Stateless; animation-only state lives inside.
 *
 * Picks the renderer per [CompanionRendererSelector]: Rive when the form's `.riv` is bundled and
 * the slot is ≥ 48 dp, animations are on and the screen's live budget allows it; Canvas otherwise.
 *
 * @param state the app state or Create mode to draw; [mode], when set, overrides it.
 * @param level mic or TTS amplitude 0..1, read in the draw phase only.
 * @param contentDescription null = decorative (cleared semantics), the default.
 * @param palette defaults to the theme accent.
 * @param onRenderer reports the renderer chosen (Companion Lab).
 */
@Composable
fun Companion(
    form: CompanionForm,
    state: CompanionVisual,
    size: Dp,
    modifier: Modifier = Modifier,
    level: () -> Float = { 0f },
    mode: CreateMode? = null,
    variant: CompanionVariant = CompanionVariant.Normal,
    tier: CompanionTier = CompanionTier.forSize(size),
    contentDescription: String? = null,
    palette: CompanionPalette = companionPalette(MM.colors),
    onRenderer: ((RendererChoice) -> Unit)? = null
) {
    val visual: CompanionVisual = mode ?: state
    val context = LocalContext.current
    val reducedOverride = LocalCompanionReducedMotion.current
    val durationScale = when (reducedOverride) { true -> 0f; false -> 1f; null -> MM.motion.durationScale }
    val forceCanvas = LocalCompanionForceCanvas.current
    val bundled = remember(form) { CompanionAssets.isBundled(context, form) }
    var riveFailed by remember(form) { mutableStateOf(false) }

    val base = RendererInputs(
        sizeDp = size.value, durationScale = durationScale, assetBundled = bundled, hasBudgetSlot = false,
        riveEnabled = CompanionFlags.rive, forceCanvas = forceCanvas, riveFailed = riveFailed
    )
    val wantsRive = CompanionRendererSelector.wantsRive(base)
    val budget = LocalCompanionRiveBudget.current
    var hasSlot by remember { mutableStateOf(false) }
    DisposableEffect(wantsRive, budget) {
        val got = wantsRive && budget.tryAcquire()
        hasSlot = got
        onDispose { if (got) budget.release() }
    }
    val choice = CompanionRendererSelector.select(base.copy(hasBudgetSlot = hasSlot))
    if (onRenderer != null) SideEffect { onRenderer(choice) }

    val bytes by produceState<ByteArray?>(null, form, choice.renderer) {
        value = if (choice.renderer == CompanionRenderer.RIVE) withContext(Dispatchers.IO) { CompanionAssets.read(context, form) } else null
    }

    val onScreen = rememberOnScreenTracker()
    val semantics = if (contentDescription == null) Modifier.clearAndSetSemantics { }
    else Modifier.semantics { this.contentDescription = contentDescription }

    Box(modifier.size(size).then(onScreen.modifier).then(semantics)) {
        val riveBytes = bytes
        if (choice.renderer == CompanionRenderer.RIVE && riveBytes != null) {
            RiveCompanion(
                form = form, bytes = riveBytes, visual = visual, variant = variant, palette = palette, level = level,
                active = onScreen.isOnScreen && lifecycleAtLeast(Lifecycle.State.RESUMED),
                onFailure = { riveFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CanvasCompanion(
                form = form, visual = visual, variant = variant, palette = palette, size = size, tier = tier,
                level = level, durationScale = durationScale,
                visible = onScreen.isOnScreen && lifecycleAtLeast(Lifecycle.State.STARTED)
            )
        }
    }
}

/**
 * The Canvas renderer: the Compose port of the visual reference. Used under 48 dp, with reduced
 * motion, in screenshot tests, for card export and until the `.riv` files land.
 */
@Composable
internal fun CanvasCompanion(
    form: CompanionForm,
    visual: CompanionVisual,
    variant: CompanionVariant,
    palette: CompanionPalette,
    size: Dp,
    tier: CompanionTier,
    level: () -> Float,
    durationScale: Float,
    visible: Boolean,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val spec = ZuriMotion.spec(visual, variant)
    var finished by remember(visual, variant) { mutableStateOf(false) }
    val running = CompanionClockGate.shouldRun(spec, tier, durationScale, visible, finished)
    val time = rememberCompanionTime(
        running = running, restartKey = visual to variant, fps = spec.fps, durationScale = durationScale,
        runMs = spec.runMs, onFinished = { finished = true }
    )
    // A capped loop that played out (Sleepy's three z cycles) settles to its static pose; a
    // one-shot holds its last frame.
    val static = durationScale <= 0f || tier == CompanionTier.T0 || (finished && spec.timing is Timing.Loop)
    val kit = remember { CompanionDrawKit() }
    val frame = remember { CompanionFrame() }
    val smoother = remember { LevelSmoother() }
    val holder = remember { PoseMemory() }
    val transition = remember { Animatable(1f) }
    LaunchedEffect(visual, variant) {
        holder.from = holder.last
        if (durationScale <= 0f || holder.from == null) { transition.snapTo(1f); return@LaunchedEffect }
        transition.snapTo(0f)
        val ms = if (tier == CompanionTier.T0) 150 else spec.enterMs
        transition.animateTo(1f, tween((ms * durationScale).toInt(), easing = LinearEasing))
    }
    val drawer = FormDrawer.of(form)
    val lod = size >= CompanionLodCutoff
    Canvas(modifier) {
        val t = time.value
        val dt = ((t - holder.lastT) * 1000f).coerceIn(0f, 100f)
        holder.lastT = t
        val l = if (static) StaticLevel else smoother.update(level(), if (dt == 0f) 16f else dt)
        val target = CompanionPoses.pose(form, visual, t, l, static, variant)
        val from = holder.from
        val progress = transition.value
        val pose = if (from != null && progress < 1f) lerp(from, target, progress) else target
        holder.last = pose
        frame.pose = pose
        frame.visual = visual
        frame.t = t
        frame.level = l
        frame.reduced = static
        frame.lod = lod
        drawCompanion(kit, frame, drawer, palette)
    }
}

/** The level the static Listening pose shows (§3.1). */
private const val StaticLevel = 0.55f

private class PoseMemory {
    var last: Pose? = null
    var from: Pose? = null
    var lastT: Float = 0f
}

/** Draws [frame] scaled from the 100-unit box to this scope's size. */
internal fun DrawScope.drawCompanion(kit: CompanionDrawKit, frame: CompanionFrame, drawer: FormDrawer, palette: CompanionPalette) {
    kit.ensure(palette)
    scale(size.minDimension / 100f, pivot = Offset.Zero) {
        with(drawer) { draw(kit, frame, palette) }
    }
}

/**
 * Renders a companion's static pose into a bitmap, for Create card export (Z-22). Always the
 * Canvas renderer, so the export never depends on a `.riv` file or on animation time.
 */
fun renderCompanionBitmap(
    form: CompanionForm,
    visual: CompanionVisual,
    palette: CompanionPalette,
    sizePx: Int,
    variant: CompanionVariant = CompanionVariant.Normal
): ImageBitmap {
    val bitmap = ImageBitmap(sizePx, sizePx)
    val frame = CompanionFrame().apply {
        pose = CompanionPoses.pose(form, visual, 0f, StaticLevel, reduced = true, variant = variant)
        this.visual = visual
        reduced = true
        lod = true
        level = if (visual == CompanionState.LISTENING) StaticLevel else 0f
    }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GraphicsCanvas(bitmap), Size(sizePx.toFloat(), sizePx.toFloat())) {
        drawCompanion(CompanionDrawKit(), frame, FormDrawer.of(form), palette)
    }
    return bitmap
}
