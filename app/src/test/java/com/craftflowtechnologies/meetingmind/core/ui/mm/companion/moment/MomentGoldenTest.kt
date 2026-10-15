package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.feature.companionlab.LabMoment
import com.craftflowtechnologies.meetingmind.feature.companionlab.LabNotes
import com.craftflowtechnologies.meetingmind.feature.companionlab.MomentPreview
import com.craftflowtechnologies.meetingmind.feature.companionlab.labPlan
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
 * Moment goldens (§7.9 engine sets, Z-30): a bounded set of 73 key frames, frozen with [LocalMomentTime]
 * on the sample note, Indigo, Canvas renderer, 360 × 720 dp at 2×.
 *
 * - First Keep: 0, 0.7, 1.5, 3.6 s × Paper/Graphite × Zuri/Nas (16) + the quiet Faith version at its
 *   1.5 s and end (×1.15) × Paper/Graphite (4)
 * - Unveils, Zuri: 5 variants × {Signature peak, end} × Paper/Graphite (20)
 * - Nas Signatures: 5 variants × peak, Paper (5)
 * - Nod and the sermon's quiet Dawn: peak × Paper/Graphite (4)
 * - Reduced motion: the First Keep end, an Unveil end and the card reveal × Paper/Graphite (6)
 * - Wren and Page takes (Z-32): First Keep 0.7, 1.5, 3.0, 3.6 s and the five Unveil peaks, Paper (18)
 *
 * Record with `./gradlew recordRoborazziDebug --tests '*MomentGoldenTest'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi", sdk = [34])
class MomentGoldenTest(private val case: Case) {

    data class Case(
        val set: String, val moment: LabMoment, val form: CompanionForm, val space: MomentSpace,
        val playedMs: Int?, val dark: Boolean, val reduced: Boolean = false
    ) {
        val path: String
            get() = "src/test/screenshots/companion/moments/$set/${moment.name.lowercase()}_${form.name.lowercase()}_${space.name.lowercase()}_" +
                "${playedMs ?: "end"}_${if (dark) "dark" else "light"}.png"

        override fun toString() = path.substringAfter("moments/").removeSuffix(".png")
    }

    @get:Rule val rule = createComposeRule()

    @Test fun golden() {
        val plan = labPlan(case.moment, case.space)
        val note = LabNotes.getValue(if (case.moment == LabMoment.DAWN_QUIET) MomentSpace.FAITH else case.space)
        rule.setContent {
            MeetMindTheme(darkTheme = case.dark, accent = MMAccent.Indigo) {
                CompositionLocalProvider(
                    LocalCompanionForceCanvas provides true,
                    LocalCompanionReducedMotion provides if (case.reduced) true else false,
                    LocalMomentTime provides (case.playedMs ?: 999_999)
                ) {
                    MomentPreview(plan, case.form, note, key = "golden", modifier = Modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(filePath = case.path)
    }

    companion object {
        private val Peaks = mapOf(
            LabMoment.LIFT to 500, LabMoment.INKLINE to 550, LabMoment.CURTAIN to 600, LabMoment.DAWN to 800, LabMoment.PLANT to 600
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val out = mutableListOf<Case>()
            val themes = listOf(false, true)
            val z = CompanionForm.ZURI
            val e = MomentSpace.EVERYDAY
            for (form in listOf(z, CompanionForm.NAS)) for (t in listOf(0, 700, 1500, 3600)) for (d in themes)
                out += Case("first_keep", LabMoment.FIRST_KEEP, form, e, t, d)
            for (t in listOf(1725, 4140)) for (d in themes) out += Case("first_keep", LabMoment.FIRST_KEEP, z, MomentSpace.FAITH, t, d)
            for ((m, peak) in Peaks) for (d in themes) {
                out += Case("unveil", m, z, e, peak, d)
                out += Case("unveil", m, z, e, null, d)
            }
            for ((m, peak) in Peaks) out += Case("nas", m, CompanionForm.NAS, e, peak, false)
            for (d in themes) {
                out += Case("nod", LabMoment.NOD, z, e, 300, d)
                out += Case("nod", LabMoment.DAWN_QUIET, z, MomentSpace.FAITH, 920, d)
            }
            // Z-32: the Wren and Page takes. First Keep 0.7, 1.5, 3.0 (Wren's perch, Page's mini) and 3.6 s, and the five Unveil peaks, Paper.
            for (form in listOf(CompanionForm.WREN, CompanionForm.PAGE)) {
                val set = form.name.lowercase()
                for (t in listOf(700, 1500, 3000, 3600)) out += Case(set, LabMoment.FIRST_KEEP, form, e, t, false)
                for ((m, peak) in Peaks) out += Case(set, m, form, e, peak, false)
            }
            for (d in themes) {
                out += Case("reduced", LabMoment.FIRST_KEEP, z, e, null, d, reduced = true)
                out += Case("reduced", LabMoment.LIFT, z, e, null, d, reduced = true)
                out += Case("reduced", LabMoment.CARD_REVEAL, z, e, null, d, reduced = true)
            }
            return out.map { arrayOf<Any>(it) }
        }
    }
}
