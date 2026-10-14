package com.craftflowtechnologies.meetingmind.feature.devotional

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.devotional.DailyDevotional
import com.craftflowtechnologies.meetingmind.core.devotional.Devotional
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalLabels
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalNotes
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalOrigin
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalTip
import com.craftflowtechnologies.meetingmind.core.devotional.LiturgicalCalendar
import com.craftflowtechnologies.meetingmind.core.devotional.LocalDay
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteDocument
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The plan reading on the devotional page: the quiet "Write one with AI" strip, and the one-time tip. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi", sdk = [34])
class DevotionalPlansScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 9, 24)

    private val plan = Devotional(
        day = LocalDay.of(date), origin = DevotionalOrigin.CLASSIC, title = "Morning, 24 September",
        scripture = listOf(ScriptureReferenceParser.parse("Psalm 46:1")!!), keyText = "“God is our refuge and strength, a very present help in trouble.”",
        reflection = listOf("When the ground gives way, the first thing to know is who holds it. A refuge is not a place we build; it is a place we run to."),
        label = "From Morning and Evening by C. H. Spurgeon (1865), public domain.", engine = "C. H. Spurgeon, Morning and Evening"
    )
    private val ai = plan.copy(origin = DevotionalOrigin.CLOUD_AI, title = "Rest in the middle of the work", label = DevotionalLabels.CLOUD, engine = "gemini", keyText = null)

    private fun daily(d: Devotional): DailyDevotional {
        val (blocks, refs) = DevotionalNotes.build("n1", d)
        val note = Note("n1", d.title, RecordingType.DEVOTIONAL, null, 0, 0, 0, false, false, NoteStatus.OPEN, null, DevotionalNotes.metadata(d))
        return DailyDevotional(note, d, NoteDocument(note, blocks, emptyList(), emptyList(), refs))
    }

    private fun state(d: Devotional, tip: Boolean = false) = DevotionalUiState(
        loading = false, today = daily(d), season = LiturgicalCalendar.dayOf(date), date = date, tipVisible = tip
    )

    private fun capture(state: DevotionalUiState, name: String, dark: Boolean, onWrite: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                DevotionalContent(state, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, liveScripture = false, onWriteWithAi = onWrite, onDismissTip = onDismiss)
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun `a plan reading with the quiet Write one with AI action, light`() = capture(state(plan), "devotional_plan_write_ai_light", dark = false)
    @Test fun `a plan reading with the quiet Write one with AI action, dark`() = capture(state(plan), "devotional_plan_write_ai_dark", dark = true)
    @Test fun `the one-time tip above it`() = capture(state(plan, tip = true), "devotional_plan_tip_light", dark = false)

    @Test fun `the action writes, and the tip can be dismissed`() {
        var wrote = 0; var dismissed = 0
        capture(state(plan, tip = true), "devotional_plan_tip_dark", dark = true, onWrite = { wrote++ }, onDismiss = { dismissed++ })
        compose.onNodeWithTag("devotional_write_ai").performClick()
        assertEquals(1, wrote)
        compose.onNodeWithTag("devotional_ai_tip_dismiss").performClick()
        assertEquals(1, dismissed)
    }

    @Test fun `an AI devotional is named as written with AI`() {
        assertEquals("Morning and Evening", DevotionalTip.readingName(plan))
        assertEquals("Written with AI", DevotionalTip.readingName(ai))
        assertNull(DevotionalTip.readingName(plan.copy(origin = DevotionalOrigin.MINE)))
    }

    @Test fun `the tip shows on a plan reading until it is dismissed, then never again`() = runBlocking {
        val prefs = UserPreferencesManager(ApplicationProvider.getApplicationContext())
        assertFalse(prefs.devotionalTipDismissed.first())
        assertTrue(DevotionalTip.visible(prefs.devotionalTipDismissed.first(), past = false, shown = plan))
        // Not on an AI devotional or a past one.
        assertFalse(DevotionalTip.visible(false, past = false, shown = ai))
        assertFalse(DevotionalTip.visible(false, past = true, shown = plan))
        prefs.dismissDevotionalTip()
        assertTrue("the dismissal is stored", UserPreferencesManager(ApplicationProvider.getApplicationContext()).devotionalTipDismissed.first())
        assertFalse(DevotionalTip.visible(prefs.devotionalTipDismissed.first(), past = false, shown = plan))
    }
}
