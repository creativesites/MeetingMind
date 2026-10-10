package com.craftflowtechnologies.meetingmind.feature.recording

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.audio.RecordingState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.Presence
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Z-8: the recording screen with a Listening and a Quiet companion, Paper and Graphite. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class RecordingCompanionScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val compose = createComposeRule()

    private fun capture(type: RecordingType, settings: CompanionSettings, name: String) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalCompanionSettingsSource provides FakeCompanionSettings(settings),
                    LocalCompanionReducedMotion provides true,
                    LocalCompanionForceCanvas provides true
                ) {
                    LiveRecordingSurface(
                        type = type, title = type.displayName, hasPermission = true, state = RecordingState.RECORDING,
                        amplitude = 0.4f, durationMs = 754_000, capacityWarning = null,
                        onRequestPermission = {}, onDiscard = {}, onToggle = {}, onFinish = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/recording/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun listening() {
        capture(RecordingType.MEETING, CompanionSettings(), "listening")
        compose.onNodeWithTag("record_companion").assertIsDisplayed()
    }

    @Test fun quietSermon() {
        capture(RecordingType.SERMON, CompanionSettings(), "quiet")
        compose.onNodeWithTag("record_quiet_chip").assertIsDisplayed()
    }

    @Test fun presenceOffDrawsNothing() {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings(presence = Presence.OFF))) {
                    LiveRecordingSurface(
                        type = RecordingType.SERMON, title = "Sermon", hasPermission = true, state = RecordingState.RECORDING,
                        amplitude = 0f, durationMs = 0, capacityWarning = null,
                        onRequestPermission = {}, onDiscard = {}, onToggle = {}, onFinish = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("record_companion").assertDoesNotExist()
    }
}
