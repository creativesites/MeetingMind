package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.compose.animation.core.Easing
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Eyes
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.BLINK
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.DY
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.EAR
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.FOLD
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.HEAD_TILT
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.HOP
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.LEAN
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.LOOK_X
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.LOOK_Y
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.MINI_BLINK
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.MINI_FOLD
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.SPROUT_K
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.SX
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.SY
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.TAIL
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.TILT
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.WAG
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentChannel.WING
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentEasing.Back
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentEasing.In
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentEasing.Io
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentEasing.Lin
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentEasing.Out
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentEasing.Spring
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentEasing.Std
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageProp.ALPHA
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageProp.BLUR_DP
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageProp.PROGRESS
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageProp.ROTATION
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageProp.SCALE
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageProp.SCALE_Y
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageProp.X_DP
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageProp.Y_DP
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.ACTIONS
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.BLOOM
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.BODY
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.CAPTION
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.CARD
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.CARET
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.COMPANION
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.CURTAIN
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.GLINT
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.GLOW
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.HALO
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.HEADER
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.HEADLINE
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.KEY_POINTS
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.MINI_COMPANION
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.RING
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.RIPPLE
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.SPARKLES
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.SPROUTS
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.SUBLINE
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.TITLE_WORDS
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.UNDERLINE
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget.WORDS

/**
 * The moment table (§2.3, §2.4, §3.4, §3.5, §5.4), ported from the prototype `docs/mvp/zuri-moments.html`
 * (`FK`, `FK_FORMS`, `FK_QUIET`, `UV`, `NOD`). Tuned in Companion Lab → Moments without touching the renderer.
 *
 * Units: stage offsets are dp (the prototype's 300 × 640 phone in CSS px, which is dp at 1:1). Unveil
 * [StageTarget.COMPANION] offsets are from the header's horizontal centre ([X_DP]) and top ([Y_DP]); its
 * [SCALE] is a fraction of [UnveilCompanionDp] (0.89 → 64 dp, 0.67 → 48 dp, 0.56 → 40 dp). First Keep
 * companion offsets are from its start position (centred at 47% of the height, 160 dp).
 */
object MomentSpecs {

    /** The Unveil character box: SCALE 1 = 72 dp (the prototype's `.u-z`). */
    const val UnveilCompanionDp = 72
    /** The First Keep hero. */
    const val FirstKeepCompanionDp = 160

    private fun k(ms: Int, v: Float, e: Easing = Std) = MsKey(ms, v, e)
    private fun s(target: StageTarget, prop: StageProp, vararg keys: MsKey) = StageTrack(target, prop, keys.toList())
    private fun p(channel: MomentChannel, vararg keys: MsKey) = PoseTrack(channel, keys.toList())
    private fun states(vararg pairs: Pair<Int, MomentVisual>) = pairs.map { StateKey(it.first, it.second) }

    // ------------------------------------------------------------------ First Keep (§2.3)

