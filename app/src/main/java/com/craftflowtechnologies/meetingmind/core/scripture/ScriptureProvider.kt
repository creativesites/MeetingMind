package com.craftflowtechnologies.meetingmind.core.scripture

/** A translation the person can pick. Availability depends on the licences accepted for the app. */
data class BibleVersion(val id: Int, val abbreviation: String, val title: String)

object BibleVersions {
    /** Berean Standard Bible, from the Free Use Bible API. Its id is computed from the id text, so it is negative. */
    val DEFAULT_ID: Int = HelloAo.intId("BSB")

    /** The translation offered first. Every other one comes from the open catalogue on the phone's library screen. */
    val common = listOf(BibleVersion(DEFAULT_ID, "BSB", "Berean Standard Bible"))

    fun abbreviation(id: Int) = common.firstOrNull { it.id == id }?.abbreviation
}

/** Verse text, with the attribution that must be shown wherever it appears. */
data class Passage(
    val reference: ScriptureReference,
    val text: String,
    val versionId: Int,
    val versionAbbreviation: String,
    val attribution: String
) {
    /** The text may go to a cloud speech service and be kept as audio (see [BibleLicensing.openForCloud]). */
    val openForCloud: Boolean get() = BibleLicensing.openForCloud(versionId, versionAbbreviation, attribution)
}

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

/**
 * Stands in for a remote Bible service. The app has none: open translations come from the Free Use
 * Bible API and the phone's own store, and licensed translations are not offered.
 */
object NoRemoteBible : BibleProvider {
    override val isConfigured: Boolean get() = false
    override suspend fun bibles(): List<BibleInfo> = emptyList()
    override suspend fun chapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult =
        ChapterResult.Unavailable(PassageResult.Reason.NOT_CONFIGURED, "That translation isn't on this phone.")
    override suspend fun passage(reference: ScriptureReference, versionId: Int): PassageResult =
        PassageResult.Unavailable(PassageResult.Reason.NOT_CONFIGURED, "That translation isn't on this phone.")
    override suspend fun verseOfTheDay(dayOfYear: Int): ScriptureReference? = null
}

/** Passage ids such as "JHN.3.16", "JHN.3.16-18" or "PSA.23", read back into a reference. */
object PassageIds {
    fun parse(id: String): ScriptureReference? {
        val first = id.substringBefore('-').split('.')
        val book = BibleBooks.byUsfm(first.getOrNull(0) ?: return null) ?: return null
        val chapter = first.getOrNull(1)?.toIntOrNull() ?: return null
        val verse = first.getOrNull(2)?.toIntOrNull()
        val endPart = id.substringAfter('-', "")
        val end = endPart.split('.').lastOrNull()?.toIntOrNull()?.takeIf { endPart.isNotEmpty() && verse != null && it > verse }
        return ScriptureReference(book, chapter, verse, end)
    }
}
