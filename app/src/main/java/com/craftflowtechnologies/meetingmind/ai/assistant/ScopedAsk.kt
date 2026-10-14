package com.craftflowtechnologies.meetingmind.ai.assistant

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.scripture.BibleStore
import com.craftflowtechnologies.meetingmind.core.work.ContextPackBuilder
import com.craftflowtechnologies.meetingmind.core.work.ContextRepository
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.ItemKind
import com.craftflowtechnologies.meetingmind.core.work.PackPrivacy
import com.craftflowtechnologies.meetingmind.core.work.PackScope
import com.craftflowtechnologies.meetingmind.core.work.WorkModels
import com.craftflowtechnologies.meetingmind.core.work.WorkTypeNames
import com.craftflowtechnologies.meetingmind.core.work.itemKind
import com.craftflowtechnologies.meetingmind.core.work.itemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What Ask is limited to (docs/PLAN_PROFESSIONAL.md D5.5): one person, organisation or project, or
 * everything in Work, optionally within a stretch of time. [label] is what the scope chip says.
 */
data class AskScope(val type: ContextType? = null, val id: String? = null, val from: Long? = null, val to: Long? = null, val label: String = "My work") {
    val packScope: PackScope get() = if (type != null && id != null) PackScope.Entity(type, id) else PackScope.AllWork
}

enum class ScopedReason { NO_MODEL, NOTHING_FOUND, UNCITED, FAILED }

/** [text] is null unless the answer cites at least one source. [sources] are always returned so the person can read them. */
data class ScopedAnswer(val text: String?, val cited: List<AskSource>, val sources: List<AskSource>, val reason: ScopedReason? = null)

/**
 * Ask, limited to a scope. The scope decides which notes and transcripts are even searched (through
 * `note_people`, `item_links` and the project's notes), the context pack goes in ahead of the
 * transcript chunks, and an answer that cites nothing is not shown. It goes through [WorkModels],
 * so anything that has to stay on the phone only ever meets the local model, or none.
 */
