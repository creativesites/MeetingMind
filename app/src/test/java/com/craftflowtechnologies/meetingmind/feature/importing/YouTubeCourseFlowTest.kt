package com.craftflowtechnologies.meetingmind.feature.importing

import android.app.Application
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.feature.learning.CourseChoice
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A video link made from a course's new-session sheet carries that course into the YouTube sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeCourseFlowTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var learn: LearnViewModel

    @Before fun setUp() {
        val app: Application = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        learn = LearnViewModel(app, db, LearningRepository(db), NoteRepository(app, db), LearningAiEngine())
    }

    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    @Test
    fun `the sheet controller hands the course to the sheet state`() {
        val state = YouTubeSheetState()

        state.controller.open(from = NotebookSpace.LEARNING, courseId = "course-bio")

        assertTrue(state.open)
        assertEquals("course-bio", state.courseId)
        assertEquals(NotebookSpace.LEARNING, state.from)
    }

    @Test
    fun `opening the sheet with no course leaves no course`() {
        val state = YouTubeSheetState()

        state.controller.open(from = NotebookSpace.LEARNING)

        assertNull(state.courseId)
    }

    @Test
    fun `preparing the video's course returns the course the learner chose`() {
        var received: String? = "unset"

        learn.prepareCourseForLecture(CourseChoice.Existing("course-bio")) { received = it }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("course-bio", received)
    }

    @Test
    fun `no course chosen is passed on as no course`() {
        var received: String? = "unset"

        learn.prepareCourseForLecture(CourseChoice.None) { received = it }
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(received)
    }
}
