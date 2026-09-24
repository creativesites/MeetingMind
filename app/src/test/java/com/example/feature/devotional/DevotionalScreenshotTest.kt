package com.example.feature.devotional

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.core.devotional.DailyDevotional
import com.example.core.devotional.Devotional
import com.example.core.devotional.DevotionalLabels
import com.example.core.devotional.DevotionalNotes
import com.example.core.devotional.DevotionalOrigin
import com.example.core.devotional.LiturgicalCalendar
import com.example.core.devotional.LocalDay
import com.example.core.devotional.Quote
import com.example.core.model.Note
import com.example.core.model.NoteDocument
import com.example.core.model.NoteStatus
import com.example.core.model.RecordingType
import com.example.core.scripture.ScriptureReferenceParser
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2600dp-xxhdpi", sdk = [34])
class DevotionalScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val date = LocalDate.of(2026, 9, 24)

    private fun daily(d: Devotional, id: String = "n1", created: Long = 0, key: String? = null): DailyDevotional {
        val (blocks, refs) = DevotionalNotes.build(id, d)
        val meta = DevotionalNotes.metadata(d).let { m -> if (key != null) m + (DevotionalNotes.META_KEY to key) else m }
        val note = Note(id, d.title, RecordingType.DEVOTIONAL, null, created, created, 0, false, false, NoteStatus.OPEN, null, meta)
        return DailyDevotional(note, d, NoteDocument(note, blocks, emptyList(), emptyList(), refs))
    }

    private fun capture(state: DevotionalUiState, name: String) {
        compose.setContent {
            MeetMindTheme { DevotionalContent(state, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, liveScripture = false) }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun `an AI devotional reads like a page`() {
        val d = Devotional(
            day = LocalDay.of(date), origin = DevotionalOrigin.CLOUD_AI, title = "Rest for the weary",
            scripture = listOf(ScriptureReferenceParser.parse("Matthew 11:28-30")!!),
            reflection = listOf(
                "Jesus doesn't wait for the tired to tidy themselves up. He calls them as they are — heavy, worn, a little behind on everything.",
                "Rest, in his hands, isn't a reward for finishing. It's where the work begins: yoked to him, walking at his pace."
            ),
            application = listOf("Take ten quiet minutes before your first meeting", "Name one burden and hand it over"),
            prayer = "Lord Jesus, I come tired. Teach me your pace today, and let me find rest in walking with you. Amen.",
            motivation = "You don't have to carry today alone.",
            insight = Quote("Thou hast made us for thyself, O Lord, and our heart is restless until it finds its rest in thee.", "Augustine of Hippo", "Confessions", emptySet()),
            question = "Where are you trying to earn rest instead of receiving it?",
            label = DevotionalLabels.CLOUD, engine = "gemini"
        )
        capture(DevotionalUiState(loading = false, today = daily(d), season = LiturgicalCalendar.dayOf(date), date = date), "devotional_ai")
    }

    @Test fun `before it's written there's an invitation`() {
        capture(DevotionalUiState(loading = false, season = LiturgicalCalendar.dayOf(date), date = date), "devotional_intro")
    }

    @Test fun `several devotionals in a day, and a failed new one says so`() {
        val base = Devotional(
            day = LocalDay.of(date), origin = DevotionalOrigin.CLASSIC, title = "Morning, 24 September",
            scripture = listOf(ScriptureReferenceParser.parse("Psalm 46:1")!!), reflection = listOf("God is our refuge and strength."), label = "Charles Spurgeon · Morning and Evening"
        )
        val morning = daily(base, "a", 1_790_000_000_000L, key = "${date}-earlier-1")
        val phone = daily(base.copy(origin = DevotionalOrigin.DEVICE_AI, title = "Held in the middle of it", label = DevotionalLabels.DEVICE), "b", 1_790_010_000_000L)
        capture(
            DevotionalUiState(loading = false, today = phone, all = listOf(morning, phone), writeError = "Gemini took too long to answer.", season = LiturgicalCalendar.dayOf(date), date = date),
            "devotional_many"
        )
    }

    @Test fun `the new devotional sheet lets you choose who writes it`() {
        compose.setContent {
            MeetMindTheme {
                AskDevotionalSheet(com.example.core.devotional.DevotionalProfile(), {}, {},
                    writers = setOf(com.example.ai.devotional.DevotionalWriter.AUTO, com.example.ai.devotional.DevotionalWriter.CLASSIC, com.example.ai.devotional.DevotionalWriter.DEVICE))
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/devotional_new_sheet.png")
    }
}
