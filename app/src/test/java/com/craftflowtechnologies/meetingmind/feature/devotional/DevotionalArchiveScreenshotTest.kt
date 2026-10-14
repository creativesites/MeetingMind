package com.craftflowtechnologies.meetingmind.feature.devotional

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.devotional.ArchiveItem
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalFormat
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalOrigin
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h900dp-xxhdpi", sdk = [34])
class DevotionalArchiveScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun item(id: String, day: String, title: String, passage: String, reflection: String? = null, fav: Boolean = false) = ArchiveItem(
        noteId = id, day = day, title = title, passage = passage, format = DevotionalFormat.REFLECTION, series = null,
        favourite = fav, evening = false, origin = DevotionalOrigin.CLOUD_AI, text = title, reflection = reflection
    )

    @Test fun `the archive reads as a journal`() {
        val items = listOf(
            item("a", "2026-09-26", "Held in the middle of it", "Psalm 46:1", "I read this in the car park before the meeting and it slowed me down."),
            item("b", "2026-09-25", "Bread for today", "Matthew 6:11"),
            item("c", "2026-09-24", "Rest for the weary", "Matthew 11:28–30", "Maybe the yoke is the list I was never asked to carry.\nI want to try ten quiet minutes.", fav = true),
            item("d", "2026-09-23", "A lamp for my feet", "Psalm 119:105")
        )
        compose.setContent { MeetMindTheme(darkTheme = false) { DevotionalArchive(items, {}, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/devotional_archive_reflections.png")
    }

    @Test fun `the reflections filter keeps only days with words of their own`() {
        val items = listOf(item("a", "2026-09-26", "One", "Psalm 46:1", "Mine."), item("b", "2026-09-25", "Two", "Matthew 6:11"))
        org.junit.Assert.assertEquals(listOf("a"), items.filter { ArchiveFilter.Reflections.accepts(it) }.map { it.noteId })
        org.junit.Assert.assertEquals(2, items.filter { ArchiveFilter.All.accepts(it) }.size)
    }
}
