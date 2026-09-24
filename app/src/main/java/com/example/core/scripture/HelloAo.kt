package com.example.core.scripture

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/** A translation in the Free Use Bible API catalogue (bible.helloao.org). All are free to keep. */
data class HelloAoTranslation(
    val id: String,
    val shortName: String,
    val name: String,
    val englishName: String,
    val language: String,
    val languageName: String,
    val licenseUrl: String?,
    val books: Int,
    val chapters: Int
) {
    val intId: Int get() = HelloAo.intId(id)
    val attribution: String get() = "$name ($shortName), via the Free Use Bible API (bible.helloao.org)" + (licenseUrl?.let { ". Licence: $it" } ?: ".")
    fun info() = BibleInfo(intId, shortName, name, attribution, emptyList(), offlineAllowed = true)
}

/** A section heading inside a chapter, placed before [beforeVerse]. */
data class ChapterHeading(val beforeVerse: Int, val text: String)

/** A narrated chapter: where to stream it, and when each verse starts (seconds). */
data class ChapterAudio(val narrator: String, val url: String, val timings: List<Double>, val timingsLink: String? = null) {
    /** The verse being read at [ms] into the recording; null during the chapter's announcement. */
    fun verseAt(ms: Long): Int? {
        val s = ms / 1000.0
        val i = timings.indexOfLast { it <= s }
        return if (i < 0) null else i + 1
    }
    /** Where verse [verse] starts, in milliseconds (each timing is a verse's start; before the first is the chapter's announcement). */
    fun startOf(verse: Int): Long? = timings.getOrNull(verse - 1)?.let { (it * 1000).toLong() }
}

/** A cross-reference from a verse, with how many people linked the two (the dataset's votes). */
data class CrossRef(val fromVerse: Int, val to: ScriptureReference, val score: Int)

/** One commentary entry: it speaks to [verse] (and on to the next entry). */
data class CommentaryEntry(val verse: Int, val text: String)

data class Commentary(val id: String, val name: String, val short: String)

/** A chapter as HelloAO gives it: verses, headings and narrations. */
data class HelloAoChapter(val verses: List<ChapterVerse>, val headings: List<ChapterHeading>, val audio: List<ChapterAudio>)

/**
 * The Free Use Bible API (PLAN_V2 F5): 1,200+ translations, audio with verse timings, public-domain
 * commentaries and the Open Bible cross-references, with no key and no usage limits.
 */
object HelloAo {
    const val BASE = "https://bible.helloao.org"

    val commentaries = listOf(
        Commentary("matthew-henry", "Matthew Henry", "Henry"),
        Commentary("jamieson-fausset-brown", "Jamieson, Fausset & Brown", "JFB"),
        Commentary("adam-clarke", "Adam Clarke", "Clarke"),
        Commentary("john-gill", "John Gill", "Gill"),
        Commentary("john-calvin", "John Calvin", "Calvin"),
        Commentary("keil-delitzsch", "Keil & Delitzsch (Old Testament)", "K&D"),
        Commentary("tyndale", "Tyndale Open Study Notes", "Tyndale")
    )

    /** HelloAO ids are strings; the app's Bible ids are ints. Negative, so they never meet YouVersion's. */
    fun intId(id: String): Int {
        val crc = CRC32().apply { update(id.toByteArray()) }.value
        return -((crc % 1_000_000_000L).toInt() + 1)
    }

    fun isHelloAo(bibleId: Int) = bibleId < 0

