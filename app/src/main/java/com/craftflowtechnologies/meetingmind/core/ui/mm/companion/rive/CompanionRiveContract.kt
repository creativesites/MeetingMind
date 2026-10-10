package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive

import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode

/**
 * The typed contract between the app and the `.riv` files (docs/mvp/ZURI_RIVE_BRIEF.md §3).
 * Every name here must exist in the file exactly as written; Companion Lab checks them.
 *
 * - One file per form in `assets/companion/`: `zuri.riv`, `nas.riv`, `wren.riv`, `page.riv`.
 * - Artboard per form: "Zuri", "Nas", "Wren", "Page".
 * - State machine: "Companion".
 * - Inputs: [Inputs]. Colours: view model [ViewModel] with the colour properties in [Colors].
 */
object CompanionRiveContract {
    const val AssetDir = "companion"
    const val StateMachine = "Companion"
    const val ViewModel = "CompanionTheme"

    fun artboard(form: CompanionForm): String = when (form) {
        CompanionForm.ZURI -> "Zuri"
        CompanionForm.NAS -> "Nas"
        CompanionForm.WREN -> "Wren"
        CompanionForm.PAGE -> "Page"
    }

    fun fileName(form: CompanionForm): String = form.name.lowercase() + ".riv"

    fun assetPath(form: CompanionForm): String = "$AssetDir/${fileName(form)}"

    /** State machine inputs. */
    object Inputs {
        /** Number: [CompanionState] ordinal, see [stateValue]. Required. */
        const val State = "state"
        /** Number 0–100: the smoothed mic or TTS level. Required. */
        const val Level = "level"
        /** Number: 0 = none, 1–6 = [CreateMode] ordinal + 1. Overrides [State] when non-zero. Required. */
        const val Mode = "mode"
        /** Trigger: replay the Celebrating hop. Required. */
        const val Celebrate = "celebrate"
        /** Trigger: the 600 ms nod. Required. */
        const val Nod = "nod"
        /** Trigger: one blink. Optional (the file may blink on its own timer). */
        const val Blink = "blink"
        /** Numbers −100..100: parallax look. Optional. */
        const val LookX = "lookX"
        const val LookY = "lookY"
        /** Boolean: sermon quiet (Peaceful pose, no level reaction, no rings). Required. */
        const val Calm = "calm"

        val required = listOf(State, Level, Mode, Celebrate, Nod, Calm)
    }

    /** Colour properties on the [ViewModel] view model, set from the user's accent at runtime. */
    object Colors {
        const val Accent = "accent"
        const val BodyTop = "bodyTop"
        const val BodyBottom = "bodyBottom"
        const val Deep = "deep"
        const val Light = "light"
        const val Rim = "rim"
        const val Paper = "paper"
        const val PaperLine = "paperLine"
        const val Lines = "lines"
        const val Eye = "eye"
        const val Blush = "blush"
        const val Sparkle = "sparkle"
        const val Gold = "gold"
        const val Mute = "mute"
        const val Shadow = "shadow"
        const val Halo = "halo"

        /** Every form must bind these; [Paper], [PaperLine] and [Lines] are used by Page only. */
        val required = listOf(Accent, BodyTop, BodyBottom, Deep, Light, Eye, Blush, Sparkle, Gold, Shadow)
    }

    /** The `state` input for a visual. Create modes keep `state` at IDLE and use `mode`. */
    fun stateValue(visual: CompanionVisual): Float = when (visual) {
        is CompanionState -> visual.ordinal.toFloat()
        is CreateMode -> CompanionState.IDLE.ordinal.toFloat()
    }

    fun modeValue(visual: CompanionVisual): Float = when (visual) {
        is CreateMode -> (visual.ordinal + 1).toFloat()
        is CompanionState -> 0f
    }

    fun calmValue(variant: CompanionVariant): Boolean = variant == CompanionVariant.Quiet

    /** Mic level 0..1 to the `level` input's 0–100. */
    fun levelValue(level: Float): Float = (level.coerceIn(0f, 1f) * 100f)
}
