package com.craftflowtechnologies.meetingmind.core.ui.mm

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme

/*
 * Sample catalogue of every component. Used by the @Preview functions below and by
 * MMComponentScreenshotTest, so what you see in Studio is what the screenshot tests record.
 */

/** A stand-in for the companion in samples. */
@Composable
private fun SampleAvatar() {
    Box(Modifier.size(MMSize.minTouch).background(MM.colors.accentWash, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Mic, null, tint = MM.colors.accent)
    }
}

@Composable
internal fun SampleHeaders() = Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.l)) {
    ScreenHeader("Notes", actions = {
        IconButton(onClick = {}) { Icon(Icons.Rounded.Search, "Search", tint = MM.colors.inkSecondary) }
        IconButton(onClick = {}) { Icon(Icons.Rounded.Settings, "Settings", tint = MM.colors.inkSecondary) }
    })
    HomeHeader("Good morning, Ada", subtitle = "Two meetings today", leading = { SampleAvatar() }, actions = {
        IconButton(onClick = {}) { Icon(Icons.Rounded.Settings, "Settings", tint = MM.colors.inkSecondary) }
    })
    SectionHeader("Tasks", count = 5, actionLabel = "All", onAction = {})
    SectionHeader("Recent notes")
}

@Composable
internal fun SampleSurfaces() = Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.l)) {
    HeroCard {
        Text("NEXT UP", style = MM.type.overline, color = MM.colors.accent)
        Text("Design review at 10:30", style = MM.type.heading, color = MM.colors.ink)
        Text("Room 4, six people", style = MM.type.secondary, color = MM.colors.inkSecondary)
    }
    MMCard {
        Text("A grouped card", style = MM.type.heading, color = MM.colors.ink)
        InsetPanel(Modifier.padding(top = MM.space.s)) {
            Text("An inset panel for fields and nested content", style = MM.type.body, color = MM.colors.ink)
        }
    }
}

@Composable
internal fun SampleRows() = Column(Modifier.padding(MM.space.l)) {
    ListRow("Weekly sync", subtitle = "Work", meta = "10:30", leading = { SampleAvatar() }, trailing = {
        Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkMuted)
    }, onClick = {})
    NoteRow(
        NoteRowModel(
            title = "Q3 planning", preview = "Agreed to ship the redesign before the offsite, with Sam owning the migration plan.",
            spaceColor = MM.colors.accent, hasRecording = true, transcriptStatus = NoteTranscriptStatus.Ready,
            taskCount = 3, attachmentCount = 2, timeLabel = "Today", pinned = true, isPrivate = true
        ), onClick = {}
    )
    NoteRow(
        NoteRowModel(
            title = "Sunday reflection", preview = "Grateful for a quiet week.", spaceColor = MM.colors.gold,
            hasRecording = true, transcriptStatus = NoteTranscriptStatus.Processing, timeLabel = "Sun"
        ), onClick = {}
    )
    NoteRow(NoteRowModel(title = "Untitled", spaceColor = MM.colors.success, transcriptStatus = NoteTranscriptStatus.Failed, timeLabel = "Mon"))
}

@Composable
internal fun SampleButtons() = Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
    PrimaryButton("Record", onClick = {}, leadingIcon = Icons.Rounded.Mic)
    SecondaryButton("Import audio", onClick = {})
    TextAction("See all", onClick = {})
    PrimaryButton("Unavailable", onClick = {}, enabled = false)
}

@Composable
internal fun SampleChoices() = Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
    var picked by remember { mutableStateOf(setOf("Work", "Tasks")) }
    FilterChipRow(listOf("All", "Work", "Faith", "Study", "Tasks"), picked, onToggle = { picked = if (it in picked) picked - it else picked + it })
    var seg by remember { mutableIntStateOf(1) }
    SegmentedControl(listOf("Day", "Week", "Month"), seg, onSelect = { seg = it })
}

@Composable
internal fun SampleFeedback() = Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
    StatusLine(StatusKind.Info, "Summaries are on-device and may take a minute.")
    StatusLine(StatusKind.Warning, "No model downloaded, showing the transcript only.", actionLabel = "Get model", onAction = {})
    StatusLine(StatusKind.Error, "Summary failed.", actionLabel = "Retry", onAction = {})
    EmptyState(
        title = "No notes yet", body = "Record a meeting or write a note and it will appear here.",
        illustration = { SampleAvatar() }, action = { PrimaryButton("Record", onClick = {}) }
    )
}

