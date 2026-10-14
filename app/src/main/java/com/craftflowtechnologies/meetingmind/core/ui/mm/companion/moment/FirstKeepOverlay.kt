package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Pose
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The note as the First Keep's card shows it: **verbatim** text only (§2.3 beat 4). The overlay never
 * writes or changes text; at most the first three key points are shown.
 */
data class FirstKeepNote(val title: String, val meta: String, val keyPoints: List<String>)

/**
 * The First Keep (§2): a full-screen overlay composed **over the already-rendered note screen**. The note is
 * underneath from frame 0; "Open note" just dismisses this overlay ([onOpen]) and plays nothing more.
 *
 * Every string comes from the caller (Z-35 `strings_moments.xml`): the headline ("Your first note is
 * ready." / with the user's name / the sermon line), the sub line ("Everything you said, kept." or the real
 * action-item count), the optional companion line ("I kept every word.", none in Faith), the action labels,
 * and the one polite [announcement] ("Your first note is ready. {title}.").
 *
 * ```
 * if (plan is MomentPlan.FirstKeep && showOverlay) FirstKeepOverlay(
 *     plan = plan, form = settings.form,
 *     note = FirstKeepNote(note.title, meta, note.keyPoints.take(3)),
 *     headline = stringResource(R.string.moment_first_keep_headline), subline = ..., openLabel = ..., shareLabel = ...,
 *     announcement = ..., sounds = settings.sounds, key = "firstkeep:${note.id}",
 *     onOpen = { showOverlay = false }, onShare = { openCreate(note.title) }
 * )
 * ```
 *
 * Behaviour: any tap skips to the end (150 ms) and still reaches what it landed on, so a tap on Open note
 * opens the note; Back calls [onBack] (default [onOpen]); the actions are focusable and clickable from frame 0;
 * at most two haptics (none in Faith); the chime at 1.5 s only on the once-ever plays with [sounds] on, the
 * ringer normal and Do Not Disturb off; with reduced motion the end layout shows at once with the static
 * Celebrating pose (Peaceful and glad when quiet); with [form] null (no companion, presence off) the
 * character is left out and the bloom, card, words and actions still play. Leaving the app mid-moment shows
 * the end state on return; it never replays under the same [key].
 *
 * Focus order: headline → sub line → card → Open note → Share a moment.
 */
