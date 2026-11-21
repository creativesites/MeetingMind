package com.craftflowtechnologies.meetingmind.feature.recording

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A recording that hears nothing for a while says so, and stops saying so the moment sound returns. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SilenceWatchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longSilenceWhileRecordingIsNoticed() {
        val watch = SilenceWatch()
        assertFalse(watch.update(0f, recording = true, nowMs = 0))
        assertFalse(watch.update(0.005f, recording = true, nowMs = SilenceWatch.QuietForMs - 1))
        assertTrue(watch.update(0f, recording = true, nowMs = SilenceWatch.QuietForMs))
    }

    @Test fun anySoundResetsTheClock() {
        val watch = SilenceWatch()
        watch.update(0f, recording = true, nowMs = 0)
        assertFalse(watch.update(0.2f, recording = true, nowMs = 20_000))
        assertFalse(watch.update(0f, recording = true, nowMs = 30_000))
        assertTrue(watch.update(0f, recording = true, nowMs = 30_000 + SilenceWatch.QuietForMs))
    }

    @Test fun aPauseIsNotSilence() {
        val watch = SilenceWatch()
        watch.update(0f, recording = true, nowMs = 0)
        assertFalse(watch.update(0f, recording = false, nowMs = SilenceWatch.QuietForMs * 2))
        assertFalse(watch.update(0f, recording = true, nowMs = SilenceWatch.QuietForMs * 2 + 1))
    }

    @Test fun theCueAppearsAfterSilenceAndClearsWhenSoundReturns() {
        var level = 0f
        compose.mainClock.autoAdvance = false
        compose.setContent { MeetMindTheme { SilenceCue(recording = true, level = { level }) } }
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("record_silence_cue").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(SilenceWatch.QuietForMs + 1_000)
        compose.onNodeWithTag("record_silence_cue").assertExists()
        level = 0.3f
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("record_silence_cue").assertDoesNotExist()
    }

    @Test fun noCueWhilePaused() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MeetMindTheme { SilenceCue(recording = false, level = { 0f }) } }
        compose.mainClock.advanceTimeBy(SilenceWatch.QuietForMs * 2)
        compose.onNodeWithTag("record_silence_cue").assertDoesNotExist()
    }
}
