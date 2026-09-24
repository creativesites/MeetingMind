package com.example.feature.stories

import android.content.Context
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.devotional.DevotionalRepository
import com.example.core.devotional.LocalDay
import com.example.core.devotional.Quotes
import com.example.core.identity.AppIdentity
import com.example.core.model.NoteStatus
import com.example.core.model.RecordingType
import com.example.core.repository.NoteCodec
import com.example.core.repository.NoteRepository
import com.example.core.scripture.PassageResult
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureService
import com.example.core.share.BackgroundPack
import com.example.core.share.BackgroundSpec
import com.example.core.share.ShareCardContent
import com.example.core.timeline.TimelineRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

enum class StoryKind(val ring: String) {
    VERSE("Verse"), DEVOTIONAL("Devotional"), PRAYER("Prayer"), WORD("Word"), QUOTE("Quote"),
    DAY("Your day"), PRAYING_FOR("Praying"), MEMORY("On this day"), RECAP("Recap")
}

/** Where a story leads when opened. */
sealed interface StoryOpen {
    data object Devotional : StoryOpen
    data class Note(val id: String) : StoryOpen
    data class Recording(val id: String) : StoryOpen
    data class Passage(val reference: ScriptureReference) : StoryOpen
}

/** One full-screen card in today's stories. */
data class Story(
    val kind: StoryKind,
    val eyebrow: String,
    val title: String? = null,
    val body: String,
    /** Printed small at the foot: a translation's attribution, an author, a label. */
    val footer: String? = null,
    val background: BackgroundSpec,
    /** Body set in quotation marks and serif (Scripture, quotes, prayer). */
    val quoted: Boolean = false,
    /** Lines shown as a list (the day's events, people prayed for). */
    val lines: List<String> = emptyList(),
    val share: ShareCardContent? = null,
    val open: StoryOpen? = null,
    val openLabel: String? = null
)

/**
 * Gathers today's stories (PLAN_V2 F4) from what's already on the phone: the Verse of the Day, the
 * devotional and its prayer, quote and word, the calendar, prayer requests, memories and the
 * latest recording's recap. Faith stories only appear for people with the Faith space.
 */
class StoryBuilder(private val context: Context, private val database: MeetMindDatabase = MeetMindDatabase.getInstance(context)) {

