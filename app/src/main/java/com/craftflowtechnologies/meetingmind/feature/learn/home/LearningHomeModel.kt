package com.craftflowtechnologies.meetingmind.feature.learn.home

import com.craftflowtechnologies.meetingmind.core.learning.SkyLayout
import com.craftflowtechnologies.meetingmind.core.learning.StudyFormat
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState
import com.craftflowtechnologies.meetingmind.feature.learning.space.buildTodayPlan
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Learning home and course page, as plain data. The screens draw these states; the
 * view models build them with [LearningHomeBuilder] and [CourseBuilder], which are pure so tests
 * can run them on fakes.
 */

/** The raw Learning data the home and the course page read. Built from [LearningHomeSource]. */
data class LearningInputs(
    val courses: List<LearningRepository.LearningCourse>,
    val lectures: List<LearningRepository.LectureNoteSummary>,
    val sessions: List<LearningSession>,
    val concepts: List<LearningConcept>,
    val brief: LearningRepository.DailyCompanionBrief?
)

/** Clock-dependent wording, injected so tests can pin the time and locale. */
data class LearningFormat(
    /** A clock time, e.g. "9:20 PM". */
    val time: (Long) -> String,
    /** A short day, e.g. "Tue 12 Nov". */
    val day: (Long) -> String
) {
    companion object {
        fun system(): LearningFormat = LearningFormat(
            time = { ms -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms)) },
            day = { ms -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms)) }
        )
    }
}

/** The lecture to carry on with: its note (for the lesson route) and where the learner stopped. */
data class HomeLesson(
    val noteId: String,
    val title: String,
    val courseName: String?,
    /** e.g. "2 · Inhibition"; null when not started. */
    val stoppedAt: String?,
    /** 0..1 */
    val progress: Float
)

/** A course row on the home. [solid] of [total] ideas are solid, for the progress bar and TalkBack. */
data class HomeCourse(
    val id: String,
    val code: String?,
    val name: String,
    val solid: Int,
    val total: Int,
    val dueCount: Int,
    val examInDays: Int?,
    /** False for a course with no lecture yet: the status says so instead of "All caught up". */
    val hasLectures: Boolean = true
) {
    val progress: Float get() = if (total == 0) 0f else solid.toFloat() / total
    val status: String get() = courseStatus(hasLectures, dueCount, examInDays)
    val needsAttention: Boolean get() = dueCount > 0 || examInDays != null
}

/** One line in Recent lectures. [isLesson] is false for a plain note, which opens in the note editor. */
data class HomeLecture(
    val noteId: String,
    val title: String,
    val meta: String,
    val isLesson: Boolean,
    val ready: Boolean
) {
    val statusLabel: String get() = if (ready) "Lesson ready" else "Making lesson…"
}

data class LearningHomeUiState(
    val study: StudyHomeState = StudyHomeState(loading = true),
    val continueLesson: HomeLesson? = null,
    val courses: List<HomeCourse> = emptyList(),
    val recent: List<HomeLecture> = emptyList(),
    /** "9:20 PM" when the plan is non-empty and there is a round; the time the plan ends if started now. */
    val doneBy: String? = null,
    val planMinutes: Int = 0
) {
    val loading: Boolean get() = study.loading
    val hasAnyLesson: Boolean get() = study.hasAnyLesson
}

/** The one-line status under a course. Plain words, no codes. */
internal fun courseStatus(hasLectures: Boolean, dueCount: Int, examInDays: Int?): String {
    val exam = examInDays?.let { examPhrase(it) }
    return when {
        !hasLectures -> "No lectures yet"
        dueCount > 0 && exam != null -> "$dueCount due · $exam"
        dueCount > 0 -> "$dueCount due"
        exam != null -> exam.replaceFirstChar { it.uppercase() }
        else -> "All caught up"
    }
}

/** "exam today", "exam in 1 day", "exam in 12 days". */
internal fun examPhrase(days: Int): String = when {
    days <= 0 -> "exam today"
    days == 1 -> "exam in 1 day"
    else -> "exam in $days days"
}

/** Builds the Learning home from Study's state and Learning's data. Pure. */
object LearningHomeBuilder {

    private const val RECENT_LIMIT = 5

    fun build(study: StudyHomeState, inputs: LearningInputs, now: Long, format: LearningFormat): LearningHomeUiState {
        val sessionsById = inputs.sessions.associateBy { it.id }
        val continueLesson = study.continueLesson?.let { cl ->
            sessionsById[cl.sessionId]?.let { s ->
                HomeLesson(noteId = s.noteId, title = cl.title, courseName = cl.courseName, stoppedAt = cl.stoppedAt, progress = cl.progress)
            }
        }
        val plan = buildTodayPlan(inputs.brief, inputs.sessions, inputs.lectures)
        val conceptsBySession = inputs.concepts.filter { !it.isDismissed }.groupBy { it.sessionId }
        val courses = inputs.courses
            .sortedByDescending { it.dueReviewCount }
            .map { c -> homeCourse(c, conceptsBySession, inputs.brief, now) }
        val recent = inputs.lectures
            .sortedByDescending { it.updatedAt }
            .take(RECENT_LIMIT)
            .map { l ->
                HomeLecture(
                    noteId = l.noteId,
                    title = l.title,
                    meta = listOfNotNull(
                        l.courseName?.takeIf { it.isNotBlank() },
                        format.day(l.updatedAt),
                        if (l.hasRecording && l.durationSeconds > 0) StudyFormat.durationLabel(l.durationSeconds * 1000) else null
                    ).joinToString(" · "),
                    isLesson = l.sessionId != null,
                    ready = l.conceptCount > 0 || l.activityCount > 0
                )
            }
        val hasRound = study.round != null
        return LearningHomeUiState(
            study = study,
            continueLesson = continueLesson,
            courses = courses,
            recent = recent,
            doneBy = if (hasRound && plan.minutes > 0) format.time(now + plan.minutes * 60_000L) else null,
            planMinutes = plan.minutes
        )
    }

