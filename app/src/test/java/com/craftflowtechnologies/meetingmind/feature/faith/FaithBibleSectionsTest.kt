package com.craftflowtechnologies.meetingmind.feature.faith

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.biblestudy.TodayReading
import com.craftflowtechnologies.meetingmind.core.faith.PrayerPerson
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.feature.bible.BibleHomeActions
import com.craftflowtechnologies.meetingmind.feature.bible.BibleHomeContent
import com.craftflowtechnologies.meetingmind.feature.bible.BibleHomeState
import com.craftflowtechnologies.meetingmind.feature.bible.PlanDay
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Faith home and the Bible home on a 360 dp phone: every section the redesign promises has its
 * test tag on screen. Sample content is test-only and never reaches the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2600dp-xxhdpi", sdk = [34])
class FaithBibleSectionsTest {
    @get:Rule val compose = createComposeRule()

    private fun ref(text: String) = ScriptureReferenceParser.parse(text)!!

    private fun note(id: String, title: String, type: RecordingType, status: NoteStatus = NoteStatus.OPEN) = Note(
        id = id, title = title, workflow = type, notebookId = null, createdAt = 1_700_000_000_000, updatedAt = 1_700_000_000_000,
        eventDate = null, pinned = false, isPrivate = false, status = status, answeredAt = null, metadata = emptyMap(), plainText = ""
    )

    private val faithState = FaithHomeState(
        place = ref("John 3:16"),
        today = TodayReading(ref("Psalm 23"), listOf("A psalm of trust, short enough to read slowly.")),
        devotional = FaithDevotionalLine("Rest in the middle of the work", "Matthew 11:28-30", written = true),
        reading = "Morning and Evening",
        verse = FaithVerseLine("Philippians 4:6-7", "WEB", "In nothing be anxious, but in everything, by prayer.", "World English Bible, public domain"),
        plan = FaithPlanLine("Gospels in 90 days", "Luke 4–5", 12, 90, 13, 1, ref("Luke 4")),
        prayerPeople = listOf(PrayerPerson(1, "Grace", "recovering from surgery", null, 3)),
        requests = listOf(note("r1", "Wisdom about the new job", RecordingType.PRAYER_REQUEST)),
        answered = 2, journeyLine = "3 prayers · 2 reflections", entryCount = 14, scripturePassages = 9, collections = 2,
        recent = listOf(note("n1", "Thankful for this week", RecordingType.GRATITUDE))
    )

    @Test fun `Faith home shows every section on a 360 dp screen`() {
        compose.setContent {
            MeetMindTheme {
                FaithHomeContent(
                    faithState, FaithHomeActions(),
                    createRow = { Box(Modifier.size(1.dp).testTag("faith_create_row")) },
                    hasMore = true,
                    more = {}
                )
            }
        }
        compose.waitForIdle()
        listOf(
            "faith_bible_card", "faith_bible_header", "faith_continue", "faith_guide_me", "faith_just_read",
            "faith_today_devotional", "faith_reading_line", "faith_write_ai",
            "faith_pray_with_me", "faith_circles", "faith_verse", "faith_verse_write", "faith_verse_share",
            "faith_praying_for", "faith_reading_plan", "plan_mark_read",
            "faith_testimonies", "faith_journey", "faith_scripture",
            "faith_create_row", "faith_write", "faith_write_prayer", "faith_write_testimony",
            "faith_record_sermon", "faith_more"
        ).forEach { tag -> compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed() }
        compose.onNodeWithText("Recent").assertIsDisplayed()
        compose.onNodeWithText("Praying for").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/faith_home_360_light.png")
    }

    @Test fun `Bible home shows every section on a 360 dp screen`() {
        val state = BibleHomeState(
            loaded = true,
            place = ref("John 3:16"),
            today = TodayReading(ref("Psalm 121"), listOf("A psalm: honest prayer and praise.")),
            plans = listOf(PlanDay("morning-evening", "Morning and Evening", 3, "Psalm 23", ref("Psalm 23"))),
            journalCount = 2
        )
        compose.setContent { MeetMindTheme { BibleHomeContent(state, BibleHomeActions()) } }
        compose.waitForIdle()
        listOf(
            "bible_home_continue", "bible_home_today", "bible_home_guide", "bible_home_read",
            "bible_home_study", "bible_home_plan", "bible_home_topic",
            "bible_home_journal", "bible_home_more"
        ).forEach { tag -> compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed() }
        compose.onNodeWithText("Study a passage", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("How I feel").assertIsDisplayed()
        assert(compose.onAllNodesWithText("Reading plans", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/bible_home_360_light.png")
    }
}
