package com.craftflowtechnologies.meetingmind.feature.work

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.timeline.WorkWeek
import com.craftflowtechnologies.meetingmind.core.work.BriefTarget
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.PlanChoice
import com.craftflowtechnologies.meetingmind.core.work.PlanDecision
import com.craftflowtechnologies.meetingmind.core.work.WeeklyReviewData
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The weekly review (docs/PLAN_PROFESSIONAL.md D5.6): the week's facts, the five questions, and
 * next week's plan in one pass — every open item is carried, moved to a day, or dropped. Nothing
 * changes until "Create next week's plan".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyReviewScreen(
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenContext: (ContextType, String) -> Unit,
    onOpenBrief: (BriefTarget) -> Unit
) {
    val context = LocalContext.current
    val review by viewModel.review.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadReview() }
    // What to do with each open item. Everything starts as "carry", so doing nothing loses nothing.
    val choices = remember { mutableStateMapOf<String, PlanDecision>() }

    Scaffold(containerColor = SurfaceBase) { padding ->
        val r = review
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("weekly_review"), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Column(Modifier.weight(1f)) {
                        Text("Weekly review", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.5).sp)
                        if (r != null) Text("${day(r.from)} – ${day(r.to)}", fontSize = 13.sp, color = InkMuted)
                    }
                }
            }
            if (r == null) { item { EmptyLine("Reading your week…") }; return@LazyColumn }

            item { Facts(r) }

            item { WorkSectionTitle("What changed") }
            if (r.changed.isEmpty()) item { EmptyLine("Nothing moved this week.") }
            r.changed.forEach { g ->
                item(key = "cg-${g.title}") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(g.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary,
                            modifier = if (g.entityType != null && g.entityId != null) Modifier.clickable { onOpenContext(g.entityType, g.entityId) } else Modifier)
                        g.lines.take(4).forEach { Text("• ${it.text}", fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 2.dp)) }
                    }
                }
            }

            item { WorkSectionTitle("What I finished", "${r.finished.size}") }
            if (r.finished.isEmpty()) item { EmptyLine("Nothing ticked off yet this week.") }
            items(r.finished.take(6), key = { "fin-" + it.id }) { Line(it, null) }

            item { WorkSectionTitle("What slipped", "${r.slipped.size}") }
            if (r.slipped.isEmpty()) item { EmptyLine("Nothing is overdue. ") }
            items(r.slipped, key = { "slp-" + it.id }) { Line(it, "Was due ${it.dueAt?.let(::day) ?: ""}") }

            item { WorkSectionTitle("Waiting on me", "${r.waitingOnMe.size}") }
            if (r.waitingOnMe.isEmpty()) item { EmptyLine("Nothing else on you.") }
            items(r.waitingOnMe.take(8), key = { "me-" + it.id }) { Line(it, it.dueAt?.let { d -> "Due ${day(d)}" }) }

            item { WorkSectionTitle("Waiting on them", "${r.waitingOnThem.size}") }
            if (r.waitingOnThem.isEmpty()) item { EmptyLine("Nobody owes you anything open.") }
            items(r.waitingOnThem.take(8), key = { "th-" + it.id }) { Line(it, it.dueAt?.let { d -> "Due ${day(d)}" }) }

            item { WorkSectionTitle("Needs attention") }
            if (r.projects.isEmpty()) item { EmptyLine("No project is asking for you.") }
            items(r.projects, key = { "pj-" + it.projectId }) { p ->
                val why = listOfNotNull(p.overdue.takeIf { it > 0 }?.let { "$it overdue" }, p.proposedDecisions.takeIf { it > 0 }?.let { "$it to decide" }, p.openRisks.takeIf { it > 0 }?.let { "$it open ${if (it == 1) "risk" else "risks"}" })
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(14.dp))
                    .clickable { onOpenContext(ContextType.PROJECT, p.projectId) }.padding(14.dp)) {
                    Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(why.joinToString(" · "), fontSize = 12.sp, color = InkSecondary)
                }
            }

            // Next week's plan, in one pass.
            item { WorkSectionTitle("Next week's plan") }
            val plan = r.planItems
            if (plan.isEmpty()) item { EmptyLine("Nothing open to plan. Enjoy the clear week.") }
            else {
                item { Text("Carry keeps it (a date that has passed moves to Monday). Choose a day to move it, or drop it.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                items(plan, key = { "plan-" + it.id }) { item ->
                    val d = choices[item.id] ?: PlanDecision(item.id, PlanChoice.CARRY)
                    PlanRow(item, d, viewModel.nextMonday) { choices[item.id] = it }
                }
                item {
                    val changing = choices.values.count { it.choice != PlanChoice.CARRY }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)
                        .clip(RoundedCornerShape(50)).background(Accent).clickable {
                            viewModel.applyPlan(plan.map { choices[it.id] ?: PlanDecision(it.id, PlanChoice.CARRY) }) { n ->
                                choices.clear()
                                Toast.makeText(context, if (n == 0) "Next week's plan is set" else "Next week's plan is set · $n updated", Toast.LENGTH_SHORT).show()
                            }
                        }.padding(vertical = 14.dp).testTag("weekly_create_plan"), contentAlignment = Alignment.Center) {
                        Text("Create next week's plan" + if (changing > 0) " · $changing changes" else "", color = OnAccent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }

            item {
                Row(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) { Pill("✨ Create brief") { onOpenBrief(BriefTarget.weekly) } }
                Text("A short status, with every line traced to what was said, ready to send.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun Facts(r: WeeklyReviewData) {
    val w: WorkWeek = r.week.work ?: WorkWeek(0, 0, 0, 0, 0, 0)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Fact("${w.meetings}", "Meetings", Modifier.weight(1f))
        Fact("${w.decisions}", "Decisions", Modifier.weight(1f))
        Fact("${w.commitmentsMade}", "Promised", Modifier.weight(1f))
        Fact("${w.commitmentsDone}", "Done", Modifier.weight(1f))
        Fact("${w.openQuestions}", "Open questions", Modifier.weight(1f))
    }
}

@Composable
private fun Fact(number: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(14.dp)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 10.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Line(item: ItemEntity, detail: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(item.text, fontSize = 14.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!detail.isNullOrBlank()) Text(detail, fontSize = 12.sp, color = InkMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanRow(item: ItemEntity, d: PlanDecision, monday: Long, onChange: (PlanDecision) -> Unit) {
    var picking by remember(item.id) { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(14.dp)).padding(12.dp).testTag("plan_${item.id}")) {
        Text(item.text, fontSize = 14.sp, color = if (d.choice == PlanChoice.DROP) InkMuted else Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("Carry", d.choice == PlanChoice.CARRY) { picking = false; onChange(PlanDecision(item.id, PlanChoice.CARRY)) }
            Choice(if (d.choice == PlanChoice.RESCHEDULE && d.dueAt != null) "Move · ${day(d.dueAt)}" else "Move to…", d.choice == PlanChoice.RESCHEDULE) { picking = !picking }
            Choice("Drop", d.choice == PlanChoice.DROP) { picking = false; onChange(PlanDecision(item.id, PlanChoice.DROP)) }
        }
        if (picking) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..4).forEach { i ->
                val at = monday + i * 86_400_000L
                Choice(SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(at)), d.dueAt == at) { picking = false; onChange(PlanDecision(item.id, PlanChoice.RESCHEDULE, at, null)) }
            }
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) AccentWash else SurfaceBase).border(1.dp, if (on) Accent else LineSoft, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) Accent else Ink)
    }
}

private fun day(ms: Long) = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
