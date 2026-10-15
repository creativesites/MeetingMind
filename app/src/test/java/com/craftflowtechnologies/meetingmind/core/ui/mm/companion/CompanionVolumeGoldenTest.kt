package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Volume-layer goldens (Z-14, §10.10): a deliberate subset, 56 images, not the full matrix.
 *
 * - **States:** 4 forms × {Idle, Listening, Celebrating} × light/dark at 96 dp, Indigo (24).
 * - **Accents:** 4 forms × 6 accents at 64 dp, one theme each, alternating so every accent is seen in
 *   both themes across the forms (24). The shading is accent-neutral; only the rim takes the accent.
 * - **T1 floor:** 4 forms × Idle × light/dark at 48 dp, the smallest size that gets the layer (8).
 *
 * Static poses, Canvas, the layer forced on with [LocalCompanionVolume]. With the flag off the
 * existing 288 goldens in `CompanionGoldenTest` are unchanged.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CompanionVolumeGoldenTest(private val case: Case) {

    data class Case(val form: CompanionForm, val state: CompanionState, val dark: Boolean, val sizeDp: Int, val accent: MMAccent) {
        val path: String get() {
            val theme = if (dark) "dark" else "light"
            val base = "src/test/screenshots/companion/volume/${form.name.lowercase()}"
            return if (sizeDp == 64) "$base/accents/${state.name.lowercase()}_${accent.name.lowercase()}_${theme}_$sizeDp.png"
            else "$base/${state.name.lowercase()}_${theme}_$sizeDp.png"
        }
        override fun toString() = path.substringAfter("companion/").removeSuffix(".png")
    }

    @get:Rule val rule = createComposeRule()

    @Test fun golden() {
        rule.setContent {
            MeetMindTheme(darkTheme = case.dark, accent = case.accent) {
                CompositionLocalProvider(
                    LocalCompanionReducedMotion provides true, LocalCompanionForceCanvas provides true,
                    LocalCompanionVolume provides true
                ) {
                    Box(Modifier.background(MM.colors.background).padding(8.dp)) {
                        Companion(case.form, case.state, case.sizeDp.dp)
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage(filePath = case.path)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val out = mutableListOf<Case>()
            CompanionForm.entries.forEachIndexed { fi, form ->
                for (s in listOf(CompanionState.IDLE, CompanionState.LISTENING, CompanionState.CELEBRATING))
                    for (dark in listOf(false, true)) out += Case(form, s, dark, 96, MMAccent.Indigo)
                MMAccent.entries.forEachIndexed { ai, a -> out += Case(form, CompanionState.IDLE, (ai + fi) % 2 == 1, 64, a) }
                for (dark in listOf(false, true)) out += Case(form, CompanionState.IDLE, dark, 48, MMAccent.Indigo)
            }
            return out.map { arrayOf<Any>(it) }
        }
    }
}
