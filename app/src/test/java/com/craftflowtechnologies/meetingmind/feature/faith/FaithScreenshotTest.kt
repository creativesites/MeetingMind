package com.craftflowtechnologies.meetingmind.feature.faith

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.biblestudy.TodayReading
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.faith.PrayerPerson
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.EntryKind
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2200dp-xxhdpi", sdk = [34])
class FaithScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MeetMindDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    private fun ref(text: String) = ScriptureReferenceParser.parse(text)!!

    // Test-only sample content: it never reaches the app.
    private fun note(id: String, title: String, type: RecordingType, status: NoteStatus = NoteStatus.OPEN) = Note(
        id = id, title = title, workflow = type, notebookId = null, createdAt = 1_700_000_000_000, updatedAt = 1_700_000_000_000,
        eventDate = null, pinned = false, isPrivate = false, status = status, answeredAt = null, metadata = emptyMap(), plainText = ""
    )

    private val populated = FaithHomeState(
        place = ref("John 3:16"),
        today = TodayReading(ref("Psalm 23"), listOf("A psalm of trust, short enough to read slowly.", "Good for a day that feels full.")),
        devotional = FaithDevotionalLine("Rest in the middle of the work", "Matthew 11:28-30", written = true),
        reading = "Morning and Evening",
        verse = FaithVerseLine("Philippians 4:6-7", "WEB", "In nothing be anxious, but in everything, by prayer and petition with thanksgiving, let your requests be made known to God.", "World English Bible, public domain"),
        plan = FaithPlanLine("Gospels in 90 days", "Luke 4–5", 12, 90, 13, 1, ref("Luke 4")),
        prayerPeople = listOf(PrayerPerson(1, "Grace", "recovering from surgery", null, 3), PrayerPerson(2, "Daniel", "", null, 1)),
        requests = listOf(note("r1", "Wisdom about the new job", RecordingType.PRAYER_REQUEST)),
        answered = 2, journeyLine = "3 prayers · 2 reflections", entryCount = 14, scripturePassages = 9, collections = 2,
        recent = listOf(
            note("n1", "Sunday: Walking in Grace", RecordingType.SERMON),
            note("n2", "Thankful for this week", RecordingType.GRATITUDE),
            note("n3", "For my brother's interview", RecordingType.PRAYER_REQUEST)
        )
    )
    private val firstTime = FaithHomeState(start = ref("John 1"), today = populated.today)

    private fun shot(name: String, state: FaithHomeState, dark: Boolean = false) {
        compose.setContent { MeetMindTheme(darkTheme = dark) { FaithHomeContent(state, FaithHomeActions()) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/faith_home_$name.png")
    }

    @Test fun `the reading line sits under Today's devotional and writes one with AI`() {
        var wrote = 0
        compose.setContent { MeetMindTheme { FaithHomeContent(populated, FaithHomeActions(onWriteDevotionalWithAi = { wrote++ })) } }
        compose.onNodeWithTag("faith_reading_line").assertExists()
        compose.onNodeWithText("Reading: Morning and Evening").assertExists()
        compose.onNodeWithTag("faith_write_ai").performClick()
        assertEquals(1, wrote)
        // Nothing to say, nothing shown.
    }

    @Test fun `populated light`() = shot("populated_light", populated)
    @Test fun `populated dark`() = shot("populated_dark", populated, dark = true)
    @Test fun `first time light`() = shot("first_time_light", firstTime)

    /** Every Faith entry the registry lists as shown here is on screen under its registry label. */
    @Test fun `the home shows the registry labels and the writing tiles open notes`() {
        val written = mutableListOf<RecordingType>()
        val state = mutableStateOf(populated)
        compose.setContent { MeetMindTheme { FaithHomeContent(state.value, FaithHomeActions(onWrite = { written += it })) } }
        compose.waitForIdle()
        val shown = listOf(
            EntryPoints.faithBible, EntryPoints.faithGuideMe, EntryPoints.faithJustRead, EntryPoints.faithDevotional, EntryPoints.faithPray,
            EntryPoints.faithCircles, EntryPoints.faithVerseWrite, EntryPoints.faithVerseShare, EntryPoints.faithTestimonies, EntryPoints.faithRecordSermon
        ) + EntryPoints.faithWriteTiles
        shown.forEach { e -> compose.onNodeWithText(e.label, useUnmergedTree = true).assertExists("missing ${e.label}") }
        // With today's reading set, the hero leads with it (the Faith redesign), not Continue.
        compose.onNodeWithText("Today's reading · ", substring = true, useUnmergedTree = true).assertExists()
        listOf("Bible study", "Search", "Begin").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
        EntryPoints.faithWriteTiles.forEach { assertEquals(EntryKind.WRITE, it.kind) }
        compose.onNodeWithText("Write a prayer", useUnmergedTree = true).performClick()
        assertEquals(listOf(RecordingType.PRAYER), written)

        state.value = firstTime
        compose.waitForIdle()
        compose.onNodeWithText("Today's reading · ", substring = true, useUnmergedTree = true).assertExists()
    }
}
