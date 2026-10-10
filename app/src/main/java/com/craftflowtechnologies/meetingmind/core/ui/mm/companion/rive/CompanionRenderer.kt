package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Which renderer draws a companion slot. */
enum class CompanionRenderer { RIVE, CANVAS }

/** Why a slot got the renderer it got (shown in Companion Lab). */
enum class RendererReason {
    RIVE_READY,
    /** Under 48 dp: the T0 glyph is Canvas. */
    TOO_SMALL,
    /** Animator duration scale is 0: static poses. */
    REDUCED_MOTION,
    /** The form's `.riv` is not bundled yet. */
    NO_ASSET,
    /** The screen's live Rive budget is used up. */
    OVER_BUDGET,
    /** `CompanionFlags.rive` is off. */
    DISABLED,
    /** Screenshot tests and card export force Canvas. */
    FORCED_CANVAS,
    /** The file failed to load or lacks the contract (missing state machine or colours). */
    RIVE_FAILED
}

data class RendererChoice(val renderer: CompanionRenderer, val reason: RendererReason)

/** Inputs to [CompanionRendererSelector.select]. */
data class RendererInputs(
    val sizeDp: Float,
    val durationScale: Float,
    val assetBundled: Boolean,
    /** True when this slot holds (or can take) one of the screen's live Rive slots. */
    val hasBudgetSlot: Boolean,
    val riveEnabled: Boolean = true,
    val forceCanvas: Boolean = false,
    val riveFailed: Boolean = false
)

/**
 * Picks the renderer (docs/mvp/ZURI_RIVE_BRIEF.md §1). Rive only when every condition holds:
 * ≥ 48 dp, animations on, the `.riv` bundled, a free budget slot, the flag on and nothing forcing
 * Canvas. Canvas otherwise. Pure, so every rule is unit-tested.
 */
object CompanionRendererSelector {
    const val MinRiveSizeDp = 48f

    fun select(i: RendererInputs): RendererChoice {
        fun canvas(r: RendererReason) = RendererChoice(CompanionRenderer.CANVAS, r)
        return when {
            i.forceCanvas -> canvas(RendererReason.FORCED_CANVAS)
            !i.riveEnabled -> canvas(RendererReason.DISABLED)
            i.sizeDp < MinRiveSizeDp -> canvas(RendererReason.TOO_SMALL)
            i.durationScale <= 0f -> canvas(RendererReason.REDUCED_MOTION)
            !i.assetBundled -> canvas(RendererReason.NO_ASSET)
            i.riveFailed -> canvas(RendererReason.RIVE_FAILED)
            !i.hasBudgetSlot -> canvas(RendererReason.OVER_BUDGET)
            else -> RendererChoice(CompanionRenderer.RIVE, RendererReason.RIVE_READY)
        }
    }

    /** Everything but the budget: whether a slot should try to take a budget slot at all. */
    fun wantsRive(i: RendererInputs): Boolean = select(i.copy(hasBudgetSlot = true)).renderer == CompanionRenderer.RIVE
}

/**
 * How many Rive companions may be live at once on a screen (§10.8: ≤ 2 animating instances).
 * Slots past the budget fall back to Canvas. Screens may provide their own with
 * [LocalCompanionRiveBudget]; the default allows 2.
 */
class CompanionRiveBudget(val max: Int = DefaultMax) {
    var live by mutableIntStateOf(0)
        private set

    fun tryAcquire(): Boolean {
        if (live >= max) return false
        live++
        return true
    }

    fun release() {
        if (live > 0) live--
    }

    companion object {
        const val DefaultMax = 2
    }
}

val LocalCompanionRiveBudget = staticCompositionLocalOf { CompanionRiveBudget() }