    val FirstKeep = MomentSpec(
        kind = MomentKind.FIRST_KEEP,
        baseMs = 3600,
        beats = listOf(
            Beat("Complete", 0, 300), Beat("Anticipate", 300, 450), Beat("Hop & glow · Signature", 450, 950),
            Beat("Present", 900, 1600), Beat("Title writes", 1500, 2300), Beat("Words", 2100, 2900), Beat("Settle", 2700, 3600)
        ),
        haptics = listOf(HapticBeat(700, HapticKind.TICK), HapticBeat(1500, HapticKind.CONFIRM)),
        sparkles = 6,
        sound = true,
        stage = listOf(
            s(RING, PROGRESS, k(0, .82f), k(200, 1f, Out)), s(RING, ALPHA, k(0, 1f), k(150, 1f), k(350, 0f)),
            s(CAPTION, ALPHA, k(0, 1f), k(200, 0f)),
            s(BLOOM, SCALE, k(0, .2f), k(100, .2f), k(600, 1f, Out)),
            s(BLOOM, ALPHA, k(0, 0f), k(100, 0f), k(600, 1f, Out), k(2400, 1f), k(3200, .3f, Io)),
            s(HALO, SCALE, k(0, .8f), k(700, .8f), k(1200, 1.8f, Out)),
            s(HALO, ALPHA, k(0, 0f), k(699, 0f), k(700, .6f), k(1200, 0f, Out)),
            s(SPARKLES, PROGRESS, k(0, 0f), k(700, 0f), k(1700, 1f, Out)),
            s(CARD, ALPHA, k(0, 0f), k(900, 0f), k(1100, 1f, Out)),
            s(CARD, Y_DP, k(0, 118f), k(900, 118f), k(1600, 0f)),
            s(CARD, X_DP, k(0, 0f)),
            s(CARD, SCALE, k(0, .45f), k(900, .45f), k(1400, 1.03f, Out), k(1600, 1f)),
            s(CARD, ROTATION, k(0, -4f), k(900, -4f), k(1600, 0f)),
            s(TITLE_WORDS, PROGRESS, k(0, 0f), k(1500, 0f), k(2300, 1f, Lin)),
            s(KEY_POINTS, PROGRESS, k(0, 0f), k(1900, 0f), k(2300, 1f, Io)),
            s(HEADLINE, ALPHA, k(0, 0f), k(2100, 0f), k(2500, 1f)), s(HEADLINE, Y_DP, k(0, 8f), k(2100, 8f), k(2500, 0f)),
            s(SUBLINE, ALPHA, k(0, 0f), k(2300, 0f), k(2700, 1f)),
            s(ACTIONS, ALPHA, k(0, 0f), k(2500, 0f), k(2900, 1f)),
            s(COMPANION, SCALE, k(0, .6f), k(500, 1f, Spring), k(1000, 1f), k(1600, .72f)),
            s(COMPANION, Y_DP, k(0, 0f), k(1000, 0f), k(1600, 60f)),
            s(COMPANION, X_DP, k(0, 0f)), s(COMPANION, ALPHA, k(0, 1f)),
            s(GLINT, ALPHA, k(0, 0f))
        ),
        states = states(0 to MomentVisual.THINKING, 200 to MomentVisual.CELEBRATING, 2700 to MomentVisual.IDLE),
        pose = listOf(
            p(SY, k(300, 1f), k(450, .92f, In), k(700, 1.06f, Out), k(950, 1f, In), k(1020, .92f, Out), k(1250, 1f, Spring)),
            p(SX, k(300, 1f), k(450, 1.04f, In), k(700, .97f, Out), k(950, 1f, In), k(1020, 1.08f, Out), k(1250, 1f, Spring)),
            p(DY, k(450, 0f), k(700, -12f, Out), k(950, 0f, In)),
            p(HOP, k(450, 0f), k(700, 1f, Out), k(950, 0f, In)),
            p(SPROUT_K, k(450, 1f), k(700, 1.33f, Out), k(2300, 1.33f), k(2700, 1f)),
            p(LOOK_Y, k(0, 0f), k(1500, 0f), k(1800, -1.5f), k(2700, -1.5f), k(3000, 0f)),
            p(BLINK, k(2950, 0f), k(3030, 1f), k(3110, 0f))
        ),
        formTakes = mapOf(
            CompanionForm.NAS to FormTake(
                pose = listOf(
                    p(DY, k(450, 0f), k(700, -6f, Out), k(950, 0f, In)),
                    p(HOP, k(450, 0f), k(700, .5f, Out), k(950, 0f, In)),
                    p(WAG, k(0, 0f), k(450, 0f), k(500, 1f), k(1650, 0f, Out)),
                    p(EAR, k(0, 14f), k(450, 14f), k(700, 24f, Out), k(1650, 14f, Io)),
                    p(HEAD_TILT, k(0, 0f), k(1500, 0f), k(1800, -8f))
                ),
                stage = listOf(
                    s(CARD, X_DP, k(0, 40f), k(900, 40f), k(1600, 0f)),
                    s(CARD, ROTATION, k(0, 6f), k(900, 6f), k(1600, 0f))
                ),
                beats = listOf(
                    Beat("Complete", 0, 300), Beat("Anticipate", 300, 450), Beat("Proud fetch · Signature", 450, 950),
                    Beat("Present", 900, 1600), Beat("Title writes", 1500, 2300), Beat("Words", 2100, 2900), Beat("Settle", 2700, 3600)
                )
            ),
            CompanionForm.WREN to FormTake(
                pose = listOf(
                    p(WING, k(0, 0f), k(450, -20f), k(600, -55f, Out), k(750, -28f), k(900, -55f, Out), k(1050, -28f), k(1300, -8f),
                        k(2400, -8f), k(2550, -55f, Out), k(2700, -20f), k(2850, -55f, Out), k(3000, -6f, Out)),
                    p(TAIL, k(0, 0f), k(450, 12f, Out)),
                    p(DY, k(450, 0f), k(700, -12f, Out), k(950, 0f, In), k(2400, 0f), k(2700, -6f, Out), k(3000, 0f, In))
                ),
                stage = listOf(
                    s(COMPANION, X_DP, k(0, 0f), k(2400, 0f), k(3000, 100f)),
                    s(COMPANION, Y_DP, k(0, 0f), k(1000, 0f), k(1600, 60f), k(2400, 60f), k(3000, -224f)),
                    s(COMPANION, SCALE, k(0, .6f), k(500, 1f, Spring), k(1000, 1f), k(1600, .72f), k(2400, .72f), k(3000, .45f)),
                    s(WORDS, Y_DP, k(0, -92f))
                ),
                beats = listOf(
                    Beat("Complete", 0, 300), Beat("Anticipate", 300, 450), Beat("Flutter-lift · Signature", 450, 1300),
                    Beat("Present", 900, 1600), Beat("Title writes", 1500, 2300), Beat("Words", 2100, 2900), Beat("Perch", 2400, 3000)
                )
            ),
            CompanionForm.PAGE to FormTake(
                pose = listOf(
                    p(MINI_FOLD, k(0, 12f), k(2500, 12f), k(2650, 16f), k(2800, 12f), k(2950, 15f), k(3100, 12f)),
                    p(MINI_BLINK, k(0, 0f), k(3050, 0f), k(3120, 1f), k(3190, 0f))
                ),
                stage = listOf(
                    s(COMPANION, ALPHA, k(0, 1f), k(900, 1f), k(1200, 0f, In)),
                    s(COMPANION, SCALE, k(0, .6f), k(500, 1f, Spring), k(900, 1f), k(1200, 1.4f, In)),
                    s(COMPANION, Y_DP, k(0, 0f)),
                    s(CARD, SCALE, k(0, .4f), k(900, .4f), k(1400, 1.03f, Out), k(1600, 1f)),
                    s(WORDS, Y_DP, k(0, -92f)),
                    s(MINI_COMPANION, ALPHA, k(0, 0f), k(2500, 0f), k(2800, 1f, Out)),
                    s(MINI_COMPANION, SCALE, k(0, 0f), k(2500, 0f), k(2800, .4f, Back))
                ),
                beats = listOf(
                    Beat("Complete", 0, 300), Beat("Anticipate", 300, 450), Beat("Hop · Signature", 450, 950),
                    Beat("Become the note", 900, 1600), Beat("Title writes", 1500, 2300), Beat("Words", 2100, 2900), Beat("Mini Page", 2500, 3600)
                )
            )
        ),
        quiet = MomentPatch(
            pose = listOf(
                p(DY, k(0, 0f)), p(HOP, k(0, 0f)), p(WAG, k(0, 0f)),
                p(SY, k(300, 1f), k(450, .97f, In), k(800, 1.02f, Out), k(1100, 1f, Io)),
                p(SX, k(300, 1f), k(450, 1.015f), k(800, .99f), k(1100, 1f))
            ),
            stage = listOf(
                s(SPARKLES, PROGRESS, k(0, 0f)), s(HALO, ALPHA, k(0, 0f)),
                s(GLINT, ALPHA, k(0, 0f), k(1500, 0f), k(1800, 1f, Out), k(2300, 0f, Io))
            ),
            states = states(0 to MomentVisual.THINKING, 200 to MomentVisual.PEACEFUL, 1100 to MomentVisual.IDLE),
            eyes = listOf(EyesKey(0, Eyes.OPEN), EyesKey(200, Eyes.CLOSED), EyesKey(1100, Eyes.HAPPY), EyesKey(2700, Eyes.OPEN)),
            haptics = emptyList(),
            beats = listOf(
                Beat("Complete", 0, 300), Beat("Breathe in", 300, 450), Beat("Glow · Signature", 450, 1100),
                Beat("Present", 900, 1600), Beat("Title writes", 1500, 2300), Beat("Words", 2100, 2900), Beat("Settle", 2700, 3600)
            )
        ),
        quietTakes = mapOf(
            CompanionForm.WREN to MomentPatch(
                pose = listOf(p(WING, k(0, -6f))),
                stage = listOf(
                    s(WORDS, Y_DP, k(0, 0f)), s(COMPANION, X_DP, k(0, 0f)),
                    s(COMPANION, Y_DP, k(0, 0f), k(1000, 0f), k(1600, 60f)),
                    s(COMPANION, SCALE, k(0, .6f), k(500, 1f, Spring), k(1000, 1f), k(1600, .72f))
                )
            ),
            CompanionForm.NAS to MomentPatch(pose = listOf(p(EAR, k(0, 12f), k(1100, 16f, Out))))
        )
    )

