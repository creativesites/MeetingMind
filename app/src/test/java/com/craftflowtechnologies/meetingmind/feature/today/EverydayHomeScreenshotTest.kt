package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.Color
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.settings.HomePicker
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Everyday home (populated and empty, light and dark) and Settings' home picker.
 * The companion draws on Canvas with static poses, so the images are deterministic.
 * Record with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class EverydayHomeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val identity = AppIdentity(spaces = setOf(NotebookSpace.WORK, NotebookSpace.PERSONAL), displayName = "Ana Banda")

    private fun populated() = EverydayModel(
        identity = identity, greeting = "Good morning, Ana", contextLine = "2 events today · 1 recording processing",
        next = EverydayNext(
            eyebrow = "NEXT · IN 25 MIN", title = "Design review", subtitle = "Last time: Q3 planning",
            primaryLabel = "Record", onPrimary = {}, secondaryLabel = "Prepare", onSecondary = {}, onClick = {}
        ),
        needs = listOf(
            EverydayNeed("j", NeedsKind.Processing, "Call with Sarah", "Processing · 64%", onClick = {}),
            EverydayNeed("t1", NeedsKind.Task, "Send the revised budget", null, "Today", "Done", {}, {}),
            EverydayNeed("t2", NeedsKind.Task, "Book the offsite venue", null, "Yesterday", "Done", {}, {}),
            EverydayNeed("d", NeedsKind.Devotional, "Abide in the vine", "John 15:1-8", onClick = {})
        ),
        recent = listOf(
            EverydayNote("1", NoteRowModel("Product sync", "Agreed to ship the referral programme; Tom owns pricing.", Color.Gray, hasRecording = true, transcriptStatus = NoteTranscriptStatus.Ready, taskCount = 3, timeLabel = "10:30")),
            EverydayNote("2", NoteRowModel("Reading list", "Three books to start before the retreat.", Color.Gray, timeLabel = "Yesterday")),
            EverydayNote("3", NoteRowModel("Interview: backend", "Strong on systems design; follow up on references.", Color.Gray, hasRecording = true, transcriptStatus = NoteTranscriptStatus.Processing, timeLabel = "8 Oct")),
            EverydayNote("4", NoteRowModel("Weekend plans", "", Color.Gray, pinned = true, timeLabel = "6 Oct")),
            EverydayNote("5", NoteRowModel("Sunday sermon", "Abide in the vine.", Color.Gray, hasRecording = true, attachmentCount = 1, timeLabel = "5 Oct"))
        ),
        agenda = listOf(
            EverydayAgendaRow("a1", "Stand-up", "Team", "11:00 – 11:15") {},
            EverydayAgendaRow("a2", "Lunch with Tom", "Cafe Aroma", "13:00 – 14:00") {}
        )
    )

    private fun empty() = EverydayModel(identity = identity, greeting = "Good evening, Ana", contextLine = null)

    @Composable
    private fun Harness(dark: Boolean, content: @Composable () -> Unit) = MMPreviewFrame(dark) {
        CompositionLocalProvider(
            LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings()),
            LocalCompanionForceCanvas provides true,
            LocalCompanionReducedMotion provides true,
            content = content
        )
    }

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent { Harness(dark, content) }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/home/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun everyday_populated_light() = shoot("everyday_populated", false) { EverydayHomeContent(populated(), EverydayActions()) }
    @Test fun everyday_populated_dark() = shoot("everyday_populated", true) { EverydayHomeContent(populated(), EverydayActions()) }
    @Test fun everyday_empty_light() = shoot("everyday_empty", false) { EverydayHomeContent(empty(), EverydayActions()) }
    @Test fun everyday_empty_dark() = shoot("everyday_empty", true) { EverydayHomeContent(empty(), EverydayActions()) }

    @Test fun picker_light() = shoot("home_picker", false) { HomePicker(HomeStyle.EVERYDAY, {}) }
    @Test fun picker_dark() = shoot("home_picker", true) { HomePicker(HomeStyle.TODAY, {}) }

    @Test fun `needs you shows at most four lines`() {
        val many = populated().copy(needs = populated().needs + EverydayNeed("x", NeedsKind.Task, "Fifth line", null, onClick = {}))
        rule.setContent { Harness(false) { EverydayHomeContent(many, EverydayActions()) } }
        rule.onNodeWithText("Fifth line").assertDoesNotExist()
        rule.onNodeWithText("Needs you · 5").assertIsDisplayed()
    }

    @Test fun `empty sections render nothing`() {
        val bare = populated().copy(needs = emptyList(), agenda = emptyList())
        rule.setContent { Harness(false) { EverydayHomeContent(bare, EverydayActions()) } }
        rule.onNodeWithText("Needs you", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Today · ", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Recent notes").assertIsDisplayed()
    }

    @Test fun `picking a home reports the choice`() {
        var picked: HomeStyle? = null
        rule.setContent { Harness(false) { HomePicker(HomeStyle.EVERYDAY, { picked = it }) } }
        rule.onNodeWithTag("home_today").performClick()
        assertEquals(HomeStyle.TODAY, picked)
    }
}
