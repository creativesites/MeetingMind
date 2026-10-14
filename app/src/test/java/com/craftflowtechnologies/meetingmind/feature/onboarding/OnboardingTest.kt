package com.craftflowtechnologies.meetingmind.feature.onboarding

import android.app.Application
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
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
        val shots = listOf("welcome", "sample", "goals", "setup", "bible", "permissions")
        shots.forEachIndexed { i, name ->
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            if (name == "goals") {
                compose.onNodeWithTag("goal_SERMONS").performClick()
                compose.onNodeWithTag("outcome_SUMMARIES").performClick()
                compose.onNodeWithTag("onboarding_name_field").performTextInput("Ana")
            }
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

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi", sdk = [34])
    fun `welcome pills stay readable on a narrow screen`() {
        val vm = OnboardingViewModel(app)
        compose.setContent { MeetMindTheme { OnboardingScreen(vm) { } } }
        compose.waitForIdle()
        listOf("welcome_pill_private", "welcome_pill_offline", "welcome_pill_ai").forEach { tag ->
            val node = compose.onNodeWithTag(tag)
            node.assertIsDisplayed()
            // A squeezed pill is a tall sliver; a readable one is wider than it is tall.
            val b = node.getUnclippedBoundsInRoot()
            assertTrue("$tag is ${b.width} x ${b.height}", b.width > b.height)
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_welcome_320.png")
    }

    @Test fun `record now skips the sample and finishes through the recorder`() {
        val vm = OnboardingViewModel(app)
        var recorded = false
        var finished = false
        compose.setContent { MeetMindTheme { OnboardingScreen(vm, onFinishOnboarding = { finished = true }, onRecordNow = { recorded = true }) } }
        compose.waitForIdle()
        compose.onNodeWithTag("onboarding_record_now_btn").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(OnboardingStep.SETUP, vm.step.value)
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        runBlocking { repeat(50) { if (!recorded) { Thread.sleep(50); compose.waitForIdle() } } }
        assertTrue(recorded && !finished)
    }

    @Test fun `the sample is reachable offline and ends with notes and an answer`() {
        val vm = OnboardingViewModel(app)
        compose.setContent { MeetMindTheme { OnboardingScreen(vm) { } } }
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        compose.waitForIdle()
        // The transcript streams in on a timer; skip ahead past the last line.
        compose.mainClock.advanceTimeBy(30_000)
        compose.waitForIdle()
        compose.onNodeWithTag("sample_summary").assertExists()
        compose.onNodeWithTag("sample_question").performClick()
        compose.onNodeWithTag("sample_answer").assertExists()
    }

    @Test fun `internet mode is the default, with the offline pack sized for this phone`() {
        val vm = OnboardingViewModel(app)
        assertEquals(SetupChoice.INTERNET, vm.setup.value)
        assertEquals(3, vm.pack.size)
        assertTrue(vm.packBytes > 500_000_000L)
    }
}
