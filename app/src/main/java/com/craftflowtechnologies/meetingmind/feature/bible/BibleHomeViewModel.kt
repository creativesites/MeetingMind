package com.craftflowtechnologies.meetingmind.feature.bible

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.biblestudy.StudyJournal
import com.craftflowtechnologies.meetingmind.core.biblestudy.TodayPassage
import com.craftflowtechnologies.meetingmind.core.biblestudy.TodayReading
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.faith.FaithStore
import com.craftflowtechnologies.meetingmind.core.faith.ReadingPlans
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.BibleBooks
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** An active reading plan's day for today. */
data class PlanDay(val planId: String, val planName: String, val dayIndex: Int, val label: String, val reference: ScriptureReference)

/**
 * What the Bible home shows (ONE_PLACE §3.1). [place] is null for someone who has never read here,
 * which makes the hero "Start reading". An empty plan list or journal renders nothing.
 */
data class BibleHomeState(
    val loaded: Boolean = false,
    val place: ScriptureReference? = null,
    /** Where "Start reading" opens for someone who has never read here: John 1, or Psalm 23 with no connection and no Bible on the phone. */
    val start: ScriptureReference? = null,
    val today: TodayReading? = null,
    val plans: List<PlanDay> = emptyList(),
    val journalCount: Int = 0
)

class BibleHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(BibleHomeState())
    val state: StateFlow<BibleHomeState> = _state.asStateFlow()

    private var variant = 0

    init {
        refresh()
        viewModelScope.launch {
            val journal = StudyJournal(NoteRepository(application, MeetMindDatabase.getInstance(application)))
            journal.observe().collect { list -> _state.value = _state.value.copy(journalCount = list.size) }
        }
    }

    /** Reloads where the person left off, today's reading and plan progress. Called when the screen resumes. */
    fun refresh() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val profile = runCatching { UserPreferencesManager(app).devotionalProfile.first() }.getOrDefault(DevotionalProfile())
            val (place, plans) = withContext(Dispatchers.IO) { readerPlace(app) to activePlans(app) }
            val start = if (place == null) withContext(Dispatchers.IO) { firstReading(app) } else null
            _state.value = _state.value.copy(
                loaded = true, place = place, start = start, plans = plans,
                today = TodayPassage.forDate(LocalDate.now(), profile, variant)
            )
        }
    }

    /** "Another one": steps to a different reading for today. */
    fun another() { variant++; refresh() }

    companion object {
        /** Where the reader was left, or null if the person has never read here. */
        fun readerPlace(context: Context): ScriptureReference? {
            val prefs = context.getSharedPreferences("bible_reader", Context.MODE_PRIVATE)
            if (!prefs.contains("book")) return null
            val book = BibleBooks.byUsfm(prefs.getString("book", null) ?: return null) ?: return null
            return ScriptureReference(book, prefs.getInt("chapter", 1).coerceIn(1, book.chapterCount), prefs.getInt("verse", 0).takeIf { it > 0 })
        }

        /** John 1 normally. Psalm 23 when the phone is offline and holds no Bible, since that is what a short offline cache is likeliest to have. */
        fun firstReading(context: Context): ScriptureReference {
            val online = runCatching {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }.getOrDefault(true)
            val onPhone = runCatching { com.craftflowtechnologies.meetingmind.core.scripture.BibleStore.get(context).offlineBibles().isNotEmpty() }.getOrDefault(false)
            return if (online || onPhone) ScriptureReference(BibleBooks.byUsfm("JHN")!!, 1) else ScriptureReference(BibleBooks.byUsfm("PSA")!!, 23)
        }

        private fun activePlans(context: Context): List<PlanDay> = runCatching {
            FaithStore.get(context).active().mapNotNull { p ->
                if (p.finished) return@mapNotNull null
                val day = p.nextUnread ?: return@mapNotNull null
                val refs = p.plan.days.getOrNull(day) ?: return@mapNotNull null
                PlanDay(p.plan.id, p.plan.name, day, ReadingPlans.describe(refs), refs.first())
            }
        }.getOrDefault(emptyList())
    }
}
