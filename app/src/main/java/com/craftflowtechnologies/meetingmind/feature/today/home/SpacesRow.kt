package com.craftflowtechnologies.meetingmind.feature.today.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.navigation.BarConfig
import com.craftflowtechnologies.meetingmind.core.navigation.LocalAppMenu
import com.craftflowtechnologies.meetingmind.core.navigation.SpaceStyle
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import com.craftflowtechnologies.meetingmind.ui.theme.LocalAppearance
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.flow.map

/** What each space card says right now. Every line comes from data the app already has. */
data class SpaceGlance(
    val nextTitle: String? = null,
    val todayCount: Int = 0,
    val devotionalTitle: String? = null,
    val learnDue: Int = 0,
    val tasksDue: Int = 0,
    val personalLine: String? = null
)

/** The live line under a space's name. Pure, so the wording is unit-tested on the JVM. */
object SpaceLines {
    fun line(space: NotebookSpace, g: SpaceGlance): String = when (space) {
        NotebookSpace.WORK -> g.nextTitle?.takeIf { it.isNotBlank() }?.let { "Next: $it" }
            ?: if (g.todayCount > 0) "${g.todayCount} on today" else "Your day, projects and people"
        NotebookSpace.LEARNING -> when (g.learnDue) {
            0 -> "Today's plan is clear"
            1 -> "1 card to review"
            else -> "${g.learnDue} cards to review"
        }
        NotebookSpace.FAITH -> g.devotionalTitle?.takeIf { it.isNotBlank() } ?: "Today's devotional"
        NotebookSpace.PERSONAL -> g.personalLine?.takeIf { it.isNotBlank() } ?: "Routines, journal and growth"
    }

    fun tasks(g: SpaceGlance): String = when (g.tasksDue) {
        0 -> "Nothing due today"
        1 -> "1 due today"
        else -> "${g.tasksDue} due today"
    }
}

/** Open tasks due today and Learning cards due now, read live from the database. */
@Composable
fun rememberSpaceCounts(): Pair<Int, Int> {
    val context = LocalContext.current
    val db = remember { MeetMindDatabase.getInstance(context) }
    val tasksFlow = remember { TaskRepository(db.taskDao(), db.peopleDao()).observeToday().map { l -> l.count { !it.done } } }
    val learnFlow = remember { db.reviewScheduleDao().observeDue(System.currentTimeMillis()).map { it.size } }
    val tasks by tasksFlow.collectAsState(initial = 0)
    val learn by learnFlow.collectAsState(initial = 0)
    return tasks to learn
}

/** Live one-line Personal space glance computed from active routines, journeys, and today's plan. */
@Composable
fun rememberPersonalGlanceLine(): String? {
    val context = LocalContext.current
    var line by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) {
        line = runCatching {
            com.craftflowtechnologies.meetingmind.core.personal.PersonalRepository(context).computeLiveSpaceGlance()
        }.getOrNull()
    }
    return line
}

/**
 * "Your spaces": one card per vertical the person uses, right under the hero, so Work, Learning
 * and Faith are visible in the first seconds on Home rather than behind Customize home. Tasks is
 * always there. "All features" opens the app menu. A space that is turned off has no card.
 */
@Composable
fun SpacesRow(
    glance: SpaceGlance,
    onOpen: (NotebookSpace) -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val identity = LocalAppIdentity.current
    val home = LocalAppearance.current.homeStyle
    val menu = LocalAppMenu.current
    val spaces = remember(identity.spaces, identity.faithFirst, home) {
        BarConfig.verticals(identity.spaces, BarConfig.lead(home, identity))
    }
    val colors = MM.colors
    Column(modifier.fillMaxWidth().testTag("home_spaces_row")) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR SPACES", style = MM.type.overline, color = colors.inkMuted, modifier = Modifier.weight(1f))
            TextButton(onClick = { menu.open(null) }, modifier = Modifier.testTag("home_spaces_all")) {
                Icon(Icons.Rounded.Apps, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(MM.space.xs))
                Text("All features", style = MM.type.bodyStrong, color = colors.accent)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(spaces, key = { it.name }) { space ->
                SpaceCard(
                    title = SpaceStyle.title(space), line = SpaceLines.line(space, glance),
                    icon = SpaceStyle.icon(space), tint = SpaceStyle.tint(space),
                    tag = "home_space_${space.name.lowercase()}"
                ) { onOpen(space) }
            }
            item(key = "tasks") {
                SpaceCard("Tasks", SpaceLines.tasks(glance), Icons.Rounded.TaskAlt, colors.success, "home_space_tasks", onOpenTasks)
            }
        }
    }
}

@Composable
private fun SpaceCard(title: String, line: String, icon: ImageVector, tint: Color, tag: String, onClick: () -> Unit) {
    val colors = MM.colors
    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = colors.surfaceRaised,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = Modifier.width(152.dp).height(118.dp).testTag(tag)
    ) {
        Box(Modifier.background(tint.copy(alpha = 0.08f))) {
            Column(Modifier.padding(MM.space.m)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(title, style = MM.type.heading, color = colors.ink, maxLines = 1)
                Text(line, style = MM.type.caption, color = colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
