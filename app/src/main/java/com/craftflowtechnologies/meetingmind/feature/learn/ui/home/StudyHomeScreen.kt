package com.craftflowtechnologies.meetingmind.feature.learn.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.learn.ContinueLesson
import com.craftflowtechnologies.meetingmind.feature.learn.CourseTile
import com.craftflowtechnologies.meetingmind.feature.learn.RoundSummary
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.SkyLegend
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.SkyView
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.WaveBars
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

private val DayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val DayDot = 28.dp

/**
 * Study home: today's round, the sky of ideas, the lesson to continue, courses and the week's rhythm.
 * Stateless: the view model builds [state]; every tap goes out through a callback. The app scaffold
 * owns the bottom bar, so this screen draws none.
 */
@Composable
fun StudyHomeScreen(
    state: StudyHomeState,
    onStartRound: () -> Unit,
    onPractiseAnyway: () -> Unit,
    onLater: () -> Unit,
    onOpenLesson: (sessionId: String) -> Unit,
    onOpenCourse: (name: String) -> Unit,
    onOpenAllIdeas: () -> Unit,
    onSearch: () -> Unit,
    onRecordLecture: () -> Unit,
    onImportLecture: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudyTheme {
        LazyColumn(
            modifier.fillMaxSize().background(MM.colors.background),
            contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.xl)
        ) {
            item(key = "header") { StudyHeader(onSearch) }
            when {
                state.loading -> item(key = "loading") { Loading() }
                !state.hasAnyLesson -> item(key = "first-use") {
                    FirstUse(onRecordLecture = onRecordLecture, onImportLecture = onImportLecture)
                }
                else -> {
                    item(key = "zuri") { ZuriLine(state.companionLine, round = state.round != null) }
                    item(key = "round") {
                        val round = state.round
                        if (round != null) RoundCard(round, onStartRound, onLater) else CaughtUpCard(onPractiseAnyway)
                    }
                    item(key = "sky") { SkyCard(state, onOpenAllIdeas) }
                    state.continueLesson?.let { lesson ->
                        item(key = "continue") { ContinueSection(lesson, onOpenLesson) }
                    }
                    if (state.courses.isNotEmpty()) {
                        item(key = "courses") { CoursesSection(state.courses, onOpenCourse) }
                    }
                    item(key = "week") { WeekCard(state.studiedDays, state.todayIndex) }
                    if (state.recentLessons.isNotEmpty()) {
                        item(key = "recent") { RecentSection(state.recentLessons, onOpenLesson) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyHeader(onSearch: () -> Unit) {
    HomeHeader(
        greeting = "Study",
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MM.colors.ink)
            }
        }
    )
}

/** Zuri with a speech bubble. Curious when a round is due, idle when caught up. */
@Composable
private fun ZuriLine(line: String, round: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        ZuriSlot(
            CompanionPage.STUDY_HOME,
            64.dp,
            state = if (round) CompanionState.CURIOUS else CompanionState.IDLE
        )
        if (line.isNotBlank()) {
            Spacer(Modifier.width(MM.space.m))
            Surface(
                shape = MM.radius.bubble,
                color = MM.colors.surface,
                border = BorderStroke(MMSize.hairline, MM.colors.line)
            ) {
                Text(
                    line,
                    style = MM.type.body,
                    color = MM.colors.ink,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s)
                )
            }
        }
    }
}

/** Tonight's round: the one accent card on the screen. */
@Composable
private fun RoundCard(round: RoundSummary, onStart: () -> Unit, onLater: () -> Unit) {
    val c = MM.colors
    Surface(color = c.accent, shape = MM.radius.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Text("Tonight's round", style = MM.type.caption, color = c.onAccent)
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                Text(
                    "${round.dueCount} cards · about ${round.minutes} min",
                    style = MM.type.display,
                    color = c.onAccent
                )
                sourcesLine(round.sourceTitles)?.let { line ->
                    Text(line, style = MM.type.secondary, color = c.onAccent)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                // A white pill on the accent card: the shared PrimaryButton would vanish into it.
                Button(
                    onClick = onStart,
                    shape = MM.radius.pill,
                    modifier = Modifier.weight(1f).heightIn(min = MMSize.minTouch),
                    colors = ButtonDefaults.buttonColors(containerColor = c.surface, contentColor = c.accent),
                    contentPadding = PaddingValues(horizontal = MM.space.xl, vertical = MM.space.s)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(MMSize.iconSmall))
                    Spacer(Modifier.width(MM.space.s))
                    Text("Start round", style = MM.type.bodyStrong)
                }
                OutlinedButton(
                    onClick = onLater,
                    shape = MM.radius.pill,
                    border = BorderStroke(MMSize.hairline, c.onAccent.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onAccent),
                    modifier = Modifier.heightIn(min = MMSize.minTouch),
                    contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s)
                ) {
                    Text("Later", style = MM.type.bodyStrong)
                }
            }
        }
    }
}

/** "From A, B and C", or null when there are no sources. */
internal fun sourcesLine(titles: List<String>): String? = when (titles.size) {
    0 -> null
    1 -> "From ${titles[0]}"
    else -> "From ${titles.dropLast(1).joinToString(", ")} and ${titles.last()}"
}

