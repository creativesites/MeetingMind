package com.craftflowtechnologies.meetingmind.feature.personal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.practice.Practice
import com.craftflowtechnologies.meetingmind.core.practice.PracticeGrowth
import com.craftflowtechnologies.meetingmind.core.practice.PracticeRepository
import com.craftflowtechnologies.meetingmind.core.practice.Rhythm
import com.craftflowtechnologies.meetingmind.core.repository.NoteContent
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenQuestions
import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenTime
import com.craftflowtechnologies.meetingmind.feature.personal.garden.PlantLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The garden's state: practices as plants, this week's reflections as fireflies, ideas in the jar,
 * and the clock that decides the sky. Everything here stays on the phone.
 */
class PersonalViewModel(app: Application) : AndroidViewModel(app) {
    private val db = MeetMindDatabase.getInstance(app)
    private val practices = PracticeRepository(
        db, onRemindersChanged = { com.craftflowtechnologies.meetingmind.core.tasks.TaskReminders.sync(app) }
    )
    private val notes = NoteRepository(app, db)
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Ticks each minute so the sky turns and "today" moves on at midnight. */
    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now(zone))
            delay(60_000)
        }
    }

    /** Recent talk and how-to notes, with the lines in their "try this" sections. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val packetSources = notes.observeNotesForWorkflows(SeedPackets.WORKFLOWS).mapLatest { list ->
        list.filterNot { NoteContent.isDraft(it) }.sortedByDescending { it.createdAt }.take(SeedPackets.MAX_PACKETS).mapNotNull { note ->
            val doc = runCatching { notes.getDocument(note.id) }.getOrNull() ?: return@mapNotNull null
            val lines = SeedPackets.lines(doc.blocks.map { SeedPackets.Line(it.sectionKey, it.type.name, it.content.text) })
            if (lines.isEmpty()) null else Triple(note, lines, note.workflow)
        }
    }

    private val reflectionsAndIdeas = combine(
        notes.observeNotesForWorkflows(REFLECTIONS),
        notes.observeNotesForWorkflows(listOf(RecordingType.IDEA))
    ) { reflections, ideas -> reflections.filterNot { NoteContent.isDraft(it) } to ideas.filterNot { NoteContent.isDraft(it) } }

    val state: StateFlow<PersonalUiState> = combine(
        practices.observeGarden(),
        practices.observeLetGo(),
        reflectionsAndIdeas,
        packetSources,
        clock
    ) { garden, letGo, (reflections, ideas), sources, now ->
        val today = now.toLocalDate()
        val weekAgo = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val planted = (garden + letGo).filter { it.sourceNoteId != null }.groupBy({ it.sourceNoteId!! }, { SeedPackets.key(it.title) })
        PersonalUiState(
            phase = GardenTime.phase(now.toLocalTime()),
            whenLine = GardenTime.whenLine(today, now.toLocalTime()),
            questionLabel = GardenTime.questionLabel(now.toLocalTime()),
            question = GardenQuestions.forDay(today, now.toLocalTime()),
            lights = reflections.count { it.createdAt >= weekAgo },
            plants = garden.map { PlantMapping.toUi(it, today, zone) },
            ideas = ideas.size,
            letGo = letGo.map { PlantMapping.letGo(it) },
            packets = sources.map { (note, lines, workflow) ->
                val done = planted[note.id].orEmpty().toSet()
                SeedPacketUi(note.id, note.title.ifBlank { workflow.displayName }, workflow.displayName, lines.map { SeedUi(it, SeedPackets.key(it) in done) })
            },
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonalUiState())

    fun plant(request: PlantRequest) = viewModelScope.launch {
        practices.plant(
            title = request.title,
            rhythm = PlantMapping.rhythm(request.rhythm),
            remindMinutes = request.nudge.minutes,
            checkInWeeks = request.checkInWeeks,
            sourceNoteId = request.sourceNoteId,
            sourceTitle = request.sourceTitle
        )
    }

    fun adjust(id: String, rhythm: PlantRhythm, nudge: PlantNudge) = viewModelScope.launch {
        practices.adjust(id, PlantMapping.rhythm(rhythm), nudge.minutes)
    }

    fun tend(id: String, done: Boolean) = viewModelScope.launch { if (done) practices.tendToday(id) else practices.untendToday(id) }
    fun keepGoing(id: String) = viewModelScope.launch { practices.keepGoing(id) }
    fun bloom(id: String) = viewModelScope.launch { practices.bloom(id) }
    fun letGo(id: String) = viewModelScope.launch { practices.letGo(id) }

    /** A written answer: a private journal note titled with the question, opened for typing. */
    suspend fun startWrittenAnswer(question: String): String =
        notes.createNote(workflow = RecordingType.JOURNAL, title = question, draft = true).id

    companion object {
        /** The notes that count as a reflection, and light a firefly. */
        val REFLECTIONS = listOf(RecordingType.JOURNAL, RecordingType.REFLECTION, RecordingType.GRATITUDE)
    }
}