    // ------------------------------------------------------------------ Unveil variants (§3.4)

    val Lift = MomentSpec(
        kind = MomentKind.UNVEIL, variant = UnveilVariant.LIFT, baseMs = 1500,
        beats = listOf(Beat("Arrive", 0, 150), Beat("Anticipate", 150, 300), Beat("Lift · Signature", 300, 700),
            Beat("Land", 700, 900), Beat("Step back", 900, 1300), Beat("Body", 600, 1300)),
        haptics = listOf(HapticBeat(700, HapticKind.TICK)),
        stage = listOf(
            s(COMPANION, X_DP, k(0, 0f), k(900, 0f), k(1300, 82f, In)),
            s(COMPANION, Y_DP, k(0, 48f), k(900, 48f), k(1300, -30f, In)),
            s(COMPANION, SCALE, k(0, .53f), k(150, .89f, Out), k(900, .89f), k(1300, .45f, In)),
            s(COMPANION, ALPHA, k(0, 0f), k(150, 1f, Out), k(1100, 1f), k(1300, 0f)),
            s(HEADER, ALPHA, k(0, 0f), k(300, 0f), k(500, 1f, Out)),
            s(HEADER, Y_DP, k(0, 16f), k(300, 16f), k(700, -4f, Back), k(900, 0f)),
            s(HEADER, SCALE, k(0, .85f), k(300, .85f), k(700, 1.02f, Back), k(900, 1f)),
            s(BODY, PROGRESS, k(0, 0f), k(600, 0f), k(1300, 1f, Out))
        ),
        states = states(0 to MomentVisual.IDLE, 300 to MomentVisual.CELEBRATING, 1300 to MomentVisual.IDLE),
        pose = listOf(
            p(SY, k(150, 1f), k(300, .93f, In), k(500, 1.05f, Out), k(700, 1f, In), k(760, .92f, Out), k(900, 1f, Spring)),
            p(SX, k(150, 1f), k(300, 1.04f, In), k(500, .97f, Out), k(700, 1f, In), k(760, 1.08f, Out), k(900, 1f, Spring)),
            p(DY, k(300, 0f), k(500, -10f, Out), k(700, 0f, In)),
            p(HOP, k(300, 0f), k(500, 1f, Out), k(700, 0f, In)),
            p(SPROUT_K, k(300, 1f), k(500, 1.33f, Out), k(900, 1f))
        ),
        formTakes = mapOf(
            CompanionForm.NAS to FormTake(pose = listOf(
                p(DY, k(300, 0f), k(500, -5f, Out), k(700, 0f, In)),
                p(HEAD_TILT, k(0, 0f), k(300, 0f), k(500, -10f, Out), k(900, 0f)),
                p(WAG, k(0, 0f), k(600, 0f), k(650, 1f), k(1300, 0f, Out))
            )),
            CompanionForm.WREN to FormTake(pose = listOf(p(WING, k(0, 0f), k(300, 0f), k(500, -45f, Out), k(900, -10f)))),
            CompanionForm.PAGE to FormTake(pose = listOf(p(FOLD, k(0, 12f), k(300, 12f), k(450, 17f, Out), k(650, 12f))))
        )
    )