/** Nothing due: a quiet card, and "Practise anyway" as a text action. */
@Composable
private fun CaughtUpCard(onPractise: () -> Unit) {
    MMCard {
        Text("All caught up. Nice work.", style = MM.type.heading, color = MM.colors.ink)
        Spacer(Modifier.size(MM.space.xs))
        TextAction("Practise anyway", onPractise)
    }
}

@Composable
private fun SkyCard(state: StudyHomeState, onOpenAllIdeas: () -> Unit) {
    val sky = state.sky
    MMCard {
        SectionHeader(
            "Your sky",
            actionLabel = if (sky.totalIdeas > 0) "All ${sky.totalIdeas} ideas" else null,
            onAction = if (sky.totalIdeas > 0) onOpenAllIdeas else null
        )
        Text(
            "Each idea becomes a star when you can recall it. Lines join ideas that belong together.",
            style = MM.type.secondary,
            color = MM.colors.inkSecondary
        )
        Spacer(Modifier.size(MM.space.m))
        if (sky.stars.isEmpty()) {
            Text(
                "Ideas appear here after your first lecture.",
                style = MM.type.secondary,
                color = MM.colors.inkMuted
            )
        } else {
            SkyView(sky, ignite = emptySet())
        }
        Spacer(Modifier.size(MM.space.m))
        SkyLegend(sky)
    }
}

@Composable
private fun ContinueSection(lesson: ContinueLesson, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Continue")
        MMCard(onClick = { onOpen(lesson.sessionId) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                lesson.courseName?.let {
                    Text(it, style = MM.type.caption, color = MM.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(MM.space.s))
                Text(
                    lesson.stoppedAt?.let { "Stopped at $it" } ?: "Not started yet",
                    style = MM.type.caption,
                    color = MM.colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.size(MM.space.s))
            Text(lesson.title, style = MM.type.heading, color = MM.colors.ink)
            if (lesson.waveform.isNotEmpty()) {
                Spacer(Modifier.size(MM.space.m))
                WaveBars(lesson.waveform, played = lesson.progress)
            }
        }
    }
}

@Composable
private fun CoursesSection(courses: List<CourseTile>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Courses")
        courses.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                row.forEach { course -> CourseTileView(course, onOpen, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CourseTileView(course: CourseTile, onOpen: (String) -> Unit, modifier: Modifier) {
    MMCard(modifier, onClick = { onOpen(course.name) }, contentPadding = MM.space.m) {
        course.code?.let { Text(it, style = MM.type.caption, color = MM.colors.accent, maxLines = 1) }
        Spacer(Modifier.size(MM.space.xs))
        Text(
            course.name,
            style = MM.type.bodyStrong,
            color = MM.colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(MM.space.xs))
        Text(
            course.status,
            style = MM.type.caption,
            color = if (course.needsAttention) MM.colors.goldInk else MM.colors.inkMuted
        )
    }
}

/** "You studied N days this week", seven dots Monday first, and the no-streak line. */
@Composable
private fun WeekCard(studied: List<Boolean>, todayIndex: Int) {
    val count = studied.count { it }
    Surface(color = MM.colors.surfaceSunk, shape = MM.radius.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Text(
                "You studied $count ${if (count == 1) "day" else "days"} this week",
                style = MM.type.bodyStrong,
                color = MM.colors.ink
            )
            Row(Modifier.fillMaxWidth()) {
                DayNames.indices.forEach { i ->
                    DayMark(
                        letter = DayLetters[i],
                        name = DayNames[i],
                        studied = studied.getOrElse(i) { false },
                        today = i == todayIndex,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text("No streaks to lose. A quiet day is fine.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
    }
}

@Composable
private fun DayMark(letter: String, name: String, studied: Boolean, today: Boolean, modifier: Modifier) {
    val c = MM.colors
    val description = buildString {
        append(name)
        append(if (studied) ", studied" else ", no study")
        if (today) append(", today")
    }
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        Box(
            Modifier
                .size(DayDot)
                .drawBehind {
                    when {
                        studied -> drawCircle(c.accent)
                        !today -> drawCircle(c.track)
                    }
                    if (today) {
                        val dash = 4.dp.toPx()
                        drawCircle(
                            color = c.accent,
                            radius = (size.minDimension - 2.dp.toPx()) / 2f,
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
                        )
                    }
                }
        )
        Text(letter, style = MM.type.caption, color = c.inkMuted)
    }
}

@Composable
private fun RecentSection(lessons: List<ContinueLesson>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Recent lectures")
        MMCard(contentPadding = MM.space.s) {
            lessons.forEach { lesson ->
                ListRow(
                    title = lesson.title,
                    subtitle = lesson.courseName,
                    onClick = { onOpen(lesson.sessionId) }
                )
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(MM.space.xxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MM.colors.accent,
            modifier = Modifier.semantics { contentDescription = "Loading Study" }
        )
    }
}

/** First use: no lecture yet. Record one, or import a recording. */
@Composable
private fun FirstUse(onRecordLecture: () -> Unit, onImportLecture: () -> Unit) {
    EmptyState(
        title = "Study from your lectures",
        body = "Record a lecture or import a recording. Zuri turns it into a readable lesson and a few quick checks.",
        illustration = { ZuriSlot(CompanionPage.STUDY_HOME, 64.dp, state = CompanionState.CURIOUS) },
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                PrimaryButton("Record a lecture", onRecordLecture, leadingIcon = Icons.Rounded.Mic)
                TextAction("Import a recording", onImportLecture)
            }
        }
    )
}
