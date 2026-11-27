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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.ui.work.BriefingButton
import com.craftflowtechnologies.meetingmind.core.ui.work.BriefingCard
import com.craftflowtechnologies.meetingmind.core.ui.work.BriefingChip
import com.craftflowtechnologies.meetingmind.core.ui.work.BriefingLabel
import com.craftflowtechnologies.meetingmind.core.ui.work.DayRibbon
import com.craftflowtechnologies.meetingmind.core.ui.work.RibbonBlock
import com.craftflowtechnologies.meetingmind.core.ui.work.RibbonDot
import com.craftflowtechnologies.meetingmind.core.work.AttentionKind
import com.craftflowtechnologies.meetingmind.core.work.AttentionRow
import com.craftflowtechnologies.meetingmind.core.work.ChangeGroup
import com.craftflowtechnologies.meetingmind.core.work.ChangeLine
import com.craftflowtechnologies.meetingmind.core.work.PulseDayLine
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.feature.work.home.HomeAttentionKind
import com.craftflowtechnologies.meetingmind.feature.work.home.WorkHomeAttention
import com.craftflowtechnologies.meetingmind.ui.theme.Briefing
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date

/** A recording on the Work home that needs the person: it did not process, is ready to review, or its follow-up is unsent. */
data class PulseRecording(val meetingId: String, val title: String, val line: String)

/** Everything the Your day card shows, already worked out from the database. Nothing here needs a model. */
data class PulseUi(
    val attention: List<AttentionRow> = emptyList(),
    /** "Since yesterday", "Since this morning", "Since Tue". */
    val sinceLabel: String = "Since yesterday",
    val changes: List<ChangeGroup> = emptyList(),
    val today: List<PulseDayLine> = emptyList(),
    /** True when there is nothing yet in the record: the card offers a way to begin instead of an empty state. */
    val coldStart: Boolean = true,
    val summary: String = "",
    /** Things shared in, waiting to be filed. */
    val inbox: Int = 0,
    /** Recordings that did not process. Their row offers Try again. */
    val failed: List<PulseRecording> = emptyList(),
    /** Recordings ready to wrap up. Their row offers Review. */
    val toReview: List<PulseRecording> = emptyList(),
    /** Follow-ups not sent yet. Their row offers Review. */
    val followUps: List<PulseRecording> = emptyList(),
    /** The label of the one button that leads the card (Prepare or Review); null hides it. */
    val primaryLabel: String? = null,
    /** Today's meetings, as blocks on the day ribbon. */
    val dayBlocks: List<RibbonBlock> = emptyList(),
    /** Today's timed tasks, as priority dots on the day ribbon. */
    val dayDots: List<RibbonDot> = emptyList(),
    /** The current time for the ribbon's now-line; null hides it. */
    val dayNow: LocalTime? = null
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
    val onTemplates: () -> Unit = {},
    val onOpenInbox: () -> Unit = {},
    /** The card's leading button: Prepare for the next meeting, or Review. */
    val onPrimary: () -> Unit = {},
    /** Wrap up a recording (second argument true opens its follow-up). */
    val onWrapUp: (PulseRecording, Boolean) -> Unit = { _, _ -> },
    /** Try again on a recording that did not process. */
    val onRetry: (PulseRecording) -> Unit = {},
    /** Mark an overdue or due commitment done. */
    val onDone: (AttentionRow) -> Unit = {},
    /** Open the full Pulse. */
    val onOpenPulse: () -> Unit = {}
)

private const val MAX_CHANGES_SHOWN = 3

/** One row of "Needs you": what it is, why, and the one verb it offers. */
private class DayRow(
    val kind: HomeAttentionKind,
    val key: String,
    val title: String,
    val detail: String,
    val onVerb: () -> Unit,
    val onOpen: () -> Unit
)

/**
 * "Your day" (the Work Pulse, docs/PLAN_PROFESSIONAL.md D5.1), in the Briefing card. A summary, the
 * day ribbon, at most four things that need the person (one verb each), what changed since the last
 * look, today's calendar with what is still open, and one leading button. It is dark in both themes.
 */