class ScopedAsk(
    private val database: MeetMindDatabase,
    private val models: WorkModels?,
    private val privacy: PackPrivacy = PackPrivacy { _, _ -> false },
    private val clock: () -> Long = System::currentTimeMillis,
    private val dateLabel: (Long) -> String = { it.toString() },
    private val maxSources: Int = 14
) {
    private val context = ContextRepository(database, clock)

    suspend fun ask(question: String, scope: AskScope): ScopedAnswer = withContext(Dispatchers.IO) {
        val sources = gather(question, scope)
        if (sources.isEmpty()) return@withContext ScopedAnswer("I couldn't find anything about that in ${scope.label}.", emptyList(), emptyList(), ScopedReason.NOTHING_FOUND)
        // Privacy is decided on everything that will actually be shown to a model.
        val meetingIds = sources.mapNotNull { it.meetingId }.distinct()
        val model = models?.forPack(privacy.mustStayOnDevice(meetingIds, scope.packScope)) ?: return@withContext ScopedAnswer(null, emptyList(), sources, ScopedReason.NO_MODEL)
        val raw = (model.generate(prompt(question, sources), maxOutputTokens = 400) as? AiResult.Success)?.value
            ?: return@withContext ScopedAnswer(null, emptyList(), sources, ScopedReason.FAILED)
        val parsed = AskEverything.parse(raw, sources)
        if (parsed.cited.isEmpty()) return@withContext ScopedAnswer(null, emptyList(), sources, ScopedReason.UNCITED)
        ScopedAnswer(parsed.text, parsed.cited, sources)
    }

    /** The context pack first, then what search finds inside the scope. Numbered from 1. */
    internal suspend fun gather(question: String, scope: AskScope): List<AskSource> {
        val pack = ContextPackBuilder(database, privacy, clock).build(scope.packScope, scope.from, scope.to)
        val fromPack = pack.items.take(maxSources).map { i ->
            val said = pack.evidenceFor(i.id)
            AskSource(
                0, SourceKind.ITEM, i.itemKind.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                buildString {
                    append(i.text).append(" (").append(i.itemStatus.name.lowercase()).append(')')
                    said?.let { append(" — said: “").append(it.quote).append('”') }
                },
                date = i.createdAt, meetingId = i.meetingId, startMs = said?.startMs, noteId = i.noteId
            )
        }
        val meetingIds = allowedMeetings(scope)
        val noteIds = allowedNotes(scope, meetingIds)
        val terms = AskEverything.terms(question).ifEmpty { listOf(question) }
        val hits = mutableListOf<AskSource>()
        val seen = HashSet<String>()
        // One search per content word, so a question's filler words don't have to match too.
        for (term in terms) {
            val fts = BibleStore.ftsQuery(term) ?: continue
            runCatching { database.searchDao().segments(fts, 40) }.getOrDefault(emptyList()).filter { it.meetingId in meetingIds && seen.add("s${it.id}") }.take(4).forEach { h ->
                if (hits.count { it.kind == SourceKind.RECORDING } < 6) hits += AskSource(0, SourceKind.RECORDING, h.meetingTitle, h.snippet, meetingId = h.meetingId, startMs = h.startMs)
            }
            runCatching { database.searchDao().notes(fts, 40) }.getOrDefault(emptyList()).filter { it.id in noteIds && seen.add("n${it.id}") }.take(3).forEach { h ->
                val note = database.noteDao().getById(h.id)?.takeIf { !it.isPrivate } ?: return@forEach
                if (hits.count { it.kind == SourceKind.NOTE } >= 4) return@forEach
                val excerpt = terms.firstNotNullOfOrNull { com.craftflowtechnologies.meetingmind.core.repository.SearchRepository.snippetAround(note.plainText, it, 500) } ?: note.plainText.take(500)
                hits += AskSource(0, SourceKind.NOTE, note.title.ifBlank { "Untitled note" }, excerpt, date = note.eventDate ?: note.createdAt, noteId = note.id)
            }
        }
        return (fromPack + hits).take(maxSources).mapIndexed { i, s -> s.copy(key = i + 1) }
    }

    /** The recordings inside the scope: those of the person, organisation or project, and those its items came from. */
    private suspend fun allowedMeetings(scope: AskScope): Set<String> {
        val inRange: (Long) -> Boolean = { (scope.from == null || it >= scope.from) && (scope.to == null || it < scope.to) }
        return if (scope.type != null && scope.id != null) {
            (context.meetings(scope.type, scope.id).filter { inRange(it.createdAt) }.map { it.id } +
                context.items(scope.type, scope.id).mapNotNull { it.meetingId }).toSet()
        } else database.workDao().allMeetings().filter { it.recordingType in WorkTypeNames && inRange(it.createdAt) }.map { it.id }.toSet()
    }

    private suspend fun allowedNotes(scope: AskScope, meetingIds: Set<String>): Set<String> {
        val fromMeetings = meetingIds.mapNotNull { database.meetingDao().getMeetingById(it)?.noteId }.toSet()
        val direct = if (scope.type != null && scope.id != null) {
            context.ids(scope.type, scope.id).flatMap { database.workDao().noteIdsFor(it) }.toSet() +
                (if (scope.type == ContextType.PROJECT) database.workDao().noteIdsInProject(scope.id) else emptyList())
        } else emptySet()
        return fromMeetings + direct
    }

    private fun prompt(question: String, sources: List<AskSource>) = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine("Answer the question using ONLY the numbered sources below. After every sentence, cite the sources it rests on, like [1] or [2][3]. If the sources don't answer it, say so plainly. Never add a fact, name, date or number that is not in them.")
        appendLine()
        appendLine("Sources:")
        appendLine(AskEverything.render(sources, dateLabel))
        appendLine()
        append("Question: $question")
    }
}
