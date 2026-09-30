package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.AttentionAction
import com.craftflowtechnologies.meetingmind.core.work.AttentionRow
import com.craftflowtechnologies.meetingmind.core.work.ChangeGroup
import com.craftflowtechnologies.meetingmind.core.work.ChangeLine
import com.craftflowtechnologies.meetingmind.core.work.PulseDayLine
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.ui.theme.Briefing
import java.text.DateFormat
import java.util.Date

/** Everything the Pulse card shows, already worked out from the database. Nothing here needs a model. */
data class PulseUi(
    val attention: List<AttentionRow> = emptyList(),
    /** "Since yesterday", "Since this morning", "Since Tue". */
    val sinceLabel: String = "Since yesterday",
    val changes: List<ChangeGroup> = emptyList(),
    val today: List<PulseDayLine> = emptyList(),
    /** True when there is nothing yet in the record: the card offers a way to begin instead of an empty state. */
    val coldStart: Boolean = true,
    val summary: String = ""
) {
    val changeLines: List<ChangeLine> get() = changes.flatMap { it.lines }
}

/** What each part of the card does; the screen decides where it goes. */
class PulseActions(
    val onPlay: (AttentionRow) -> Unit = {},
    val onNudge: (AttentionRow) -> Unit = {},
    val onPrepareRow: (AttentionRow) -> Unit = {},
    val onOpenRow: (AttentionRow) -> Unit = {},
    val onPlayChange: (ChangeLine) -> Unit = {},
    val onPrepareEvent: (PulseEvent) -> Unit = {},
    val onRecordEvent: (PulseEvent) -> Unit = {},
    val onAsk: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onTemplates: () -> Unit = {}
)

private const val MAX_CHANGES_SHOWN = 3

/**
 * The Work Pulse (docs/PLAN_PROFESSIONAL.md D5.1): the briefing card's body. What needs you (at
 * most four), what changed, and today's calendar with what is still open. It keeps the briefing's
 * deep gradient in both light and dark.
 */
@Composable
fun PulseCard(ui: PulseUi, timeFormat: DateFormat, actions: PulseActions, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Briefing.Top, Briefing.Bottom))).padding(20.dp).testTag("pulse_card")
    ) {
        Text("YOUR DAY", fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = Briefing.Lavender)
        Text(ui.summary, fontSize = 14.sp, color = Color.White.copy(alpha = 0.78f), modifier = Modifier.padding(top = 2.dp))

        if (ui.attention.isNotEmpty()) {
            Divider()
            Label("NEEDS YOU")
            ui.attention.forEach { AttentionLine(it, actions) }
        }

        if (ui.changeLines.isNotEmpty()) {
            Divider()
            Label(ui.sinceLabel.uppercase())
            ui.changeLines.take(MAX_CHANGES_SHOWN).forEach { line ->
                Text(
                    line.text, fontSize = 14.sp, color = Briefing.OnBrief, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { actions.onPlayChange(line) }.padding(vertical = 5.dp)
                )
            }
            ui.changeLines.size.minus(MAX_CHANGES_SHOWN).takeIf { it > 0 }?.let {
                Text("and $it more changes", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp))
            }
        }

        if (ui.today.isNotEmpty()) {
            Divider()
            Label("TODAY")
            ui.today.forEachIndexed { i, line -> DayLine(line, timeFormat, actions, first = i == 0) }
        }

        // Nothing in the record and nothing on the calendar: a way to begin, never a bare empty state.
        if (ui.coldStart && ui.attention.isEmpty() && ui.changeLines.isEmpty() && ui.today.isEmpty()) {
            Divider()
            Text("Start with one conversation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Briefing.OnBrief)
            Text("Record a meeting, or begin from a template. What's decided and promised will show up here.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PulseButton("● Record a conversation", primary = true, onClick = actions.onRecord)
                PulseButton("Use a template", onClick = actions.onTemplates)
            }
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PulseButton("Ask about my work", primary = ui.attention.isEmpty() && !ui.coldStart, onClick = actions.onAsk, tag = "pulse_ask")
            if (!ui.coldStart || ui.today.isNotEmpty()) PulseButton("● Record", onClick = actions.onRecord)
        }
    }
}

@Composable
private fun AttentionLine(row: AttentionRow, actions: PulseActions) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).testTag("pulse_row")) {
        Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Briefing.OnBrief, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(row.detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.actions.forEach { a ->
                when (a) {
                    AttentionAction.PLAY -> SmallChip("▶ Evidence") { actions.onPlay(row) }
                    AttentionAction.NUDGE -> SmallChip("Nudge") { actions.onNudge(row) }
                    AttentionAction.PREPARE -> SmallChip("Prepare") { actions.onPrepareRow(row) }
                    AttentionAction.OPEN -> SmallChip("Open") { actions.onOpenRow(row) }
                }
            }
        }
    }
}

@Composable
private fun DayLine(line: PulseDayLine, fmt: DateFormat, actions: PulseActions, first: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("pulse_today")) {
        Row {
            Text(fmt.format(Date(line.event.begin)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Briefing.Rose, modifier = Modifier.padding(end = 10.dp))
            Text(line.event.title.ifBlank { "Untitled" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Briefing.OnBrief, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val open = listOfNotNull(
            line.youOwe.takeIf { it > 0 }?.let { "you owe $it" }, line.theyOwe.takeIf { it > 0 }?.let { "they owe $it" },
            line.questions.takeIf { it > 0 }?.let { "$it open ${if (it == 1) "question" else "questions"}" },
            line.decisionsNeeded.takeIf { it > 0 }?.let { "$it to decide" }
        )
        val detail = when {
            line.firstMeeting -> "First meeting with ${line.withLabel ?: line.event.title}"
            open.isNotEmpty() -> (listOfNotNull(line.withLabel) + open.joinToString(" · ")).joinToString(" — ")
            else -> line.withLabel?.let { "With $it — nothing open" } ?: "Nothing open"
        }
        Text(detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.66f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!line.firstMeeting) SmallChip("Prepare") { actions.onPrepareEvent(line.event) }
            if (first) SmallChip("● Record") { actions.onRecordEvent(line.event) }
        }
    }
}

@Composable private fun Divider() = Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(Color.White.copy(alpha = 0.10f)))

@Composable private fun Label(text: String) = Text(text, fontSize = 10.5.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Briefing.Lavender, modifier = Modifier.padding(bottom = 2.dp))

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Briefing.OnBrief)
    }
}

@Composable
private fun PulseButton(label: String, primary: Boolean = false, tag: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (primary) Briefing.OnBrief else Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (primary) Briefing.Top else Briefing.OnBrief) }
}
