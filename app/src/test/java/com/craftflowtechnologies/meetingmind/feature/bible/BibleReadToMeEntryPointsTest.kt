package com.craftflowtechnologies.meetingmind.feature.bible

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionKind
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionPlan
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionRequest
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionState
import com.craftflowtechnologies.meetingmind.core.biblestudy.StepType
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterVerse
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.feature.scripture.VerseSheetActions
import com.craftflowtechnologies.meetingmind.feature.study.PassageHeader
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every Read to Me entry point in the Bible flow must be on screen on a 360 dp phone.
 * Reader: the "Read aloud" control under the top bar. Guided session: "Read aloud" on the
 * reading step, and "Listen to my study" once the session is saved. Verse sheet: "Listen".
 * Study workspace: the chapter's listen control in the passage heading.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi", sdk = [34])
class BibleReadToMeEntryPointsTest {
    @get:Rule val rule = createComposeRule()

    private fun ref(s: String) = ScriptureReferenceParser.parse(s)!!

    private val verses = listOf(
        ChapterVerse(15, "I am the true vine, and my Father is the gardener.", true, false),
        ChapterVerse(16, "Every branch in me that bears no fruit he takes away.", false, false),
        ChapterVerse(17, "Abide in me, and I in you.", false, false)
    )

    @Test fun reader_read_aloud_is_displayed_at_360dp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        rule.setContent { MeetMindTheme(darkTheme = false) { BibleScreen(viewModel = BibleViewModel(app), onNavigateBack = {}) } }
        rule.waitForIdle()
        rule.onNodeWithTag("bible_listen").assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/bible/readtome_reader_light.png")
    }

    @Test fun guided_reading_step_read_aloud_is_displayed_at_360dp() {
        val passage = ref("John 15:15-17")
        val steps = SessionPlan.steps(SessionKind.TODAY)
        val readIndex = steps.indexOfFirst { it.type == StepType.READ }
        val ui = GuidedUi(
            request = SessionRequest(SessionKind.TODAY, passage),
            load = SessionLoad.Ready(verses.joinToString(" ") { it.text }, verses, "WEB · Public domain"),
            session = SessionState(SessionKind.TODAY, steps, index = readIndex),
            canListen = true,
        )
        rule.setContent { MeetMindTheme(darkTheme = false) { GuidedSessionContent(ui, reading = ReadAloudState(), actions = SessionActions()) } }
        rule.waitForIdle()
        rule.onNodeWithTag("session_read_aloud").assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/bible/readtome_session_read_light.png")
    }

    @Test fun guided_finished_study_listen_is_displayed_at_360dp() {
        val passage = ref("John 15:15-17")
        val steps = SessionPlan.steps(SessionKind.TODAY)
        val session = SessionState(SessionKind.TODAY, steps, index = steps.lastIndex)
            .withPrompt("q1", "What stands out to you?")
            .answer("q1", "That I am chosen, not only choosing.")
        val ui = GuidedUi(
            request = SessionRequest(SessionKind.TODAY, passage),
            load = SessionLoad.Ready(verses.joinToString(" ") { it.text }, verses, "WEB · Public domain"),
            session = session,
            savedNoteId = "note-1",
            canListen = true,
        )
        rule.setContent { MeetMindTheme(darkTheme = false) { GuidedSessionContent(ui, reading = ReadAloudState(), actions = SessionActions()) } }
        rule.waitForIdle()
        rule.onNodeWithTag("session_listen_study").assertIsDisplayed()
    }

    @Test fun verse_sheet_listen_is_displayed_at_360dp_with_all_actions() {
        rule.setContent {
            MeetMindTheme(darkTheme = false) {
                VerseSheetActions(
                    canPlay = true, canListen = true,
                    onPlay = {}, onRead = {}, onListen = {}, onSave = {}, onCopy = {}, onBibleApp = {}
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("verse_listen").assertIsDisplayed()
    }

    @Test fun verse_sheet_hides_listen_until_the_verse_text_loads() {
        rule.setContent {
            MeetMindTheme(darkTheme = false) {
                VerseSheetActions(
                    canPlay = false, canListen = false,
                    onPlay = {}, onRead = {}, onListen = {}, onSave = {}, onCopy = {}, onBibleApp = {}
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("verse_listen").assertDoesNotExist()
    }

    @Test fun study_chapter_listen_is_displayed_at_360dp() {
        rule.setContent {
            MeetMindTheme(darkTheme = false) {
                PassageHeader(title = "John 15", canListen = true, onPrevious = {}, onNext = {}, onListen = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("study_listen").assertIsDisplayed()
    }

    @Test fun study_chapter_listen_waits_for_the_chapter() {
        rule.setContent {
            MeetMindTheme(darkTheme = false) {
                PassageHeader(title = "John 15", canListen = false, onPrevious = {}, onNext = {}, onListen = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("study_listen").assertDoesNotExist()
    }
}