@Composable
fun FirstKeepOverlay(
    plan: MomentPlan.FirstKeep,
    form: CompanionForm?,
    note: FirstKeepNote,
    headline: String,
    subline: String,
    openLabel: String,
    shareLabel: String,
    announcement: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    companionLine: String? = null,
    caption: String? = null,
    sounds: Boolean = true,
    key: String = "firstkeep",
    onBack: () -> Unit = onOpen,
    onEnd: () -> Unit = {}
) {
    val moment = remember(plan, form) { ResolvedMoment.firstKeep(form ?: CompanionForm.ZURI, plan.space, plan.quiet, plan.chime) }
    val showCharacter = form != null && plan.companion
    val reduced = when (LocalCompanionReducedMotion.current) { true -> true; false -> false; null -> MM.motion.reduced }
    val clock = rememberMomentEngine(moment, "$key/${moment.describe()}", haptics = true, sounds = sounds, onEnd = onEnd)
    val geometry = remember { StageGeometry() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = remember(moment, clock, geometry, rtl) { MomentScope(moment, clock, geometry, rtl) }
    BackHandler { if (clock.playing) clock.skip(); onBack() }

    val c = MM.colors
    val palette = companionPalette(c).copy(halo = Color.Transparent)
    val staticPose = remember(form, plan.quiet, reduced) {
        if (reduced && form != null) MomentPose.firstKeepStaticInto(Pose(), form, plan.quiet) else null
    }
    val geo = remember { FkGeometry() }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier.fillMaxSize().background(c.background).skipOnTap(clock).semantics { isTraversalGroup = true }
    ) {
        val screenH = maxHeight
        FirstKeepBackLayers(scope, moment, geo, plan.quiet)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Layout(
                modifier = Modifier.fillMaxWidth().heightIn(min = screenH),
                content = {
                    // 0: the card.
                    with(scope) {
                        FirstKeepCard(
                            note, scope,
                            Modifier.momentLayer(StageTarget.CARD).graphicsLayer { transformOrigin = TransformOrigin(0.5f, 0.6f) }
                                .semantics { traversalIndex = 2f }
                        )
                    }
                    // 1: the character.
                    if (showCharacter) MomentCharacter(
                        moment, clock, form!!, palette, MomentSpecs.FirstKeepCompanionDp.dp, staticPose = staticPose,
                        modifier = with(scope) { Modifier.momentLayer(StageTarget.COMPANION) }
                    ) else Spacer(Modifier.size(0.dp))
                    // 2: Page's mini.
                    if (showCharacter && form == CompanionForm.PAGE) MomentCharacter(
                        moment, clock, CompanionForm.PAGE, palette, MomentSpecs.FirstKeepCompanionDp.dp, staticPose = staticPose, mini = true,
                        modifier = with(scope) { Modifier.momentLayer(StageTarget.MINI_COMPANION) }
                    ) else Spacer(Modifier.size(0.dp))
                    // 3: the words.
                    Column(
                        with(scope) { Modifier.momentLayer(StageTarget.WORDS) }.padding(horizontal = MM.space.xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            headline, style = MM.type.title, color = c.ink, textAlign = TextAlign.Center,
                            modifier = with(scope) { Modifier.momentLayer(StageTarget.HEADLINE) }.semantics { traversalIndex = 0f }
                        )
                        Spacer(Modifier.size(MM.space.xs))
                        Text(
                            subline, style = MM.type.body, color = c.inkSecondary, textAlign = TextAlign.Center,
                            modifier = with(scope) { Modifier.momentLayer(StageTarget.SUBLINE) }.semantics { traversalIndex = 1f }
                        )
                        if (companionLine != null && !plan.quiet && showCharacter) {
                            Spacer(Modifier.size(MM.space.xs))
                            Text(
                                companionLine, style = MM.type.caption, color = c.inkMuted, textAlign = TextAlign.Center,
                                modifier = with(scope) { Modifier.momentLayer(StageTarget.SUBLINE) }.semantics { traversalIndex = 1.5f }
                            )
                        }
                    }
                    // 4: the actions, clickable from frame 0.
                    Column(
                        with(scope) { Modifier.momentLayer(StageTarget.WORDS).momentLayer(StageTarget.ACTIONS) }.padding(horizontal = MM.space.xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PrimaryButton(openLabel, onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics { traversalIndex = 3f })
                        TextAction(shareLabel, onClick = onShare, modifier = Modifier.fillMaxWidth().semantics { traversalIndex = 4f })
                    }
                }
            ) { measurables, constraints ->
                with(density) {
                    val w = constraints.maxWidth
                    val h = constraints.minHeight
                    val cardW = minOf(w - 64.dp.roundToPx(), 360.dp.roundToPx()).coerceAtLeast(0)
                    val card = measurables[0].measure(Constraints.fixedWidth(cardW))
                    val char = measurables[1].measure(Constraints())
                    val mini = measurables[2].measure(Constraints())
                    val loose = Constraints(maxWidth = w)
                    val words = measurables[3].measure(loose.copy(minWidth = w))
                    val actions = measurables[4].measure(Constraints(minWidth = minOf(w, 360.dp.roundToPx()), maxWidth = minOf(w, 360.dp.roundToPx())))
                    // The prototype's 300 × 640 frame: card top 92, Zuri centre 300 (+60 at the end, ×0.72), words 428, actions 520.
                    val design = 540.dp.toPx()
                    val cardTop = maxOf(48.dp.toPx(), (h - design) / 2f)
                    var charCy = cardTop + 208.dp.toPx()
                    val charEndTop = charCy + 60.dp.toPx() - 0.72f * 80.dp.toPx()
                    val push = (cardTop + card.height + 12.dp.toPx() - charEndTop).coerceAtLeast(0f)
                    charCy += push
                    val wordsTop = maxOf(charCy + 128.dp.toPx(), cardTop + card.height + 16.dp.toPx())
                    val actionsTop = maxOf(wordsTop + 92.dp.toPx(), wordsTop + words.height + 16.dp.toPx())
                    val total = maxOf(h.toFloat(), actionsTop + actions.height + 24.dp.toPx()).toInt()
                    geo.cx = w / 2f; geo.charCy = charCy; geo.cardTop = cardTop
                    geo.cardLeft = (w - cardW) / 2f; geo.cardRight = geo.cardLeft + cardW; geo.cardBottom = cardTop + card.height
                    layout(w, total) {
                        card.place(((w - cardW) / 2), cardTop.toInt())
                        char.place((w - char.width) / 2, (charCy - char.height / 2f).toInt())
                        mini.place((w - mini.width) / 2 + 108.dp.roundToPx(), (charCy - mini.height / 2f - 30.dp.toPx()).toInt())
                        words.place(0, wordsTop.toInt())
                        actions.place((w - actions.width) / 2, actionsTop.toInt())
                    }
                }
            }
        }
        FirstKeepFrontLayers(scope, moment, geo, palette.gold, palette.sparkle, reduced)
        PoliteAnnouncement(announcement, key)
    }
}

