package com.craftflowtechnologies.meetingmind.core.create

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** One lock per card: generating, remixing, changing the verse and undoing never overlap on the same card. */
object CreateLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun of(cardId: String): Mutex = locks.getOrPut(cardId) { Mutex() }
    fun forget(cardId: String) { locks.remove(cardId) }
}

/**
 * The card being edited, and everything that changes it. Every change that waits on a model or the
 * Bible library runs under the card's lock and reads the card *inside* the lock, so two quick
 * remixes build on each other instead of racing (the old Spark studio overwrote one with the other).
 */
class CreateSession(initial: CreateCard, private val generator: CreateGenerator) {
    private val _card = MutableStateFlow(initial)
    val card: StateFlow<CreateCard> = _card.asStateFlow()

    private val _scripture = MutableStateFlow<ResolvedScripture?>(null)
    /** The verse text for the preview and the export, fetched from the Bible library. Never saved with the card. */
    val scripture: StateFlow<ResolvedScripture?> = _scripture.asStateFlow()

    private val _working = MutableStateFlow<String?>(null)
    /** What is running ("Writing", "Shorter"), or null. */
    val working: StateFlow<String?> = _working.asStateFlow()

    private val lock get() = CreateLocks.of(_card.value.id)

    private suspend fun <T> serialised(label: String, block: suspend () -> T): T = lock.withLock {
        _working.value = label
        try { block() } finally { _working.value = null }
    }

    /** Asks for options. Does not change the card: the person picks one with [choose]. */
    suspend fun write(req: CreateRequest): CreateOutcome = serialised("Writing") { generator.generate(req) }

    /** Takes a written option (or a starter) as the card's words. */
    suspend fun choose(piece: CreatePiece, label: String = "Written", vibe: CreateVibe? = null) = serialised(label) {
        _scripture.value = piece.scripture
        _card.value = _card.value.withVersion(CardVersion(piece.text, piece.scripture?.reference, label)).let { c ->
            c.copy(
                vibe = vibe ?: c.vibe,
                scriptureVersionId = piece.scripture?.versionId ?: c.scriptureVersionId
            )
        }
    }

    suspend fun remix(action: RemixAction): RemixOutcome = serialised(action.label) {
        val outcome = generator.remix(_card.value, action)
        if (outcome is RemixOutcome.Done) {
            _card.value = outcome.card
            _scripture.value = outcome.scripture ?: _scripture.value.takeIf { outcome.card.scriptureRef == it?.reference }
        }
        outcome
    }

    /** The person changed the reference. The text is fetched again; a bad reference leaves the card as it was. */
    suspend fun setReference(reference: String): ScriptureFetch = serialised("Verse") {
        val trimmed = reference.trim()
        if (trimmed.isEmpty()) {
            _card.value = _card.value.withVersion(CardVersion(_card.value.text, null, "No verse"))
            _scripture.value = null
            return@serialised ScriptureFetch.Invalid("")
        }
        val fetched = generator.resolveReference(trimmed)
        if (fetched is ScriptureFetch.Found) {
            val s = fetched.scripture
            _card.value = _card.value.withVersion(CardVersion(_card.value.text, s.reference, "Verse")).copy(scriptureVersionId = s.versionId)
            _scripture.value = s
        }
        fetched
    }

    /** Loads the verse text for a reopened card. */
    suspend fun loadScripture() = serialised("Verse") {
        val c = _card.value
        _scripture.value = (generator.scriptureFor(c) as? ScriptureFetch.Found)?.scripture
    }

    /** Typing. Consecutive edits are one version, so undo steps back past the whole edit. */
    suspend fun edit(text: String) = serialised("Edit") {
        val c = _card.value
        _card.value = if (c.current.label == EDITED) c.copy(current = c.current.copy(text = text), updatedAt = System.currentTimeMillis())
        else c.withVersion(CardVersion(text, c.scriptureRef, EDITED))
    }

    suspend fun undo() = serialised("Undo") {
        _card.value = _card.value.undo()
        val ref = _card.value.scriptureRef
        if (_scripture.value?.reference != ref) _scripture.value = if (ref == null) null else (generator.scriptureFor(_card.value) as? ScriptureFetch.Found)?.scripture
    }

    // Plain settings: no waiting, no lock needed.
    fun setVibe(vibe: CreateVibe) { _card.value = _card.value.copy(vibe = vibe) }
    fun setFormat(format: CreateFormat) { _card.value = _card.value.copy(format = format) }
    fun setDesign(design: CreateDesign) { _card.value = _card.value.copy(design = design) }
    fun setSource(source: CreateSourceKind, text: String, reference: String?) {
        _card.value = _card.value.copy(source = source, sourceText = text.take(CreateCard.MAX_SOURCE), sourceRef = reference)
    }

    companion object { const val EDITED = "Edited" }
}
