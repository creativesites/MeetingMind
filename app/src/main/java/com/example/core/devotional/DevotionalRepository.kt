package com.example.core.devotional

import android.content.Context
import com.example.ai.devotional.DevotionalEngine
import com.example.ai.devotional.DevotionalSignals
import com.example.ai.devotional.ModelCandidate
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.ModelCapability
import com.example.core.model.Note
import com.example.core.model.NoteDocument
import com.example.core.model.NoteStatus
import com.example.core.model.ProcessingProfile
import com.example.core.model.RecordingType
import com.example.core.repository.NoteCodec
import com.example.core.repository.NoteRepository
import com.example.core.scripture.PassageResult
import com.example.core.scripture.ScriptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** The devotional of a day, as its note and what was read back from it. */
data class DailyDevotional(val note: Note, val devotional: Devotional, val document: NoteDocument) {
    val response: String get() = DevotionalNotes.response(document)
    val feedback: String? get() = note.metadata[DevotionalNotes.META_FEEDBACK]
}

/**
 * Finds, writes and keeps the daily devotional (PLAN_V2 F2). What it knows about the person is
 * gathered here, on the phone; private lines only reach a cloud model with the person's say-so.
 */
private const val META_VARIANT = "devotionalVariant"
const val META_ASKED = "devotionalAsked"
const val META_WRITER = "devotionalWriter"
private const val EARLIER = "-earlier-"

