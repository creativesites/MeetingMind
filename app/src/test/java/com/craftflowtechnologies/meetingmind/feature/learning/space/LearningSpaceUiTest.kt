package com.craftflowtechnologies.meetingmind.feature.learning.space

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The redesigned Learning space and Practice mode (design/learning-vertical-design-spec.md). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class LearningSpaceUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: MeetMindDatabase
    private lateinit var viewModel: LearnViewModel

    @Before
    fun setUp() {
        val app: Application = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        viewModel = LearnViewModel(
            application = app,
            database = db,
            learningRepo = LearningRepository(db),
            noteRepo = NoteRepository(app, db),
            aiEngine = LearningAiEngine()
        )
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    private fun actions(onRecord: () -> Unit = {}) = LearningSpaceActions(
        onBack = {}, onOpenSpace = {}, onOpenSession = {}, onStartPractice = {},
        onRecordLecture = onRecord, onWriteStudyNote = {}, onAsk = {}
    )

    private fun item(id: String, type: LearningActivityType, options: List<String> = emptyList(), answer: String) = LearningActivity(
        id = id, sessionId = "s1", conceptId = null, type = type, prompt = "Prompt $id",
        expectedAnswer = answer, options = options, createdAt = 0L, updatedAt = 0L
    )

    @Test
    fun `first run explains the space and records a lecture`() {
        var recorded = false
        compose.setContent { MeetMindTheme { LearningSpaceScreen(viewModel, actions(onRecord = { recorded = true })) } }
        compose.onNodeWithTag("learn_first_run").assertIsDisplayed()
        compose.onNodeWithTag("learn_record_lecture").performClick()
        assertTrue(recorded)
    }

    @Test
    fun `space has three tabs and practice is not one of them`() {
        compose.setContent { MeetMindTheme { LearningSpaceScreen(viewModel, actions()) } }
        compose.onNodeWithTag("learn_space_tab_learn").assertIsDisplayed()
        compose.onNodeWithTag("learn_space_tab_library").performClick()
        compose.onNodeWithText("0 courses · 0 sessions").assertIsDisplayed()
        compose.onNodeWithTag("learn_space_tab_progress").performClick()
        compose.onNodeWithText("Nothing to show yet").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTagCount("learn_space_tab_practice") == 0)
    }

    @Test
    fun `home style shows the learn pane with an entry into the space and no space bar`() {
        var opened = false
        compose.setContent {
            MeetMindTheme {
                LearningSpaceScreen(viewModel, actions().copy(onOpenSpace = { opened = true }), onNavigateBottomNav = {})
            }
        }
        assertTrue(compose.onAllNodesWithTagCount("learn_space_tab_learn") == 0)
        compose.onNodeWithTag("learn_open_space").performClick()
        assertTrue(opened)
    }

    @Test
    fun `recall hides the answer until the student asks, then offers three ratings`() {
        viewModel.startPracticeSession(listOf(item("r1", LearningActivityType.RECALL, answer = "Km rises, Vmax unchanged")))
        compose.setContent { MeetMindTheme { PracticeModeScreen(viewModel, onClose = {}, onOpenSession = {}) } }
        compose.onNodeWithTag("practice_prompt").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTagCount("practice_answer") == 0)
        compose.onNodeWithTag("practice_show_answer").performClick()
        compose.onNodeWithTag("practice_answer").assertIsDisplayed()
        compose.onNodeWithTag("rate_again").assertIsDisplayed()
        compose.onNodeWithTag("rate_hard").assertIsDisplayed()
        compose.onNodeWithTag("rate_good").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTagCount("rate_easy") == 0)
        // Rating moves on even though this item is not in the database: a failed save never blocks.
        compose.onNodeWithTag("rate_good").performClick()
        compose.onNodeWithTag("practice_done").assertIsDisplayed()
    }

    @Test
    fun `choice question checks only after a choice and labels the correct answer`() {
        viewModel.startPracticeSession(
            listOf(item("m1", LearningActivityType.MULTIPLE_CHOICE, listOf("Km falls", "Km rises", "Vmax falls"), answer = "B"))
        )
        compose.setContent { MeetMindTheme { PracticeModeScreen(viewModel, onClose = {}, onOpenSession = {}) } }
        compose.onNodeWithTag("practice_check").assertIsNotEnabled()
        compose.onNodeWithText("Km falls").performClick()
        compose.onNodeWithTag("practice_check").assertIsEnabled().performClick()
        compose.onNodeWithText("Not quite").assertIsDisplayed()
        compose.onNodeWithText("Correct answer").assertIsDisplayed()
        compose.onNodeWithText("Your answer").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
