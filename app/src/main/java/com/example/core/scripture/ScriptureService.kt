package com.example.core.scripture

import android.content.Context
import com.example.core.datastore.UserPreferencesManager
import com.example.core.export.ExportPassage
import com.example.core.export.PassageSource
import com.example.core.model.ScriptureRef
import kotlinx.coroutines.flow.first

/**
 * The app's one way to get verse text: the person's chosen translation, the provider's session
 * cache, and a single shared provider for the whole process.
 */
class ScriptureService(private val context: Context, private val provider: ScriptureProvider = library(context)) {
    private val prefs = UserPreferencesManager(context.applicationContext)

    val isConfigured: Boolean get() = provider.isConfigured

    /** The whole-Bible side (reader, search, downloads), when the provider offers it. */
    val bible: BibleProvider? get() = provider as? BibleProvider

    suspend fun setDefaultVersion(id: Int) = prefs.setBibleVersionId(id)

    suspend fun defaultVersionId(): Int = prefs.preferencesFlow.first().bibleVersionId

    suspend fun passage(reference: ScriptureReference, versionId: Int? = null): PassageResult =
        provider.passage(reference, versionId ?: defaultVersionId())

    /** Today's verse: YouVersion's when reachable, else the key verse of the day's classic reading — so it works offline. */
    suspend fun verseOfTheDay(dayOfYear: Int): ScriptureReference? =
        runCatching { provider.verseOfTheDay(dayOfYear) }.getOrNull()
            ?: com.example.core.devotional.ClassicDevotionals.get(context.applicationContext)
                .forDate(java.time.LocalDate.ofYearDay(java.time.LocalDate.now().year, dayOfYear.coerceIn(1, 365)))?.reference

    /** Verse text for exports, fetched at export time and never stored (PLAN_V1 §7). */
    fun passageSource(): PassageSource = PassageSource { ref -> exportPassage(ref) }

    private suspend fun exportPassage(ref: ScriptureRef): ExportPassage? {
        val reference = ref.toReference() ?: return null
        return when (val result = passage(reference, ref.versionId)) {
            is PassageResult.Found -> ExportPassage(reference.display(), result.passage.text, result.passage.versionAbbreviation, result.passage.attribution)
            is PassageResult.Unavailable -> ExportPassage(reference.display(), null, null, null)
        }
    }

    companion object {
        @Volatile private var shared: BibleLibrary? = null

        /** One library per process: the YouVersion session cache in front, the phone's store behind. */
        fun library(context: Context): BibleLibrary = shared ?: synchronized(this) {
            shared ?: BibleLibrary(
                YouVersionScriptureProvider(), BibleStore.get(context.applicationContext),
                HelloAoClient(), java.io.File(context.applicationContext.filesDir, "helloao_catalog.json")
            ).also { shared = it }
        }
    }
}

fun ScriptureRef.toReference(): ScriptureReference? =
    BibleBooks.byUsfm(bookUsfm)?.let { ScriptureReference(it, chapter, verseStart, verseEnd) }

/** A link that opens the passage in the Bible app, or on bible.com when it isn't installed. */
fun ScriptureReference.webLink(versionId: Int): String = "https://www.bible.com/bible/$versionId/${passageId().substringBefore('-')}"
