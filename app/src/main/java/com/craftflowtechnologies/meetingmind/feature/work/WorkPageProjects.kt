package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** The Projects segment: each project with its counts. Tap opens the project hub. */
@Composable
internal fun ProjectsContent(
    projects: List<ProjectCard>,
    projectWord: String,
    projectsWord: String,
    now: Long,
    listState: LazyListState,
    onOpen: (ProjectCard) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier.testTag("work_projects_list"), state = listState,
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xxl),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        if (projects.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "Start a ${projectWord.lowercase()}",
                    body = "One place for its meetings, notes, people, tasks and decisions.",
                    action = { PrimaryButton("New ${projectWord.lowercase()}", onNew, leadingIcon = Icons.Rounded.Add) }
                )
            }
        } else {
            item(key = "header") { SectionHeader(projectsWord, count = projects.size) }
            items(projects, key = { it.notebook.id }) { p -> ProjectRow(p, now, onClick = { onOpen(p) }) }
            item(key = "new") { SecondaryButton("New ${projectWord.lowercase()}", onNew, Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Add) }
        }
    }
}

@Composable
private fun ProjectRow(p: ProjectCard, now: Long, onClick: () -> Unit) {
    val counts = listOfNotNull(
        "${p.notes} ${if (p.notes == 1) "note" else "notes"}",
        "${p.openTasks} open ${if (p.openTasks == 1) "task" else "tasks"}".takeIf { p.openTasks > 0 },
        p.lastActivity?.let { "active ${dayLabel(it, now).lowercase()}" }
    ).joinToString(" · ")
    val summary = listOfNotNull(p.notebook.name, p.org, p.notebook.status, counts).joinToString(", ")
    MMCard(Modifier.semantics(mergeDescendants = true) { contentDescription = summary }, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(MM.space.m).background(projectColor(p), MM.radius.pill))
            Text(
                p.notebook.name, style = MM.type.heading, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = MM.space.m)
            )
            if (p.notebook.confidential) Icon(Icons.Rounded.Lock, "Confidential", Modifier.size(MMSize.iconSmall), tint = MM.colors.inkMuted)
        }
        Text(
            listOfNotNull(p.org, p.notebook.status).joinToString(" · ").ifEmpty { " " }, style = MM.type.secondary, color = MM.colors.inkSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs)
        )
        Text(counts, style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.xs))
    }
}
