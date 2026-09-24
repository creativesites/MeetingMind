package com.example.feature.onboarding

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.ProcessingProfile
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class OnboardingTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `walks through every step and remembers the choices`() {
        val vm = OnboardingViewModel(app)
        var finished = false
        compose.setContent { MeetMindTheme { OnboardingScreen(vm) { finished = true } } }
        val shots = listOf("welcome", "what", "name", "spaces", "setup", "permissions")
        shots.forEachIndexed { i, name ->
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            if (name == "name") compose.onNodeWithTag("onboarding_name_field").performTextInput("Ana")
            if (name == "setup") compose.onNodeWithTag("setup_choice_internet").performClick()
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_${i}_$name.png")
            compose.onNodeWithTag("onboarding_next_btn").performClick()
        }
        compose.waitForIdle()
        runBlocking {
            repeat(50) { if (!finished) { Thread.sleep(50); compose.waitForIdle() } }
            val prefs = UserPreferencesManager(app).preferencesFlow.first()
            assertTrue(finished && prefs.onboardingCompleted)
            assertEquals("Ana", prefs.userName)
            assertEquals(ProcessingProfile.INTERNET, prefs.processingProfile)
        }
    }

    @Test fun `the offline pack is the recommended default, sized for this phone`() {
        val vm = OnboardingViewModel(app)
        assertEquals(SetupChoice.OFFLINE_PACK, vm.setup.value)
        assertEquals(3, vm.pack.size)
        assertTrue(vm.packBytes > 500_000_000L)
    }
}
