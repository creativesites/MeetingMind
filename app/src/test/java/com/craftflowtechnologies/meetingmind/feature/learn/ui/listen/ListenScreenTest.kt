package com.craftflowtechnologies.meetingmind.feature.learn.ui.listen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.feature.learn.ListenState
import com.craftflowtechnologies.meetingmind.feature.learn.ListenVoice
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson.StudySamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class ListenScreenTest {
    @get:Rule val compose = createComposeRule()

    private val voices = mutableListOf<ListenVoice>()
    private var toggles = 0
    private val skips = mutableListOf<Long>()

    private fun show(state: ListenState = StudySamples.listen()) {
        compose.setContent {
            LearnHarness(dark = true) {
                ListenScreen(
                    state = state,
                    onClose = {},
                    onTogglePlay = { toggles++ },
                    onSeek = {},
                    onSkip = { skips += it },
                    onNextStressed = {},
                    onSpeed = {},
                    onVoice = { voices += it },
                    onToggleStopAtSectionEnd = {},
                    onToggleQuizAfter = {}
                )
            }
        }
    }

    @Test
    fun `the voice control switches to Read to Me`() {
        show()

        compose.onNodeWithText("Read to Me").performClick()

        assertEquals(listOf(ListenVoice.READ_TO_ME), voices)
    }

    @Test
    fun `without lecturer audio only Read to Me is offered`() {
        show(StudySamples.listen(hasLecturerAudio = false, voice = ListenVoice.READ_TO_ME))

        compose.onNodeWithText("Read to Me").assertIsDisplayed()
        compose.onAllNodesWithText("Lecturer").assertCountEquals(0)
    }

    @Test
    fun `play starts a paused lecture and the button says pause when playing`() {
        show()

        compose.onNodeWithContentDescription("Play").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `the button says pause while playing`() {
        show(StudySamples.listen(isPlaying = true))

        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `the back and forward buttons skip fifteen seconds`() {
        show()

        compose.onNodeWithContentDescription("Back 15 seconds").performClick()
        compose.onNodeWithContentDescription("Forward 15 seconds").performClick()

        assertEquals(listOf(-15_000L, 15_000L), skips)
    }

    @Test
    fun `a stressed moment within twenty seconds is announced`() {
        show()

        compose.onNodeWithText("Stressed moment in 9 s").assertIsDisplayed()
    }

    @Test
    fun `the read-along lines show and the current line is in the list`() {
        show()

        compose.onNodeWithText("It gets pushed out. A competitive inhibitor sits in the active site, so enough substrate wins the seat back.")
            .assertIsDisplayed()
    }
}