@Composable
fun PulseCard(ui: PulseUi, timeFormat: DateFormat, actions: PulseActions, modifier: Modifier = Modifier) {
    val rows = WorkHomeAttention.rank(dayRows(ui, actions), { it.kind }, { it.key })
    BriefingCard(modifier.testTag("pulse_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BriefingLabel("YOUR DAY", Modifier.weight(1f))
            BriefingChip("Open Pulse ›", onClick = actions.onOpenPulse)
        }
        Text(ui.summary, fontSize = 14.sp, color = Color.White.copy(alpha = 0.78f), modifier = Modifier.padding(top = 4.dp))

        if (ui.dayBlocks.isNotEmpty() || ui.dayDots.isNotEmpty()) {
            DayRibbon(
                modifier = Modifier.padding(top = 10.dp).testTag("pulse_ribbon"),
                blocks = ui.dayBlocks,
                dots = ui.dayDots,
                now = ui.dayNow,
                onDark = true
            )
        }

        if (rows.isNotEmpty()) {
            Divider()
            BriefingLabel("NEEDS YOU")
            rows.forEach { DayRowLine(it) }
        }

        if (ui.changeLines.isNotEmpty()) {
            Divider()
            BriefingLabel(ui.sinceLabel.uppercase())
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
            BriefingLabel("TODAY")
            ui.today.forEachIndexed { i, line -> DayLine(line, timeFormat, actions, first = i == 0) }
        }

        // Nothing in the record and nothing on the calendar: a way to begin, never a bare empty state.
        if (ui.coldStart && rows.isEmpty() && ui.changeLines.isEmpty() && ui.today.isEmpty()) {
            Divider()
            Text("Start with one conversation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Briefing.OnBrief)
            Text(
                "Record a meeting, or open Work to write one. What's decided and promised will show up here.",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp)
            )
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BriefingButton("● Record a conversation", onClick = actions.onRecord, primary = true)
                BriefingButton("Open Work", onClick = actions.onTemplates, primary = false)
            }
        }

        if (ui.inbox > 0) Text(
            "Inbox · ${ui.inbox} to file", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Briefing.Lavender,
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = actions.onOpenInbox)
                .padding(vertical = 4.dp).testTag("pulse_inbox")
        )

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ui.primaryLabel?.let { label ->
                BriefingButton(label, onClick = actions.onPrimary, primary = true, modifier = Modifier.testTag("pulse_primary"))
            }
            BriefingButton(
                "Ask about my work", onClick = actions.onAsk, primary = ui.primaryLabel == null,
                modifier = Modifier.testTag("pulse_ask")
            )
        }
    }
}

/** Every "needs you" candidate, before ranking: recordings first in their own kinds, then the Pulse rows. */
private fun dayRows(ui: PulseUi, actions: PulseActions): List<DayRow> {
    val failed = ui.failed.map { r ->
        DayRow(HomeAttentionKind.FAILED, "failed:${r.meetingId}", r.title, r.line, { actions.onRetry(r) }, { actions.onRetry(r) })
    }
    val reviews = ui.toReview.map { r ->
        DayRow(HomeAttentionKind.REVIEW, "review:${r.meetingId}", r.title, r.line, { actions.onWrapUp(r, false) }, { actions.onWrapUp(r, false) })
    }
    val followUps = ui.followUps.map { r ->
        DayRow(HomeAttentionKind.FOLLOW_UP, "followup:${r.meetingId}", r.title, r.line, { actions.onWrapUp(r, true) }, { actions.onWrapUp(r, true) })
    }
    val pulse = ui.attention.map { row ->
        val kind = when (row.kind) {
            AttentionKind.YOU_OWE -> HomeAttentionKind.YOU_OWE
            AttentionKind.DECISION_NEEDED -> HomeAttentionKind.DECISION
            AttentionKind.THEY_OWE -> HomeAttentionKind.THEY_OWE
            AttentionKind.QUIET -> HomeAttentionKind.QUIET
        }
        DayRow(kind, "row:" + (row.itemId ?: row.taskId ?: row.title), row.title, row.detail, { attentionVerb(kind, row, actions) }, { actions.onOpenRow(row) })
    }
    return failed + reviews + followUps + pulse
}

/** What the single verb of a Pulse row does: done, nudge, prepare, or open. */
private fun attentionVerb(kind: HomeAttentionKind, row: AttentionRow, actions: PulseActions) {
    when (kind) {
        HomeAttentionKind.YOU_OWE -> actions.onDone(row)
        HomeAttentionKind.THEY_OWE -> actions.onNudge(row)
        HomeAttentionKind.QUIET -> actions.onPrepareRow(row)
        else -> actions.onOpenRow(row)
    }
}

@Composable
private fun DayRowLine(row: DayRow) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = row.onOpen).padding(vertical = 8.dp).testTag("pulse_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Briefing.OnBrief, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                row.detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.66f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp)
            )
        }
        BriefingButton(WorkHomeAttention.verbFor(row.kind).label, onClick = row.onVerb, primary = false)
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

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Briefing.OnBrief)
    }
}
