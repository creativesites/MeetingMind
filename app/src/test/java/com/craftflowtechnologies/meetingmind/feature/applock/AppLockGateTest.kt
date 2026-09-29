package com.craftflowtechnologies.meetingmind.feature.applock

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.applock.AppLockAvailability
import com.craftflowtechnologies.meetingmind.core.applock.AppLockState
import com.craftflowtechnologies.meetingmind.core.applock.AuthResult
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class AppLockGateTest {
    @get:Rule val compose = createComposeRule()

    private val ready = mutableStateOf(true)
    private val state = mutableStateOf(AppLockState.Disabled)

    private fun showGate() = compose.setContent {
        MeetMindTheme {
            AppLockGateSwitch(ready.value, state.value, lockScreen = { Text("LOCK SCREEN") }, content = { Text("Private transcript") })
        }
    }

    @Test fun `disabled shows the app`() {
        showGate()
        compose.onNodeWithText("Private transcript").assertIsDisplayed()
    }

    @Test fun `unlocked shows the app`() {
        state.value = AppLockState.Unlocked
        showGate()
        compose.onNodeWithText("Private transcript").assertIsDisplayed()
    }

    @Test fun `locked and unlocking never compose private content`() {
        state.value = AppLockState.Locked
        showGate()
        compose.onNodeWithText("LOCK SCREEN").assertIsDisplayed()
        compose.onNodeWithText("Private transcript").assertDoesNotExist()
        state.value = AppLockState.Unlocking
        compose.waitForIdle()
        compose.onNodeWithText("LOCK SCREEN").assertIsDisplayed()
        compose.onNodeWithText("Private transcript").assertDoesNotExist()
    }

    @Test fun `nothing is shown until the preference is known`() {
        ready.value = false
        showGate()
        compose.onNodeWithText("Private transcript").assertDoesNotExist()
        compose.onNodeWithText("LOCK SCREEN").assertDoesNotExist()
    }

    @Test fun `content disappears when the lock engages and returns on unlock`() {
        state.value = AppLockState.Unlocked
        showGate()
        compose.onNodeWithText("Private transcript").assertIsDisplayed()
        state.value = AppLockState.Locked
        compose.waitForIdle()
        compose.onNodeWithText("Private transcript").assertDoesNotExist()
        state.value = AppLockState.Unlocked
        compose.waitForIdle()
        compose.onNodeWithText("Private transcript").assertIsDisplayed()
    }

    @Test fun `lock screen has an unlock button and reports taps`() {
        var taps = 0
        compose.setContent { MeetMindTheme { AppLockContent(AppLockAvailability.Available, null, { taps++ }, {}, {}) } }
        compose.onNodeWithText("MeetingMind is locked").assertIsDisplayed()
        compose.onNodeWithTag("app_lock_unlock").performClick()
        assertEquals(1, taps)
    }

    @Test fun `lock screen explains a phone with no screen lock and offers a way out`() {
        var off = 0
        compose.setContent { MeetMindTheme { AppLockContent(AppLockAvailability.NoneEnrolled, null, {}, {}, { off++ }) } }
        compose.onNodeWithTag("app_lock_notice").assertIsDisplayed()
        compose.onNodeWithTag("app_lock_turn_off").performClick()
        assertEquals(1, off)
        compose.onNodeWithTag("app_lock_unlock").assertDoesNotExist()
    }

    @Test fun `lock screen explains a temporary lockout and still offers unlock`() {
        compose.setContent { MeetMindTheme { AppLockContent(AppLockAvailability.Available, AuthResult.LockedOut(false), {}, {}, {}) } }
        compose.onNodeWithTag("app_lock_notice").assertIsDisplayed()
        compose.onNodeWithTag("app_lock_unlock").assertIsDisplayed()
    }
}
