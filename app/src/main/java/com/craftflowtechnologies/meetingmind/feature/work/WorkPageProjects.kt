package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.FilterChipRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.work.ProjectBar
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.feature.work.hub.ProjectCounts
import com.craftflowtechnologies.meetingmind.feature.work.hub.ProjectStatus
import com.craftflowtechnologies.meetingmind.feature.work.hub.ProjectStatusKind
import com.craftflowtechnologies.meetingmind.feature.work.hub.projectStatus
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** The three lists under Work › Projects. */
internal enum class ProjectsTab(val label: String) { PROJECTS("Projects"), PEOPLE("People"), ORGANISATIONS("Organisations") }

/**
 * Work › Projects (artifact #2d #2e): a toggle between Projects, People and Organisations. A project card has its
 * colour bar, a status pill and reason that come from counts (past-due promises, open risks, decisions to confirm),
 * and its figures. People whose match is uncertain show Confirm and Different people, as the Work duplicates do.
 */
@Composable
internal fun WorkProjectsPane(
    viewModel: WorkViewModel,
    projectWord: String,
    initialTab: ProjectsTab,
    now: Long,
    onOpenProject: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onNewProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val projects by viewModel.projects.collectAsState()
    val counts by remember(viewModel) { viewModel.work.observeProjectItemCounts() }.collectAsState(emptyList())
    val everyone by viewModel.everyone.collectAsState()
    val orgs by viewModel.organisations.collectAsState()
    val duplicates by viewModel.duplicates.collectAsState()
    val countsById = counts.associateBy { it.projectId }
    val projectWordLower = projectWord.lowercase()

    LazyColumn(
        modifier.fillMaxSize().testTag("work_projects_list"),
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xxl),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        item(key = "tabs") {
            FilterChipRow(
                options = ProjectsTab.entries.map { it.name },
                selected = setOf(tab.name),
                onToggle = { tab = ProjectsTab.valueOf(it) },
                label = { ProjectsTab.valueOf(it).label }
            )
        }
        when (tab) {
            ProjectsTab.PROJECTS -> {
                if (projects.isEmpty()) item(key = "empty") {
                    EmptyState(
                        illustration = { ZuriSlot(CompanionPage.EMPTY, EmptyCompanionSize) },
                        title = "Start a $projectWordLower",
                        body = "One place for its meetings, notes, people, tasks and decisions.",
                        action = { PrimaryButton("New $projectWordLower", onNewProject, leadingIcon = Icons.Rounded.Add) }
                    )
                } else {
                    items(projects, key = { it.notebook.id }) { p ->
                        val c = countsById[p.notebook.id]
                        val status = projectStatus(
                            ProjectCounts(c?.pastDuePromises ?: 0, c?.openRisks ?: 0, c?.pendingDecisions ?: 0),
                            p.lastActivity, now
                        )
                        ProjectCardRow(p, status, now) { onOpenProject(p.notebook.id) }
                    }
                    item(key = "new") { SecondaryButton("New $projectWordLower", onNewProject, Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Add) }
                }
            }
            ProjectsTab.PEOPLE -> {
                if (everyone.isEmpty()) item(key = "people-empty") {
                    EmptyState(
                        title = "No people yet",
                        body = "People appear here on their own when you name a speaker or record from a calendar invite."
                    )
                }
                items(everyone, key = { "p-" + it.id }) { p ->
                    val org = orgs.firstOrNull { it.id == p.orgId }?.name
                    val uncertain = duplicates.any { (a, b) -> a.id == p.id || b.id == p.id }
                    PersonLine(
                        person = p,
                        subtitle = org,
                        uncertain = uncertain,
                        onOpen = { onOpenPerson(p.id) },
                        onConfirm = { duplicates.firstOrNull { (a, b) -> a.id == p.id || b.id == p.id }?.let { (a, b) ->
                            val (from, into) = if (a.name.length >= b.name.length) b to a else a to b
                            viewModel.merge(from, into)
                        } },
                        onDifferent = { duplicates.firstOrNull { (a, b) -> a.id == p.id || b.id == p.id }?.let { (a, b) -> viewModel.markDifferent(a, b) } }
                    )
                }
            }
            ProjectsTab.ORGANISATIONS -> {
                if (orgs.isEmpty()) item(key = "orgs-empty") {
                    EmptyState(title = "No organisations yet", body = "An organisation appears when you name one on a person or a project.")
                }
                if (orgs.isNotEmpty()) item(key = "orgs-header") { SectionHeader("Organisations", count = orgs.size) }
                items(orgs, key = { "o-" + it.id }) { o ->
                    val people = everyone.count { it.orgId == o.id }
                    OrganisationLine(o, people, onOpen = { onOpenPerson(o.id) })
                }
            }
        }
    }
}

