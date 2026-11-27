package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotRecipe
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenPlace
import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenTheme
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import com.github.takahirom.roborazzi.captureRoboImage
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.DisabledBillingGateway
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The garden's places and the Sanctuary pages they open: each place is reachable from the garden home,
 * the privacy page and the autopilot card are restyled to the garden and keep their gates, and the
 * screens are recorded in light and dark. Record with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class GardenPagesScreenshotTest {
    @get:Rule val rule = createComposeRule()

    /** Billing is not configured in these tests, whatever another test in this JVM left behind. */
    @Before fun billingNotConfigured() {
        Billing.gateway = DisabledBillingGateway
    }

    private fun homeState() = PersonalUiState(
        phase = GardenPhase.DAY, whenLine = "Thursday morning", questionLabel = "TODAY'S QUESTION",
        question = "What is one thing you can finish before lunch?", lights = 0, plants = emptyList(), ideas = 0, loaded = true
    )

    private fun privacy(dark: Boolean) {
        rule.setContent {
            MMPreviewFrame(dark) {
                GardenTheme(GardenPhase.DUSK) {
                    PrivacySyncPage(
                        lockEnabled = true, recentsShieldEnabled = true,
                        obsidianVaultLabel = "Journal vault", lastSyncLabel = "Nov 27, 9:14 AM",
                        onToggleLock = {}, onToggleRecentsShield = {}, onLockNow = {},
                        onPickObsidianFolder = {}, onSyncObsidianNow = {}, onImportDayOneJson = {}
                    )
                }
            }
        }
        rule.onNodeWithTag("garden_privacy_page").assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/privacy_sync_${if (dark) "dark" else "light"}.png")
    }

    private fun autopilot(dark: Boolean, unlocked: Boolean) {
        rule.setContent {
            MMPreviewFrame(dark) {
                GardenTheme(GardenPhase.DAY) {
                    PersonalAutopilotCard(unlocked, isAutopilotRunning = false, onRunPersonalAutopilot = {}, onOpenPaywall = {})
                }
            }
        }
        rule.onNodeWithText("PERSONAL AUTOPILOT").assertIsDisplayed()
        val name = if (unlocked) "open" else "locked"
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/autopilot_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun privacy_page_light() = privacy(false)
    @Test fun privacy_page_dark() = privacy(true)
    @Test fun autopilot_locked_light() = autopilot(false, unlocked = false)
    @Test fun autopilot_open_dark() = autopilot(true, unlocked = true)

    @Test fun `a locked autopilot does not run a recipe`() {
        var ran = 0
        rule.setContent {
            MMPreviewFrame(false) { GardenTheme(GardenPhase.DAY) { PersonalAutopilotCard(false, false, { ran++ }, {}) } }
        }
        rule.onNodeWithText(AutopilotRecipe.PERSONAL_RECIPES.first().label).performClick()
        assertEquals(0, ran)
    }

    @Test fun `an unlocked autopilot runs the recipe`() {
        var ran = 0
        rule.setContent {
            MMPreviewFrame(false) { GardenTheme(GardenPhase.DAY) { PersonalAutopilotCard(true, false, { ran++ }, {}) } }
        }
        rule.onNodeWithText(AutopilotRecipe.PERSONAL_RECIPES.first().label).performClick()
        assertEquals(1, ran)
    }

    @Test fun `every garden place opens its page from the garden home`() {
        val opened = mutableListOf<GardenPlace>()
        rule.setContent {
            MMPreviewFrame(false) {
                PersonalHomeScreen(
                    homeState(), animate = false, onAnswerAloud = {}, onWriteAnswer = {}, onOpenPlant = {}, onPlantNew = {},
                    onOpenPlace = { opened += it }, onCaptureIdea = {}
                )
            }
        }
        GardenPlace.entries.forEach { place ->
            val tag = if (place == GardenPlace.SEED_JAR) "personal_seed_jar" else "personal_place_${place.name.lowercase()}"
            rule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        assertEquals(GardenPlace.entries.toList(), opened)
    }
}