    val Inkline = MomentSpec(
        kind = MomentKind.UNVEIL, variant = UnveilVariant.INKLINE, baseMs = 1400,
        beats = listOf(Beat("Arrive", 0, 150), Beat("Write · Signature", 150, 950), Beat("Underline", 950, 1150),
            Beat("Clear", 1150, 1400), Beat("Body", 700, 1300)),
        haptics = listOf(HapticBeat(1000, HapticKind.TICK)),
        tickPerWord = true,
        stage = listOf(
            s(COMPANION, X_DP, k(0, 56f)), s(COMPANION, Y_DP, k(0, -30f)),
            s(COMPANION, SCALE, k(0, .5f), k(150, .67f, Out)),
            s(COMPANION, ALPHA, k(0, 0f), k(150, 1f, Out), k(1150, 1f), k(1400, 0f)),
            s(TITLE_WORDS, PROGRESS, k(0, 0f), k(150, 0f), k(950, 1f, Lin)),
            s(CARET, ALPHA, k(0, 0f), k(150, 1f), k(950, 1f), k(1000, 0f)),
            s(UNDERLINE, PROGRESS, k(0, 0f), k(950, 0f), k(1150, 1f)),
            s(UNDERLINE, ALPHA, k(0, 0f), k(950, 1f), k(1150, 1f), k(1400, 0f)),
            s(BODY, PROGRESS, k(0, 0f), k(700, 0f), k(1300, 1f, Out))
        ),
        pose = listOf(
            p(LOOK_X, k(0, -1.2f), k(150, -1.2f), k(950, 1.2f, Lin), k(1150, 0f)),
            p(LOOK_Y, k(0, 1.4f)),
            p(DY, k(0, 0f), k(950, 0f), k(1050, 3f, Out), k(1150, 0f, In)),
            p(SY, k(950, 1f), k(1050, .97f), k(1150, 1f))
        ),
        formTakes = mapOf(CompanionForm.PAGE to FormTake(states = states(0 to MomentVisual.THINKING, 950 to MomentVisual.IDLE)))
    )