/** From a stored practice to a plant on screen. Pure, so it is unit-tested on the JVM. */
object PlantMapping {

    fun toUi(p: Practice, today: LocalDate, zone: ZoneId): GardenPlantUi {
        val started = Instant.ofEpochMilli(p.startedAt).atZone(zone).toLocalDate()
        val bloomed = p.state == com.craftflowtechnologies.meetingmind.core.practice.PracticeState.BLOOMED
        val stage = PracticeGrowth.stage(p.days.size, bloomed)
        val resting = !bloomed && PracticeGrowth.resting(p.days.maxOrNull(), started, p.rhythm, today)
        val tended = today in p.days
        val week = PracticeGrowth.week(p.days, p.rhythm, started, today).map { DayDotUi(it.letter.toString(), dayState(it.state)) }
        val keptThisWeek = PracticeGrowth.keptThisWeek(p.days, today)
        val (kept, scheduled) = PracticeGrowth.window(p.days, p.rhythm, started, today)
        return GardenPlantUi(
            id = p.id,
            title = p.title,
            look = look(stage, resting, tended),
            tendedToday = tended,
            bloomed = bloomed,
            resting = resting,
            week = week,
            rhythmLine = PracticeGrowth.rhythmLine(keptThisWeek),
            rhythmLabel = p.rhythm.label,
            sourceLine = p.sourceTitle?.let { "From “$it”" },
            sourceNoteId = p.sourceNoteId,
            windowLine = if (bloomed) "Part of you now" else if (scheduled == 0) "Planted today" else "$kept of $scheduled days kept since you planted it",
            checkInDue = !bloomed && PracticeGrowth.checkInDue(p.checkInAt, System.currentTimeMillis()),
            rhythm = plantRhythm(p.rhythm),
            nudge = PlantNudges.of(p.remindMinutes)
        )
    }

    fun letGo(p: Practice): LetGoUi = LetGoUi(
        id = p.id, title = p.title, keptDays = p.days.size, rhythm = plantRhythm(p.rhythm),
        nudge = PlantNudges.of(p.remindMinutes), sourceNoteId = p.sourceNoteId, sourceTitle = p.sourceTitle
    )

    fun plantRhythm(r: Rhythm): PlantRhythm = when (r) {
        Rhythm.DAILY -> PlantRhythm.DAILY
        Rhythm.WEEKDAYS -> PlantRhythm.WEEKDAYS
        Rhythm.THREE_A_WEEK -> PlantRhythm.THREE_A_WEEK
        Rhythm.WEEKLY -> PlantRhythm.WEEKLY
    }

    fun look(stage: PracticeGrowth.Stage, resting: Boolean, tended: Boolean): PlantLook = when (stage) {
        PracticeGrowth.Stage.SEED -> PlantLook(leaves = 0)
        PracticeGrowth.Stage.SPROUT -> PlantLook(leaves = 2, resting = resting, glowing = tended)
        PracticeGrowth.Stage.LEAFY -> PlantLook(leaves = 4, resting = resting, glowing = tended)
        PracticeGrowth.Stage.BUDDING -> PlantLook(leaves = 4, bud = true, resting = resting, glowing = tended)
        PracticeGrowth.Stage.BLOOM -> PlantLook(leaves = 2, bloom = true)
    }

    fun rhythm(r: PlantRhythm): Rhythm = when (r) {
        PlantRhythm.DAILY -> Rhythm.DAILY
        PlantRhythm.WEEKDAYS -> Rhythm.WEEKDAYS
        PlantRhythm.THREE_A_WEEK -> Rhythm.THREE_A_WEEK
        PlantRhythm.WEEKLY -> Rhythm.WEEKLY
    }

    private fun dayState(s: PracticeGrowth.DayState): DayState = when (s) {
        PracticeGrowth.DayState.KEPT -> DayState.KEPT
        PracticeGrowth.DayState.MISSED -> DayState.MISSED
        PracticeGrowth.DayState.TODAY -> DayState.TODAY
        PracticeGrowth.DayState.UPCOMING -> DayState.UPCOMING
        PracticeGrowth.DayState.OFF -> DayState.OFF
    }
}
