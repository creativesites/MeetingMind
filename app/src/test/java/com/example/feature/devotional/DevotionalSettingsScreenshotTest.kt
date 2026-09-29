package com.example.feature.devotional

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.example.core.devotional.ArchiveItem
import com.example.core.devotional.DevotionalFormat
import com.example.core.devotional.DevotionalOrigin
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.DevotionalSeries
import com.example.core.devotional.SeriesProgress
import com.example.core.devotional.TraditionPreset
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
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class DevotionalSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val profile = TraditionPreset.ANGLICAN.applyTo(DevotionalProfile(enabled = true)).copy(
        series = DevotionalSeries.catalog.first().let { SeriesProgress(it.id, it.title, it.passages, LocalDate.now().minusDays(2).toEpochDay()) },
        topics = setOf("Joy", "Work & calling")
    )

    private fun shot(page: String?, name: String) {
        compose.setContent { MeetMindTheme { DevotionalSettingsContent(profile, { _, _ -> }, {}, startPage = page) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun `settings open on sections with what's chosen`() {
        shot(null, "devotional_settings")
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        compose.onNodeWithText("7 Days in Philippians", substring = true).assertIsDisplayed()
    }

    @Test fun `style page offers formats with their roots`() {
        shot("STYLE", "devotional_settings_style")
        compose.onNodeWithText("Mix it up").assertIsDisplayed()
    }

    @Test fun `series page shows progress`() {
        shot("SERIES", "devotional_settings_series")
        compose.onNodeWithText("Day 3 of 7", substring = true).assertIsDisplayed()
    }

    @Test fun `the archive lists, searches and filters`() {
        val items = (0 until 12).map { i ->
            ArchiveItem("n$i", LocalDate.of(2026, 9, 28).minusDays(i.toLong()).toString(), listOf("Bread for the road", "The mind of Christ", "Held in the middle of it", "Joy in chains")[i % 4],
                listOf("1 Kings 19:1-8", "Philippians 2:1-11", "Psalm 46:1", "Philippians 1:12-26")[i % 4], DevotionalFormat.entries[i % 5],
                if (i % 3 == 0) "7 Days in Philippians" else null, favourite = i % 5 == 0, evening = i == 1, origin = DevotionalOrigin.CLOUD_AI, text = "")
        }
        compose.setContent { MeetMindTheme { DevotionalArchive(items, {}, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/devotional_archive.png")
        compose.onNodeWithText("Past devotionals").assertIsDisplayed()
    }
}
