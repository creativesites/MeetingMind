package com.craftflowtechnologies.meetingmind.feature.recording

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.audio.RecordingState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.work.Mark
import com.craftflowtechnologies.meetingmind.core.work.MarkKind
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R-1: the redesigned recording screen for a sermon and a meeting, in Paper and Graphite. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class RecordingScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val compose = createComposeRule()
    private val taps = mutableListOf<Pair<String, String?>>()

    private fun show(type: RecordingType, marks: List<Mark>, state: RecordingState = RecordingState.RECORDING) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings()),
                    LocalCompanionReducedMotion provides true,
                    LocalCompanionForceCanvas provides true
                ) {
                    LiveRecordingSurface(
                        type = type, title = if (type == RecordingType.SERMON) "Sunday service" else "Acme weekly sync", hasPermission = true,
                        state = state, amplitude = 0.4f, durationMs = 2_714_000, capacityWarning = null,
                        onRequestPermission = {}, onDiscard = {}, onToggle = {}, onFinish = {},
                        marks = marks, onAction = { a, t -> taps += a.id to t }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/recording/${name}_${if (dark) "dark" else "light"}.png")

    @Test fun sermon() {
        show(RecordingType.SERMON, listOf(Mark(MarkKind.KEY, 612_000), Mark(MarkKind.SCRIPTURE, 1_204_000, "Ephesians 2:8"), Mark(MarkKind.NOTE, 1_890_000, "Ask Sam")))
        listOf("highlight", "scripture", "note", "prayer").forEach { compose.onNodeWithTag("mark_$it").assertIsDisplayed() }
        compose.onNodeWithTag("mark_strip").assertIsDisplayed()
        shot("sermon")
    }

    @Test fun meeting() {
        show(RecordingType.MEETING, listOf(Mark(MarkKind.DECISION, 420_000), Mark(MarkKind.ACTION, 1_100_000)))
        listOf("decision", "action", "question", "note").forEach { compose.onNodeWithTag("mark_$it").assertIsDisplayed() }
        shot("meeting")
    }

    @Test fun pausedHasNoMarkersYetAndNoStrip() {
        show(RecordingType.LECTURE, emptyList(), RecordingState.PAUSED)
        compose.onNodeWithTag("mark_strip").assertDoesNotExist()
        compose.onNodeWithTag("mark_important").assertIsDisplayed()
    }

    @Test fun aOneTapButtonMarksImmediately() {
        show(RecordingType.SERMON, emptyList())
        compose.onNodeWithTag("mark_highlight").performClick()
        assertEquals(listOf("highlight" to null), taps)
    }

    @Test fun aButtonWithInputOpensASheetFirst() {
        show(RecordingType.SERMON, emptyList())
        compose.onNodeWithTag("mark_note").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("marker_input").assertIsDisplayed()
        assertEquals(emptyList<Pair<String, String?>>(), taps)
    }
}
