package com.example.feature.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.calendar.CalendarEvent
import com.example.core.calendar.CalendarEvents
import com.example.core.database.MeetMindDatabase
import com.example.core.database.ProcessingJobEntity
import com.example.core.datastore.UserPreferencesManager
import com.example.core.identity.AppIdentity
import com.example.core.model.Note
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.repository.NoteCodec.toDomain
import com.example.core.repository.NoteRepository
import com.example.core.timeline.Greetings
import com.example.core.timeline.ItemKind
import com.example.core.timeline.MeetingPrep
import com.example.core.timeline.Rhythms
import com.example.core.timeline.TimelineDays
import com.example.core.timeline.TimelineItem
import com.example.core.timeline.TimelineLayer
import com.example.core.timeline.TimelineRepository
import com.example.core.timeline.WeekReview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Calendar

enum class CalendarView(val label: String) { AGENDA("Agenda"), DAY("Day"), WEEK("Week"), MONTH("Month"), RIVER("Timeline") }

/** A quick lens on Today, separate from the person's settings: everything, or one space. */
/** The "Getting started" checklist (see TodayViewModel.gettingStarted). */
data class GettingStarted(
    val recorded: Boolean = false,
    val wrote: Boolean = false,
    val calendar: Boolean = false,
    val devotional: Boolean = false,
    val showsFaith: Boolean = false,
    val dismissed: Boolean = false,
    /** Nothing recorded or written yet: Home shows the welcome instead of an empty calendar. */
    val isNew: Boolean = true
) {
    val steps: Int get() = if (showsFaith) 4 else 3
    val done: Int get() = listOf(recorded, wrote, calendar).count { it } + (if (showsFaith && devotional) 1 else 0)
    val visible: Boolean get() = !dismissed && done < steps
}

enum class TodayFocus(val label: String) { ALL("Everything"), FAITH("Faith"), WORK("Work & study") }

/** What Home's "Up next" tile shows. */
sealed interface UpNextTile {
    data class Event(val event: CalendarEvent, val prep: MeetingPrep.Prep?) : UpNextTile
    data class Item(val item: TimelineItem) : UpNextTile
    data object Nothing : UpNextTile
}

data class TodayStats(val streakDays: Int, val weekCount: Int, val streakLabel: String, val weekLabel: String)

