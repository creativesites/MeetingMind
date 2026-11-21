package com.craftflowtechnologies.meetingmind.feature.recording

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first-recording coach: three tips at the right moments, shown on a first recording only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class FirstRecordingCoachTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun tipsFollowTheRecording() {
        assertEquals(CoachTip.PLACEMENT, FirstRecordingCoachPolicy.tipAt(0L, paused = false))
        assertEquals(CoachTip.PLACEMENT, FirstRecordingCoachPolicy.tipAt(19_999L, paused = false))
        assertEquals(CoachTip.PLACEMENT, FirstRecordingCoachPolicy.tipAt(5_000L, paused = true))
        assertEquals(CoachTip.MARK, FirstRecordingCoachPolicy.tipAt(20_000L, paused = false))
        assertEquals(CoachTip.FINISH, FirstRecordingCoachPolicy.tipAt(30_000L, paused = true))
        assertEquals(CoachTip.FINISH, FirstRecordingCoachPolicy.tipAt(120_000L, paused = false))
    }

    @Test fun onlyTheLastTipCompletesCoaching() {
        assertFalse(FirstRecordingCoachPolicy.completesCoaching(CoachTip.PLACEMENT))
        assertFalse(FirstRecordingCoachPolicy.completesCoaching(CoachTip.MARK))
        assertTrue(FirstRecordingCoachPolicy.completesCoaching(CoachTip.FINISH))
    }

    @Test fun showsPlacementThenMarkAsTheRecordingRuns() {
        var recorded by mutableLongStateOf(0L)
        compose.setContent { MeetMindTheme { FirstRecordingCoach(recordedMs = recorded, paused = false) } }
        compose.onNodeWithTag("first_recording_tip_placement").assertExists()
        recorded = 30_000L
        compose.waitForIdle()
        compose.onNodeWithTag("first_recording_tip_mark").assertExists()
        compose.onNodeWithTag("first_recording_tip_placement").assertDoesNotExist()
        assertFalse(FirstRecordingCoachStore.isDone(context))
    }

    @Test fun gotItEndsCoachingForGood() {
        compose.setContent { MeetMindTheme { FirstRecordingCoach(recordedMs = 0L, paused = false) } }
        compose.onNodeWithTag("first_recording_got_it").performClick()
        compose.onNodeWithTag("first_recording_coach").assertDoesNotExist()
        assertTrue(FirstRecordingCoachStore.isDone(context))
    }

    @Test fun reachingTheLastTipCountsAsCoached() {
        compose.setContent { MeetMindTheme { FirstRecordingCoach(recordedMs = 150_000L, paused = false) } }
        compose.onNodeWithTag("first_recording_tip_finish").assertExists()
        compose.waitForIdle()
        assertTrue(FirstRecordingCoachStore.isDone(context))
    }

    @Test fun aCoachedPersonSeesNothing() {
        FirstRecordingCoachStore.markDone(context)
        compose.setContent { MeetMindTheme { FirstRecordingCoach(recordedMs = 0L, paused = false) } }
        compose.onNodeWithTag("first_recording_coach").assertDoesNotExist()
    }
}