    fun parseCatalog(json: String): List<HelloAoTranslation> {
        val a = JSONObject(json).optJSONArray("translations") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            HelloAoTranslation(
                o.getString("id"), o.optString("shortName", o.getString("id")), o.optString("name"), o.optString("englishName"),
                o.optString("language"), o.optString("languageEnglishName", o.optString("languageName")),
                o.optString("licenseUrl").takeIf { it.isNotBlank() && it != "null" }, o.optInt("numberOfBooks"), o.optInt("totalNumberOfChapters")
            )
        }
    }

    /** The text of a content array: strings, and `{text}` / `{heading}` objects; footnote markers dropped. */
    private fun text(content: JSONArray?): Pair<String, Boolean> {
        if (content == null) return "" to false
        val sb = StringBuilder()
        var poem = false
        for (i in 0 until content.length()) {
            when (val item = content.get(i)) {
                is String -> sb.append(item)
                is JSONObject -> {
                    if (item.has("text")) { sb.append(item.optString("text")); if (item.optInt("poem", 0) > 0) { poem = true; sb.append('\n') } }
                    if (item.optBoolean("lineBreak")) sb.append('\n')
                }
            }
        }
        return sb.toString().replace(Regex("[ \\t]+"), " ").replace(Regex(" *\n+ *"), "\n").trim() to poem
    }

    /** A chapter object ({number, content[]}) and its surrounding audio fields. */
    fun parseChapter(root: JSONObject): HelloAoChapter {
        val chapter = root.optJSONObject("chapter") ?: root
        val content = chapter.optJSONArray("content") ?: JSONArray()
        val verses = mutableListOf<ChapterVerse>()
        val headings = mutableListOf<ChapterHeading>()
        var paragraphNext = true
        var pendingHeading: String? = null
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "heading" -> { pendingHeading = listOfNotNull(pendingHeading, text(item.optJSONArray("content")).first).joinToString("\n"); paragraphNext = true }
                "hebrew_subtitle" -> { pendingHeading = listOfNotNull(pendingHeading, text(item.optJSONArray("content")).first).joinToString("\n"); paragraphNext = true }
                "line_break" -> paragraphNext = true
                "verse" -> {
                    val number = item.optInt("number")
                    val (t, poem) = text(item.optJSONArray("content"))
                    pendingHeading?.let { h -> if (h.isNotBlank()) headings += ChapterHeading(number, h) }
                    pendingHeading = null
                    if (t.isNotBlank()) verses += ChapterVerse(number, t, paragraphNext, poem)
                    paragraphNext = false
                }
            }
        }
        return HelloAoChapter(verses, headings, parseAudio(root))
    }

    fun parseAudio(root: JSONObject): List<ChapterAudio> {
        val links = root.optJSONObject("thisChapterAudioLinks") ?: return emptyList()
        val timings = root.optJSONObject("thisChapterAudioTimings")
        return links.keys().asSequence().mapNotNull { narrator ->
            val url = links.optString(narrator).takeIf { it.startsWith("http") } ?: return@mapNotNull null
            // complete.json carries the timings inline; a single chapter links to them.
            val t = timings?.optJSONArray(narrator)
            val link = if (t == null) timings?.optString(narrator)?.takeIf { it.startsWith("/") } else null
            ChapterAudio(narrator, url, (0 until (t?.length() ?: 0)).map { t!!.getDouble(it) }, link)
        }.sortedByDescending { it.timings.isNotEmpty() || it.timingsLink != null }.toList()
    }

    /** A narrator's verse start times, from its audioTimings file. */
    fun parseTimings(json: String): List<Double> {
        val a = JSONObject(json).optJSONArray("verses") ?: return emptyList()
        return (0 until a.length()).map { a.getDouble(it) }
    }

    fun parseCommentary(json: String): List<CommentaryEntry> {
        val content = JSONObject(json).optJSONObject("chapter")?.optJSONArray("content") ?: return emptyList()
        return (0 until content.length()).mapNotNull { i ->
            val o = content.optJSONObject(i) ?: return@mapNotNull null
            if (o.optString("type") != "verse") return@mapNotNull null
            val t = text(o.optJSONArray("content")).first
            if (t.isBlank()) null else CommentaryEntry(o.optInt("number"), t)
        }
    }

    fun parseCrossRefs(json: String): List<CrossRef> {
        val content = JSONObject(json).optJSONObject("chapter")?.optJSONArray("content") ?: return emptyList()
        val out = mutableListOf<CrossRef>()
        for (i in 0 until content.length()) {
            val v = content.optJSONObject(i) ?: continue
            val from = v.optInt("verse")
            val refs = v.optJSONArray("references") ?: continue
            for (j in 0 until refs.length()) {
                val r = refs.optJSONObject(j) ?: continue
                val book = BibleBooks.byUsfm(r.optString("book")) ?: continue
                val start = r.optInt("verse").takeIf { it > 0 }
                val end = r.optInt("endVerse", 0).takeIf { it > 0 && it != start }
                out += CrossRef(from, ScriptureReference(book, r.optInt("chapter"), start, end), r.optInt("score"))
            }
        }
        return out
    }

    /**
     * Streams a whole translation (complete.json, several MB) into [onChapter] book by book,
     * without holding the file in memory. [onBook] reports progress after each book.
     */
    fun streamComplete(input: InputStream, onChapter: (BibleBook, Int, HelloAoChapter) -> Unit, onBook: (Int) -> Unit) {
        JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "books") { r.skipValue(); continue }
                r.beginArray()
                var n = 0
                while (r.hasNext()) {
                    r.beginObject()
                    var book: BibleBook? = null
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "id" -> book = BibleBooks.byUsfm(r.nextString())
                            "chapters" -> {
                                r.beginArray()
                                while (r.hasNext()) {
                                    val obj = readObject(r)
                                    val number = obj.optJSONObject("chapter")?.optInt("number") ?: 0
                                    val b = book
                                    if (b != null && number > 0) onChapter(b, number, parseChapter(obj))
                                }
                                r.endArray()
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    onBook(++n)
                }
                r.endArray()
            }
            r.endObject()
        }
    }

    /** Reads the next JSON value from [r] into org.json (one chapter at a time is small). */
    private fun readObject(r: JsonReader): JSONObject {
        val o = JSONObject()
        r.beginObject()
        while (r.hasNext()) o.put(r.nextName(), readValue(r))
        r.endObject()
        return o
    }

    private fun readValue(r: JsonReader): Any? = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> readObject(r)
        JsonToken.BEGIN_ARRAY -> JSONArray().also { a -> r.beginArray(); while (r.hasNext()) a.put(readValue(r)); r.endArray() }
        JsonToken.STRING -> r.nextString()
        JsonToken.NUMBER -> r.nextString().let { s -> s.toLongOrNull() ?: s.toDouble() }
        JsonToken.BOOLEAN -> r.nextBoolean()
        JsonToken.NULL -> { r.nextNull(); JSONObject.NULL }
        else -> { r.skipValue(); null }
    }
}

