package com.example.core.scripture

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A translation the person can pick. Availability depends on the licences accepted for the app. */
data class BibleVersion(val id: Int, val abbreviation: String, val title: String)

object BibleVersions {
    /** Berean Standard Bible: openly licensed, so it always works once a key is set. */
    const val DEFAULT_ID = 3034

    /** The translations offered in the picker, in the order people ask for them. */
    val common = listOf(
        BibleVersion(3034, "BSB", "Berean Standard Bible"),
        BibleVersion(1, "KJV", "King James Version"),
        BibleVersion(114, "NKJV", "New King James Version"),
        BibleVersion(111, "NIV", "New International Version"),
        BibleVersion(59, "ESV", "English Standard Version"),
        BibleVersion(116, "NLT", "New Living Translation"),
        BibleVersion(1588, "AMP", "Amplified Bible"),
        BibleVersion(97, "MSG", "The Message")
    )

    fun abbreviation(id: Int) = common.firstOrNull { it.id == id }?.abbreviation
}

/** Verse text, with the attribution that must be shown wherever it appears. */
data class Passage(
    val reference: ScriptureReference,
    val text: String,
    val versionId: Int,
    val versionAbbreviation: String,
    val attribution: String
)

sealed interface PassageResult {
    data class Found(val passage: Passage) : PassageResult
    data class Unavailable(val reason: Reason, val message: String) : PassageResult

    enum class Reason { NOT_CONFIGURED, OFFLINE, NOT_LICENSED, NOT_FOUND, ERROR }
}

/**
 * Where verse text comes from (docs/PLAN_V1.md §7). Only the reference is ever stored with a note;
 * text is fetched when shown and again at export, and always comes with its attribution.
 */
interface ScriptureProvider {
    val isConfigured: Boolean
    suspend fun passage(reference: ScriptureReference, versionId: Int): PassageResult
    suspend fun verseOfTheDay(dayOfYear: Int): ScriptureReference?
}

/** The HTTP layer, separated so tests can answer without a network. */
fun interface YouVersionHttp {
    /** Returns the status code and body, or throws [IOException] when offline. */
    suspend fun get(path: String): Pair<Int, String>
}

/**
 * The YouVersion Platform REST API (`api.youversion.com/v1`, header `X-YVP-App-Key`).
 *
 * Text is cached in memory for the session only — publishers license it for display, not for
 * storage — so a verse opened twice isn't fetched twice, and nothing outlives the process.
 * Version metadata (for attribution) is cached the same way.
 */
class YouVersionScriptureProvider(
    private val appKey: String = BuildConfig.YOUVERSION_APP_KEY,
    private val http: YouVersionHttp = OkHttpYouVersion(appKey)
) : ScriptureProvider {

    override val isConfigured: Boolean get() = appKey.isNotBlank()

    private val passages = ConcurrentHashMap<String, Passage>()
    private val versions = ConcurrentHashMap<Int, Pair<String, String>>() // id -> (abbreviation, attribution)

    override suspend fun passage(reference: ScriptureReference, versionId: Int): PassageResult {
        if (!isConfigured) {
            return PassageResult.Unavailable(PassageResult.Reason.NOT_CONFIGURED, "Verse text isn't set up in this build.")
        }
        val cacheKey = "$versionId/${reference.passageId()}"
        passages[cacheKey]?.let { return PassageResult.Found(it) }

        return try {
            val meta = version(versionId) ?: return PassageResult.Unavailable(
                PassageResult.Reason.NOT_LICENSED, "This translation isn't available. Choose another in Settings."
            )
            val text = fetchText(reference, versionId) ?: return PassageResult.Unavailable(
                PassageResult.Reason.NOT_FOUND, "${reference.display()} couldn't be loaded in ${meta.first}."
            )
            val passage = Passage(reference, text, versionId, meta.first, meta.second)
            passages[cacheKey] = passage
            PassageResult.Found(passage)
        } catch (e: IOException) {
            PassageResult.Unavailable(PassageResult.Reason.OFFLINE, "Verse text is unavailable offline.")
        } catch (e: Exception) {
            PassageResult.Unavailable(PassageResult.Reason.ERROR, "Verse text couldn't be loaded.")
        }
    }

    /**
     * Tries the passage as one request (full USFM range, then the short range form), and falls
     * back to fetching each verse — so a range always works whichever form the API accepts.
     */
    private suspend fun fetchText(reference: ScriptureReference, versionId: Int): String? {
        val ids = buildList {
            add(reference.passageId())
            if (reference.verseStart != null && reference.verseEnd != null && reference.verseEnd != reference.verseStart) {
                add("${reference.usfm}.${reference.chapter}.${reference.verseStart}-${reference.verseEnd}")
            }
        }
        for (id in ids) {
            val (code, body) = http.get("/v1/bibles/$versionId/passages/$id?format=text")
            if (code == 200) return cleanText(JSONObject(body).optString("content"))
            if (code == 401 || code == 403) return null
        }
        val start = reference.verseStart ?: return null
        val end = reference.verseEnd ?: return null
        val parts = (start..end).map { v ->
            val (code, body) = http.get("/v1/bibles/$versionId/passages/${reference.usfm}.${reference.chapter}.$v?format=text")
            if (code != 200) return null
            cleanText(JSONObject(body).optString("content"))
        }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }

    /** Abbreviation and required attribution: the version's copyright, else its promotional line. */
    private suspend fun version(versionId: Int): Pair<String, String>? {
        versions[versionId]?.let { return it }
        val (code, body) = http.get("/v1/bibles/$versionId")
        if (code != 200) return null
        val json = JSONObject(body)
        val abbreviation = json.optString("localized_abbreviation").ifBlank { json.optString("abbreviation") }
            .ifBlank { BibleVersions.abbreviation(versionId) ?: "Bible" }
        val attribution = json.optString("copyright").trim().takeIf { it.isNotEmpty() && it != "null" }
            ?: json.optString("promotional_content").trim().takeIf { it.isNotEmpty() && it != "null" }
            // No attribution means the text may not be shown (YouVersion's rule), so treat the
            // version as unavailable rather than display it bare.
            ?: return null
        return (abbreviation to cleanText(attribution)).also { versions[versionId] = it }
    }

    override suspend fun verseOfTheDay(dayOfYear: Int): ScriptureReference? = try {
        val (code, body) = http.get("/v1/verse_of_the_days/$dayOfYear")
        if (code != 200) null else parsePassageId(JSONObject(body).optString("passage_id"))
    } catch (e: Exception) {
        null
    }

    companion object {
        /** "JHN.3.16", "JHN.3.16-18", "JHN.3.16-JHN.3.18" or "PSA.23" back into a reference. */
        fun parsePassageId(id: String): ScriptureReference? {
            val first = id.substringBefore('-').split('.')
            val book = BibleBooks.byUsfm(first.getOrNull(0) ?: return null) ?: return null
            val chapter = first.getOrNull(1)?.toIntOrNull() ?: return null
            val verse = first.getOrNull(2)?.toIntOrNull()
            val endPart = id.substringAfter('-', "")
            val end = endPart.split('.').lastOrNull()?.toIntOrNull()?.takeIf { endPart.isNotEmpty() && verse != null && it > verse }
            return ScriptureReference(book, chapter, verse, end)
        }

        /** Collapses the API's line breaks and doubled spaces into readable running text. */
        fun cleanText(raw: String): String =
            raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

/** The real HTTP client. */
class OkHttpYouVersion(private val appKey: String) : YouVersionHttp {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun get(path: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.youversion.com$path")
            .header("X-YVP-App-Key", appKey)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { it.code to (it.body?.string() ?: "") }
    }
}