/**
 * The Today hub (PLAN_V2 F1): the hero, the week strip, today's cards and the calendar views, all
 * read from [TimelineRepository]. It reloads whenever notes or recordings change, and every
 * minute while Home is showing.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MeetMindDatabase.getInstance(application)
    private val prefs = UserPreferencesManager(application)
    private val timeline = TimelineRepository(application, database)
    private val calendar = CalendarEvents(application)
    private val notes = NoteRepository(application, database)

    val identity: StateFlow<AppIdentity> = prefs.preferencesFlow.map { it.identity }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppIdentity())

    /** Layers turned on in settings (or the defaults for the person's spaces). */
    val layers: StateFlow<Set<TimelineLayer>> = prefs.preferencesFlow.map { p ->
        val allowed = TimelineLayer.defaultsFor(p.identity)
        (p.timelineLayers?.mapNotNull { runCatching { TimelineLayer.valueOf(it) }.getOrNull() }?.toSet() ?: allowed) intersect allowed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TimelineLayer.entries.toSet())

    val view: StateFlow<CalendarView> = prefs.preferencesFlow.map { p -> runCatching { CalendarView.valueOf(p.timelineView) }.getOrDefault(CalendarView.AGENDA) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarView.AGENDA)

    val calendarOn: StateFlow<Boolean?> = prefs.preferencesFlow.map { it.calendarEnabled && calendar.hasPermission() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val calendarPromptDismissed: StateFlow<Boolean> = prefs.preferencesFlow.map { it.calendarPromptDismissed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val activeJobs: StateFlow<List<ProcessingJobEntity>> = database.processingJobDao().getActiveJobs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Today's devotional, once written (PLAN_V2 F2). */
    val devotional: StateFlow<com.example.core.devotional.DailyDevotional?> =
        com.example.core.devotional.DevotionalRepository(application).observe(com.example.core.devotional.LocalDay.today())
            .let { f -> kotlinx.coroutines.flow.flow { try { f.collect { emit(it) } } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; emit(null) } } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** First steps for someone new, each ticking itself off as it happens. */
    val gettingStarted: StateFlow<GettingStarted?> = kotlinx.coroutines.flow.combine(
        database.meetingDao().getMeetingCountFlow(),
        database.noteDao().observeWrittenCount(),
        prefs.preferencesFlow,
        devotional
    ) { recordings, written, p, d ->
        GettingStarted(
            recorded = recordings > 0, wrote = written > 0,
            calendar = p.calendarEnabled && calendar.hasPermission(),
            devotional = d?.note?.metadata?.get(com.example.core.devotional.DevotionalNotes.META_OPENED) != null,
            showsFaith = p.identity.showsFaith,
            dismissed = p.gettingStartedDismissed,
            isNew = recordings == 0 && written == 0
        )
    }.catch { emit(GettingStarted()) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null until known, so the tour doesn't flash for people who've seen it. */
    val tourCompleted: StateFlow<Boolean?> = prefs.preferencesFlow.map { it.tourCompleted }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finishTour() = viewModelScope.launch { prefs.setTourCompleted(true) }
    fun dismissGettingStarted() = viewModelScope.launch { prefs.setGettingStartedDismissed(true) }

    private val _focus = MutableStateFlow(TodayFocus.ALL)
    val focus: StateFlow<TodayFocus> = _focus.asStateFlow()

    private val _selectedDay = MutableStateFlow(TimelineDays.startOfDay(System.currentTimeMillis()))
    val selectedDay: StateFlow<Long> = _selectedDay.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** Items in the current view's range. */
    private val _items = MutableStateFlow<List<TimelineItem>>(emptyList())
    val items: StateFlow<List<TimelineItem>> = _items.asStateFlow()

    /** Today's items (for the cards), independent of which day is being browsed. */
    private val _today = MutableStateFlow<List<TimelineItem>>(emptyList())
    val today: StateFlow<List<TimelineItem>> = _today.asStateFlow()

    /** Layers with something on each day of the visible week or month: day key → layers. */
    private val _activity = MutableStateFlow<Map<Int, List<TimelineLayer>>>(emptyMap())
    val activity: StateFlow<Map<Int, List<TimelineLayer>>> = _activity.asStateFlow()

    private val _memories = MutableStateFlow<List<TimelineItem>>(emptyList())
    val memories: StateFlow<List<TimelineItem>> = _memories.asStateFlow()

    private val _upNext = MutableStateFlow<UpNextTile>(UpNextTile.Nothing)
    val upNext: StateFlow<UpNextTile> = _upNext.asStateFlow()

    private val _rhythm = MutableStateFlow<Rhythms.Rhythm?>(null)
    val rhythm: StateFlow<Rhythms.Rhythm?> = _rhythm.asStateFlow()

    private val _weekReview = MutableStateFlow<WeekReview?>(null)
    val weekReview: StateFlow<WeekReview?> = _weekReview.asStateFlow()

    private val _stats = MutableStateFlow(TodayStats(0, 0, "0 days", "0 this week"))
    val stats: StateFlow<TodayStats> = _stats.asStateFlow()

    private val _contextLine = MutableStateFlow<String?>(null)
    val contextLine: StateFlow<String?> = _contextLine.asStateFlow()

    /** How far back the timeline river has loaded. */
    private var riverFrom = TimelineDays.addMonths(TimelineDays.startOfMonth(System.currentTimeMillis()), -2)

    private var loadJob: Job? = null

    init {
        // Reload when notes, recordings or settings change.
        viewModelScope.launch {
            combine(database.noteDao().observeActive(), database.meetingDao().getAllMeetings(), layers, view, _focus) { _, _, _, _, _ -> Unit }
                .debounce(250)
                .collect { reload() }
        }
        viewModelScope.launch {
            // Stories follow who the app is for, and today's devotional.
            combine(identity, devotional) { i, d -> i.spaces to d?.note?.id }.distinctUntilChanged().debounce(400).collect { loadStories() }
        }
    }

    /** Today's stories, for the rings under the hero (PLAN_V2 F4). */
    private val _stories = MutableStateFlow<List<com.example.feature.stories.StoryKind>>(emptyList())
    val stories: StateFlow<List<com.example.feature.stories.StoryKind>> = _stories.asStateFlow()

    fun loadStories() {
        viewModelScope.launch {
            _stories.value = runCatching {
                com.example.feature.stories.StoryBuilder(getApplication(), database).build(identity.value).map { it.kind }
            }.getOrDefault(emptyList())
        }
    }

    fun tick() { _now.value = System.currentTimeMillis(); reload() }

    fun select(day: Long) { _selectedDay.value = TimelineDays.startOfDay(day); reload() }
    fun goToday() = select(System.currentTimeMillis())

    fun shift(step: Int) {
        val d = _selectedDay.value
        select(when (view.value) {
            CalendarView.DAY, CalendarView.AGENDA -> TimelineDays.addDays(d, step)
            CalendarView.WEEK -> TimelineDays.addDays(d, 7 * step)
            CalendarView.MONTH, CalendarView.RIVER -> TimelineDays.addMonths(d, step)
        })
    }

    fun setView(v: CalendarView) = viewModelScope.launch { prefs.setTimelineView(v.name) }
    fun setFocus(f: TodayFocus) { _focus.value = f }

    fun setLayer(layer: TimelineLayer, on: Boolean) = viewModelScope.launch {
        val current = layers.value
        prefs.setTimelineLayers((if (on) current + layer else current - layer).map { it.name }.toSet())
    }

    /** The timeline river reaches further back. */
    fun loadEarlier() { riverFrom = TimelineDays.addMonths(riverFrom, -3); reload() }

    fun setCalendarEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setCalendarEnabled(enabled)
        if (enabled) prefs.setCalendarPromptDismissed(true)
        reload()
    }

    fun dismissCalendarPrompt() = viewModelScope.launch { prefs.setCalendarPromptDismissed(true) }

    /** Opens (or makes) the event's note; [onReady] gets its id and the type it was filed as. */
    fun noteForEvent(event: CalendarEvent, onReady: (String, RecordingType) -> Unit) = viewModelScope.launch {
        val note = notes.noteForCalendarEvent(event, com.example.core.calendar.UpNext.suggestedType(event.title))
        onReady(note.id, note.workflow)
    }

    /** A note for a day that has nothing yet (morning of that day), opened for writing. */
    fun planNote(day: Long, onCreated: (String) -> Unit) = viewModelScope.launch {
        val at = Calendar.getInstance().apply { timeInMillis = TimelineDays.startOfDay(day); set(Calendar.HOUR_OF_DAY, 9) }.timeInMillis
        onCreated(notes.createNote(eventDate = at, draft = true).id)
    }

    fun setAvatar(uri: android.net.Uri) = viewModelScope.launch {
        com.example.core.identity.AvatarStore.save(getApplication(), uri)?.let { prefs.setAvatarPath(it) }
    }

    private fun focusedLayers(): Set<TimelineLayer> = when (_focus.value) {
        TodayFocus.ALL -> layers.value
        TodayFocus.FAITH -> layers.value intersect setOf(TimelineLayer.FAITH, TimelineLayer.EVENTS, TimelineLayer.MEMORIES)
        TodayFocus.WORK -> layers.value - TimelineLayer.FAITH
    }

    private fun range(): Pair<Long, Long> {
        val d = _selectedDay.value
        return when (view.value) {
            CalendarView.DAY -> d to TimelineDays.addDays(d, 1)
            CalendarView.WEEK -> TimelineDays.startOfWeek(d).let { it to TimelineDays.addDays(it, 7) }
            CalendarView.MONTH -> TimelineDays.startOfWeek(TimelineDays.startOfMonth(d)).let { it to TimelineDays.addDays(it, 42) }
            CalendarView.AGENDA -> d to TimelineDays.addDays(d, 14)
            CalendarView.RIVER -> riverFrom to TimelineDays.addDays(TimelineDays.startOfDay(System.currentTimeMillis()), 15)
        }
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // A failed read (the calendar provider, a closing database) keeps the last good state
            // rather than taking Home down.
            try { load() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.w("TodayViewModel", "Couldn't refresh Today", e)
            }
        }
    }

    private suspend fun load() {
        run {
            val p = prefs.preferencesFlow.first()
            val calOn = p.calendarEnabled && calendar.hasPermission()
            val layerSet = focusedLayers()
            val now = System.currentTimeMillis()
            _now.value = now
            val todayStart = TimelineDays.startOfDay(now)

            val (from, to) = range()
            _items.value = timeline.between(from, to, layerSet, calOn)

            // Week strip / month dots.
            val stripFrom = if (view.value == CalendarView.MONTH) from else TimelineDays.startOfWeek(_selectedDay.value)
            val stripTo = if (view.value == CalendarView.MONTH) to else TimelineDays.addDays(stripFrom, 7)
            val stripItems = if (stripFrom == from && stripTo == to) _items.value else timeline.between(stripFrom, stripTo, layerSet, calOn)
            _activity.value = stripItems.groupBy { it.dayKey }.mapValues { (_, list) -> list.map { it.layer }.distinct() }

            val todays = timeline.between(todayStart, TimelineDays.addDays(todayStart, 1), layerSet, calOn)
            _today.value = todays
            _memories.value = if (TimelineLayer.MEMORIES in layerSet) timeline.onThisDay(now) else emptyList()

            // Up next: an event happening now or soon, with prep; else today's latest devotional or note.
            val next = if (calOn) runCatching { calendar.upNext(now) }.getOrDefault(emptyList()).firstOrNull() else null
            _upNext.value = when {
                next != null -> {
                    val recentNotes = database.noteDao().getNotesBetween(TimelineDays.addDays(now, -120), now).map { it.toDomain() }
                    UpNextTile.Event(next, if (next.begin - now < 90 * 60_000L) MeetingPrep.find(next.otherPeople, recentNotes, next.begin) else null)
                }
                else -> todays.lastOrNull { it.kind != ItemKind.EVENT }?.let { UpNextTile.Item(it) } ?: UpNextTile.Nothing
            }

            // Rhythms from the last eight weeks.
            _rhythm.value = Rhythms.dueNow(Rhythms.learn(timeline.recentRecordings(TimelineDays.addDays(now, -56))))
                ?.takeIf { p.identity.allows(it.type) }

            // Week in review: Sunday evening and Monday morning.
            val cal = Calendar.getInstance()
            val showReview = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && cal.get(Calendar.HOUR_OF_DAY) >= 17) ||
                (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY && cal.get(Calendar.HOUR_OF_DAY) < 12)
            val weekItems = timeline.between(TimelineDays.addDays(todayStart, -6), TimelineDays.addDays(todayStart, 1), layerSet, false)
            _weekReview.value = if (showReview) WeekReview.of(weekItems).takeIf { !it.isEmpty } else null

            // Stats: streak of days with something of the person's (faith items when faith-first).
            val streakItems = timeline.between(TimelineDays.addDays(todayStart, -60), TimelineDays.addDays(todayStart, 1), layerSet, false)
                .filter { it.kind != ItemKind.EVENT && (!p.identity.faithFirst || it.layer == TimelineLayer.FAITH) }
            val days = streakItems.map { it.dayKey }.toSet()
            var streak = 0
            var day = todayStart
            if (TimelineDays.key(day) !in days) day = TimelineDays.addDays(day, -1) // today not done yet doesn't break it
            while (TimelineDays.key(day) in days) { streak++; day = TimelineDays.addDays(day, -1) }
            val weekCount = weekItems.count { it.kind == ItemKind.RECORDING || it.kind == ItemKind.NOTE }
            _stats.value = TodayStats(
                streak, weekCount,
                streakLabel = if (streak == 1) "1 day" else "$streak days",
                weekLabel = if (p.identity.faithFirst) "$weekCount this week" else "$weekCount captured"
            )
            _contextLine.value = Greetings.contextLine(
                eventsToday = todays.count { it.kind == ItemKind.EVENT && it.end?.let { e -> e > now } != false },
                processing = activeJobs.value.size,
                answeredThisWeek = weekItems.count { it.kind == ItemKind.ANSWERED_PRAYER }
            )
        }
    }

    /** Whether the focus switch is worth showing (more than one space, one of them Faith). */
    fun canSwitchFocus(identity: AppIdentity) = identity.showsFaith && identity.spaces.any { it != NotebookSpace.FAITH && it != NotebookSpace.PERSONAL }
}
