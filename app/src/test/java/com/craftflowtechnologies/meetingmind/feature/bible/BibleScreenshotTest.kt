package com.craftflowtechnologies.meetingmind.feature.bible

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionKind
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionPlan
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionRequest
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionState
import com.craftflowtechnologies.meetingmind.core.biblestudy.TodayReading
import com.craftflowtechnologies.meetingmind.core.biblestudy.WhyLines
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterVerse
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Bible home (populated and first-time) and a guided session step, in light. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class BibleScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun ref(s: String) = ScriptureReferenceParser.parse(s)!!

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { MeetMindTheme(darkTheme = false) { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/bible/${name}_light.png")
    }

    private val today = ref("Psalm 121").let { TodayReading(it, WhyLines.lines(it, setOf("Trust"))) }

    @Test fun home_populated() = shoot("bible_home") {
        BibleHomeContent(
            BibleHomeState(
                loaded = true, place = ref("John 3:16"), today = today,
                plans = listOf(PlanDay("gospels30", "The Gospels in 30 days", 3, "Matthew 4–6", ref("Matthew 4"))),
                journalCount = 5
            ),
            BibleHomeActions()
        )
    }

    @Test fun home_first_time() = shoot("bible_home_first_time") {
        BibleHomeContent(BibleHomeState(loaded = true, place = null, start = ref("John 1"), today = today), BibleHomeActions())
    }

    @Test fun session_step() = shoot("bible_session_step") {
        val passage = ref("John 15:1-5")
        val verses = listOf(
            ChapterVerse(1, "I am the true vine, and my Father is the gardener.", true, false),
            ChapterVerse(2, "He cuts off every branch in me that bears no fruit, while every branch that does bear fruit he prunes so that it will be even more fruitful.", false, false),
            ChapterVerse(3, "You are already clean because of the word I have spoken to you.", false, false)
        )
        val text = verses.joinToString(" ") { it.text }
        val session = SessionState(SessionKind.TODAY, SessionPlan.steps(SessionKind.TODAY), index = 2)
            .withPrompt("q1", "What word or phrase stands out to you, and why?")
            .answer("q1", "Abide. I keep trying to do this on my own.")
        GuidedSessionContent(
            GuidedUi(
                request = SessionRequest(SessionKind.TODAY, passage), load = SessionLoad.Ready(text, verses, "WEB · Public domain"),
                session = session, canListen = true,
                help = mapOf("OBSERVE-0" to StepHelp(line = "Zuri's suggestions need an internet connection or an on-device model. These are our own questions."))
            ),
            reading = ReadAloudState(), actions = SessionActions()
        )
    }
}