class DevotionalRepository(
    private val context: Context,
    private val database: MeetMindDatabase = MeetMindDatabase.getInstance(context),
    private val notes: NoteRepository = NoteRepository(context, database),
    private val prefs: UserPreferencesManager = UserPreferencesManager(context)
) {
    private val noteDao = database.noteDao()

    val profile: Flow<DevotionalProfile> get() = prefs.devotionalProfile

    suspend fun setProfile(profile: DevotionalProfile) {
        prefs.setDevotionalProfile(profile)
        DevotionalScheduler.sync(context, profile)
    }

    private fun pattern(key: String) = "%\"${DevotionalNotes.META_KEY}\":\"$key\"%"

    fun observe(day: LocalDay, evening: Boolean = false): Flow<DailyDevotional?> =
        noteDao.observeByMetadata(pattern(DevotionalNotes.key(day, evening)))
            .map { entity -> entity?.takeIf { it.archivedAt == null }?.let { load(it.id) } }
            .flowOn(Dispatchers.IO)

    /**
     * Every devotional of [day] (morning), oldest first: the day's current one and any the person
     * asked for or set aside. Nothing written for the day is ever thrown away.
     */
    fun observeDay(day: LocalDay): Flow<List<DailyDevotional>> =
        noteDao.observeAllByMetadata("%\"${DevotionalNotes.META_KEY}\":\"${day.iso}%")
            .map { list ->
                list.filter { e -> keyOf(e.metadataJson).let { it == day.iso || it?.startsWith("${day.iso}$EARLIER") == true } }
                    .mapNotNull { load(it.id) }
            }
            .flowOn(Dispatchers.IO)

    private fun keyOf(metadataJson: String?): String? =
        runCatching { org.json.JSONObject(metadataJson ?: "{}").optString(DevotionalNotes.META_KEY) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Makes [daily] the day's devotional (the one stories, widgets and Home show); the current one steps aside. */
    suspend fun makeCurrent(daily: DailyDevotional) = writing.withLock {
        withContext(Dispatchers.IO) {
            val day = daily.devotional.day
            val key = DevotionalNotes.key(day)
            if (daily.note.metadata[DevotionalNotes.META_KEY] == key) return@withContext
            find(day)?.let { setAside(it) }
            editMeta(daily.note.id) { it + (DevotionalNotes.META_KEY to key) }
        }
    }

    suspend fun find(day: LocalDay, evening: Boolean = false): DailyDevotional? = withContext(Dispatchers.IO) {
        noteDao.findByMetadata(pattern(DevotionalNotes.key(day, evening)))?.takeIf { it.archivedAt == null }?.let { load(it.id) }
    }

    private suspend fun load(noteId: String): DailyDevotional? {
        val doc = notes.getDocument(noteId) ?: return null
        val d = DevotionalNotes.read(doc) ?: return null
        return DailyDevotional(doc.note, d, doc)
    }

    private val writing = Mutex()

    /**
     * Writes [date]'s devotional unless there already is one. With [another], a new one is
     * written for the day and becomes current; the one before is kept (set aside, still listed for
     * the day). If writing fails nothing changes — the error is thrown, never a blank page saved.
     */
    suspend fun ensure(
        date: LocalDate = LocalDate.now(), another: Boolean = false, evening: Boolean = false,
        ask: com.example.ai.devotional.DevotionalAsk? = null
    ): DailyDevotional? = writing.withLock {
        withContext(Dispatchers.IO) {
            val day = LocalDay.of(date)
            val existing = find(day, evening)
            if (existing != null && !another) return@withContext existing
            val profile = prefs.devotionalProfile.first()
            // Each new one steps further, so it never repeats what was just read.
            val variant = if (another) (existing?.note?.metadata?.get(META_VARIANT)?.toIntOrNull() ?: 0) + 1 else 0
            val request = (ask ?: com.example.ai.devotional.DevotionalAsk()).copy(variant = variant)
            val (devotional, reason) = write(date, profile, evening, request)
            val saved = save(devotional, profile, evening, reason, extra = buildMap {
                put(META_VARIANT, variant.toString())
                ask?.about?.takeIf { it.isNotBlank() }?.let { put(META_ASKED, it.take(300)) }
                put(META_WRITER, request.writer.name)
            })
            if (saved != null && existing != null && existing.note.id != saved.note.id) setAside(existing)
            saved
        }
    }

    /** Last fallback reason, shown quietly under a classic that stood in for an AI devotional. */
    var lastFallbackReason: String? = null
        private set

    private suspend fun write(date: LocalDate, profile: DevotionalProfile, evening: Boolean, ask: com.example.ai.devotional.DevotionalAsk?): Pair<Devotional, String?> {
        val app = prefs.preferencesFlow.first()
        val scripture = ScriptureService(context)
        val engine = DevotionalEngine(
            candidates = { candidates(app.processingProfile, ask?.writer ?: com.example.ai.devotional.DevotionalWriter.AUTO) },
            verseText = { ref -> (scripture.passage(ref) as? PassageResult.Found)?.passage?.text },
            classics = ClassicDevotionals.get(context),
            quotes = Quotes.get(context),
            verseOfTheDay = { d -> runCatching { scripture.verseOfTheDay(d.dayOfYear) }.getOrNull() }
        )
        val signals = if (profile.source == DevotionalSource.CLASSIC) DevotionalSignals() else signals(date)
        return try {
            val d = engine.write(date, profile, signals, app.identity.displayName?.substringBefore(' '), evening, ask)
            d to engine.lastFallbackReason.takeIf { d.origin == DevotionalOrigin.CLASSIC && profile.source != DevotionalSource.CLASSIC }
        } finally {
            runCatching { com.example.ai.modelmanagement.LlmEngineManager.release() }
        }
    }

    private suspend fun candidates(processing: ProcessingProfile, writer: com.example.ai.devotional.DevotionalWriter): List<ModelCandidate> {
        val factory = com.example.ai.routing.LanguageModelFactory(
            context = context,
            modelStorage = com.example.ai.modelmanagement.LocalModelStorage(context),
            geminiTransport = com.example.ai.cloud.GeminiHttpTransport(com.example.ai.cloud.GeminiCredentialStore(context))
        )
        fun local() = runCatching { factory.resolveLocal(ModelCapability.SUMMARIZATION, com.example.core.model.ModelTier.RECOMMENDED) }.getOrNull()
        // Asking for Gemini by name uses it even when recordings stay offline: it's this one request.
        suspend fun cloud() = runCatching { factory.resolve(ProcessingProfile.INTERNET, ModelCapability.SUMMARIZATION) }.getOrNull()?.takeIf { it.isCloud }
        val chosen = when (writer) {
            com.example.ai.devotional.DevotionalWriter.DEVICE -> listOfNotNull(local())
            com.example.ai.devotional.DevotionalWriter.GEMINI -> listOfNotNull(cloud())
            com.example.ai.devotional.DevotionalWriter.CLASSIC -> emptyList()
            com.example.ai.devotional.DevotionalWriter.AUTO -> {
                val first = runCatching { factory.resolve(processing, ModelCapability.SUMMARIZATION) }.getOrNull()
                listOfNotNull(first, if (first?.isCloud == true) local() else null)
            }
        }
        return chosen.map { ModelCandidate(it.languageModel, it.modelId, it.isCloud) }
    }

    /** Which writers can run now, for the "New devotional" sheet. */
    suspend fun writersAvailable(): Set<com.example.ai.devotional.DevotionalWriter> = withContext(Dispatchers.IO) {
        val storage = com.example.ai.modelmanagement.LocalModelStorage(context)
        buildSet {
            add(com.example.ai.devotional.DevotionalWriter.CLASSIC)
            add(com.example.ai.devotional.DevotionalWriter.AUTO)
            if (com.example.ai.modelmanagement.ModelCatalog.entries.any { ModelCapability.SUMMARIZATION in it.capability && storage.isInstalled(it.id) }) add(com.example.ai.devotional.DevotionalWriter.DEVICE)
            if (com.example.ai.cloud.GeminiCredentialStore(context).getApiKey() != null) add(com.example.ai.devotional.DevotionalWriter.GEMINI)
        }
    }

    /** What's been happening lately, sorted by who may read it. */
    suspend fun signals(date: LocalDate): DevotionalSignals = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val since = date.minusDays(21).atStartOfDay(zone).toInstant().toEpochMilli()
        val recent = noteDao.getUpdatedSince(since).map { with(NoteCodec) { it.toDomain() } }
        val general = mutableListOf<String>()
        val private = mutableListOf<String>()
        val words = mutableListOf<String>()
        val threeDays = date.minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()

        recent.filter { it.workflow == RecordingType.SERMON }.take(3).forEach { n ->
            general += "Heard a sermon: \"${n.title.take(80)}\"" + (n.metadata["speaker"]?.takeIf { it.isNotBlank() }?.let { " by $it" } ?: "")
        }
        recent.filter { it.metadata[DevotionalNotes.META_FEEDBACK] != null }.take(6).forEach { n ->
            when (n.metadata[DevotionalNotes.META_FEEDBACK]) {
                "up", "more" -> general += "Found this devotional helpful: \"${n.title.take(60)}\""
                "down", "less" -> general += "Didn't connect with: \"${n.title.take(60)}\""
            }
        }
        recent.filter { it.workflow == RecordingType.PRAYER_REQUEST && it.status == NoteStatus.OPEN }.take(4).forEach {
            private += "Praying about: ${it.title.take(80)}"
        }
        recent.filter { it.workflow == RecordingType.PRAYER_REQUEST && it.status == NoteStatus.ANSWERED }.take(2).forEach {
            general += "Recently saw an answered prayer"
        }
        recent.filter { it.workflow in setOf(RecordingType.JOURNAL, RecordingType.REFLECTION, RecordingType.GRATITUDE) }.take(3).forEach {
            it.plainText.lineSequence().map { l -> l.trim() }.firstOrNull { l -> l.length > 20 && l != it.title }?.let { l -> private += "Wrote: ${l.take(140)}" }
        }
        // The care check reads the person's own recent words — on the phone, never sent.
        recent.filter { it.updatedAt >= threeDays && it.metadata[DevotionalNotes.META_DAY] == null }
            .filter { it.workflow in com.example.core.model.Workflows.faith || it.workflow == RecordingType.JOURNAL }
            .forEach { words += it.plainText.take(2000) }

        runCatching {
            val prefsNow = prefs.preferencesFlow.first()
            val calendar = com.example.core.calendar.CalendarEvents(context)
            if (prefsNow.calendarEnabled && calendar.hasPermission()) {
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val count = calendar.between(start, start + 86_400_000L).count { !it.allDay }
                if (count >= 5) general += "A full day ahead: $count events on the calendar"
                else if (count in 1..4) general += "$count events on the calendar today"
            }
        }
        DevotionalSignals(general, private, words)
    }

    private suspend fun save(d: Devotional, profile: DevotionalProfile, evening: Boolean, reason: String?, extra: Map<String, String> = emptyMap()): DailyDevotional? {
        lastFallbackReason = reason
        val zone = ZoneId.systemDefault()
        val at = d.day.date.atStartOfDay(zone).toInstant().toEpochMilli() + profile.deliveryMinutes * 60_000L + (if (evening) 12 * 3_600_000L else 0L)
        val note = notes.createNote(
            workflow = RecordingType.DEVOTIONAL,
            title = d.title,
            isPrivate = false,
            eventDate = at,
            metadata = DevotionalNotes.metadata(d, evening) + extra + (reason?.let { mapOf("devotionalFallback" to it) } ?: emptyMap()),
            initialBlocks = listOf(com.example.core.model.NoteBlock("tmp", "tmp", 0, com.example.core.model.NoteBlockType.PARAGRAPH)),
            useTemplate = false
        )
        if (d.origin == DevotionalOrigin.MINE) {
            // "My own": the person's usual devotional page, starting from the day's passage.
            val blocks = com.example.core.model.Workflows.startingBlocks(RecordingType.DEVOTIONAL, note.id, forRecording = false)
            notes.saveBlocks(note.id, blocks)
            d.scripture.firstOrNull()?.let { ref ->
                val (withVerse, refs) = DevotionalNotes.build(note.id, d.copy(reflection = emptyList()))
                val verseBlocks = withVerse.filter { it.sectionKey == DevotionalNotes.S_SCRIPTURE && it.type == com.example.core.model.NoteBlockType.SCRIPTURE }
                val merged = blocks.toMutableList()
                val at2 = merged.indexOfFirst { it.sectionKey == "scripture" && it.type != com.example.core.model.NoteBlockType.HEADING_2 }
                if (at2 >= 0) merged[at2] = verseBlocks.first().copy(sectionKey = "scripture") else merged.addAll(0, verseBlocks)
                notes.saveBlocks(note.id, merged)
                notes.addScriptureRefs(refs.take(1))
            }
        } else {
            val (blocks, refs) = DevotionalNotes.build(note.id, d)
            notes.saveBlocks(note.id, blocks)
            notes.addScriptureRefs(refs)
        }
        return load(note.id)
    }

    /** Moves a devotional out of the day's current slot, keeping it (and its response) for the day. */
    private suspend fun setAside(old: DailyDevotional) {
        val key = old.note.metadata[DevotionalNotes.META_KEY] ?: return
        editMeta(old.note.id) { it + (DevotionalNotes.META_KEY to "$key$EARLIER${System.currentTimeMillis()}") }
    }

    /**
     * Paints the day's picture with Gemini and makes it the devotional's cover — it becomes the
     * story background, the timeline card and the share card's first choice. No-op offline.
     */
    suspend fun paint(daily: DailyDevotional, profile: DevotionalProfile): Boolean {
        if (daily.note.metadata[NoteRepository.COVER_KEY] != null) return true
        val images = com.example.core.share.ImageBackgrounds(context)
        if (!images.available()) return false
        val style = runCatching { com.example.core.share.ImageStyle.valueOf(profile.imageStyle) }.getOrDefault(com.example.core.share.ImageStyle.LANDSCAPE)
        val theme = listOfNotNull(daily.devotional.title, daily.devotional.scripture.firstOrNull()?.display(), daily.devotional.motivation).joinToString(" — ")
        val target = java.io.File(java.io.File(context.filesDir, "devotional_images").apply { mkdirs() }, "${daily.note.id}.png")
        val file = (images.generate(theme, style, com.example.core.share.ShareFormat.STORY, target) as? com.example.ai.common.AiResult.Success)?.value ?: return false
        val attachment = notes.addAttachment(
            com.example.core.model.Attachment(
                id = NoteRepository.newId("att"), noteId = daily.note.id, kind = com.example.core.model.AttachmentKind.IMAGE, path = file.path,
                mimeType = "image/png", sizeBytes = file.length(), caption = "Picture for ${daily.devotional.title} (AI-generated)", createdAt = System.currentTimeMillis()
            )
        )
        editMeta(daily.note.id) { it + (NoteRepository.COVER_KEY to attachment.id) }
        return true
    }

    /** Changes a devotional's metadata on the stored note as it is now, so concurrent edits aren't lost. */
    private suspend fun editMeta(noteId: String, change: (Map<String, String>) -> Map<String, String>) = writingMeta.withLock {
        val fresh = notes.getNote(noteId) ?: return@withLock
        notes.updateNote(fresh.copy(metadata = change(fresh.metadata)))
    }
    private val writingMeta = Mutex()

    suspend fun setFeedback(daily: DailyDevotional, value: String?) {
        editMeta(daily.note.id) { m -> if (value == null) m - DevotionalNotes.META_FEEDBACK else m + (DevotionalNotes.META_FEEDBACK to value) }
        if (value == "more" || value == "less") {
            val topics = daily.devotional.scripture.flatMap { TopicPassages.topicsOf(it) }.toSet()
            if (topics.isNotEmpty()) {
                val p = prefs.devotionalProfile.first()
                prefs.setDevotionalProfile(
                    if (value == "more") p.copy(moreOf = p.moreOf + topics, lessOf = p.lessOf - topics)
                    else p.copy(lessOf = p.lessOf + topics, moreOf = p.moreOf - topics)
                )
            }
        }
    }

    suspend fun markOpened(daily: DailyDevotional) {
        if (daily.note.metadata[DevotionalNotes.META_OPENED] != null) return
        editMeta(daily.note.id) { it + (DevotionalNotes.META_OPENED to System.currentTimeMillis().toString()) }
    }

    suspend fun saveResponse(daily: DailyDevotional, text: String) {
        val doc = notes.getDocument(daily.note.id) ?: return
        notes.saveBlocks(doc.note.id, DevotionalNotes.withResponse(doc, text))
    }

    /** Recent devotionals, newest first — for streaks and the "past days" row. */
    suspend fun recent(limit: Int = 30): List<Note> = withContext(Dispatchers.IO) {
        noteDao.findAllByMetadata("%\"${DevotionalNotes.META_DAY}\":%", limit).map { with(NoteCodec) { it.toDomain() } }
    }
}
