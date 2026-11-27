package com.craftflowtechnologies.meetingmind.feature.personal.seeds

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The seed jar: ideas and To sort, light and dark, and both empty states. The screen is
 * stateless, so the images come straight from the state below. Record with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SeedJarScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val day = 24L * 60 * 60 * 1000
    private val nov = 1_794_823_200_000L // Monday 16 November 2026, 10:00 UTC

    private val ideas = listOf(
        SeedIdeaUi("i1", "A reading plan for the quiet weeks", nov + 3 * day),
        SeedIdeaUi("i2", "Ask Maria about the Q3 budget", nov + 2 * day),
        SeedIdeaUi("i3", "Sunday walk with a notebook", nov + day),
        SeedIdeaUi("i4", "Name for the garden app", nov),
        SeedIdeaUi("i5", "Shorter weekly review", nov - day)
    )

    private val sortable = listOf(
        SeedSortUi(
            id = "s1", kind = "Voice Memo", createdAt = nov, title = "Budget numbers for the client review",
            suggestion = Suggestion.MoveTo(NotebookSpace.WORK), chipSpaces = listOf(NotebookSpace.FAITH, NotebookSpace.LEARNING)
        ),
        SeedSortUi(
            id = "s2", kind = "General", createdAt = nov - day, title = "Renew the passport before the trip",
            suggestion = Suggestion.MakeTask, chipSpaces = listOf(NotebookSpace.WORK, NotebookSpace.FAITH, NotebookSpace.LEARNING)
        ),
        SeedSortUi(
            id = "s3", kind = "Dictation", createdAt = nov - 2 * day, title = "Thoughts on mercy from the sermon",
            suggestion = Suggestion.MoveTo(NotebookSpace.FAITH), chipSpaces = listOf(NotebookSpace.WORK, NotebookSpace.LEARNING)
        )
    )

    private fun state(phase: GardenPhase, tab: SeedTab, ideas: List<SeedIdeaUi>, sorting: List<SeedSortUi>) = SeedJarUiState(
        phase = phase, tab = tab, ideas = ideas, selectedIdeaId = "i2", toSort = sorting
    )

    private fun shoot(name: String, dark: Boolean, s: SeedJarUiState) {
        rule.setContent {
            MMPreviewFrame(dark) {
                SeedJarScreen(
                    state = s, onBack = {}, onOpenNote = {}, onCaptureIdea = {}, onTab = {}, onSelectIdea = {},
                    onMove = { _, _ -> }, onKeep = {}, onMakeTask = {}
                )
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/personal/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun seeds_ideas_light() = shoot("seeds_ideas", false, state(GardenPhase.DAY, SeedTab.IDEAS, ideas, sortable))
    @Test fun seeds_ideas_dark() = shoot("seeds_ideas", true, state(GardenPhase.DUSK, SeedTab.IDEAS, ideas, sortable))
    @Test fun seeds_ideas_empty_light() = shoot("seeds_ideas_empty", false, state(GardenPhase.DAY, SeedTab.IDEAS, emptyList(), emptyList()))
    @Test fun seeds_ideas_empty_dark() = shoot("seeds_ideas_empty", true, state(GardenPhase.NIGHT, SeedTab.IDEAS, emptyList(), emptyList()))
    @Test fun seeds_sort_light() = shoot("seeds_sort", false, state(GardenPhase.DAY, SeedTab.TO_SORT, ideas, sortable))
    @Test fun seeds_sort_dark() = shoot("seeds_sort", true, state(GardenPhase.DUSK, SeedTab.TO_SORT, ideas, sortable))
    @Test fun seeds_sort_empty_light() = shoot("seeds_sort_empty", false, state(GardenPhase.DAY, SeedTab.TO_SORT, ideas, emptyList()))
    @Test fun seeds_sort_empty_dark() = shoot("seeds_sort_empty", true, state(GardenPhase.NIGHT, SeedTab.TO_SORT, emptyList(), emptyList()))

    @Test fun `tapping the To sort tab asks for that tab`() {
        var asked: SeedTab? = null
        rule.setContent {
            MMPreviewFrame(false) {
                SeedJarScreen(
                    state = state(GardenPhase.DAY, SeedTab.IDEAS, ideas, sortable), onBack = {}, onOpenNote = {}, onCaptureIdea = {},
                    onTab = { asked = it }, onSelectIdea = {}, onMove = { _, _ -> }, onKeep = {}, onMakeTask = {}
                )
            }
        }
        rule.onNodeWithTag("seeds_tab_sort").assertIsDisplayed().performClick()
        assertEquals(SeedTab.TO_SORT, asked)
    }

    @Test fun `a to sort card offers its suggestion, the other spaces and keep here`() {
        var moved: Pair<String, NotebookSpace>? = null
        var kept: String? = null
        rule.setContent {
            MMPreviewFrame(false) {
                SeedJarScreen(
                    state = state(GardenPhase.DAY, SeedTab.TO_SORT, ideas, sortable), onBack = {}, onOpenNote = {}, onCaptureIdea = {},
                    onTab = {}, onSelectIdea = {}, onMove = { id, space -> moved = id to space }, onKeep = { kept = it }, onMakeTask = {}
                )
            }
        }
        rule.onNodeWithTag("seeds_move_WORK_s1").assertIsDisplayed().performClick()
        assertEquals("s1" to NotebookSpace.WORK, moved)
        rule.onNodeWithTag("seeds_keep_s1").performClick()
        assertEquals("s1", kept)
        rule.onNodeWithTag("seeds_task_s2").assertIsDisplayed()
    }
}
