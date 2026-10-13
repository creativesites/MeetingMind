package com.craftflowtechnologies.meetingmind.feature.onboarding

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    /** DataStore outlives a test in this JVM: start every test from a fresh, unfinished onboarding. */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    @org.junit.Before fun freshOnboarding() {
        val prefs = UserPreferencesManager(app)
        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            prefs.setOnboardingCompleted(false)
            prefs.saveOnboardingProgress(0, OnboardingStep.WELCOME.name, null, emptySet(), emptySet())
        }
        // The main looper is idled while waiting, in case the store needs it.
        repeat(300) { if (!job.isCompleted) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(10) } }
    }

    @Test fun `walks through every step and remembers the choices`() {
        val vm = OnboardingViewModel(app)
        var finished = false
        compose.setContent {
            MeetMindTheme {
                // The companion's idle loop never settles, so the walk-through draws it static on Canvas.
                CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                    OnboardingScreen(vm, onFinishOnboarding = { finished = true })
                }
            }
        }
        val shots = listOf("welcome", "companion", "sample", "goals", "setup", "bible", "plan", "permissions")
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
            repeat(50) { if (!finished) { org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS); Thread.sleep(50); compose.waitForIdle() } }
            val prefs = UserPreferencesManager(app).preferencesFlow.first()
            assertTrue(finished && prefs.onboardingCompleted)
            assertEquals("Ana", prefs.userName)
            assertEquals(ProcessingProfile.INTERNET, prefs.processingProfile)
            // The reading step was skipped: nothing is written and the devotional stays on Morning and Evening.
            val profile = UserPreferencesManager(app).devotionalProfile.first()
            assertEquals(com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSource.CLASSIC, profile.source)
            assertEquals("morning-and-evening", profile.planId)
        }
    }

    @Test fun `record now skips the sample and finishes through the recorder`() {
        val vm = OnboardingViewModel(app)
        var recorded = false
        var finished = false
        compose.setContent { Static { OnboardingScreen(vm, onFinishOnboarding = { finished = true }, onRecordNow = { recorded = true }) } }
        compose.waitForIdle()
        compose.onNodeWithTag("onboarding_record_now_btn").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        // Step 1 on both routes: who keeps you company.
        assertEquals(OnboardingStep.COMPANION, vm.step.value)
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(OnboardingStep.SETUP, vm.step.value)
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        runBlocking { repeat(50) { if (!recorded) { Thread.sleep(50); compose.waitForIdle() } } }
        assertTrue(recorded && !finished)
    }

    @Test fun `the sample is reachable offline and ends with notes and an answer`() {
        val vm = OnboardingViewModel(app)
        compose.setContent { Static { OnboardingScreen(vm, onFinishOnboarding = { }) } }
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(OnboardingStep.COMPANION, vm.step.value)
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        compose.waitForIdle()
        // The transcript streams in on a timer; skip ahead past the last line.
        compose.mainClock.advanceTimeBy(30_000)
        compose.waitForIdle()
        compose.onNodeWithTag("sample_summary").assertExists()
        compose.onNodeWithTag("sample_question").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sample_answer").assertExists()
    }

    @Test fun `internet mode is the default, with the offline pack sized for this phone`() {
        val vm = OnboardingViewModel(app)
        assertEquals(SetupChoice.INTERNET, vm.setup.value)
        assertEquals(3, vm.pack.size)
        assertTrue(vm.packBytes > 500_000_000L)
    }

    /** The companion's idle loop never settles, so these draw it static on Canvas. */
    @androidx.compose.runtime.Composable
    private fun Static(content: @androidx.compose.runtime.Composable () -> Unit) {
        MeetMindTheme {
            CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true, content = content)
        }
    }
}
