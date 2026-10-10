package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette

/** Draws one form into a 100 × 100 unit box (the caller scales the canvas). */
interface FormDrawer {
    fun DrawScope.draw(kit: CompanionDrawKit, f: CompanionFrame, p: CompanionPalette)

    companion object {
        fun of(form: CompanionForm): FormDrawer = when (form) {
            CompanionForm.ZURI -> OrbDrawer
            CompanionForm.NAS -> NasDrawer
            CompanionForm.WREN -> WrenDrawer
            CompanionForm.PAGE -> PageDrawer
        }
    }
}
