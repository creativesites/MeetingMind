package com.craftflowtechnologies.meetingmind.core.ui.work

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import java.time.LocalTime

/*
 * Light and dark previews for every Work component. The sample composables below are the same
 * content that WorkComponentsTest exercises, so what Studio shows is what the test covers.
 */

@Composable
private fun WorkPreviewFrame(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    MeetMindTheme(darkTheme = dark) {
        Surface(color = MM.colors.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

private fun workRow(key: String, body: @Composable () -> Unit): Pair<String, @Composable () -> Unit> = key to body

@Composable
private fun ChipsSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceChip(label = "10:42", onClick = {})
            Spacer(Modifier.width(8.dp))
            CiteChip(n = 1, onClick = {})
        }
        TrustMark(TrustKind.CONFIRMED)
        TrustMark(TrustKind.SUGGESTED)
        TrustMark(TrustKind.UNCERTAIN)
        WhereItRan(onDevice = true)
        WhereItRan(onDevice = false)
    }
}

@Composable
private fun RibbonSample(onDark: Boolean) {
    DayRibbon(
        now = LocalTime.of(9, 30),
        blocks = listOf(
            RibbonBlock(LocalTime.of(10, 0), LocalTime.of(10, 45), "Review", onClick = {}),
            RibbonBlock(LocalTime.of(15, 30), LocalTime.of(16, 15), "Pricing", onClick = {}, past = true)
        ),
        dots = listOf(
            RibbonDot(LocalTime.of(12, 0), color = MM.colors.danger, onClick = {}),
            RibbonDot(LocalTime.of(15, 0), color = MM.colors.warning)
        ),
        onDark = onDark
    )
}

@Composable
private fun RingSample() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProgressRing(done = 2, total = 5)
        ProgressRing(done = 3, total = 3, size = 64.dp)
    }
}

@Composable
private fun BriefingSample() {
    BriefingCard {
        BriefingLabel("TO REVIEW · PROJECT REVIEW · 10:00")
        Text("9 findings, about a minute", style = MM.type.heading, color = FixedWhite)
        RibbonSample(onDark = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BriefingButton(text = "Review findings", onClick = {})
            BriefingButton(text = "Open note", onClick = {}, primary = false)
            BriefingChip(text = "2 due", onClick = {})
        }
    }
}

@Composable
private fun SkySample() {
    SkyHero(time = LocalTime.of(9, 30)) {
        Column(Modifier.padding(24.dp)) {
            Text("Good morning, Ana", style = MM.type.display, color = FixedWhite)
            Text("Your first meeting is in an hour.", style = MM.type.secondary, color = FixedWhite)
        }
    }
}

@Composable
private fun PulseSample() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AttentionRow(
            kindLabel = "YOU OWE",
            title = "Send pricing options",
            subtitle = "Sarah · due Thu",
            verb = "Prepare",
            onVerb = {},
            onClick = {},
            leadingIcon = Icons.Filled.Work,
            tone = AttentionTone.WARNING
        )
        PulseHeaderCard(
            rows = listOf(
                workRow("You owe") { Text("Pricing options", style = MM.type.secondary, color = MM.colors.ink) },
                workRow("Next") { Text("Project Review · 10:00", style = MM.type.secondary, color = MM.colors.ink) }
            )
        )
        ChangeLine(label = "Credentials", old = "Mon 17 Nov", new = "Thu 19 Nov")
    }
}

@Composable
private fun ControlsSample() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProjectBar(color = projectColor("acme", MM.colors.isDark), modifier = Modifier.size(width = 3.dp, height = 36.dp))
            Spacer(Modifier.width(8.dp))
            Text("Acme Integration", style = MM.type.bodyStrong, color = MM.colors.ink)
        }
        SwipeActionRow(onSwipeRight = {}, onSwipeLeft = {}) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskCheck(checked = false, priority = 1, onToggle = {})
                Spacer(Modifier.width(8.dp))
                Text("Send API docs", style = MM.type.body, color = MM.colors.ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TaskCheck(checked = true, priority = 2, onToggle = {})
            TaskCheck(checked = false, priority = 3, onToggle = {})
            TaskCheck(checked = false, priority = 4, onToggle = {})
        }
        VerticalBar(
            items = listOf(
                VerticalBarItem("today", "Today", Icons.Filled.Home),
                VerticalBarItem("tasks", "Tasks", Icons.Filled.CheckCircle),
                VerticalBarItem("search", "Search", Icons.Filled.Search)
            ),
            selected = "tasks",
            onSelect = {},
            homeItem = {}
        )
    }
}

@Preview(name = "Chips light", showBackground = true)
@Composable
private fun ChipsLight() = WorkPreviewFrame(dark = false) { ChipsSample() }

@Preview(name = "Chips dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChipsDark() = WorkPreviewFrame(dark = true) { ChipsSample() }

@Preview(name = "Ring and ribbon light", showBackground = true)
@Composable
private fun RingLight() = WorkPreviewFrame(dark = false) {
    RingSample()
    RibbonSample(onDark = false)
}

@Preview(name = "Ring and ribbon dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RingDark() = WorkPreviewFrame(dark = true) {
    RingSample()
    RibbonSample(onDark = false)
}

@Preview(name = "Briefing card light", showBackground = true)
@Composable
private fun BriefingLight() = WorkPreviewFrame(dark = false) { BriefingSample() }

@Preview(name = "Briefing card dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BriefingDark() = WorkPreviewFrame(dark = true) { BriefingSample() }

@Preview(name = "Sky hero light", showBackground = true)
@Composable
private fun SkyLight() = WorkPreviewFrame(dark = false) { SkySample() }

@Preview(name = "Sky hero dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SkyDark() = WorkPreviewFrame(dark = true) { SkySample() }

@Preview(name = "Pulse rows light", showBackground = true)
@Composable
private fun PulseLight() = WorkPreviewFrame(dark = false) { PulseSample() }

@Preview(name = "Pulse rows dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PulseDark() = WorkPreviewFrame(dark = true) { PulseSample() }

@Preview(name = "Controls light", showBackground = true)
@Composable
private fun ControlsLight() = WorkPreviewFrame(dark = false) { ControlsSample() }

@Preview(name = "Controls dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ControlsDark() = WorkPreviewFrame(dark = true) { ControlsSample() }
