package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.OpenSession
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette
import kotlinx.coroutines.delay

/*
 * The moment stage (docs/mvp/ZURI_SIGNATURE_MOMENTS.md §7.4): one host per moment. It owns the clock
 * (frames only while playing), skip (a non-consuming tap, Back), haptics, the chime, the one polite
 * announcement and the reduced-motion end state. Hosts tag their own layouts with
 * `Modifier.momentLayer(target)`; the stage draws the character and the effect layers around them.
 */

/**
 * What a moment's content can use. Every read happens in the draw phase: nothing recomposes per frame.
 * For a plain open (no moment) every call is a no-op, so the same content code serves both.
 */
@Stable
class MomentScope internal constructor(
    /** The moment, or null for a plain open. */
    val moment: ResolvedMoment?,
    /** Its clock, or null for a plain open. */
    val clock: MomentClock?,
    internal val stage: StageGeometry,
    internal val rtl: Boolean
) {
    /** Base-timeline milliseconds now (the spec's own times). The end state for a plain open. */
    fun baseMs(): Float {
        val m = moment ?: return 0f
        val c = clock ?: return m.spec.baseMs.toFloat()
        return m.baseMsAt(c.timeMs)
    }

    /** 0..1 through the moment, for custom drawing. 1 for a plain open. */
    val progress: () -> Float = {
        val m = moment
        val c = clock
        if (m == null || c == null) 1f else c.timeMs / m.playedMs
    }

    /** True while the moment plays (before its end or skip). */
    val playing: Boolean get() = clock?.playing == true

    /** A stage value now, or [default] (the end-state value) without a moment or without that track. */
    fun value(target: StageTarget, prop: StageProp, default: Float): Float {
        val m = moment ?: return default
        return m.stage(target, prop, baseMs(), default)
    }

    /**
     * Drives this layout from the spec's [target] tracks with a `graphicsLayer` (alpha, offset, scale,
     * rotation; blur on API 31+ for Dawn), read in the draw phase.
     *
     * - [StageTarget.HEADER]: tag the note header block (title, meta row, chips). The stage also measures
     *   it to place the character and the effects (curtain, glow, ripple, sprouts) around it.
     * - [StageTarget.BODY]: tag each body section with its [index] (summary 0, key points 1, actions 2…):
     *   alpha 0 → 1 and y +8 → 0 dp, 70 ms apart, as the variant says.
     * - Any other target (CARD, HEADLINE, SUBLINE, ACTIONS, WORDS…) maps alpha, X/Y, scale and rotation.
     *
     * The layout stays laid out, focusable and clickable from frame 0 (alpha never disables it).
     */
    fun Modifier.momentLayer(target: StageTarget, index: Int = 0): Modifier {
        val m = moment ?: return this
        val base = if (target == StageTarget.HEADER) this.onGloballyPositioned { stage.header(it) } else this
        return base.graphicsLayer {
            val t = baseMs()
            if (target == StageTarget.BODY) {
                val p = m.stage(StageTarget.BODY, StageProp.PROGRESS, t, 1f)
                val e = (p * 1.6f - index * 0.3f).coerceIn(0f, 1f)
                alpha = e
                translationY = 8.dp.toPx() * (1f - MomentEasing.Out.transform(e))
                return@graphicsLayer
            }
            alpha = m.stage(target, StageProp.ALPHA, t, 1f).coerceIn(0f, 1f)
            val dir = if (rtl) -1f else 1f
            translationX = m.stage(target, StageProp.X_DP, t, 0f).dp.toPx() * dir
            translationY = m.stage(target, StageProp.Y_DP, t, 0f).dp.toPx()
            val s = m.stage(target, StageProp.SCALE, t, 1f)
            scaleX = s
            scaleY = s * m.stage(target, StageProp.SCALE_Y, t, 1f)
            rotationZ = m.stage(target, StageProp.ROTATION, t, 0f) * dir
            if (target == StageTarget.HEADER) transformOrigin = TransformOrigin(0.5f, 1f)
            if (Build.VERSION.SDK_INT >= 31 && m.has(target, StageProp.BLUR_DP)) {
                val b = m.stage(target, StageProp.BLUR_DP, t, 0f)
                // Quantised to 0.5 dp so a new effect is made only a few times in the 600 ms rise.
                val q = (b * 2f).toInt() / 2f
                renderEffect = if (q > 0f) BlurEffect(q.dp.toPx(), q.dp.toPx()) else null
            }
        }
    }

    /** The character with this moment's pose overrides, at [size]. Decorative (no semantics). */
    @Composable
    fun MomentCompanion(form: CompanionForm, size: Dp, modifier: Modifier = Modifier, palette: CompanionPalette = companionPalette(MM.colors)) {
        val m = moment ?: return
        MomentCharacter(m, clock, form, palette, size, modifier)
    }
}