/** Positions the layout computed, read by the draw-phase layers. */
private class FkGeometry {
    var cx = 0f
    var charCy = 0f
    var cardTop = 0f
    var cardLeft = 0f
    var cardRight = 0f
    var cardBottom = 0f
}

/** The card: meta, the title writing in, and the key points resolving from skeleton bars. */
@Composable
private fun FirstKeepCard(note: FirstKeepNote, scope: MomentScope, modifier: Modifier) {
    val c = MM.colors
    MMCard(modifier) {
        Text(note.meta, style = MM.type.caption, color = c.inkMuted)
        Spacer(Modifier.size(MM.space.xs))
        scope.MomentTitle(note.title, MM.type.heading, color = c.ink)
        Spacer(Modifier.size(MM.space.s))
        val skeleton = listOf(150, 120, 96)
        note.keyPoints.take(3).forEachIndexed { i, point ->
            Row(Modifier.padding(bottom = MM.space.xs), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = MM.space.s - 2.dp).size(5.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(MM.space.s))
                Box {
                    Box(
                        Modifier.padding(top = 5.dp).size(skeleton[i].dp, 7.dp).clip(CircleShape).background(c.track)
                            .graphicsLayer { alpha = 1f - scope.value(StageTarget.KEY_POINTS, StageProp.PROGRESS, 1f) }
                    )
                    Text(
                        point, style = MM.type.secondary, color = c.inkSecondary,
                        modifier = Modifier.graphicsLayer { alpha = scope.value(StageTarget.KEY_POINTS, StageProp.PROGRESS, 1f) }
                    )
                }
            }
        }
    }
}