    val Curtain = MomentSpec(
        kind = MomentKind.UNVEIL, variant = UnveilVariant.CURTAIN, baseMs = 1500,
        beats = listOf(Beat("Cover", 0, 200), Beat("Grip · Signature", 200, 400), Beat("Part", 400, 1000),
            Beat("Settle", 1000, 1500), Beat("Body", 800, 1400)),
        haptics = listOf(HapticBeat(700, HapticKind.TICK)),
        stage = listOf(
            s(CURTAIN, PROGRESS, k(0, 0f), k(400, 0f), k(1000, 1f)),
            s(COMPANION, X_DP, k(0, 0f), k(400, 0f), k(1000, 64f)),
            s(COMPANION, Y_DP, k(0, 58f)),
            s(COMPANION, SCALE, k(0, .47f), k(200, .78f, Out)),
            s(COMPANION, ALPHA, k(0, 0f), k(120, 1f), k(900, 1f), k(1150, 0f)),
            s(HEADER, ALPHA, k(0, 0f), k(400, 0f), k(900, 1f, Out)),
            s(HEADER, SCALE, k(0, .98f), k(400, .98f), k(1000, 1f)),
            s(BODY, PROGRESS, k(0, 0f), k(800, 0f), k(1400, 1f, Out))
        ),
        states = states(0 to MomentVisual.IDLE, 400 to MomentVisual.CELEBRATING),
        pose = listOf(
            p(SY, k(200, 1f), k(400, .92f, In), k(560, 1f, Spring)),
            p(SX, k(200, 1f), k(400, 1.05f, In), k(560, 1f, Spring)),
            p(LOOK_Y, k(200, 0f), k(400, 1.5f), k(700, 0f)),
            p(LEAN, k(0, 0f), k(200, 0f), k(400, -14f, Out), k(700, 10f), k(1000, 0f)),
            p(TILT, k(400, 0f), k(700, 8f), k(1000, 0f))
        ),
        formTakes = mapOf(
            CompanionForm.NAS to FormTake(pose = listOf(
                p(HEAD_TILT, k(0, 0f), k(200, 0f), k(400, -12f, Out), k(700, 0f)),
                p(EAR, k(0, 12f), k(200, 12f), k(400, 4f), k(800, 12f))
            )),
            CompanionForm.WREN to FormTake(pose = listOf(p(WING, k(0, 0f), k(200, 0f), k(400, -35f), k(800, -62f, Out), k(1100, 0f)))),
            CompanionForm.PAGE to FormTake(pose = listOf(p(FOLD, k(0, 12f), k(200, 12f), k(400, 19f, Out), k(700, 12f))))
        )
    )

