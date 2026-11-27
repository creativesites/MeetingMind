package com.craftflowtechnologies.meetingmind.ai.automation

import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Study pack after every lecture" runs only the flashcard recipe, and only for a lecture. It must not turn on the
 * app-wide auto-run, which also runs sermon and meeting recipes.
 */
class AutoRunPolicyTest {

    @Test
    fun `the lecture switch alone runs the flashcard recipe for a lecture`() {
        assertEquals(
            AutopilotRecipe.STUDY_FLASHCARD_DRILL,
            AutoRunPolicy.recipeFor(RecordingType.LECTURE, appWideOn = false, studyPackAfterLectureOn = true)
        )
    }

    @Test
    fun `the lecture switch alone does not run sermon, meeting or research recipes`() {
        assertNull(AutoRunPolicy.recipeFor(RecordingType.SERMON, appWideOn = false, studyPackAfterLectureOn = true))
        assertNull(AutoRunPolicy.recipeFor(RecordingType.MEETING, appWideOn = false, studyPackAfterLectureOn = true))
        assertNull(AutoRunPolicy.recipeFor(RecordingType.RESEARCH, appWideOn = false, studyPackAfterLectureOn = true))
    }

    @Test
    fun `the app-wide switch keeps running each recording's recommended recipe`() {
        assertEquals(
            AutopilotRecipe.SERMON_SMALL_GROUP_PACK,
            AutoRunPolicy.recipeFor(RecordingType.SERMON, appWideOn = true, studyPackAfterLectureOn = false)
        )
    }

    @Test
    fun `with both switches off nothing runs`() {
        assertNull(AutoRunPolicy.recipeFor(RecordingType.LECTURE, appWideOn = false, studyPackAfterLectureOn = false))
    }
}
