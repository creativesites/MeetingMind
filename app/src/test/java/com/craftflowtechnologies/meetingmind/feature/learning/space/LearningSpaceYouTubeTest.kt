package com.craftflowtechnologies.meetingmind.feature.learning.space

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Learn home shows the shared YouTube card, titled for lectures, under today's plan. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class LearningSpaceYouTubeTest {

    @get:Rule val compose = createComposeRule()
    private lateinit var db: MeetMindDatabase
    private lateinit var viewModel: LearnViewModel

    @Before fun setUp() {
        val app: Application = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        viewModel = LearnViewModel(app, db, LearningRepository(db), NoteRepository(app, db), LearningAiEngine())
    }

    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    @Test fun `the Learn home shows the lecture YouTube card`() {
        compose.setContent {
            MeetMindTheme {
                LearningSpaceScreen(viewModel, LearningSpaceActions(
                    onBack = {}, onOpenSpace = {}, onOpenSession = {}, onStartPractice = {},
                    onRecordLecture = {}, onWriteStudyNote = {}
                ))
            }
        }
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag("learn_youtube"))
        compose.onNodeWithTag("learn_youtube").assertIsDisplayed()
        compose.onNodeWithText("Lecture from a YouTube video", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("learn_youtube").captureRoboImage("build/outputs/roborazzi/learn_youtube_card.png")
    }
}
