package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.work.Finding
import com.craftflowtechnologies.meetingmind.feature.circles2.CircleSheet
import com.craftflowtechnologies.meetingmind.feature.circles2.CirclesPalette
import com.craftflowtechnologies.meetingmind.feature.circles2.PostTypeBadge
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch

@Composable
fun ShareToCircleSheet(
    meetingTitle: String,
    summary: String?,
    decisions: List<Finding>,
    tasks: List<Finding>,
    meetingId: String? = null,
    onDismiss: () -> Unit,
    onNavigateCreateCircle: () -> Unit,
    onShared: (circleName: String, count: Int) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val circlesState by repo.circles().collectAsState(CirclesListState.Loading)
    val scope = rememberCoroutineScope()
    val mustStayOnDevice = remember(meetingId) {
        meetingId?.let { WorkPrivacy.mustStayOnDevice(context, it) } ?: false
    }

    var shareSummary by rememberSaveable { mutableStateOf(!summary.isNullOrBlank()) }
    val selectedDecisions = remember { mutableStateListOf<String>().also { list -> list.addAll(decisions.map { it.id }) } }
    val selectedTasks = remember { mutableStateListOf<String>().also { list -> list.addAll(tasks.map { it.id }) } }
    var selectedCircleId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showClientConfirmDialog by remember { mutableStateOf(false) }

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Share to Team Circle",
                    style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MM.colors.ink
                )
                Text(
                    "Publish meeting brief, decisions and tasks to your team",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }

        if (mustStayOnDevice) {
            StatusLine(
                StatusKind.Warning,
                "This meeting is marked confidential or policy-restricted and must stay on this device."
            )
            return@CircleSheet
        }

        when (val state = circlesState) {
            CirclesListState.Loading -> {
                StatusLine(StatusKind.Info, "Loading your team circles…")
            }
            CirclesListState.NotConnected -> {
                EmptyState(
                    title = "Circles not connected",
                    body = "Work Circles allow your team to collaborate on meeting outcomes and tasks in real-time.",
                    illustration = { Icon(Icons.Rounded.Groups, null, tint = MM.colors.inkMuted) }
                )
            }
            is CirclesListState.Ready -> {
                val workCircles = state.circles.filter { it.isWork }
                if (workCircles.isEmpty()) {
                    Column(
                        Modifier.padding(vertical = MM.space.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Text(
                            "You don't have any Work Circles yet. Start a Work Circle for your project team to collaborate seamlessly.",
                            style = MM.type.body,
                            color = MM.colors.inkSecondary
                        )
                        PrimaryButton("Start a team circle", {
                            onDismiss()
                            onNavigateCreateCircle()
                        })
                    }
                } else {
                    val defaultPick = selectedCircleId?.takeIf { id -> workCircles.any { it.id == id } } ?: workCircles.first().id
                    if (selectedCircleId == null) {
                        selectedCircleId = defaultPick
                    }
                    val currentCircle = workCircles.firstOrNull { it.id == selectedCircleId } ?: workCircles.first()

                    SectionHeader("Target Circle")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        workCircles.forEach { c ->
                            val isSelected = c.id == currentCircle.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) CirclesPalette.AccentSoft else MM.colors.surfaceSunk,
                                border = BorderStroke(1.dp, if (isSelected) CirclesPalette.Accent else MM.colors.line),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedCircleId = c.id }
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        c.name,
                                        style = MM.type.bodyStrong.copy(fontSize = 13.5.sp),
                                        color = if (isSelected) CirclesPalette.AccentDeep else MM.colors.ink,
                                        maxLines = 1
                                    )
                                    Text(
                                        c.groupWord,
                                        style = MM.type.caption,
                                        color = if (isSelected) CirclesPalette.AccentDeep.copy(alpha = 0.8f) else MM.colors.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedCircleId = c.id }
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        c.name,
                                        style = MM.type.bodyStrong.copy(fontSize = 13.5.sp),
                                        color = if (isSelected) CirclesPalette.AccentDeep else MM.colors.ink,
                                        maxLines = 1
                                    )
                                    Text(
                                        c.groupWord,
                                        style = MM.type.caption,
                                        color = if (isSelected) CirclesPalette.AccentDeep.copy(alpha = 0.8f) else MM.colors.inkSecondary
                                    )
                                }
                            }
                        }
                    }

                    SectionHeader("Select content to publish")
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!summary.isNullOrBlank()) {
                            item {
                                MMCard(onClick = { shareSummary = !shareSummary }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = shareSummary,
                                            onCheckedChange = { shareSummary = it },
                                            colors = CheckboxDefaults.colors(checkedColor = CirclesPalette.Accent)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Meeting Brief", style = MM.type.bodyStrong, color = MM.colors.ink)
                                                PostTypeBadge(PostType.Brief)
                                            }
                                            Text(summary.take(120) + if (summary.length > 120) "…" else "", style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 2)
                                        }
                                    }
                                }
                            }
                        }

                        if (decisions.isNotEmpty()) {
                            item { SectionHeader("Decisions", count = decisions.size) }
                            items(decisions, key = { "d_${it.id}" }) { d ->
                                val isChecked = d.id in selectedDecisions
                                MMCard(onClick = {
                                    if (isChecked) selectedDecisions.remove(d.id) else selectedDecisions.add(d.id)
                                }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                if (it) selectedDecisions.add(d.id) else selectedDecisions.remove(d.id)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = CirclesPalette.Accent)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(d.text, style = MM.type.body, color = MM.colors.ink)
                                        }
                                        PostTypeBadge(PostType.Decision)
                                    }
                                }
                            }
                        }

                        if (tasks.isNotEmpty()) {
                            item { SectionHeader("Action Items", count = tasks.size) }
                            items(tasks, key = { "t_${it.id}" }) { t ->
                                val isChecked = t.id in selectedTasks
                                MMCard(onClick = {
                                    if (isChecked) selectedTasks.remove(t.id) else selectedTasks.add(t.id)
                                }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                if (it) selectedTasks.add(t.id) else selectedTasks.remove(t.id)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = CirclesPalette.Accent)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(t.text, style = MM.type.body, color = MM.colors.ink)
                                            if (!t.ownerName.isNullOrBlank() || !t.dueText.isNullOrBlank()) {
                                                val meta = listOfNotNull(
                                                    t.ownerName?.let { "@$it" },
                                                    t.dueText?.let { "Due: $it" }
                                                ).joinToString(" · ")
                                                Text(meta, style = MM.type.caption, color = MM.colors.inkMuted)
                                            }
                                        }
                                        PostTypeBadge(PostType.Task)
                                    }
                                }
                            }
                        }
                    }

                    errorMsg?.let { StatusLine(StatusKind.Error, it) }

                    val totalSelected = (if (shareSummary) 1 else 0) + selectedDecisions.size + selectedTasks.size
                    val buttonLabel = if (sending) "Publishing…" else "Publish $totalSelected items to ${currentCircle.name}"

                    PrimaryButton(
                        buttonLabel,
                        onClick = {
                            if (totalSelected == 0) return@PrimaryButton
                            sending = true
                            errorMsg = null
                            scope.launch {
                                var count = 0
                                if (shareSummary && !summary.isNullOrBlank()) {
                                    val briefContent = "📋 Meeting Brief: $meetingTitle\n\n$summary"
                                    repo.createPost(
                                        circleId = currentCircle.id,
                                        body = briefContent,
                                        type = PostType.Brief,
                                        anonymous = false,
                                        requestId = java.util.UUID.randomUUID().toString()
                                    )
                                    count++
                                }
                                decisions.filter { it.id in selectedDecisions }.forEach { d ->
                                    repo.createPost(
                                        circleId = currentCircle.id,
                                        body = d.text,
                                        type = PostType.Decision,
                                        anonymous = false,
                                        requestId = java.util.UUID.randomUUID().toString()
                                    )
                                    count++
                                }
                                tasks.filter { it.id in selectedTasks }.forEach { t ->
                                    val ownerTag = if (!t.ownerName.isNullOrBlank()) " · Assigned to @${t.ownerName}" else ""
                                    val dueTag = if (!t.dueText.isNullOrBlank()) " (Due: ${t.dueText})" else ""
                                    val taskContent = "${t.text}$ownerTag$dueTag"
                                    repo.createPost(
                                        circleId = currentCircle.id,
                                        body = taskContent,
                                        type = PostType.Task,
                                        anonymous = false,
                                        requestId = java.util.UUID.randomUUID().toString()
                                    )
                                    count++
                                }
                                sending = false
                                onShared(currentCircle.name, count)
                                onDismiss()
                            }
                        },
                        enabled = !sending && totalSelected > 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
