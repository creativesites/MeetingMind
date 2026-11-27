package com.craftflowtechnologies.meetingmind.feature.learn.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.identity.Avatar
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.navigation.AppMenuButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeScaffold
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.learn.RoundSummary
import com.craftflowtechnologies.meetingmind.feature.learn.SkyState
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.home.HomeCourse
import com.craftflowtechnologies.meetingmind.feature.learn.home.HomeLecture
import com.craftflowtechnologies.meetingmind.feature.learn.home.HomeLesson
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningHomeUiState
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.SkyView
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.WaveBars
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.skySummary
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.StudyFixedColors
import kotlin.math.roundToInt

/**
 * The Learning home (design/study/LearningHome.dc.html): today's round, starting something new,
 * the lecture to continue, the sky, courses and recent lectures. Stateless: every tap goes out
 * through a callback. The app's global bar is drawn by the host, not here.
 */
@Composable
fun LearningHomeScreen(
    state: LearningHomeUiState,
    dayLine: String,
    onAsk: () -> Unit,
    onStartRound: () -> Unit,
    onPractiseAnyway: () -> Unit,
    onOpenLesson: (noteId: String) -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onOpenCourse: (courseId: String) -> Unit,
    onAddCourse: () -> Unit,
    onRecordLecture: () -> Unit,
    onOpenYouTube: () -> Unit,
    onImportFile: () -> Unit,
    onWriteStudyNote: () -> Unit,
    onOpenAllIdeas: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudyTheme {
        HomeScaffold(
            modifier = modifier.fillMaxSize().background(MM.colors.background).testTag("learning_home"),
            header = { Header(dayLine, onOpenSettings) },
            sections = {
                item(key = "ask") { AskField(onAsk) }
                when {
                    state.loading -> item(key = "loading") { Loading() }
                    !state.hasAnyLesson -> item(key = "first") { FirstUse(onRecordLecture) }
                    else -> {
                        item(key = "round") {
                            RoundOrCaughtUp(state, onStartRound, onPractiseAnyway)
                        }
                    }
                }
                item(key = "new") {
                    StartSomethingNew(
                        firstUse = !state.hasAnyLesson && !state.loading,
                        onRecordLecture = onRecordLecture,
                        onOpenYouTube = onOpenYouTube,
                        onImportFile = onImportFile,
                        onWriteStudyNote = onWriteStudyNote
                    )
                }
                if (state.hasAnyLesson) {
                    state.continueLesson?.let { lesson ->
                        item(key = "continue") {
                            Section("Continue") { ContinueCard(lesson, onClick = { onOpenLesson(lesson.noteId) }) }
                        }
                    }
                    item(key = "sky") { SkyCard(state.study.sky, onOpenAllIdeas) }
                    item(key = "courses") { CoursesSection(state.courses, onOpenCourse, onAddCourse) }
                    if (state.recent.isNotEmpty()) item(key = "recent") {
                        Section("Recent lectures") {
                            RecentCard(state.recent) { lecture ->
                                if (lecture.isLesson) onOpenLesson(lecture.noteId) else onOpenNote(lecture.noteId)
                            }
                        }
                    }
                    item(key = "week") { WeekCard(state.study.studiedDays, state.study.todayIndex) }
                }
            }
        )
    }
}

/** "Wednesday evening" from the clock, for the line above the title. */
internal fun dayLine(dayName: String, hour: Int): String {
    val part = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }
    return "$dayName $part"
}

@Composable
private fun Header(dayLine: String, onOpenSettings: () -> Unit) {
    val identity = LocalAppIdentity.current
    Row(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
        // The app menu, as every home has it (core/navigation/AppMenu.kt).
        AppMenuButton(focus = NotebookSpace.LEARNING)
        Column(Modifier.weight(1f).padding(horizontal = MM.space.s)) {
            Text(dayLine, style = MM.type.caption, color = MM.colors.inkSecondary)
            Text(
                "Learning",
                style = MM.type.display,
                color = MM.colors.ink,
                modifier = Modifier.semantics { heading() }
            )
        }
        Box(
            Modifier
                .size(MMSize.minTouch)
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .semantics { contentDescription = "Account and settings" },
            contentAlignment = Alignment.Center
        ) {
            Avatar(identity, 44.dp)
        }
    }
}

