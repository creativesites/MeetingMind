package com.craftflowtechnologies.meetingmind.feature.personal.sky

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Your sky: a populated month with a selected constellation, an empty month and the locked card, in
 * the light and the dark theme. Motion is off, so the images are deterministic.
 * Record with -Proborazzi.test.record=true, then look at the PNGs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SkyScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val november = YearMonth.of(2026, 11)

    private fun at(day: Int, hour: Int): Long = LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val monthEntries = listOf(
        ReflectionEntry("e1", "Inbox was loud after the call. Cleared half of it.", at(2, 19)),
        ReflectionEntry("e2", "Mornings were calm before the inbox filled up.", at(3, 8)),
        ReflectionEntry("e3", "Inbox zero for an hour, then it came back.", at(5, 20)),
        ReflectionEntry("e4", "Quiet morning walk, the inbox could wait.", at(7, 7)),
        ReflectionEntry("e5", "Called my sister and felt lighter.", at(9, 18)),
        ReflectionEntry("e6", "Inbox again, but the morning was gentle.", at(11, 19))
    )

    /** A populated month built with the same finder and pattern lines the app uses. */
    private fun populated(zuriAllowed: Boolean = true, zuri: ZuriState = ZuriState()): SkyUiState {
        val constellations = ReflectionThemes.find(monthEntries)
        val selected = constellations.first()
        val selectedEntries = monthEntries.filter { it.id in selected.entryIds }
        return SkyUiState(
            loaded = true,
            month = november,
            entries = monthEntries,
            constellations = constellations,
            selected = selected,
            selectedEntries = selectedEntries,
            patternLines = SkyPatterns.lines(selected.name, selectedEntries.map { it.createdAt }, zone),
            zuri = zuri,
            route = SkyRoute.ON_DEVICE,
            zuriAllowed = zuriAllowed
        )
    }

    private fun shoot(name: String, dark: Boolean, state: SkyUiState) {
        rule.setContent {
            MMPreviewFrame(dark) {
                SkyScreen(
                    state = state, animate = false, onBack = {}, onPreviousMonth = {}, onNextMonth = {},
                    onSelect = {}, onOpenNote = {}, onRequestZuri = {}, onUpgrade = {}, onOpenSetup = {}
                )
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun sky_populated_light() = shoot("sky_populated", false, populated())
    @Test fun sky_populated_dark() = shoot("sky_populated", true, populated())

    @Test fun sky_empty_light() = shoot("sky_empty", false, SkyUiState(loaded = true, month = november))
    @Test fun sky_empty_dark() = shoot("sky_empty", true, SkyUiState(loaded = true, month = november))

    @Test fun sky_locked_light() = shoot("sky_locked", false, populated(zuriAllowed = false))
    @Test fun sky_locked_dark() = shoot("sky_locked", true, populated(zuriAllowed = false))

    @Test fun `without a paid plan the locked card shows and the paid button does not`() {
        var upgraded = 0
        rule.setContent {
            MMPreviewFrame(false) {
                SkyScreen(
                    state = populated(zuriAllowed = false), animate = false, onBack = {}, onPreviousMonth = {}, onNextMonth = {},
                    onSelect = {}, onOpenNote = {}, onRequestZuri = {}, onUpgrade = { upgraded++ }, onOpenSetup = {}
                )
            }
        }
        rule.onNodeWithTag("sky_locked").assertIsDisplayed()
        rule.onNodeWithTag("sky_zuri").assertDoesNotExist()
        rule.onNodeWithTag("sky_see_plans").performClick()
        assertEquals(1, upgraded)
    }

    @Test fun `with access the read button asks for the read and the source is named`() {
        var asked = 0
        rule.setContent {
            MMPreviewFrame(false) {
                SkyScreen(
                    state = populated(zuriAllowed = true, zuri = ZuriState("Inbox", ZuriPhase.WRITTEN, "You keep coming back to the inbox.")),
                    animate = false, onBack = {}, onPreviousMonth = {}, onNextMonth = {},
                    onSelect = {}, onOpenNote = {}, onRequestZuri = { asked++ }, onUpgrade = {}, onOpenSetup = {}
                )
            }
        }
        rule.onNodeWithTag("sky_locked").assertDoesNotExist()
        rule.onNodeWithTag("sky_zuri_text").assertIsDisplayed()
        rule.onNodeWithTag("sky_zuri").performClick()
        assertEquals(1, asked)
    }

    @Test fun `tapping a chip selects its constellation`() {
        var selected: String? = null
        rule.setContent {
            MMPreviewFrame(false) {
                SkyScreen(
                    state = populated(), animate = false, onBack = {}, onPreviousMonth = {}, onNextMonth = {},
                    onSelect = { selected = it }, onOpenNote = {}, onRequestZuri = {}, onUpgrade = {}, onOpenSetup = {}
                )
            }
        }
        val other = populated().constellations.last().name
        rule.onNodeWithTag("sky_chip_$other").assertIsDisplayed().performClick()
        assertEquals(other, selected)
    }

    @Test fun `with no on-device model the card offers Setup and never offers a cloud read`() {
        var setup = 0
        val noModel = populated().copy(route = SkyRoute.NONE)
        rule.setContent {
            MMPreviewFrame(false) {
                SkyScreen(
                    state = noModel, animate = false, onBack = {}, onPreviousMonth = {}, onNextMonth = {},
                    onSelect = {}, onOpenNote = {}, onRequestZuri = {}, onUpgrade = {}, onOpenSetup = { setup++ }
                )
            }
        }
        rule.onNodeWithTag("sky_setup_needed").assertIsDisplayed()
        rule.onNodeWithTag("sky_zuri").assertDoesNotExist()
        rule.onNodeWithTag("sky_setup").performClick()
        assertEquals(1, setup)
    }

    @Test fun `an empty month invites the first answer and says so`() {
        rule.setContent {
            MMPreviewFrame(false) {
                SkyScreen(
                    state = SkyUiState(loaded = true, month = november), animate = false, onBack = {}, onPreviousMonth = {},
                    onNextMonth = {}, onSelect = {}, onOpenNote = {}, onRequestZuri = {}, onUpgrade = {}, onOpenSetup = {}
                )
            }
        }
        rule.onNodeWithTag("sky_empty_text").assertIsDisplayed()
        rule.onNodeWithTag("sky_screen").assertIsDisplayed()
    }
}
