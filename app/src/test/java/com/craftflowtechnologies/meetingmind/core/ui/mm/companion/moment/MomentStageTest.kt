package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stage's contract (Z-28): non-consuming skip, Back, haptics, the clock stopping, one announcement. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class MomentStageTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val firstKeep = MomentPlan.FirstKeep(quiet = false, hello = false, onceEver = true, chime = false, space = MomentSpace.EVERYDAY)
    private val note = FirstKeepNote("Lunch with Grace: plans for June", "Note · 18 min", listOf("Book the cabin", "Grace brings the van", "Keep it under 900"))
    private var opened = 0
    private var view: View? = null

    private fun overlay(plan: MomentPlan.FirstKeep = firstKeep) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MeetMindTheme(darkTheme = false, accent = MMAccent.Indigo) {
                CompositionLocalProvider(LocalCompanionReducedMotion provides false, LocalCompanionForceCanvas provides true) {
                    view = LocalView.current
                    FirstKeepOverlay(
                        plan = plan, form = CompanionForm.ZURI, note = note,
                        headline = "Your first note is ready.", subline = "Everything you said, kept.",
                        openLabel = "Open note", shareLabel = "Share a moment",
                        announcement = "Your first note is ready. ${note.title}.",
                        onOpen = { opened++ }, onShare = {}, sounds = false
                    )
                }
            }
        }
    }

    private val liveRegions = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    @Test fun `a tap during the moment reaches the button under it`() {
        overlay()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Open note").performClick()
        rule.mainClock.advanceTimeBy(32)
        assertEquals("the tap opened the note mid-moment", 1, opened)
    }

    @Test fun `haptics fire on the beat, at most two, and none after a skip`() {
        overlay()
        rule.mainClock.advanceTimeBy(900)
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, shadowOf(view).lastHapticFeedbackPerformed())
        rule.mainClock.advanceTimeBy(800)
        assertEquals(HapticFeedbackConstants.CONFIRM, shadowOf(view).lastHapticFeedbackPerformed())
    }

    @Test fun `no haptic after a skip`() {
        overlay()
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("Share a moment").performClick()
        rule.mainClock.advanceTimeBy(4000)
        assertEquals(-1, shadowOf(view).lastHapticFeedbackPerformed())
    }

    @Test fun `faith is silent - no haptics`() {
        overlay(firstKeep.copy(quiet = true, space = MomentSpace.FAITH))
        rule.mainClock.advanceTimeBy(5000)
        assertEquals(-1, shadowOf(view).lastHapticFeedbackPerformed())
    }

    @Test fun `announced once, then cleared`() {
        overlay()
        rule.mainClock.advanceTimeBy(100)
        rule.onAllNodes(liveRegions).fetchSemanticsNodes().let { assertEquals(1, it.size) }
        rule.mainClock.advanceTimeBy(AnnouncementMs + 500)
        assertEquals(0, rule.onAllNodes(liveRegions).fetchSemanticsNodes().size)
    }

    @Test fun `the unveil clock stops at its end, back skips, and the scope reads the end state`() {
        var scope: MomentScope? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MeetMindTheme(darkTheme = false, accent = MMAccent.Indigo) {
                CompositionLocalProvider(LocalCompanionReducedMotion provides false, LocalCompanionForceCanvas provides true) {
                    NoteUnveil(MomentPlan.Unveil(UnveilVariant.LIFT, 1500, false, MomentSpace.EVERYDAY), CompanionForm.ZURI, "Title is ready.") {
                        scope = this
                        Column {
                            Text("Title", Modifier.momentLayer(StageTarget.HEADER))
                            Button(onClick = { opened++ }, modifier = Modifier.momentLayer(StageTarget.BODY, 0)) { Text("Act") }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(400)
        val clock = scope!!.clock!!
        assertTrue(clock.playing)
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.mainClock.advanceTimeBy(300)
        assertFalse(clock.playing)
        assertTrue(clock.skipped)
        assertEquals(1500f, clock.timeMs)
        assertEquals(1f, scope!!.value(StageTarget.HEADER, StageProp.ALPHA, 0f))
        assertEquals(1f, scope!!.progress())
    }

    @Test fun `the clock runs to its end on its own and stops`() {
        var scope: MomentScope? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalCompanionReducedMotion provides false) {
                MeetMindTheme(darkTheme = true, accent = MMAccent.Indigo) {
                    NoteUnveil(MomentPlan.Nod(), CompanionForm.NAS, "Title is ready.") { scope = this; Text("Title", Modifier.momentLayer(StageTarget.HEADER)) }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        val clock = scope!!.clock!!
        assertFalse(clock.playing)
        assertFalse(clock.skipped)
        assertEquals(600f, clock.timeMs)
    }

    @Test fun `a plain plan shows the content as it is, with no clock`() {
        var scope: MomentScope? = null
        rule.setContent {
            MeetMindTheme(darkTheme = false) {
                NoteUnveil(null, CompanionForm.ZURI, "Title is ready.") { scope = this; Text("Title") }
            }
        }
        rule.onNodeWithText("Title").assertExists()
        assertEquals(null, scope!!.clock)
        assertEquals(0, rule.onAllNodes(liveRegions).fetchSemanticsNodes().size)
    }
}