/** The one way to ask: opens Ask Learning, which searches the student's own lectures. */
@Composable
private fun AskField(onAsk: () -> Unit) {
    Surface(
        onClick = onAsk,
        shape = MM.radius.pill,
        color = MM.colors.surface,
        border = BorderStroke(MMSize.hairline, MM.colors.line),
        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).testTag("learning_ask")
    ) {
        Row(
            Modifier.padding(start = MM.space.l, end = MM.space.s, top = MM.space.s, bottom = MM.space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MM.colors.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
            Spacer(Modifier.width(MM.space.s))
            Text(
                "Ask or find anything in your lectures",
                style = MM.type.body,
                color = MM.colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RoundOrCaughtUp(state: LearningHomeUiState, onStart: () -> Unit, onPractiseAnyway: () -> Unit) {
    val round = state.study.round
    if (round == null) CaughtUpCard(onPractiseAnyway)
    else RoundCard(round, state.doneBy, onStart)
}

/** Tonight's round: the one accent card on the screen, with Zuri curious. "Later" folds it away. */
@Composable
private fun RoundCard(round: RoundSummary, doneBy: String?, onStart: () -> Unit) {
    var later by rememberSaveable { mutableStateOf(false) }
    if (later) {
        MMCard(Modifier.testTag("learning_round_later")) {
            Text("Your round is still there when you want it.", style = MM.type.body, color = MM.colors.ink)
            TextAction("Start round", onStart)
        }
        return
    }
    val c = MM.colors
    Surface(color = c.accent, shape = MM.radius.card, modifier = Modifier.fillMaxWidth().testTag("learning_round")) {
        Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZuriSlot(CompanionPage.STUDY_HOME, 52.dp, state = CompanionState.CURIOUS)
                Spacer(Modifier.width(MM.space.m))
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Text("Tonight's round", style = MM.type.caption, color = c.onAccent)
                    Text(
                        "${round.dueCount} cards · about ${round.minutes} min",
                        style = MM.type.display,
                        color = c.onAccent
                    )
                    if (doneBy != null) {
                        Text("Done by $doneBy if you start now", style = MM.type.secondary, color = c.onAccent)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                // A white pill on the accent card: the shared PrimaryButton would vanish into it.
                Button(
                    onClick = onStart,
                    shape = MM.radius.pill,
                    modifier = Modifier.weight(1f).heightIn(min = MMSize.minTouch).testTag("learning_start_round"),
                    colors = ButtonDefaults.buttonColors(containerColor = c.surface, contentColor = c.accent),
                    contentPadding = PaddingValues(horizontal = MM.space.xl, vertical = MM.space.s)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(MMSize.iconSmall))
                    Spacer(Modifier.width(MM.space.s))
                    Text("Start round", style = MM.type.bodyStrong)
                }
                OutlinedButton(
                    onClick = { later = true },
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

@Composable
private fun CaughtUpCard(onPractiseAnyway: () -> Unit) {
    MMCard(Modifier.testTag("learning_caught_up")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZuriSlot(CompanionPage.STUDY_HOME, 52.dp, state = CompanionState.IDLE)
            Spacer(Modifier.width(MM.space.m))
            Text("All caught up. Nice work.", style = MM.type.heading, color = MM.colors.ink)
        }
        TextAction("Practise anyway", onPractiseAnyway)
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(MM.space.xxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MM.colors.accent,
            modifier = Modifier.semantics { contentDescription = "Loading Learning" }
        )
    }
}

/** First use: no lecture yet. Zuri is curious; recording is the one prominent way in. */
@Composable
private fun FirstUse(onRecordLecture: () -> Unit) {
    EmptyState(
        title = "Study from your lectures",
        body = "Record your first lecture, or pick a video or a file. Zuri turns it into a readable lesson and a few quick checks.",
        illustration = { ZuriSlot(CompanionPage.STUDY_HOME, 64.dp, state = CompanionState.CURIOUS) },
        action = { PrimaryButton("Record your first lecture", onRecordLecture, leadingIcon = Icons.Rounded.Mic) }
    )
}

/**
 * The four ways in. On first use the record tile is the quiet one: the lecture button above it is
 * the prominent one. Each tile fires its callback directly.
 */
@Composable
private fun StartSomethingNew(
    firstUse: Boolean,
    onRecordLecture: () -> Unit,
    onOpenYouTube: () -> Unit,
    onImportFile: () -> Unit,
    onWriteStudyNote: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionTitle("Start something new")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            WayTile("Record a lecture", Modifier.weight(1f).testTag("learning_tile_record"), onClick = onRecordLecture) {
                // The recording dot: a shape and the word carry it, not the colour alone.
                Box(Modifier.size(MMSize.iconSmall).clip(CircleShape).background(MM.colors.recording))
            }
            WayTile("From a video link", Modifier.weight(1f).testTag("learning_tile_video"), onClick = onOpenYouTube) {
                Icon(Icons.Rounded.SmartDisplay, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.icon))
            }
            WayTile("Import a file", Modifier.weight(1f).testTag("learning_tile_import"), onClick = onImportFile) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.icon))
            }
            WayTile("Write a study note", Modifier.weight(1f).testTag("learning_tile_note"), onClick = onWriteStudyNote) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.icon))
            }
        }
    }
}

