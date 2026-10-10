package com.craftflowtechnologies.meetingmind.feature.onboarding

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Z-15: the "Pick who keeps you company" step. Screenshots draw on Canvas with static poses. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CompanionPickStepTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `Zuri is pre-selected and Continue alone keeps her`() {
        val fake = FakeCompanionSettings()
        val vm = OnboardingViewModel(app, fake)
        assertEquals(CompanionForm.ZURI, vm.companion.value)
        vm.commitCompanion()
        compose.waitForIdle()
        Thread.sleep(200)
        assertEquals(CompanionForm.ZURI, fake.state.value.form)
    }

    @Test fun `a pick is saved on Continue`() {
        val fake = FakeCompanionSettings()
        val vm = OnboardingViewModel(app, fake)
        vm.setCompanion(CompanionForm.NAS)
        assertEquals(CompanionForm.ZURI, fake.state.value.form) // not yet
        vm.commitCompanion()
        awaitForm(fake, CompanionForm.NAS)
    }

    @Test fun `No companion is saved as no form`() {
        val fake = FakeCompanionSettings()
        val vm = OnboardingViewModel(app, fake)
        vm.setCompanion(null)
        assertNull(vm.companion.value)
        vm.commitCompanion()
        awaitForm(fake, null)
    }

    @Test fun `finishing onboarding saves the pick too`() {
        val fake = FakeCompanionSettings()
        val vm = OnboardingViewModel(app, fake)
        vm.setCompanion(CompanionForm.WREN)
        vm.completeOnboarding { }
        awaitForm(fake, CompanionForm.WREN)
    }

    @Test fun `tapping a pick selects it`() {
        val vm = OnboardingViewModel(app, FakeCompanionSettings())
        var picked: CompanionForm? = CompanionForm.ZURI
        compose.setContent {
            Harness(false) { CompanionPickStep(picked, { picked = it }) }
        }
        compose.onNodeWithTag("onboarding_companion_nas").performClick()
        assertEquals(CompanionForm.NAS, picked)
        compose.onNodeWithTag("onboarding_companion_none").performClick()
        assertNull(picked)
    }

    private fun awaitForm(fake: FakeCompanionSettings, expected: CompanionForm?) {
        repeat(60) { if (fake.state.value.form != expected) Thread.sleep(50) }
        assertEquals(expected, fake.state.value.form)
    }

    @androidx.compose.runtime.Composable
    private fun Harness(dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        MeetMindTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                Column(Modifier.fillMaxSize().background(Brand.Navy).padding(horizontal = 24.dp)) { content() }
            }
        }
    }

    private fun shot(selected: CompanionForm?, name: String, dark: Boolean) {
        compose.setContent {
            var current by androidx.compose.runtime.remember { mutableStateOf(selected) }
            Harness(dark) { CompanionPickStep(current, { current = it }) }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/onboarding/companion_step_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun zuri_light() = shot(CompanionForm.ZURI, "zuri", false)
    @Test fun zuri_dark() = shot(CompanionForm.ZURI, "zuri", true)
    @Test fun nas_light() = shot(CompanionForm.NAS, "nas", false)
    @Test fun nas_dark() = shot(CompanionForm.NAS, "nas", true)
    @Test fun none_light() = shot(null, "none", false)
    @Test fun none_dark() = shot(null, "none", true)
}