/** The stage's measured geometry (the header block), read in the draw phase. */
@Stable
internal class StageGeometry {
    var root: LayoutCoordinates? = null
    var headerRect by mutableStateOf(Rect.Zero)
        private set

    fun header(coords: LayoutCoordinates) {
        val r = root ?: return
        if (!r.isAttached || !coords.isAttached) return
        val rect = r.localBoundingBoxOf(coords, clipBounds = false)
        if (rect != headerRect) headerRect = rect
    }
}

/**
 * Hosts one moment around a note screen's header and body (an Unveil or the nod), or any moment in the
 * Lab. With a [MomentPlan.Plain] plan or no [form] (for an Unveil the character is just left out), the
 * content shows as it is (with the announcement when the plan has one).
 *
 * ```
 * MomentStage(plan, form, space = ..., announcement = "$title is ready.", key = "unveil:$noteId") {
 *     Header(Modifier.momentLayer(StageTarget.HEADER))
 *     Summary(Modifier.momentLayer(StageTarget.BODY, 0)); KeyPoints(Modifier.momentLayer(StageTarget.BODY, 1))
 * }
 * ```
 *
 * @param key stable across recreation (e.g. `"unveil:$noteId"`): a played moment never replays under it.
 * @param titleWords the title's word count (Inkline keeps its Signature in time with the words).
 * @param haptics false turns them off (they are already off in Faith and after a skip, and only fire when
 *   the system's touch feedback is on).
 */
@Composable
fun MomentStage(
    plan: MomentPlan?,
    form: CompanionForm?,
    announcement: String?,
    modifier: Modifier = Modifier,
    key: String = "moment",
    titleWords: Int = 5,
    haptics: Boolean = true,
    palette: CompanionPalette = companionPalette(MM.colors),
    onEnd: () -> Unit = {},
    content: @Composable MomentScope.() -> Unit
) {
    val companionForm = form
    val effective = when {
        // Without a companion the Unveil steps down to the header fade (§3.3 presence filter, decision 4).
        companionForm == null && plan is MomentPlan.Unveil -> MomentPlan.Nod(companion = false, quiet = plan.quiet, space = plan.space)
        companionForm == null && plan is MomentPlan.Nod -> plan.copy(companion = false)
        else -> plan
    }
    val moment = remember(effective, companionForm) { effective?.let { ResolvedMoment.forPlan(it, companionForm ?: CompanionForm.ZURI) } }
    val showCharacter = companionForm != null && when (effective) {
        is MomentPlan.Nod -> effective.companion
        is MomentPlan.FirstKeep -> effective.companion
        is MomentPlan.Unveil -> true
        else -> false
    }
    MomentStage(
        moment = moment, form = companionForm, showCharacter = showCharacter,
        announcement = announcement?.takeIf { effective?.announce == true }, modifier = modifier, key = "$key/${moment?.describe()}",
        titleWords = titleWords, haptics = haptics, palette = palette, onEnd = onEnd, content = content
    )
}