    private fun homeCourse(
        c: LearningRepository.LearningCourse,
        conceptsBySession: Map<String, List<LearningConcept>>,
        brief: LearningRepository.DailyCompanionBrief?,
        now: Long
    ): HomeCourse {
        val concepts = c.sessionIds.flatMap { conceptsBySession[it].orEmpty() }
        val solid = concepts.count { SkyLayout.starStateFor(it.state) == StarState.SHINING }
        val exam = brief?.upcomingExams.orEmpty()
            .filter { it.goal.notebookId == c.notebook.id }
            .minOfOrNull { it.daysRemaining }
        return HomeCourse(
            id = c.notebook.id,
            code = c.properties.courseCode.takeIf { it.isNotBlank() } ?: StudyFormat.courseCode(c.notebook.name),
            name = c.notebook.name,
            solid = solid,
            total = concepts.size,
            dueCount = c.dueReviewCount,
            examInDays = exam,
            hasLectures = c.sessionIds.isNotEmpty()
        )
    }
}

/** One lecture on the course page. [number] is its place in the course, oldest first. */
data class CourseLecture(
    val noteId: String,
    val isLesson: Boolean,
    val number: Int,
    val title: String,
    val dateLabel: String,
    val ideaCount: Int,
    /** "Solid", "Getting there", "New", "2 worth a look": a word, never a colour alone. */
    val stateWord: String
)

/** Everything the course page shows. [found] is false when the course no longer exists. */
data class CourseUiState(
    val loaded: Boolean = false,
    val found: Boolean = false,
    val id: String = "",
    val code: String? = null,
    val name: String = "",
    val lectureCount: Int = 0,
    val ideaCount: Int = 0,
    val solid: Int = 0,
    val growing: Int = 0,
    val new: Int = 0,
    val worth: Int = 0,
    val examInDays: Int? = null,
    val dueCount: Int = 0,
    val lectures: List<CourseLecture> = emptyList()
)

/** Builds one course for the course page. Pure. */
object CourseBuilder {

    fun build(courseId: String, inputs: LearningInputs, now: Long, format: LearningFormat): CourseUiState {
        val course = inputs.courses.firstOrNull { it.notebook.id == courseId }
            ?: return CourseUiState(loaded = true, found = false, id = courseId)
        val sessionIds = course.sessionIds.toSet()
        val lectures = inputs.lectures
            .filter { it.notebookId == courseId || (it.sessionId != null && it.sessionId in sessionIds) }
            .sortedBy { it.updatedAt }
        val conceptsBySession = inputs.concepts.filter { !it.isDismissed }.groupBy { it.sessionId }
        val allStars = sessionIds.flatMap { conceptsBySession[it].orEmpty() }.map { SkyLayout.starStateFor(it.state) }
        val exam = inputs.brief?.upcomingExams.orEmpty()
            .filter { it.goal.notebookId == courseId }
            .minOfOrNull { it.daysRemaining }
        return CourseUiState(
            loaded = true,
            found = true,
            id = courseId,
            code = course.properties.courseCode.takeIf { it.isNotBlank() } ?: StudyFormat.courseCode(course.notebook.name),
            name = course.notebook.name,
            lectureCount = lectures.size,
            ideaCount = allStars.size,
            solid = allStars.count { it == StarState.SHINING },
            growing = allStars.count { it == StarState.GROWING },
            new = allStars.count { it == StarState.NEW },
            worth = allStars.count { it == StarState.WORTH_A_LOOK },
            examInDays = exam,
            dueCount = course.dueReviewCount,
            lectures = lectures.mapIndexed { i, l ->
                val stars = conceptsBySession[l.sessionId].orEmpty().map { SkyLayout.starStateFor(it.state) }
                CourseLecture(
                    noteId = l.noteId,
                    isLesson = l.sessionId != null,
                    number = i + 1,
                    title = l.title,
                    dateLabel = format.day(l.updatedAt),
                    ideaCount = stars.size,
                    stateWord = lectureStateWord(stars)
                )
            }.asReversed()
        )
    }

    /** The lecture's state in one word: the worst thing first, so a "worth a look" never hides. */
    internal fun lectureStateWord(stars: List<StarState>): String {
        val worth = stars.count { it == StarState.WORTH_A_LOOK }
        return when {
            worth > 0 -> "$worth worth a look"
            stars.isEmpty() -> StarState.NEW.label
            stars.all { it == StarState.SHINING } -> StarState.SHINING.label
            stars.any { it == StarState.GROWING || it == StarState.SHINING } -> StarState.GROWING.label
            else -> StarState.NEW.label
        }
    }
}