    val Dawn = MomentSpec(
        kind = MomentKind.UNVEIL, variant = UnveilVariant.DAWN, baseMs = 1500,
        beats = listOf(Beat("Glow", 0, 300), Beat("Rise · Signature", 300, 900), Beat("Nod", 900, 1200),
            Beat("Fade", 1200, 1500), Beat("Body", 700, 1300)),
        stage = listOf(
            s(GLOW, ALPHA, k(0, 0f), k(300, .9f, Out), k(900, .9f), k(1500, 0f, Io)),
            s(GLOW, Y_DP, k(0, 24f), k(300, 0f, Out)),
            s(HEADER, ALPHA, k(0, 0f), k(300, 0f), k(900, 1f, Out)),
            s(HEADER, Y_DP, k(0, 6f), k(300, 6f), k(900, 0f, Out)),
            s(HEADER, BLUR_DP, k(0, 4f), k(300, 4f), k(900, 0f, Out)),
            s(COMPANION, X_DP, k(0, 56f)), s(COMPANION, Y_DP, k(0, -30f)), s(COMPANION, SCALE, k(0, .67f)),
            s(COMPANION, ALPHA, k(0, 0f), k(300, 1f, Out), k(1200, 1f), k(1500, 0f)),
            s(GLINT, ALPHA, k(0, 0f), k(900, 0f), k(1050, 1f, Out), k(1300, 0f, In)),
            s(BODY, PROGRESS, k(0, 0f), k(700, 0f), k(1300, 1f, Out))
        ),
        eyes = listOf(EyesKey(0, Eyes.CLOSED), EyesKey(700, Eyes.OPEN)),
        pose = listOf(
            p(DY, k(0, 0f), k(900, 0f), k(1050, 3f, Out), k(1200, 0f, In)),
            p(SY, k(900, 1f), k(1050, .97f), k(1200, 1f)),
            p(SPROUT_K, k(0, .9f), k(300, .9f), k(900, 1.15f, Out), k(1500, 1f))
        ),
        formTakes = mapOf(
            CompanionForm.NAS to FormTake(pose = listOf(p(EAR, k(0, 6f), k(300, 6f), k(900, 18f, Out)))),
            CompanionForm.WREN to FormTake(pose = listOf(
                p(HEAD_TILT, k(0, 8f), k(300, 8f), k(900, -6f, Out)),
                p(TAIL, k(0, -6f), k(300, -6f), k(900, 10f, Out))
            )),
            CompanionForm.PAGE to FormTake(pose = listOf(p(FOLD, k(0, 15f), k(300, 15f), k(900, 12f, Out))))
        ),
        // The sermon's quiet Dawn (§3.3 row 6): eyes stay closed (Peaceful), no glint.
        quiet = MomentPatch(
            stage = listOf(s(GLINT, ALPHA, k(0, 0f))),
            states = states(0 to MomentVisual.PEACEFUL),
            eyes = listOf(EyesKey(0, Eyes.CLOSED)),
            beats = listOf(Beat("Glow", 0, 300), Beat("Rise", 300, 900), Beat("Soft nod", 900, 1200), Beat("Fade", 1200, 1500), Beat("Body", 700, 1300))
        )
    )

    val Plant = MomentSpec(
        kind = MomentKind.UNVEIL, variant = UnveilVariant.PLANT, baseMs = 1600,
        beats = listOf(Beat("Drop", 0, 400), Beat("Plant · Signature", 400, 450), Beat("Grow", 450, 900),
            Beat("Pat", 600, 750), Beat("Hop away", 1050, 1450), Beat("Body", 800, 1400)),
        haptics = listOf(HapticBeat(400, HapticKind.TICK)),
        stage = listOf(
            s(COMPANION, X_DP, k(0, 0f), k(1050, 0f), k(1450, 94f, In)),
            s(COMPANION, Y_DP, k(0, -30f), k(400, 28f, In), k(1050, 28f), k(1250, 2f, Out), k(1450, -18f, In)),
            s(COMPANION, SCALE, k(0, .78f)),
            s(COMPANION, ALPHA, k(0, 0f), k(100, 1f), k(1300, 1f), k(1450, 0f)),
            s(RIPPLE, PROGRESS, k(0, 0f), k(400, 0f), k(1000, 1f, Out)),
            s(SPROUTS, PROGRESS, k(0, 0f), k(450, 0f), k(800, 1f, Back), k(1000, 1f), k(1200, 0f, In)),
            s(HEADER, ALPHA, k(0, 0f), k(450, 0f), k(700, 1f, Out)),
            s(HEADER, SCALE_Y, k(0, .6f), k(450, .6f), k(800, 1.04f, Back), k(900, 1f)),
            s(BODY, PROGRESS, k(0, 0f), k(800, 0f), k(1400, 1f, Out))
        ),
        states = states(0 to MomentVisual.IDLE, 400 to MomentVisual.CELEBRATING, 1450 to MomentVisual.IDLE),
        pose = listOf(
            p(SY, k(0, 1.06f), k(400, 1.06f), k(440, .9f, Out), k(560, 1f, Spring), k(600, 1f), k(650, .95f, Out), k(750, 1f, Spring)),
            p(SX, k(0, .97f), k(400, .97f), k(440, 1.1f, Out), k(560, 1f, Spring), k(600, 1f), k(650, 1.04f, Out), k(750, 1f, Spring)),
            p(DY, k(0, 0f), k(1050, 0f), k(1250, -8f, Out), k(1450, 0f, In))
        ),
        formTakes = mapOf(
            CompanionForm.NAS to FormTake(pose = listOf(p(WAG, k(0, 0f), k(400, 1f), k(900, 0f, Out)))),
            CompanionForm.WREN to FormTake(pose = listOf(p(WING, k(0, 0f), k(400, -40f), k(550, 0f), k(650, -40f), k(800, 0f)))),
            CompanionForm.PAGE to FormTake(pose = listOf(p(FOLD, k(0, 12f), k(400, 19f), k(700, 12f, Out))))
        )
    )