/** Behind everything: the bloom, the closing ring and the halo pulse. */
@Composable
private fun FirstKeepBackLayers(scope: MomentScope, m: ResolvedMoment, geo: FkGeometry, quiet: Boolean) {
    val c = MM.colors
    val bloomColor = if (quiet) lerp(c.accent, c.gold, 0.55f).copy(alpha = if (c.isDark) 0.42f else 0.34f)
    else c.accent.copy(alpha = if (c.isDark) 0.34f else 0.2f)
    val bloom = remember(bloomColor) { Brush.radialGradient(listOf(bloomColor, bloomColor.copy(alpha = 0f)), center = Offset.Zero, radius = 1f) }
    val strokes = remember { StrokeCache() }
    val accent = c.accent
    val track = c.track
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val t = scope.baseMs()
        val cy = geo.charCy
        val cx = geo.cx
        val ba = m.stage(StageTarget.BLOOM, StageProp.ALPHA, t, 0.3f)
        if (ba > 0f) translate(cx, cy) {
            val r = 210.dp.toPx() * m.stage(StageTarget.BLOOM, StageProp.SCALE, t, 1f)
            scale(r, r, pivot = Offset.Zero) { drawCircle(bloom, 1f, Offset.Zero, alpha = ba) }
        }
        val ra = m.stage(StageTarget.RING, StageProp.ALPHA, t, 0f)
        if (ra > 0f) {
            val r = 68.dp.toPx()
            val s = strokes.get(4.dp.toPx(), round = true)
            drawCircle(track, r, Offset(cx, cy), alpha = ra, style = s)
            drawArc(accent, -90f, 360f * m.stage(StageTarget.RING, StageProp.PROGRESS, t, 1f), false, Offset(cx - r, cy - r), Size(r * 2, r * 2), alpha = ra, style = s)
        }
        val ha = m.stage(StageTarget.HALO, StageProp.ALPHA, t, 0f)
        if (ha > 0f) drawCircle(
            accent, 85.dp.toPx() * m.stage(StageTarget.HALO, StageProp.SCALE, t, 1f), Offset(cx, cy), alpha = ha,
            style = strokes.get(2.dp.toPx(), round = false)
        )
    }
}

/** In front: the sparkle burst and the quiet version's glint at the card's corner. */
@Composable
private fun FirstKeepFrontLayers(scope: MomentScope, m: ResolvedMoment, geo: FkGeometry, gold: Color, spark: Color, reduced: Boolean) {
    val sparkle = remember { SparklePath() }
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val t = scope.baseMs()
        val sk = m.stage(StageTarget.SPARKLES, StageProp.PROGRESS, t, 0f)
        val n = m.sparkles
        if (!reduced && n > 0 && sk > 0f && sk < 1f) {
            val ccy = geo.charCy + m.stage(StageTarget.COMPANION, StageProp.Y_DP, t, 0f).dp.toPx()
            val sc = sin(PI.toFloat() * (sk * 1.15f).coerceIn(0f, 1f))
            for (i in 0 until n) {
                val a = -PI.toFloat() / 2f + i * (2f * PI.toFloat() / n) + 0.35f
                val r = (30f + 90f * sk).dp.toPx()
                val x = geo.cx + cos(a) * r
                val y = ccy + sin(a) * r * 0.92f
                val color = if (i % 2 == 1) gold else spark
                with(sparkle) {
                    if (m.sparkleBars) bars(x, y, 1.dp.toPx(), sc, color, 1f - sk * 0.6f, sk * 90f)
                    else sparkle(x, y, SparkSizes[i % 6].dp.toPx() * sc, color, 1f - sk * 0.5f, sk * 90f)
                }
            }
        }
        val g = m.stage(StageTarget.GLINT, StageProp.ALPHA, t, 0f)
        if (g > 0f) with(sparkle) { sparkle(geo.cardRight - 7.dp.toPx(), geo.cardTop + 5.dp.toPx(), 8.dp.toPx(), gold, g) }
    }
}

private val SparkSizes = floatArrayOf(9f, 7f, 6f, 8f, 6.5f, 7f)

/** Strokes made once per width. */
private class StrokeCache {
    private var roundW = -1f
    private var round: Stroke? = null
    private var flatW = -1f
    private var flat: Stroke? = null

    fun get(w: Float, round: Boolean): Stroke = if (round) {
        if (w != roundW) { roundW = w; this.round = Stroke(w, cap = StrokeCap.Round) }
        this.round!!
    } else {
        if (w != flatW) { flatW = w; flat = Stroke(w) }
        flat!!
    }
}
