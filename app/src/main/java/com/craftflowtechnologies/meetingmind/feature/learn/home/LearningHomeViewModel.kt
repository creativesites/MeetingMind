package com.craftflowtechnologies.meetingmind.feature.learn.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The Learning data the merged home reads. [RepositoryLearningHomeSource] is the app's; tests use fakes. */
interface LearningHomeSource {
    fun courses(): Flow<List<LearningRepository.LearningCourse>>
    fun lectures(): Flow<List<LearningRepository.LectureNoteSummary>>
    fun sessions(): Flow<List<LearningSession>>
    fun concepts(): Flow<List<LearningConcept>>
    fun brief(): Flow<LearningRepository.DailyCompanionBrief?>
}

/** Reads the Learning tables through [LearningRepository], the same reads the Learning space makes. */
class RepositoryLearningHomeSource(private val repo: LearningRepository) : LearningHomeSource {
    override fun courses() = repo.observeCourses()
    override fun lectures() = repo.observeLectureNotes()
    override fun sessions() = repo.observeSessions()
    override fun concepts() = repo.observeAllConcepts()
    override fun brief() = repo.observeLiveDailyBrief()
}

/** The Learning inputs as one flow, shared by the home and the course page. */
internal fun LearningHomeSource.inputs(): Flow<LearningInputs> = combine(
    courses(), lectures(), sessions(), concepts(), brief()
) { courses, lectures, sessions, concepts, brief ->
    LearningInputs(courses, lectures, sessions, concepts, brief)
}

/**
 * The Learning home: Study's round, sky and continue (from [study]) merged with Learning's courses,
 * the day's plan ("Done by"), recent lectures and exam countdowns (from [source]).
 */
class LearningHomeViewModel(
    study: Flow<StudyHomeState>,
    source: LearningHomeSource,
    private val now: () -> Long = System::currentTimeMillis,
    private val format: LearningFormat = LearningFormat.system()
) : ViewModel() {

    private val _state = MutableStateFlow(LearningHomeUiState())
    val state: StateFlow<LearningHomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(study, source.inputs()) { studyState, inputs ->
                LearningHomeBuilder.build(studyState, inputs, now(), format)
            }.collect { _state.value = it }
        }
    }
}

/** One course for the course page. */
class CourseViewModel(
    private val courseId: String,
    source: LearningHomeSource,
    private val now: () -> Long = System::currentTimeMillis,
    private val format: LearningFormat = LearningFormat.system()
) : ViewModel() {

    private val _state = MutableStateFlow(CourseUiState())
    val state: StateFlow<CourseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            source.inputs().collect { inputs ->
                _state.value = CourseBuilder.build(courseId, inputs, now(), format)
            }
        }
    }
}
