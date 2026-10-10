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
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
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
 * Companion goldens (§10.10), one parameterised test: every roster form × (6 MVP states + 6
 * Create modes) × Paper/Graphite × 24/96 dp with Indigo, plus Idle × 6 accents × 2 themes at
 * 64 dp. Static poses on the Canvas renderer. Record with `./gradlew recordRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CompanionGoldenTest(private val case: Case) {

    data class Case(val form: CompanionForm, val visual: CompanionVisual, val dark: Boolean, val sizeDp: Int, val accent: MMAccent) {
        private val theme get() = if (dark) "dark" else "light"
        val path: String
            get() = if (accent == MMAccent.Indigo && sizeDp != 64) "src/test/screenshots/companion/${form.name.lowercase()}/${visual.name.lowercase()}_${theme}_$sizeDp.png"
            else "src/test/screenshots/companion/${form.name.lowercase()}/accents/idle_${accent.name.lowercase()}_${theme}_$sizeDp.png"

        override fun toString() = path.substringAfter("companion/").removeSuffix(".png")
    }

    @get:Rule val rule = createComposeRule()

    @Test fun golden() {
        rule.setContent {
            MeetMindTheme(darkTheme = case.dark, accent = case.accent) {
                CompositionLocalProvider(LocalCompanionReducedMotion provides true, LocalCompanionForceCanvas provides true) {
                    Box(Modifier.background(MM.colors.background).padding(if (case.sizeDp < 48) 4.dp else 8.dp)) {
                        Companion(case.form, case.visual, case.sizeDp.dp)
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage(filePath = case.path)
    }

    companion object {
        private val visuals: List<CompanionVisual> = CompanionState.Mvp + CreateMode.entries

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val out = mutableListOf<Case>()
            for (form in CompanionRoster.enabled) {
                for (v in visuals) for (dark in listOf(false, true)) for (size in listOf(24, 96)) out += Case(form, v, dark, size, MMAccent.Indigo)
                for (a in MMAccent.entries) for (dark in listOf(false, true)) out += Case(form, CompanionState.IDLE, dark, 64, a)
            }
            return out.map { arrayOf<Any>(it) }
        }
    }
}