/** The lower-level stage over a [ResolvedMoment] (Companion Lab, `FirstKeepOverlay`). */
@Composable
fun MomentStage(
    moment: ResolvedMoment?,
    form: CompanionForm?,
    showCharacter: Boolean,
    announcement: String?,
    modifier: Modifier = Modifier,
    key: String = "moment/${moment?.describe()}",
    titleWords: Int = 5,
    haptics: Boolean = true,
    sounds: Boolean = false,
    palette: CompanionPalette = companionPalette(MM.colors),
    onEnd: () -> Unit = {},
    content: @Composable MomentScope.() -> Unit
) {
    val clock = if (moment != null) rememberMomentEngine(moment, key, haptics, sounds, onEnd) else null
    val geometry = remember { StageGeometry() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = remember(moment, clock, geometry, rtl) { MomentScope(moment, clock, geometry, rtl) }
    val skipModifier = if (clock != null) Modifier.skipOnTap(clock) else Modifier
    if (clock != null) BackHandler(enabled = clock.playing && !clock.skipped) { clock.skip() }

    Box(modifier.onGloballyPositioned { geometry.root = it }.then(skipModifier)) {
        if (moment != null && moment.spec.kind != MomentKind.FIRST_KEEP) BehindHeaderLayers(scope, palette)
        scope.content()
        if (moment != null && moment.spec.kind != MomentKind.FIRST_KEEP) {
            FrontHeaderLayers(scope, palette)
            if (showCharacter && form != null) HeaderCompanion(scope, form, palette, titleWords)
        }
        if (announcement != null) PoliteAnnouncement(announcement, key)
    }
}

/**
 * The clock plus haptics and the chime. Haptics: `CLOCK_TICK` for a tick, `CONFIRM` (API 30+, else
 * `VIRTUAL_KEY`) for a confirm; through `View.performHapticFeedback`, so they follow the system's touch
 * feedback setting; never in Faith, never after a skip.
 */
@Composable
internal fun rememberMomentEngine(moment: ResolvedMoment, key: String, haptics: Boolean, sounds: Boolean, onEnd: () -> Unit): MomentClock {
    val view = LocalView.current
    val context = LocalContext.current
    val durationScale = when (LocalCompanionReducedMotion.current) { true -> 0f; false -> 1f; null -> MM.motion.durationScale }
    val chimeAt = FirstKeepChime.AtBaseMs * moment.timeScale
    return rememberMomentClock(
        key = key, playedMs = moment.playedMs, durationScale = durationScale,
        onFrame = { prev, now ->
            if (haptics && !moment.quiet) for (i in moment.hapticMs.indices) {
                val h = moment.hapticMs[i]
                if (prev < h && now >= h) performHaptic(view, moment.spec.haptics[i].kind)
            }
            if (moment.chime && prev < chimeAt && now >= chimeAt) FirstKeepChime.play(context, planChime = true, quiet = moment.quiet, sounds = sounds)
        },
        onEnd = onEnd
    )
}

internal fun performHaptic(view: View, kind: HapticKind) {
    val constant = when (kind) {
        HapticKind.TICK -> HapticFeedbackConstants.CLOCK_TICK
        HapticKind.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(constant)
}

/**
 * Any press skips to the end (150 ms rush). Observed in the Initial pass and never consumed, so the tap
 * still reaches what it landed on (§2.6).
 */
internal fun Modifier.skipOnTap(clock: MomentClock): Modifier = pointerInput(clock) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Press && clock.playing) clock.skip()
        }
    }
}

/**
 * The one polite announcement (§7.8): a live region on real text, made once per moment and cleared after
 * a few seconds, so TalkBack reads it once and it never lingers in the tree.
 */
@Composable
internal fun BoxScope.PoliteAnnouncement(text: String, key: String) {
    var shown by rememberSaveable(key, text) { mutableStateOf(true) }
    LaunchedEffect(key, text) {
        delay(AnnouncementMs)
        shown = false
    }
    if (shown) Box(
        Modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        }
    )
}

/** How long the announcement node stays in the tree. */
internal const val AnnouncementMs = 4_000L

/** A short id for a resolved moment (keys and the Lab). */
fun ResolvedMoment.describe(): String = "${spec.kind}-${spec.variant ?: "-"}-$form-$space-$quiet-$playedMs"

/**
 * The [OpenSession] for this navigation entry, saved with it (configuration changes, process restore,
 * returns from sub-screens). Plan a moment only when `claim()` returns true.
 *
 * ```
 * val session = rememberOpenSession()
 * LaunchedEffect(noteId) { if (session.claim()) plan = store.planOpen(noteId, clock) { t, l, r -> facts(t, l, r) }.plan }
 * ```
 */
@Composable
fun rememberOpenSession(): OpenSession =
    rememberSaveable(saver = Saver<OpenSession, Boolean>({ it.claimed }, { OpenSession(claimed = it) })) { OpenSession() }
