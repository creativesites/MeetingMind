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
class ScriptureService(context: Context, private val provider: ScriptureProvider = shared) {
    private val prefs = UserPreferencesManager(context.applicationContext)

    val isConfigured: Boolean get() = provider.isConfigured

    suspend fun defaultVersionId(): Int = prefs.preferencesFlow.first().bibleVersionId

    suspend fun passage(reference: ScriptureReference, versionId: Int? = null): PassageResult =
        provider.passage(reference, versionId ?: defaultVersionId())

    suspend fun verseOfTheDay(dayOfYear: Int): ScriptureReference? = provider.verseOfTheDay(dayOfYear)

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
        private val shared: ScriptureProvider by lazy { YouVersionScriptureProvider() }
    }
}

fun ScriptureRef.toReference(): ScriptureReference? =
    BibleBooks.byUsfm(bookUsfm)?.let { ScriptureReference(it, chapter, verseStart, verseEnd) }

/** A link that opens the passage in the Bible app, or on bible.com when it isn't installed. */
fun ScriptureReference.webLink(versionId: Int): String = "https://www.bible.com/bible/$versionId/${passageId().substringBefore('-')}"
