package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.database.FindingRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.FilterChipRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustKind
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustMark
import com.craftflowtechnologies.meetingmind.core.work.RiskItem
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch

/** The four chips of Work › Decisions. Requirements and Risks are shown for every profile; empty when unused. */
internal enum class DecisionChip(val label: String) { DECISIONS("Decisions"), QUESTIONS("Open questions"), RISKS("Risks"), REQUIREMENTS("Requirements") }

/**
 * Work › Decisions (artifact #2c): the decisions across every project. "To confirm" holds the proposed ones, with
 * Confirm and Not decided; "In force" holds the active ones, and "Replaced" the superseded ones (the supersede history).
 * Rows open the moment the decision was said (the evidence's start time, W-08), not the top of the recording.
 */
@Composable
internal fun WorkDecisionsPane(
    viewModel: WorkViewModel,
    onOpenMeeting: (String, Long?) -> Unit,
    modifier: Modifier = Modifier,
    initial: DecisionChip = DecisionChip.DECISIONS
) {
    var chip by rememberSaveable { mutableStateOf(initial) }
    val decisions by viewModel.decisionLog.collectAsState()
    val questions by viewModel.openQuestions.collectAsState()
    val requirements by remember(viewModel) { viewModel.work.observeRequirements() }.collectAsState(emptyList())
    var risks by remember { mutableStateOf<List<RiskItem>>(emptyList()) }
    // "Not decided" keeps the item out of To confirm for this visit; it does not change the item.
    var notDecided by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(chip) {
        if (chip == DecisionChip.RISKS) risks = viewModel.loadAllRisks()
    }

    // Opens the recording at the moment the item was said: the evidence's start time (W-08).
    val openMoment: (String) -> Unit = { id ->
        scope.launch {
            val evidence = viewModel.evidenceOf(id)
            if (evidence != null) onOpenMeeting(evidence.first, evidence.second)
        }
    }

    LazyColumn(
        modifier.fillMaxSize().testTag("work_decisions_list"),
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xxl),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        item(key = "chips") {
            FilterChipRow(
                options = DecisionChip.entries,
                selected = setOf(chip),
                onToggle = { chip = it },
                label = { it.label }
            )
        }
        when (chip) {
            DecisionChip.DECISIONS -> {
                val proposed = decisions.filter { it.detail == "PROPOSED" && it.id !in notDecided }
                val active = decisions.filter { it.detail == "ACTIVE" }
                val replaced = decisions.filter { it.detail == "SUPERSEDED" }
                if (proposed.isEmpty() && active.isEmpty() && replaced.isEmpty()) item(key = "empty-dec") {
                    EmptyState(
                        title = "No decisions yet",
                        body = "Decisions from your meetings build up here, newest first, each linked to the moment it was made."
                    )
                }
                if (proposed.isNotEmpty()) item(key = "h-confirm") { SectionHeader("To confirm", count = proposed.size) }
                if (proposed.isNotEmpty()) item(key = "c-confirm") {
                    FindingCard(proposed, onOpen = openMoment, confirmable = true,
                        onConfirm = { d -> scope.launch { viewModel.work.confirmDecision(d.id) } },
                        onNotDecided = { d -> notDecided = notDecided + d.id })
                }
                if (active.isNotEmpty()) item(key = "h-force") { SectionHeader("In force", count = active.size) }
                if (active.isNotEmpty()) item(key = "c-force") { FindingCard(active, onOpen = openMoment) }
                if (replaced.isNotEmpty()) item(key = "h-replaced") { SectionHeader("Replaced", count = replaced.size) }
                if (replaced.isNotEmpty()) item(key = "c-replaced") { FindingCard(replaced, onOpen = openMoment, muted = true) }
            }
            DecisionChip.QUESTIONS -> {
                if (questions.isEmpty()) item(key = "empty-q") {
                    EmptyState(title = "No open questions", body = "Questions left unanswered in your meetings gather here until someone answers them.")
                }
                if (questions.isNotEmpty()) item(key = "c-q") {
                    FindingCard(
                        questions, onOpen = openMoment,
                        trailing = { q -> TextAction("Answered", { viewModel.resolveQuestion(q, null) }) }
                    )
                }
            }
            DecisionChip.RISKS -> {
                if (risks.isEmpty()) item(key = "empty-r") {
                    EmptyState(title = "No risks logged", body = "Risks identified in your meetings and notes gather here by severity.")
                }
                if (risks.isNotEmpty()) item(key = "c-r") {
                    MMCard(contentPadding = MM.space.m) {
                        risks.forEach { r ->
                            Column(Modifier.fillMaxWidth().padding(vertical = MM.space.xs)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                    Text(r.severity.lowercase().replaceFirstChar { it.uppercase() }, style = MM.type.caption, color = if (r.severity == "HIGH") MM.colors.danger else MM.colors.warning)
                                    Text(r.title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                                val meta = listOfNotNull(r.meetingTitle, r.counterparty).joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            DecisionChip.REQUIREMENTS -> {
                if (requirements.isEmpty()) item(key = "empty-req") {
                    EmptyState(title = "No requirements yet", body = "Requirements a meeting names, such as a deadline a client sets or a must-have, gather here.")
                }
                if (requirements.isNotEmpty()) item(key = "c-req") { FindingCard(requirements, onOpen = openMoment) }
            }
        }
    }
}

/** One card of findings: each row has its text, its meeting and day, and opens the moment it was said. */
@Composable
private fun FindingCard(
    rows: List<FindingRow>,
    onOpen: (String) -> Unit,
    confirmable: Boolean = false,
    onConfirm: (FindingRow) -> Unit = {},
    onNotDecided: (FindingRow) -> Unit = {},
    muted: Boolean = false,
    trailing: (@Composable (FindingRow) -> Unit)? = null
) {
    MMCard(contentPadding = MM.space.m) {
        rows.forEach { d ->
            Column(Modifier.fillMaxWidth().padding(vertical = MM.space.xs)) {
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(d.id) },
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Text(
                        d.text, style = MM.type.bodyStrong,
                        color = if (muted) MM.colors.inkMuted else MM.colors.ink,
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(d.meetingTitle.takeIf { it.isNotBlank() }, dayLabel(d.createdAt)).joinToString(" · ")
                    Text(meta, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (confirmable) TrustMark(TrustKind.SUGGESTED)
                }
                if (confirmable) Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    TextAction("Confirm", { onConfirm(d) })
                    TextAction("Not decided", { onNotDecided(d) })
                }
                trailing?.let { t -> Row { t(d) } }
            }
        }
    }
}
