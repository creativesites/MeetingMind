package com.craftflowtechnologies.meetingmind.feature.onboarding

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.datastore.AppPreferencesState
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

    /** A moments ledger of its own, so Zuri's once-ever hello is always still unused in a test. */
    private fun freshMoments() = com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore(
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        ) { java.io.File(app.cacheDir, "moments-${System.nanoTime()}.preferences_pb") }
    )

    /** The stored preferences once [ready] holds (writes land on Dispatchers.IO), or as they are after ~5 s. */
    private suspend fun awaitPrefs(ready: (AppPreferencesState) -> Boolean): AppPreferencesState {
        repeat(100) {
            val p = UserPreferencesManager(app).preferencesFlow.first()
            if (ready(p)) return p
            org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(50)
        }
        return UserPreferencesManager(app).preferencesFlow.first()
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(200) { if (!condition()) { Thread.sleep(25); org.robolectric.shadows.ShadowLooper.idleMainLooper(); compose.waitForIdle() } }
    }

    @Test fun `walks through every step and remembers the choices`() {
        val vm = OnboardingViewModel(app, com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsStore(app), freshMoments())
        var finished = false
        compose.setContent {
            MeetMindTheme {
                // The companion's idle loop never settles, so the walk-through draws it static on Canvas.
                CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                    OnboardingScreen(vm, onFinishOnboarding = { finished = true })
                }
            }
        }
        val shots = listOf("welcome", "companion", "spaces", "sample", "setup", "ready")
        shots.forEachIndexed { i, name ->
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            if (name == "companion") compose.onNodeWithTag("onboarding_name_field").performScrollTo().performTextInput("Ana")
            if (name == "spaces") {
                // All four start on; the person turns Personal off.
                assertEquals(OnboardingSpaces.toSet(), vm.spaces.value)
                compose.onNodeWithTag("space_PERSONAL").performScrollTo().performClick()
            }
            if (name == "setup") compose.onNodeWithTag("setup_choice_internet").performClick()
            compose.waitForIdle()
            if (name == "ready") {
                // You're set: the name, the summary and the optional Google sign-in, then the first actions.
                compose.onNodeWithText("You're all set, Ana").assertExists()
                compose.onNodeWithTag("onboarding_ready_summary").assertExists()
                compose.onNodeWithTag("onboarding_ready_google").assertExists()
                compose.onNodeWithTag("onboarding_ready_record").assertExists()
                compose.onNodeWithTag("onboarding_ready_youtube").assertExists()
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_${i}_$name.png")
            compose.onNodeWithTag(if (name == "ready") "onboarding_ready_look_around" else "onboarding_next_btn").performClick()
            if (name == "sample") {
                // Leaving the example early still plays Zuri's first reveal; closing it moves on.
                waitFor { vm.hello.value != null }
                assertTrue(vm.hello.value?.hello == true)
                compose.onNodeWithText("Open note").performClick()
                waitFor { vm.step.value == OnboardingStep.SETUP }
                assertEquals(OnboardingStep.SETUP, vm.step.value)
            }
        }
        compose.waitForIdle()
        runBlocking {
            repeat(200) { if (!finished) { org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS); Thread.sleep(50); compose.waitForIdle() } }
            val prefs = awaitPrefs { it.onboardingCompleted }
            assertTrue("finished=$finished completed=${prefs.onboardingCompleted} step=${vm.step.value} stage=${vm.finishStage}", finished && prefs.onboardingCompleted)
            assertEquals("Ana", prefs.userName)
            assertEquals(ProcessingProfile.INTERNET, prefs.processingProfile)
            assertEquals(OnboardingSpaces.toSet() - com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL, prefs.identity.spaces)
        }
    }

    @Test fun `on this phone is always saved, with large downloads waiting for Wi-Fi`() {
        val vm = OnboardingViewModel(app)
        vm.setSetup(SetupChoice.ON_DEVICE)
        var done = false
        vm.completeOnboarding { done = true }
        runBlocking {
            repeat(250) { if (!done) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(20) } }
            val prefs = awaitPrefs { it.onboardingCompleted }
            assertTrue("done=$done completed=${prefs.onboardingCompleted} stage=${vm.finishStage}", done && prefs.onboardingCompleted)
            assertEquals(ProcessingProfile.OFFLINE, prefs.processingProfile)
            assertTrue(prefs.wifiOnlyDownload)
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi", sdk = [34])
    fun `welcome pills stay readable on a narrow screen`() {
        val vm = OnboardingViewModel(app)
        compose.setContent { MeetMindTheme { OnboardingScreen(vm, onFinishOnboarding = { }) } }
        compose.waitForIdle()
        listOf("welcome_pill_private", "welcome_pill_offline", "welcome_pill_ai").forEach { tag ->
            val node = compose.onNodeWithTag(tag)
            node.assertIsDisplayed()
            // A squeezed pill is a tall sliver; a readable one is wider than it is tall.
            val b = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag is ${b.width} x ${b.height}", b.width > b.height)
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_welcome_320.png")
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
        // No permissions step: the recorder asks for the microphone, the processing screen for notifications.
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        repeat(200) { if (!recorded) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(25); compose.waitForIdle() } }
        assertTrue("recorded=$recorded finished=$finished step=${vm.step.value} route=${vm.route.value} stage=${vm.finishStage}", recorded && !finished)
    }

    @Test fun `the sample is reachable offline and ends with notes and an answer`() {
        val file = java.io.File(app.cacheDir, "moments-${System.nanoTime()}.preferences_pb")
        val store = com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
            ) { file }
        )
        val vm = OnboardingViewModel(app, com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsStore(app), store)
        compose.setContent { Static { OnboardingScreen(vm, onFinishOnboarding = { }) } }
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(OnboardingStep.COMPANION, vm.step.value)
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(OnboardingStep.SPACES, vm.step.value)
        compose.onNodeWithTag("onboarding_next_btn").performClick()
        compose.waitForIdle()
        // The transcript streams in on a timer; skip ahead past the last line.
        compose.mainClock.advanceTimeBy(30_000)
        compose.waitForIdle()
        // The example's notes arrive with the hello First Keep over them; Open note shows the example underneath.
        // The plan is made on a background thread and lands on the main looper.
        repeat(200) { if (vm.hello.value == null) { Thread.sleep(25); org.robolectric.shadows.ShadowLooper.idleMainLooper(); compose.waitForIdle() } }
        assertTrue(vm.hello.value?.hello == true)
        compose.onNodeWithText("Here's what a note looks like.").assertExists()
        compose.onNodeWithText("Open note").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sample_summary").assertExists()
        compose.onNodeWithTag("sample_question").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sample_answer").assertExists()
    }

    @Test fun `internet mode is the default, with the offline pack sized for this phone`() {
        val vm = OnboardingViewModel(app)
        assertEquals(SetupChoice.INTERNET, vm.setup.value)
        assertTrue(vm.phonePack.value.bytes > 500_000_000L)
    }

    /** The companion's idle loop never settles, so these draw it static on Canvas. */
    @androidx.compose.runtime.Composable
    private fun Static(content: @androidx.compose.runtime.Composable () -> Unit) {
        MeetMindTheme {
            CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true, content = content)
        }
    }
}