@Composable
private fun WayTile(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = MM.colors.surface,
        border = BorderStroke(MMSize.hairline, MM.colors.line),
        modifier = modifier.heightIn(min = 104.dp)
    ) {
        Column(
            Modifier.padding(horizontal = MM.space.xs, vertical = MM.space.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Box(
                Modifier.size(MMSize.minTouch - MM.space.s).clip(CircleShape).background(MM.colors.accentWash),
                contentAlignment = Alignment.Center
            ) { icon() }
            Text(label, style = MM.type.caption, color = MM.colors.ink, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ContinueCard(lesson: HomeLesson, onClick: () -> Unit) {
    MMCard(Modifier.testTag("learning_continue"), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                lesson.courseName?.let {
                    Text(it, style = MM.type.caption, color = MM.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(lesson.title, style = MM.type.heading, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    lesson.stoppedAt?.let { "You stopped at $it" } ?: "Not started yet",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(MM.space.m))
            ProgressRing(lesson.progress)
        }
    }
}

/** A progress ring with the percentage in the middle, drawn in the accent on the track colour. */
@Composable
private fun ProgressRing(progress: Float) {
    val c = MM.colors
    val percent = (progress.coerceIn(0f, 1f) * 100).roundToInt()
    Box(
        Modifier
            .size(MMSize.minTouch)
            .semantics { contentDescription = "$percent% through the lecture" }
            .drawBehind {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(c.track, -90f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                drawArc(c.accent, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            },
        contentAlignment = Alignment.Center
    ) {
        Text("$percent%", style = MM.type.caption, color = c.ink)
    }
}

/** The dark sky card. Its colours are fixed to the sky, so it reads the same in both themes. */
@Composable
private fun SkyCard(sky: SkyState, onOpenAllIdeas: () -> Unit) {
    Surface(color = StudyFixedColors.Night, shape = MM.radius.card, modifier = Modifier.fillMaxWidth().testTag("learning_sky")) {
        Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your sky",
                    style = MM.type.heading,
                    color = StudyFixedColors.StarCore,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                if (sky.totalIdeas > 0) {
                    Text(
                        "${sky.totalIdeas} ideas",
                        style = MM.type.bodyStrong,
                        color = StudyFixedColors.Star,
                        modifier = Modifier
                            .heightIn(min = MMSize.minTouch)
                            .clickable(role = Role.Button, onClick = onOpenAllIdeas)
                            .padding(horizontal = MM.space.s)
                            .testTag("learning_all_ideas")
                            .semantics { contentDescription = "All ${sky.totalIdeas} ideas" }
                    )
                }
            }
            if (sky.stars.isEmpty()) {
                Text("Ideas appear here after your first lecture.", style = MM.type.secondary, color = StudyFixedColors.Label)
            } else {
                SkyView(sky, ignite = emptySet())
                Text(skyCaption(sky), style = MM.type.secondary, color = StudyFixedColors.Label)
            }
        }
    }
}

/** "5 shining · 4 growing · 2 worth a look": words, with zeros left out. */
internal fun skyCaption(sky: SkyState): String = listOf(
    sky.count(StarState.SHINING) to "shining",
    sky.count(StarState.GROWING) to "growing",
    sky.count(StarState.WORTH_A_LOOK) to "worth a look"
).filter { it.first > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }.ifEmpty { skySummary(sky) }

@Composable
private fun CoursesSection(courses: List<HomeCourse>, onOpenCourse: (String) -> Unit, onAddCourse: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Courses", style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            TextAction("Add a course", onAddCourse, modifier = Modifier.testTag("learning_add_course"))
        }
        if (courses.isEmpty()) {
            Text("Courses group your lectures and show progress across them.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), contentPadding = PaddingValues(end = MM.space.l)) {
                items(courses, key = { it.id }) { course -> CourseTile(course, onClick = { onOpenCourse(course.id) }) }
            }
        }
    }
}

@Composable
private fun CourseTile(course: HomeCourse, onClick: () -> Unit) {
    val c = MM.colors
    MMCard(
        Modifier.width(148.dp).testTag("learning_course_${course.id}"),
        onClick = onClick,
        contentPadding = MM.space.m
    ) {
        course.code?.let { Text(it, style = MM.type.caption, color = c.accent, maxLines = 1) }
        Text(course.name, style = MM.type.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(MM.space.xs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MM.radius.pill)
                .background(c.track)
                .semantics { contentDescription = "${course.solid} of ${course.total} ideas solid" }
        ) {
            Box(Modifier.fillMaxWidth(course.progress).height(6.dp).background(c.accent))
        }
        Text(
            course.status,
            style = MM.type.caption,
            color = if (course.needsAttention) c.goldInk else c.inkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentCard(lectures: List<HomeLecture>, onOpen: (HomeLecture) -> Unit) {
    MMCard(contentPadding = MM.space.xs) {
        lectures.forEach { lecture ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MMSize.minTouch)
                    .clickable(role = Role.Button) { onOpen(lecture) }
                    .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    .testTag("learning_recent"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(lecture.title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(lecture.meta, style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(MM.space.s))
                Surface(
                    shape = MM.radius.pill,
                    color = if (lecture.ready) MM.colors.accentWash else MM.colors.surfaceSunk
                ) {
                    Text(
                        lecture.statusLabel,
                        style = MM.type.caption,
                        color = if (lecture.ready) MM.colors.accent else MM.colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    )
                }
            }
        }
    }
}

/** The week in seven dots, Monday first. Studied days are filled; today has a ring. No streaks. */
@Composable
private fun WeekCard(studied: List<Boolean>, todayIndex: Int) {
    val count = studied.count { it }
    val c = MM.colors
    Surface(color = c.surfaceSunk, shape = MM.radius.card, modifier = Modifier.fillMaxWidth().testTag("learning_week")) {
        Row(Modifier.padding(MM.space.l), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "You studied $count ${if (count == 1) "day" else "days"} this week",
                style = MM.type.bodyStrong,
                color = c.ink,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                studied.forEachIndexed { i, done ->
                    val today = i == todayIndex
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (done) c.accent else c.track)
                            .then(if (today) Modifier.border(2.dp, c.accent, CircleShape) else Modifier)
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.semantics { heading() })
}
