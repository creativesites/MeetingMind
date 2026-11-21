package com.craftflowtechnologies.meetingmind.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.feature.circles2.AccountUiState
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "You're all set" step, drawn as onboarding draws it (always light, the dark app theme outside). Images land in
 * build/outputs/roborazzi/onboarding_ready_<name>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class OnboardingReadyScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val gb = 1_000_000_000L

    private fun shot(
        name: String,
        setup: SetupChoice = SetupChoice.INTERNET,
        companion: CompanionForm? = CompanionForm.ZURI,
        userName: String = "Ana",
        spaces: Set<NotebookSpace> = OnboardingSpaces.toSet(),
        pack: PhonePack = PhonePack(13 * gb / 10, 24 * gb),
        account: AccountUiState = AccountUiState(),
        darkOutside: Boolean = true
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                MeetMindTheme(darkTheme = darkOutside) {
                    AlwaysLight { ReadyPage(companion, userName, spaces, setup, pack, account) }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_ready_$name.png")
    }

    @Composable
    private fun ReadyPage(companion: CompanionForm?, userName: String, spaces: Set<NotebookSpace>, setup: SetupChoice, pack: PhonePack, account: AccountUiState) {
        Box(Modifier.fillMaxSize()) {
            LightBackdrop()
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MM.space.xl)) {
                    ReadyStep(companion, userName, spaces, setup, pack, account, onLink = {}, onSwitchAccount = {}, onDismissConflict = {})
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = MM.space.xl).padding(top = MM.space.m, bottom = MM.space.l),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ReadyActions(readyActionsFor(setup, phoneReady = pack.ready)) { }
                }
            }
        }
    }

    @Test fun `internet with a name and every space`() {
        shot("internet")
        compose.onNodeWithText("You're all set, Ana").assertExists()
        compose.onNodeWithTag("onboarding_ready_record").assertExists()
        compose.onNodeWithTag("onboarding_ready_youtube").assertExists()
        compose.onNodeWithTag("onboarding_ready_google").assertExists()
    }

    @Test fun `on this phone leads with the download and shows the room it leaves`() {
        shot("on_device", setup = SetupChoice.ON_DEVICE, spaces = setOf(NotebookSpace.FAITH, NotebookSpace.PERSONAL))
        compose.onNodeWithTag("onboarding_ready_set_up_phone").assertExists()
        compose.onNodeWithTag("onboarding_ready_youtube").assertDoesNotExist()
    }

    @Test fun `on this phone with everything already installed offers no download`() {
        shot("on_device_ready", setup = SetupChoice.ON_DEVICE, pack = PhonePack(0L, 24 * gb))
        compose.onNodeWithTag("onboarding_ready_set_up_phone").assertDoesNotExist()
        compose.onNodeWithTag("onboarding_ready_record").assertExists()
    }

    @Test fun `on this phone short of space says so`() {
        shot("on_device_tight", setup = SetupChoice.ON_DEVICE, pack = PhonePack(13 * gb / 10, 15 * gb / 10))
        compose.onNodeWithTag("onboarding_ready_set_up_phone").assertExists()
    }

    @Test fun `no name and no companion`() {
        shot("plain", companion = null, userName = "")
        compose.onNodeWithText("You're all set").assertExists()
    }

    @Test fun `signed in with Google`() {
        shot("signed_in", account = AccountUiState(linked = true, email = "ana@example.com"))
        compose.onNodeWithTag("onboarding_ready_google").assertDoesNotExist()
    }

    @Test fun `sign-in unavailable says why`() {
        shot("sign_in_unavailable", account = AccountUiState(message = "Google sign-in isn't set up in this version of the app yet."))
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi", sdk = [34])
    fun `a small phone keeps the first action in view`() {
        shot("small_320")
        compose.onNodeWithTag("onboarding_ready_record").assertExists()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34], fontScale = 2.0f)
    fun `twice the text size still reads`() {
        shot("font_200")
        compose.onNodeWithTag("onboarding_ready_summary").assertExists()
    }
}
