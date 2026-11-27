package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.craftflowtechnologies.meetingmind.feature.personal.garden.PlantLook
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Personal, "Your garden": every time of day in the light and the dark theme, an empty garden,
 * and the two sheets. Motion is off, so the images are deterministic.
 * Record with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class PersonalHomeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun week(vararg states: DayState) = listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { i, l -> DayDotUi(l, states.getOrElse(i) { DayState.OFF }) }

    private val plants = listOf(
        GardenPlantUi(
            id = "a", title = "Protect the first hour", look = PlantLook(leaves = 3), tendedToday = false, bloomed = false, resting = false,
            week = week(DayState.KEPT, DayState.KEPT, DayState.MISSED, DayState.TODAY, DayState.UPCOMING),
            rhythmLine = "2 days this week", rhythmLabel = "Weekdays", sourceLine = "From “Deep work in a distracted week”", sourceNoteId = "n1",
            windowLine = "7 of 10 days kept since you planted it", checkInDue = false
        ),
        GardenPlantUi(
            id = "b", title = "Ask one clarifying question", look = PlantLook(leaves = 4, bud = true, glowing = true), tendedToday = true, bloomed = false, resting = false,
            week = week(DayState.KEPT, DayState.OFF, DayState.KEPT, DayState.KEPT), rhythmLine = "3 days this week", rhythmLabel = "3 times a week",
            sourceLine = null, sourceNoteId = null, windowLine = "6 of 6 days kept", checkInDue = false
        ),
        GardenPlantUi(
            id = "c", title = "Ten-minute walk after lunch", look = PlantLook(leaves = 2, bloom = true), tendedToday = false, bloomed = true, resting = false,
            week = emptyList(), rhythmLine = "", rhythmLabel = "Every day", sourceLine = null, sourceNoteId = null, windowLine = "Part of you since October", checkInDue = false
        )
    )

    private fun state(phase: GardenPhase, withPlants: Boolean = true) = PersonalUiState(
        phase = phase, whenLine = "Thursday evening", questionLabel = "TONIGHT'S QUESTION",
        question = "What took more energy today than it should have?", lights = if (withPlants) 6 else 0,
        plants = if (withPlants) plants else emptyList(), ideas = if (withPlants) 4 else 0, loaded = true
    )

    private fun shoot(name: String, dark: Boolean, s: PersonalUiState) {
        rule.setContent {
            MMPreviewFrame(dark) {
                PersonalHomeScreen(s, animate = false, onAnswerAloud = {}, onWriteAnswer = {}, onOpenPlant = {}, onPlantNew = {}, onOpenSeedJar = {}, onCaptureIdea = {})
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun garden_dawn_light() = shoot("garden_dawn", false, state(GardenPhase.DAWN))
    @Test fun garden_day_light() = shoot("garden_day", false, state(GardenPhase.DAY))
    @Test fun garden_dusk_light() = shoot("garden_dusk", false, state(GardenPhase.DUSK))
    @Test fun garden_night_light() = shoot("garden_night", false, state(GardenPhase.NIGHT))
    @Test fun garden_dawn_dark() = shoot("garden_dawn", true, state(GardenPhase.DAWN))
    @Test fun garden_day_dark() = shoot("garden_day", true, state(GardenPhase.DAY))
    @Test fun garden_dusk_dark() = shoot("garden_dusk", true, state(GardenPhase.DUSK))
    @Test fun garden_night_dark() = shoot("garden_night", true, state(GardenPhase.NIGHT))
    @Test fun garden_empty_light() = shoot("garden_empty", false, state(GardenPhase.DAY, withPlants = false))
    @Test fun garden_empty_dark() = shoot("garden_empty", true, state(GardenPhase.DUSK, withPlants = false))

    @Test fun plant_sheet_light() {
        rule.setContent { MMPreviewFrame(false) { PlantSheet(initialTitle = "Write tomorrow's three", onDismiss = {}, onPlant = {}) } }
        rule.onNodeWithTag("personal_plant_sheet").assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/plant_sheet_light.png")
    }

    @Test fun practice_sheet_dark() {
        rule.setContent { MMPreviewFrame(true) { PracticeSheet(plants[0], {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("personal_practice_sheet").assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/practice_sheet_dark.png")
    }

    @Test fun `the prominent button answers out loud and writing is the quiet one`() {
        var aloud = 0; var written = 0
        rule.setContent {
            MMPreviewFrame(false) {
                PersonalHomeScreen(state(GardenPhase.DUSK), animate = false, onAnswerAloud = { aloud++ }, onWriteAnswer = { written++ }, onOpenPlant = {}, onPlantNew = {}, onOpenSeedJar = {}, onCaptureIdea = {})
            }
        }
        rule.onNodeWithText("Light the lantern").assertIsDisplayed()
        rule.onNodeWithTag("personal_answer").performClick()
        assertEquals(1, aloud)
        assertEquals(0, written)
    }

    @Test fun `tapping a plant opens it`() {
        var opened: String? = null
        rule.setContent {
            MMPreviewFrame(false) {
                PersonalHomeScreen(state(GardenPhase.DAY), animate = false, onAnswerAloud = {}, onWriteAnswer = {}, onOpenPlant = { opened = it }, onPlantNew = {}, onOpenSeedJar = {}, onCaptureIdea = {})
            }
        }
        rule.onNodeWithTag("personal_plant_a").performClick()
        assertEquals("a", opened)
    }

    @Test fun `an empty garden asks for a first seed`() {
        var planted = 0
        rule.setContent {
            MMPreviewFrame(false) {
                PersonalHomeScreen(state(GardenPhase.DAY, withPlants = false), animate = false, onAnswerAloud = {}, onWriteAnswer = {}, onOpenPlant = {}, onPlantNew = { planted++ }, onOpenSeedJar = {}, onCaptureIdea = {})
            }
        }
        rule.onNodeWithText("Plant your first seed").assertIsDisplayed()
        rule.onNodeWithTag("personal_plant_new").performClick()
        assertTrue(planted == 1)
    }
}
