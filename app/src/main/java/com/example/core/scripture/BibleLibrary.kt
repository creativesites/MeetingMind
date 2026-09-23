package com.example.core.scripture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Bible as the app sees it: the phone first, the network second.
 *
 * - A chapter or passage already on the phone is read from there — no connection needed.
 * - Anything else comes from [remote]; when its licence allows copies, the chapter is kept, so a
 *   passage read once is there next time, offline.
 * - Search runs over what is on the phone ([BibleStore]), which is why a translation is
 *   downloaded whole before it can be searched. YouVersion has no search of its own.
 *
 * [store] is null in tests and anywhere a database isn't wanted; the library then simply passes
 * through to [remote].
 */
class BibleLibrary(
    private val remote: BibleProvider,
    private val store: BibleStore?
) : BibleProvider {

    override val isConfigured: Boolean get() = remote.isConfigured || (store?.offlineBibles()?.isNotEmpty() == true)

    override suspend fun bibles(): List<BibleInfo> {
        val online = remote.bibles()
        if (online.isNotEmpty()) return online
        // Offline: the translations on the phone are still readable.
        return io { store?.offlineBibles().orEmpty().map { BibleInfo(it.id, it.abbreviation, it.title, it.attribution, emptyList(), true) } }
    }

    /** Version details, from the network when it answers, else from the phone. */
    suspend fun info(bibleId: Int): BibleInfo? =
        (remote as? YouVersionScriptureProvider)?.let { runCatching { it.version(bibleId) }.getOrNull() }
            ?: remote.bibles().firstOrNull { it.id == bibleId }
            ?: io { store?.info(bibleId) }

    override suspend fun chapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult {
        stored(bibleId, book, chapter)?.let { return ChapterResult.Found(it) }
        val result = remote.chapter(bibleId, book, chapter)
        if (result is ChapterResult.Found && store != null) {
            val info = info(bibleId)
            if (info != null && info.offlineAllowed) io { store.saveChapter(info, result.content) }
        }
        return result
    }

    override suspend fun passage(reference: ScriptureReference, versionId: Int): PassageResult {
        val content = stored(versionId, reference.book, reference.chapter)
        if (content != null) {
            val text = content.text(reference.verseStart, reference.verseEnd)
            if (text.isNotEmpty()) {
                return PassageResult.Found(Passage(reference, text, versionId, content.abbreviation, content.attribution))
            }
        }
        return remote.passage(reference, versionId)
    }

    override suspend fun verseOfTheDay(dayOfYear: Int): ScriptureReference? = remote.verseOfTheDay(dayOfYear)

    override suspend fun canSearch(bibleId: Int): Boolean = io { store?.isComplete(bibleId) == true }

    override suspend fun search(bibleId: Int, query: String, limit: Int): List<SearchHit> =
        io { store?.search(bibleId, query, limit).orEmpty() }

    /** Stored chapters of [bibleId], so a download can resume where it stopped. */
    suspend fun hasChapter(bibleId: Int, book: BibleBook, chapter: Int): Boolean = io { store?.hasChapter(bibleId, book, chapter) == true }

    private suspend fun stored(bibleId: Int, book: BibleBook, chapter: Int): ChapterContent? {
        val s = store ?: return null
        return io {
            if (!s.hasChapter(bibleId, book, chapter)) null
            else s.info(bibleId)?.let { s.chapter(it, book, chapter) }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