@Composable
private fun ProjectCardRow(p: ProjectCard, status: ProjectStatus, now: Long, onClick: () -> Unit) {
    val last = p.lastActivity?.let { dayLabel(it, now) } ?: "None yet"
    val summary = listOfNotNull(p.notebook.name, p.org, status.label, status.reason, "${p.notes} notes", "${p.openTasks} open tasks", "last activity $last").joinToString(", ")
    MMCard(Modifier.semantics(mergeDescendants = true) { contentDescription = summary }, onClick = onClick) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            ProjectBar(projectColor(p), Modifier.padding(end = MM.space.xs), width = 4.dp)
            Column(Modifier.weight(1f).padding(start = MM.space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text(
                        p.notebook.name, style = MM.type.heading, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(status)
                }
                p.org?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
                }
                Text(status.reason, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.s))
                Row(Modifier.fillMaxWidth().padding(top = MM.space.m), horizontalArrangement = Arrangement.spacedBy(MM.space.l)) {
                    ProjectFigure(p.notes.toString(), if (p.notes == 1) "note" else "notes")
                    ProjectFigure(p.openTasks.toString(), if (p.openTasks == 1) "open task" else "open tasks")
                    ProjectFigure(last, "last activity")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ProjectStatus) {
    val colors = MM.colors
    val (wash, ink) = when (status.kind) {
        ProjectStatusKind.NEEDS_ATTENTION -> colors.dangerWash to colors.danger
        ProjectStatusKind.RISKS_OPEN -> colors.warning.copy(alpha = 0.14f) to colors.warning
        ProjectStatusKind.DECISION_PENDING -> colors.accentWash to colors.accent
        ProjectStatusKind.QUIET -> colors.surfaceSunk to colors.inkSecondary
        ProjectStatusKind.ON_TRACK -> colors.surfaceSunk to colors.inkSecondary
    }
    Box(Modifier.background(wash, MM.radius.pill).padding(horizontal = MM.space.s, vertical = 2.dp)) {
        Text(status.label, style = MM.type.caption, color = ink, maxLines = 1)
    }
}

@Composable
private fun ProjectFigure(value: String, label: String) {
    Column {
        Text(value, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PersonLine(
    person: WorkPerson,
    subtitle: String?,
    uncertain: Boolean,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
    onDifferent: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = MM.space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(MMSize.minTouch).background(MM.colors.accentWash, RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center
            ) { Text(person.initials, style = MM.type.caption, color = MM.colors.accent) }
            Column(Modifier.weight(1f).padding(start = MM.space.m)) {
                Text(person.name, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = listOfNotNull(person.role, subtitle, person.lastSeenAt?.let { "Last ${dayLabel(it).lowercase()}" }).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (uncertain) {
            Row(Modifier.padding(start = MMSize.minTouch + MM.space.m), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                TextAction("Confirm", onConfirm)
                TextAction("Different people", onDifferent)
            }
        }
    }
}

@Composable
private fun OrganisationLine(org: WorkPerson, people: Int, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clickable(role = Role.Button, onClick = onOpen).padding(vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(MMSize.minTouch).background(MM.colors.surfaceSunk, RoundedCornerShape(percent = 50)), contentAlignment = Alignment.Center) {
            Text(org.initials, style = MM.type.caption, color = MM.colors.inkSecondary)
        }
        Column(Modifier.weight(1f).padding(start = MM.space.m)) {
            Text(org.name, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$people ${if (people == 1) "person" else "people"}", style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
    }
}