    // ------------------------------------------------------------------ Nod (§3.5)

    val Nod = MomentSpec(
        kind = MomentKind.NOD, baseMs = 600,
        beats = listOf(Beat("Header", 0, 250), Beat("Body", 100, 500), Beat("Nod", 150, 450), Beat("Out", 450, 600)),
        stage = listOf(
            s(HEADER, ALPHA, k(0, 0f), k(250, 1f)), s(HEADER, Y_DP, k(0, 8f), k(250, 0f)),
            s(BODY, PROGRESS, k(0, 0f), k(100, 0f), k(500, 1f, Out)),
            s(COMPANION, X_DP, k(0, 62f)), s(COMPANION, Y_DP, k(0, -30f)), s(COMPANION, SCALE, k(0, .56f)),
            s(COMPANION, ALPHA, k(0, 0f), k(120, 1f), k(450, 1f), k(600, 0f))
        ),
        states = states(0 to MomentVisual.CELEBRATING),
        pose = listOf(
            p(DY, k(0, 0f), k(150, 0f), k(300, 3f, Out), k(450, 0f, In)),
            p(SY, k(150, 1f), k(300, .97f), k(450, 1f))
        )
    )

    // ------------------------------------------------------------------ Card reveal (§5.4)

    /** The Create reveal: mark, step on, the mode's Signature, settle to the export pose. Offsets in units of the slot. */
    val CardReveal = MomentSpec(
        kind = MomentKind.CARD_REVEAL, baseMs = 1500,
        beats = listOf(Beat("Mark", 0, 150), Beat("Step on", 150, 700), Beat("Signature", 700, 1300), Beat("Settle to export", 1300, 1500)),
        stage = listOf(
            // The dashed ring at the placement rect (drawn by the RING layer).
            s(RING, ALPHA, k(0, 0f), k(150, .5f), k(400, .5f), k(700, 0f)),
            // Y_DP here is a fraction of the slot size: +12 units of the 100-unit box.
            s(COMPANION, Y_DP, k(0, .12f), k(150, .12f), k(700, 0f, Back)),
            s(COMPANION, SCALE, k(0, .88f), k(150, .88f), k(700, 1f, Back)),
            s(COMPANION, ALPHA, k(0, 0f), k(150, 0f), k(500, 1f, Out)),
            // 0 → 1 across the Signature, then 1 → 0 while it settles into the export pose.
            s(COMPANION, PROGRESS, k(0, 0f), k(700, 0f), k(1300, 1f, Lin)),
            s(CAPTION, ALPHA, k(0, 0f), k(1300, 0f), k(1500, 1f))
        ),
        pose = emptyList()
    )

    /** The five Unveils in `VARIANT_ORDER`. */
    val Unveils: Map<UnveilVariant, MomentSpec> = mapOf(
        UnveilVariant.LIFT to Lift, UnveilVariant.INKLINE to Inkline, UnveilVariant.CURTAIN to Curtain,
        UnveilVariant.DAWN to Dawn, UnveilVariant.PLANT to Plant
    )

    val All: List<MomentSpec> = listOf(FirstKeep, Lift, Inkline, Curtain, Dawn, Plant, Nod, CardReveal)

    fun unveil(v: UnveilVariant): MomentSpec = Unveils.getValue(v)
}
