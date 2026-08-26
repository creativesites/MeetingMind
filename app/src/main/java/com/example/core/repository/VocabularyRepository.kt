package com.example.core.repository

import com.example.core.database.MeetMindDatabase
import com.example.core.database.VocabularyEntity
import com.example.core.model.VocabularyEntry
import com.example.core.model.VocabularySource
import com.example.core.model.VocabularyTermType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Learned vocabulary (Phase 15 §4) — a persisted surfaceForm -> canonicalForm lookup table, never
 * a fine-tuning mechanism (see docs/AI_ARCHITECTURE.md §11). Global across every recording: a
 * correction made once (e.g. via Replace All) is worth remembering for every future transcript,
 * not just the one it was made in.
 */
class VocabularyRepository(private val database: MeetMindDatabase) {
    private val vocabularyDao = database.vocabularyDao()

    fun getAll(): Flow<List<VocabularyEntry>> = vocabularyDao.getAll().map { list -> list.map(::toDomain) }.flowOn(Dispatchers.IO)

    suspend fun getAllDirect(): List<VocabularyEntry> = withContext(Dispatchers.IO) {
        vocabularyDao.getAllDirect().map(::toDomain)
    }

    /**
     * Records that [surfaceForm] should be corrected to [canonicalForm], from [source]. A no-op
     * for a blank input or a "correction" that doesn't actually change anything (case-insensitive
     * equal). Repeating an already-known correction increments its [VocabularyEntity.frequency]
     * and refreshes [VocabularyEntity.lastConfirmedAt] on the existing row instead of creating a
     * duplicate — the user doing the same correction again is a stronger confirmation signal, not
     * a new fact to store separately.
     */
    suspend fun recordCorrection(surfaceForm: String, canonicalForm: String, source: VocabularySource) = withContext(Dispatchers.IO) {
        val trimmedSurface = surfaceForm.trim()
        val trimmedCanonical = canonicalForm.trim()
        if (trimmedSurface.isEmpty() || trimmedCanonical.isEmpty()) return@withContext
        if (trimmedSurface.equals(trimmedCanonical, ignoreCase = true)) return@withContext

        val existing = vocabularyDao.findBySurfaceForm(trimmedSurface)
        val now = System.currentTimeMillis()
        val toSave = if (existing != null) {
            existing.copy(canonicalForm = trimmedCanonical, source = source.name, frequency = existing.frequency + 1, lastConfirmedAt = now)
        } else {
            VocabularyEntity(
                id = UUID.randomUUID().toString(),
                surfaceForm = trimmedSurface,
                canonicalForm = trimmedCanonical,
                // Honestly unknown until something categorizes it — never guessed from the text.
                type = VocabularyTermType.OTHER.name,
                // 1.0: a direct user correction is the strongest signal this system has — there is
                // no inference involved to be less than fully confident about.
                confidence = 1.0f,
                source = source.name,
                frequency = 1,
                lastConfirmedAt = now
            )
        }
        vocabularyDao.upsert(toSave)
    }

    /**
     * Finds vocabulary entries lexically relevant to [text] — real word-level fuzzy matching
     * against every word in [text], not embeddings, and never the whole vocabulary handed to a
     * caller regardless of relevance (the requirement this exists for: retrieval by lexical
     * similarity, not "dump the whole table into the prompt"). A word matches an entry when it's
     * an exact case-insensitive match to the entry's [VocabularyEntry.surfaceForm], or within a
     * small edit-distance tolerance that scales with the term's length — short terms need an
     * exact match (too easy to false-positive otherwise), longer ones tolerate a couple of
     * character differences (the kind of near-miss ASR actually produces).
     */
    suspend fun findRelevantTerms(text: String, limit: Int = 20): List<VocabularyEntry> = withContext(Dispatchers.IO) {
        val entries = vocabularyDao.getAllDirect()
        if (entries.isEmpty()) return@withContext emptyList()
        val words = text.split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext emptyList()

        entries
            .filter { entry -> isRelevant(entry.surfaceForm, text, words) }
            .sortedByDescending { it.frequency }
            .take(limit)
            .map(::toDomain)
    }

    /** A multi-word [surfaceForm] (e.g. a corrected product name) can never match via per-word
     * fuzzy comparison — no single tokenized word in [text] equals a two-word phrase — so it gets
     * an exact, case-insensitive substring check instead: the same match
     * [TranscriptRepository.replaceAllInTranscript] itself performs, so "relevant" here can never
     * diverge from "would actually get replaced." A single-word [surfaceForm] keeps the
     * word-level fuzzy check, which is where real ASR near-misses actually need the tolerance. */
    private fun isRelevant(surfaceForm: String, text: String, words: List<String>): Boolean {
        val surfaceWordCount = surfaceForm.trim().split(Regex("\\s+")).size
        return if (surfaceWordCount > 1) {
            text.contains(surfaceForm, ignoreCase = true)
        } else {
            words.any { word -> isLexicalMatch(word, surfaceForm) }
        }
    }

    private fun isLexicalMatch(word: String, surfaceForm: String): Boolean {
        if (word.equals(surfaceForm, ignoreCase = true)) return true
        val tolerance = when {
            surfaceForm.length <= 3 -> 0
            surfaceForm.length <= 6 -> 1
            else -> 2
        }
        if (tolerance == 0) return false
        // A cheap length gate before the O(n*m) DP pass below — words whose length already
        // differs by more than the tolerance can never be within it.
        if (kotlin.math.abs(word.length - surfaceForm.length) > tolerance) return false
        return levenshteinDistance(word.lowercase(), surfaceForm.lowercase()) <= tolerance
    }

    private fun toDomain(entity: VocabularyEntity) = VocabularyEntry(
        id = entity.id,
        surfaceForm = entity.surfaceForm,
        canonicalForm = entity.canonicalForm,
        type = runCatching { VocabularyTermType.valueOf(entity.type) }.getOrDefault(VocabularyTermType.OTHER),
        confidence = entity.confidence,
        source = runCatching { VocabularySource.valueOf(entity.source) }.getOrDefault(VocabularySource.SEGMENT_EDIT),
        frequency = entity.frequency,
        lastConfirmedAt = entity.lastConfirmedAt
    )
}

/** Standard DP edit distance between two strings — real word-level fuzzy matching without pulling
 * in a fuzzy-matching library for one small function. */
internal fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) {
                dp[i - 1][j - 1]
            } else {
                1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
    }
    return dp[a.length][b.length]
}
