package com.craftflowtechnologies.meetingmind.feature.learn.home

import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.CourseProperties
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-built Learning data for the home and course tests. */
internal class FakeLearningHomeSource(
    courses: List<LearningRepository.LearningCourse> = emptyList(),
    lectures: List<LearningRepository.LectureNoteSummary> = emptyList(),
    sessions: List<LearningSession> = emptyList(),
    concepts: List<LearningConcept> = emptyList(),
    brief: LearningRepository.DailyCompanionBrief? = null
) : LearningHomeSource {
    val coursesFlow = MutableStateFlow(courses)
    val lecturesFlow = MutableStateFlow(lectures)
    val sessionsFlow = MutableStateFlow(sessions)
    val conceptsFlow = MutableStateFlow(concepts)
    val briefFlow = MutableStateFlow(brief)

    override fun courses(): Flow<List<LearningRepository.LearningCourse>> = coursesFlow
    override fun lectures(): Flow<List<LearningRepository.LectureNoteSummary>> = lecturesFlow
    override fun sessions(): Flow<List<LearningSession>> = sessionsFlow
    override fun concepts(): Flow<List<LearningConcept>> = conceptsFlow
    override fun brief(): Flow<LearningRepository.DailyCompanionBrief?> = briefFlow
}

internal object LearningFakes {
    const val NOW = 1_800_000_000_000L
    private const val DAY_MS = 86_400_000L

    /** Fixed wording, so assertions do not depend on the device's locale. */
    val format = LearningFormat(
        time = { ms -> "t${ms / 60_000L % 10_000}" },
        day = { ms -> "day${ms / DAY_MS}" }
    )

    fun notebook(id: String, name: String) = Notebook(
        id = id, name = name, space = NotebookSpace.LEARNING, colorHex = null, icon = null, createdAt = 1L, updatedAt = 1L
    )

    fun course(
        id: String,
        name: String,
        code: String = "",
        sessionIds: List<String> = emptyList(),
        dueReviewCount: Int = 0
    ) = LearningRepository.LearningCourse(
        notebook = notebook(id, name),
        properties = CourseProperties(courseCode = code),
        dueReviewCount = dueReviewCount,
        sessionIds = sessionIds
    )

    fun session(id: String, noteId: String, title: String, courseName: String? = null) = LearningSession(
        id = id, noteId = noteId, title = title, courseName = courseName, createdAt = 1L, updatedAt = NOW
    )

    fun concept(id: String, sessionId: String, state: LearningMasteryState) = LearningConcept(
        id = id, sessionId = sessionId, name = id, definition = "", state = state, createdAt = 1L, updatedAt = 1L
    )

    fun lecture(
        noteId: String,
        title: String,
        sessionId: String?,
        notebookId: String?,
        courseName: String? = null,
        updatedAt: Long = NOW,
        concepts: Int = 0,
        activities: Int = 0,
        durationSeconds: Long = 0L
    ) = LearningRepository.LectureNoteSummary(
        noteId = noteId,
        sessionId = sessionId,
        meetingId = null,
        title = title,
        summary = "",
        notebookId = notebookId,
        courseName = courseName,
        domain = SubjectDomain.GENERAL,
        workflow = "LECTURE",
        durationSeconds = durationSeconds,
        hasRecording = durationSeconds > 0,
        conceptCount = concepts,
        activityCount = activities,
        strongCount = 0,
        updatedAt = updatedAt
    )

    fun goal(id: String, notebookId: String, examInDays: Int) = LearningGoal(
        id = id,
        notebookId = notebookId,
        title = "Exam",
        examDateMs = NOW + examInDays * DAY_MS,
        createdAt = 1L,
        updatedAt = 1L
    )

    fun brief(vararg exams: Pair<LearningGoal, Int>) = LearningRepository.DailyCompanionBrief(
        recommendedMinutes = 0,
        dueReviews = emptyList(),
        weakHighYieldConcepts = emptyList(),
        unresolvedConfusionCount = 0,
        upcomingExams = exams.map { (g, days) -> LearningRepository.UpcomingExamCountdown(g, days) },
        sevenDayActivity = List(7) { false },
        sevenDayAttemptCounts = List(7) { 0 }
    )
}
