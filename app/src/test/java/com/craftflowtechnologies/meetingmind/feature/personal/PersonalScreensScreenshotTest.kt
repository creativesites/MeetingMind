package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenWindowTile
import com.craftflowtechnologies.meetingmind.feature.personal.garden.PlantLook
import com.craftflowtechnologies.meetingmind.feature.personal.lantern.LanternContent
import com.craftflowtechnologies.meetingmind.feature.personal.lantern.LanternPhase
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * The rest of Personal: the lantern in each phase, the Practices list, seed packets on the garden,
 * and the garden's window on Today. Light and dark; motion off.
 * Record with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class PersonalScreensScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val question = "What took more energy today than it should have?"
    private fun week(vararg s: DayState) = listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { i, l -> DayDotUi(l, s.getOrElse(i) { DayState.OFF }) }

    private fun plant(id: String, title: String, look: PlantLook, bloomed: Boolean = false, tended: Boolean = false) = GardenPlantUi(
        id = id, title = title, look = look, tendedToday = tended, bloomed = bloomed, resting = false,
        week = if (bloomed) emptyList() else week(DayState.KEPT, DayState.KEPT, DayState.MISSED, DayState.TODAY, DayState.UPCOMING),
        rhythmLine = "2 days this week", rhythmLabel = "Weekdays", sourceLine = null, sourceNoteId = null,
        windowLine = if (bloomed) "Part of you now" else "7 of 10 days kept since you planted it", checkInDue = false
    )

    private val state = PersonalUiState(
        phase = GardenPhase.DUSK, whenLine = "Thursday evening", questionLabel = "TONIGHT'S QUESTION", question = question, lights = 4,
        plants = listOf(
            plant("a", "Protect the first hour", PlantLook(leaves = 3)),
            plant("b", "Ask one clarifying question", PlantLook(leaves = 4, bud = true, glowing = true), tended = true),
            plant("c", "Ten-minute walk after lunch", PlantLook(leaves = 2, bloom = true), bloomed = true)
        ),
        ideas = 3,
        letGo = listOf(LetGoUi("z", "Cold shower every morning", 4, PlantRhythm.DAILY, PlantNudge.MORNING, null, null)),
        packets = listOf(
            SeedPacketUi("n1", "Deep work in a distracted week", "Talk or podcast", listOf(
                SeedUi("Protect the first hour", planted = true),
                SeedUi("Write tomorrow's three before you close the laptop", planted = false),
                SeedUi("Take one real break with no screen", planted = false)
            ))
        ),
        loaded = true
    )

    private fun lantern(name: String, dark: Boolean, phase: LanternPhase, ms: Long, amp: Float) {
        rule.setContent {
            MMPreviewFrame(dark) {
                LanternContent(question, "What would you do differently tomorrow morning?", phase, ms, amp, animate = false, {}, {}, {}, {}, {}, {})
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/lantern_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun lantern_listening_light() = lantern("listening", false, LanternPhase.LISTENING, 72_000, 0.6f)
    @Test fun lantern_listening_dark() = lantern("listening", true, LanternPhase.LISTENING, 72_000, 0.6f)
    @Test fun lantern_start_dark() = lantern("start", true, LanternPhase.LISTENING, 4_000, 0.1f)
    @Test fun lantern_paused_light() = lantern("paused", false, LanternPhase.PAUSED, 50_000, 0f)
    @Test fun lantern_mic_light() = lantern("mic", false, LanternPhase.NEEDS_MICROPHONE, 0, 0f)
    @Test fun lantern_mic_dark() = lantern("mic", true, LanternPhase.NEEDS_MICROPHONE, 0, 0f)

    @Test fun `the follow-up waits, and typing is offered only at the start`() {
        rule.setContent { MMPreviewFrame(false) { LanternContent(question, "Why?", LanternPhase.LISTENING, 5_000, 0.3f, false, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("lantern_follow_up").assertDoesNotExist()
        rule.onNodeWithTag("lantern_type").assertIsDisplayed()
    }

    @Test fun `after a while the lantern offers the follow-up and keeps what was said`() {
        var kept = 0
        rule.setContent { MMPreviewFrame(true) { LanternContent(question, "Why?", LanternPhase.LISTENING, 45_000, 0.3f, false, {}, {}, {}, { kept++ }, {}, {}) } }
        rule.onNodeWithTag("lantern_follow_up").assertIsDisplayed()
        rule.onNodeWithTag("lantern_type").assertDoesNotExist()
        rule.onNodeWithTag("lantern_keep").performClick()
        assertEquals(1, kept)
    }

    private fun practices(name: String, dark: Boolean, s: PersonalUiState) {
        rule.setContent { MMPreviewFrame(dark) { PracticesScreen(s, {}, {}, {}, {}) } }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/practices_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun practices_light() = practices("list", false, state)
    @Test fun practices_dark() = practices("list", true, state)
    @Test fun practices_empty_light() = practices("empty", false, PersonalUiState(loaded = true))

    @Test fun `let go is folded and offers planting again`() {
        var again: LetGoUi? = null
        rule.setContent { MMPreviewFrame(false) { PracticesScreen(state, {}, {}, {}, { again = it }) } }
        rule.onNodeWithText("Cold shower every morning").assertDoesNotExist()
        rule.onNodeWithTag("practices_letgo_toggle").performClick()
        rule.onNodeWithTag("practices_again_z").performClick()
        assertEquals("z", again?.id)
    }

    @Test fun garden_with_packets_dark() {
        rule.setContent {
            MMPreviewFrame(true) {
                PersonalHomeScreen(state, animate = false, onAnswerAloud = {}, onWriteAnswer = {}, onOpenPlant = {}, onPlantNew = {}, onOpenSeedJar = {}, onCaptureIdea = {})
            }
        }
        rule.onNodeWithTag("personal_packets").performScrollTo()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/garden_packets_dark.png")
    }

    @Test fun `planting a seed hands over the note and the line, and the sky and practices are a tap away`() {
        var planted: Triple<String, String, String>? = null
        var sky = 0; var practices = 0
        rule.setContent {
            MMPreviewFrame(false) {
                PersonalHomeScreen(
                    state, animate = false, onAnswerAloud = {}, onWriteAnswer = {}, onOpenPlant = {}, onPlantNew = {}, onOpenSeedJar = {}, onCaptureIdea = {},
                    onOpenSky = { sky++ }, onOpenPractices = { practices++ }, onPlantSeed = { n, t, l -> planted = Triple(n, t, l) }
                )
            }
        }
        rule.onNodeWithTag("personal_open_sky").performClick()
        rule.onNodeWithTag("personal_lights").performClick()
        rule.onNodeWithTag("personal_practices_tile").performScrollTo().performClick()
        // The planted line is not offered again; the first unplanted one is.
        rule.onNodeWithTag("personal_seed_n1_0").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, sky)
        assertEquals(1, practices)
        assertEquals(Triple("n1", "Deep work in a distracted week", "Write tomorrow's three before you close the laptop"), planted)
        rule.onNodeWithTag("personal_seed_n1_2").assertDoesNotExist()
    }

    @Test fun window_light() {
        rule.setContent { MMPreviewFrame(false) { GardenWindowTile(onClick = {}, now = LocalDateTime.of(2026, 11, 26, 10, 0)) } }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/window_day_light.png")
    }

    @Test fun window_dark() {
        rule.setContent { MMPreviewFrame(true) { GardenWindowTile(onClick = {}, now = LocalDateTime.of(2026, 11, 26, 18, 30)) } }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/window_dusk_dark.png")
    }
}