@Composable
internal fun SampleHome() = HomeScaffold(
    header = { HomeHeader("Good morning", subtitle = "Tuesday", leading = { SampleAvatar() }) },
    hero = { HeroCard { Text("Next up: Design review", style = MM.type.heading, color = MM.colors.ink) } },
    modifier = Modifier.size(width = SampleWidth, height = SampleHeight)
) {
    item { SectionHeader("Tasks", count = 2, actionLabel = "All", onAction = {}) }
    item {
        MMCard {
            ListRow("Send the notes", subtitle = "Due today")
            ListRow("Book the offsite", subtitle = "Due Friday")
        }
    }
    item { SectionHeader("Recent notes") }
    item { NoteRow(NoteRowModel(title = "Q3 planning", preview = "Agreed to ship the redesign.", spaceColor = MM.colors.accent, timeLabel = "Today")) }
}

private val SampleWidth = 360.dp
private val SampleHeight = 520.dp

@Composable
internal fun SampleAccents() = Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
    MMAccent.entries.forEach { a ->
        MeetMindTheme(darkTheme = MM.colors.isDark, accent = a) {
            Surface(color = MM.colors.background) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    PrimaryButton(a.label, onClick = {})
                    HeroCard { Text(a.label, style = MM.type.bodyStrong, color = MM.colors.accent) }
                }
            }
        }
    }
}

@Composable
internal fun MMPreviewFrame(dark: Boolean, content: @Composable () -> Unit) = MeetMindTheme(darkTheme = dark) {
    Surface(color = MM.colors.background, contentColor = Color.Unspecified) { content() }
}

private const val Night = Configuration.UI_MODE_NIGHT_YES

@Preview(name = "Headers light", showBackground = true) @Composable private fun HeadersLight() = MMPreviewFrame(false) { SampleHeaders() }
@Preview(name = "Headers dark", uiMode = Night) @Composable private fun HeadersDark() = MMPreviewFrame(true) { SampleHeaders() }
@Preview(name = "Surfaces light", showBackground = true) @Composable private fun SurfacesLight() = MMPreviewFrame(false) { SampleSurfaces() }
@Preview(name = "Surfaces dark", uiMode = Night) @Composable private fun SurfacesDark() = MMPreviewFrame(true) { SampleSurfaces() }
@Preview(name = "Rows light", showBackground = true) @Composable private fun RowsLight() = MMPreviewFrame(false) { SampleRows() }
@Preview(name = "Rows dark", uiMode = Night) @Composable private fun RowsDark() = MMPreviewFrame(true) { SampleRows() }
@Preview(name = "Buttons light", showBackground = true) @Composable private fun ButtonsLight() = MMPreviewFrame(false) { SampleButtons() }
@Preview(name = "Buttons dark", uiMode = Night) @Composable private fun ButtonsDark() = MMPreviewFrame(true) { SampleButtons() }
@Preview(name = "Choices light", showBackground = true) @Composable private fun ChoicesLight() = MMPreviewFrame(false) { SampleChoices() }
@Preview(name = "Choices dark", uiMode = Night) @Composable private fun ChoicesDark() = MMPreviewFrame(true) { SampleChoices() }
@Preview(name = "Feedback light", showBackground = true) @Composable private fun FeedbackLight() = MMPreviewFrame(false) { SampleFeedback() }
@Preview(name = "Feedback dark", uiMode = Night) @Composable private fun FeedbackDark() = MMPreviewFrame(true) { SampleFeedback() }
@Preview(name = "HomeScaffold light", showBackground = true) @Composable private fun HomeLight() = MMPreviewFrame(false) { SampleHome() }
@Preview(name = "HomeScaffold dark", uiMode = Night) @Composable private fun HomeDark() = MMPreviewFrame(true) { SampleHome() }
@Preview(name = "Accents light", showBackground = true) @Composable private fun AccentsLight() = MMPreviewFrame(false) { SampleAccents() }
@Preview(name = "Accents dark", uiMode = Night) @Composable private fun AccentsDark() = MMPreviewFrame(true) { SampleAccents() }
