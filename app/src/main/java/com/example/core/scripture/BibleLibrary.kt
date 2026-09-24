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
    private val store: BibleStore?,
    /** The Free Use Bible API (PLAN_V2 F5): open translations, audio, commentaries, cross-references. */
    private val helloAo: HelloAoClient? = null,
    /** Where the HelloAO catalogue is kept between runs, so it lists offline too. */
    private val catalogCache: java.io.File? = null
) : BibleProvider {

    @Volatile private var catalog: List<HelloAoTranslation>? = null

    /** The HelloAO catalogue: remembered, refreshed from the network when it can be. */
    suspend fun helloAoCatalog(refresh: Boolean = false): List<HelloAoTranslation> {
        catalog?.takeIf { !refresh }?.let { return it }
        val cached = io { catalogCache?.takeIf { it.exists() }?.readText()?.let { runCatching { HelloAo.parseCatalog(it) }.getOrNull() } }
        if (cached != null && !refresh) { catalog = cached; return cached }
        val fresh = helloAo?.let { client ->
            val list = client.catalog()
            if (list.isNotEmpty()) io { catalogCache?.let { f -> runCatching { f.writeText(org.json.JSONObject().put("translations", org.json.JSONArray(list.map { t ->
                org.json.JSONObject().put("id", t.id).put("shortName", t.shortName).put("name", t.name).put("englishName", t.englishName)
                    .put("language", t.language).put("languageEnglishName", t.languageName).put("licenseUrl", t.licenseUrl ?: "")
                    .put("numberOfBooks", t.books).put("totalNumberOfChapters", t.chapters)
            })).toString()) } } }
            list
        }.orEmpty()
        return (fresh.ifEmpty { cached.orEmpty() }).also { catalog = it }
    }

    /** The HelloAO id behind [bibleId], from the phone's record or the catalogue. */
    private suspend fun helloAoId(bibleId: Int): String? {
        if (!HelloAo.isHelloAo(bibleId)) return null
        io { store?.source(bibleId) }?.removePrefix("helloao:")?.let { return it }
        return helloAoCatalog().firstOrNull { it.intId == bibleId }?.id
    }

    override val isConfigured: Boolean get() = remote.isConfigured || (store?.offlineBibles()?.isNotEmpty() == true)

    override suspend fun bibles(): List<BibleInfo> {
        val online = runCatching { remote.bibles() }.getOrDefault(emptyList())
        val stored = io { store?.offlineBibles().orEmpty().map { BibleInfo(it.id, it.abbreviation, it.title, it.attribution, emptyList(), true) } }
        // Open translations people read most, from the free catalogue.
        val open = helloAoCatalog().filter { it.language == "eng" && it.books >= 66 }.map { it.info() }
        return (online + stored + open).distinctBy { it.id }
    }

    /** Version details, from the network when it answers, else from the phone. */
    suspend fun info(bibleId: Int): BibleInfo? =
        if (HelloAo.isHelloAo(bibleId)) io { store?.info(bibleId) } ?: helloAoCatalog().firstOrNull { it.intId == bibleId }?.info()
        else (remote as? YouVersionScriptureProvider)?.let { runCatching { it.version(bibleId) }.getOrNull() }
            ?: remote.bibles().firstOrNull { it.id == bibleId }
            ?: io { store?.info(bibleId) }

    override suspend fun chapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult {
        stored(bibleId, book, chapter)?.let { return ChapterResult.Found(it) }
        if (HelloAo.isHelloAo(bibleId)) return helloAoChapter(bibleId, book, chapter)
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
        if (HelloAo.isHelloAo(versionId)) {
            val c = (helloAoChapter(versionId, reference.book, reference.chapter) as? ChapterResult.Found)?.content
                ?: return PassageResult.Unavailable(PassageResult.Reason.OFFLINE, "Verse text is unavailable offline.")
            val text = c.text(reference.verseStart, reference.verseEnd)
            return if (text.isEmpty()) PassageResult.Unavailable(PassageResult.Reason.NOT_FOUND, "That passage isn't in this translation.")
            else PassageResult.Found(Passage(reference, text, versionId, c.abbreviation, c.attribution))
        }
        return remote.passage(reference, versionId)
    }

    /** A HelloAO chapter from the network, kept on the phone with its headings and audio. */
    private suspend fun helloAoChapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult {
        val id = helloAoId(bibleId) ?: return ChapterResult.Unavailable(PassageResult.Reason.OFFLINE, "Connect to the internet once to open this translation.")
        val info = info(bibleId) ?: return ChapterResult.Unavailable(PassageResult.Reason.OFFLINE, "Connect to the internet once to open this translation.")
        val fetched = helloAo?.chapter(id, book, chapter)
            ?: return ChapterResult.Unavailable(PassageResult.Reason.OFFLINE, "This chapter isn't on your phone yet. Connect to read it, or download ${info.abbreviation}.")
        if (fetched.verses.isEmpty()) return ChapterResult.Unavailable(PassageResult.Reason.NOT_FOUND, "${book.name} $chapter isn't in ${info.abbreviation}.")
        val content = ChapterContent(bibleId, info.abbreviation, info.attribution, book, chapter, fetched.verses, fromDevice = false)
        io {
            store?.saveChapter(info, content, "helloao:$id")
            store?.saveExtras(bibleId, book, chapter, fetched.headings, fetched.audio)
        }
        return ChapterResult.Found(content)
    }

    /** Section headings for a chapter (HelloAO translations carry them). */
    suspend fun headings(bibleId: Int, book: BibleBook, chapter: Int): List<ChapterHeading> = io { store?.headings(bibleId, book, chapter).orEmpty() }

    /** Narrations of a chapter, with verse timings where the narrator has them. */
    suspend fun audio(bibleId: Int, book: BibleBook, chapter: Int): List<ChapterAudio> {
        val saved = io { store?.audio(bibleId, book, chapter).orEmpty() }
        if (saved.isNotEmpty() || !HelloAo.isHelloAo(bibleId)) return saved
        val id = helloAoId(bibleId) ?: return emptyList()
        val fetched = helloAo?.chapter(id, book, chapter) ?: return emptyList()
        io { store?.saveExtras(bibleId, book, chapter, fetched.headings, fetched.audio) }
        return fetched.audio
    }

    /** A commentary on a chapter: from the phone, else fetched once and kept. */
    suspend fun commentary(source: String, book: BibleBook, chapter: Int): List<CommentaryEntry>? {
        io { store?.commentary(source, book, chapter) }?.let { return it }
        val fetched = helloAo?.commentary(source, book, chapter) ?: return null
        io { store?.saveCommentary(source, book, chapter, fetched) }
        return fetched
    }

    /** Cross-references for a chapter (Open Bible, CC BY): from the phone, else fetched once and kept. */
    suspend fun crossRefs(book: BibleBook, chapter: Int): List<CrossRef>? {
        io { store?.crossRefs(book, chapter) }?.let { return it }
        val fetched = helloAo?.crossRefs(book, chapter) ?: return null
        io { store?.saveCrossRefs(book, chapter, fetched) }
        return fetched
    }

    /** Streams a whole HelloAO translation to the phone; [onProgress] gets books done of 66. */
    suspend fun importHelloAo(bibleId: Int, onProgress: (Int, Int) -> Unit): Boolean {
        val id = helloAoId(bibleId) ?: return false
        val translation = helloAoCatalog().firstOrNull { it.id == id }
        val info = info(bibleId) ?: return false
        val s = store ?: return false
        val client = helloAo ?: return false
        return io {
            val (stream, _) = client.openComplete(id) ?: return@io false
            val total = translation?.books ?: 66
            var pending = mutableListOf<Pair<ChapterContent, Pair<List<ChapterHeading>, List<ChapterAudio>>>>()
            stream.use { input ->
                HelloAo.streamComplete(input, onChapter = { book, n, ch ->
                    pending += ChapterContent(bibleId, info.abbreviation, info.attribution, book, n, ch.verses, fromDevice = true) to (ch.headings to ch.audio)
                }, onBook = { done ->
                    s.saveBook(info, "helloao:$id", pending)
                    pending = mutableListOf()
                    onProgress(done, total)
                })
            }
            s.markComplete(bibleId, true)
            true
        }
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