/** Talks to bible.helloao.org. Plain GETs, no key. */
class HelloAoClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build(),
    private val base: String = HelloAo.BASE
) {
    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(base + path).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    suspend fun catalog(): List<HelloAoTranslation> = get("/api/available_translations.json")?.let { runCatching { HelloAo.parseCatalog(it) }.getOrNull() }.orEmpty()

    suspend fun chapter(translation: String, book: BibleBook, chapter: Int): HelloAoChapter? {
        val parsed = get("/api/$translation/${book.usfm}/$chapter.json")?.let { runCatching { HelloAo.parseChapter(JSONObject(it)) }.getOrNull() } ?: return null
        // Fetch the timings each narrator links to, so the reader can follow along verse by verse.
        val audio = parsed.audio.map { a ->
            if (a.timings.isNotEmpty() || a.timingsLink == null) a
            else a.copy(timings = get(a.timingsLink)?.let { runCatching { HelloAo.parseTimings(it) }.getOrNull() }.orEmpty())
        }
        return parsed.copy(audio = audio.sortedByDescending { it.timings.isNotEmpty() })
    }

    suspend fun commentary(id: String, book: BibleBook, chapter: Int): List<CommentaryEntry>? =
        get("/api/c/$id/${book.usfm}/$chapter.json")?.let { runCatching { HelloAo.parseCommentary(it) }.getOrNull() }

    suspend fun crossRefs(book: BibleBook, chapter: Int): List<CrossRef>? =
        get("/api/d/open-cross-ref/${book.usfm}/$chapter.json")?.let { runCatching { HelloAo.parseCrossRefs(it) }.getOrNull() }

    /** Opens the whole translation for streaming; the caller closes it. */
    fun openComplete(translation: String): Pair<InputStream, Long>? = runCatching {
        val response = client.newCall(Request.Builder().url("$base/api/$translation/complete.json").build()).execute()
        if (!response.isSuccessful) { response.close(); return null }
        val body = response.body ?: return null
        body.byteStream() to body.contentLength()
    }.getOrNull()
}