    suspend fun build(identity: AppIdentity, date: LocalDate = LocalDate.now()): List<Story> {
        val out = mutableListOf<Story>()
        val epoch = date.toEpochDay()
        // Faith stories sit on the photo library; the rest on painted backgrounds.
        fun bg(salt: Int): BackgroundSpec = if (identity.showsFaith && salt in 1..4)
            com.example.core.share.BackgroundLibrary.forDay(context, epoch, salt)?.let { BackgroundSpec.Photo(it.file.path) } ?: BackgroundSpec.Pack(BackgroundPack.forDay(epoch, salt).id)
            else BackgroundSpec.Pack(BackgroundPack.forDay(epoch, salt).id)

        if (identity.showsFaith) {
            val scripture = ScriptureService(context)
            runCatching {
                val ref = scripture.verseOfTheDay(date.dayOfYear)
                val passage = ref?.let { (scripture.passage(it) as? PassageResult.Found)?.passage }
                if (ref != null && passage != null) out += Story(
                    StoryKind.VERSE, "Verse of the day", body = passage.text, footer = passage.attribution, background = bg(1), quoted = true,
                    title = "${ref.display()} · ${passage.versionAbbreviation}",
                    share = ShareCardContent("Verse of the day", passage.text, "${ref.display()} · ${passage.versionAbbreviation}", passage.attribution),
                    open = StoryOpen.Passage(ref), openLabel = "Read the chapter"
                )
            }
            val daily = runCatching { DevotionalRepository(context).find(LocalDay.of(date)) }.getOrNull()
            val d = daily?.devotional
            val cover = daily?.note?.metadata?.get(NoteRepository.COVER_KEY)?.let { id -> runCatching { NoteRepository(context, database).getAttachment(id)?.path }.getOrNull() }
                ?.takeIf { File(it).exists() }
            if (d != null && d.reflection.isNotEmpty()) {
                val bgD = cover?.let { BackgroundSpec.Photo(it) } ?: bg(2)
                out += Story(
                    StoryKind.DEVOTIONAL, "Today's devotional", title = d.title,
                    body = d.reflection.first().let { if (it.length > 320) it.take(317).substringBeforeLast(' ') + "…" else it },
                    footer = d.scripture.firstOrNull()?.display(),
                    background = bgD, open = StoryOpen.Devotional, openLabel = "Read it all",
                    share = ShareCardContent("Today's devotional", d.reflection.first().take(280), d.title,
                        d.label.takeIf { d.origin == com.example.core.devotional.DevotionalOrigin.CLASSIC }, quoted = false)
                )
                d.prayer?.let { p ->
                    out += Story(StoryKind.PRAYER, "A prayer for today", body = p, background = bg(3), quoted = false,
                        share = ShareCardContent("A prayer for today", p, null, null, quoted = false), open = StoryOpen.Devotional, openLabel = "Open the devotional")
                }
                d.motivation?.let { m ->
                    out += Story(StoryKind.WORD, "A word for today", body = m, background = BackgroundSpec.Pack("indigo"),
                        share = ShareCardContent("A word for today", m, null, null, quoted = false))
                }
            }
            val quote = d?.insight ?: Quotes.pick(Quotes.get(context), emptySet(), date)
            quote?.let { q ->
                out += Story(StoryKind.QUOTE, "Something to carry", body = q.text, footer = q.attribution, background = bg(4), quoted = true,
                    share = ShareCardContent(null, q.text, q.author, q.source))
            }
        }

        // The day ahead.
        runCatching {
            val prefs = UserPreferencesManager(context).preferencesFlow.first()
            val calendar = com.example.core.calendar.CalendarEvents(context)
            if (prefs.calendarEnabled && calendar.hasPermission()) {
                val start = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val fmt = SimpleDateFormat("H:mm", Locale.getDefault())
                val events = calendar.between(start, start + 86_400_000L)
                if (events.isNotEmpty()) out += Story(
                    StoryKind.DAY, "Your day", title = if (events.size == 1) "One thing today" else "${events.size} things today",
                    body = "", background = bg(5),
                    lines = events.sortedBy { it.begin }.take(7).map { e -> (if (e.allDay) "All day" else fmt.format(Date(e.begin))) + "  ·  " + e.title }
                )
            }
        }

        if (identity.showsFaith) runCatching {
            val requests = database.noteDao().getUpdatedSince(0, 400).map { with(NoteCodec) { it.toDomain() } }
                .filter { it.workflow == RecordingType.PRAYER_REQUEST && it.status == NoteStatus.OPEN && it.archivedAt == null }
            if (requests.isNotEmpty()) {
                // Three a day, rotating, so every request is carried in turn.
                val start = Math.floorMod(epoch * 3, requests.size.toLong()).toInt()
                val today = (0 until minOf(3, requests.size)).map { requests[(start + it) % requests.size] }
                out += Story(StoryKind.PRAYING_FOR, "Praying for", title = "Carry these today", body = "", background = bg(6),
                    lines = today.map { it.title.ifBlank { "A prayer request" } }, open = StoryOpen.Note(today.first().id), openLabel = "Open")
            }
        }

        runCatching {
            val memory = TimelineRepository(context, database).onThisDay(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                .firstOrNull { it.workflow == null || identity.showsFaith || it.workflow !in com.example.core.model.Workflows.faith }
            memory?.let { m ->
                out += Story(StoryKind.MEMORY, "On this day", title = m.subtitle, body = m.title, background = m.coverPath?.let { BackgroundSpec.Photo(it) } ?: bg(7),
                    open = m.noteId?.let { StoryOpen.Note(it) }, openLabel = "Open")
            }
        }

        runCatching {
            val since = System.currentTimeMillis() - 2 * 86_400_000L
            val recap = database.meetingDao().getAllMeetings().first()
                .filter { it.createdAt >= since && !it.summaryText.isNullOrBlank() }
                .maxByOrNull { it.createdAt }
            recap?.let { r ->
                val summary = r.summaryText!!.lineSequence().map { it.trim().trimStart('-', '•', '*', ' ') }.filter { it.isNotBlank() }.take(4).toList()
                out += Story(StoryKind.RECAP, "Latest recap", title = r.title, body = "", background = BackgroundSpec.Pack("slate"),
                    lines = summary, open = StoryOpen.Recording(r.id), openLabel = "Open the recording",
                    share = ShareCardContent("Recap · ${r.title}", summary.joinToString("\n") { "• $it" }, null, null, quoted = false))
            }
        }
        return out
    }
}

/** Which stories were seen today — the rings on Home dim once watched. On this phone only. */
object StoriesSeen {
    private fun prefs(context: Context) = context.getSharedPreferences("stories_seen", Context.MODE_PRIVATE)
    private fun key() = LocalDate.now().toString()

    fun seen(context: Context): Set<String> = runCatching { prefs(context).getStringSet(key(), emptySet()).orEmpty() }.getOrDefault(emptySet())

    fun mark(context: Context, kind: StoryKind) = runCatching {
        val p = prefs(context)
        val set = p.getStringSet(key(), emptySet()).orEmpty() + kind.name
        p.edit().clear().putStringSet(key(), set).apply()
    }
}
