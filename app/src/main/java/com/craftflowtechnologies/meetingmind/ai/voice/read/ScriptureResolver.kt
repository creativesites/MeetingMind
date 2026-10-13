package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.voice.SpeechScript
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.ScriptureRef
import com.craftflowtechnologies.meetingmind.core.scripture.BibleBook
import com.craftflowtechnologies.meetingmind.core.scripture.BibleLicensing
import com.craftflowtechnologies.meetingmind.core.scripture.BibleProvider
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterResult
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import com.craftflowtechnologies.meetingmind.core.scripture.toReference

/** Where verse text comes from, as the resolver needs it. */
interface ScriptureSource {
    suspend fun defaultVersionId(): Int
    suspend fun chapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult
    suspend fun passage(reference: ScriptureReference, versionId: Int): PassageResult
    /** "Berean Standard Bible" for the translation, if known. */
    suspend fun title(bibleId: Int): String?
}

/** [ScriptureSource] over the app's Bible library. */
class LibraryScriptureSource(private val service: ScriptureService) : ScriptureSource {
    override suspend fun defaultVersionId() = service.defaultVersionId()

    override suspend fun chapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult =
        service.bible?.chapter(bibleId, book, chapter)
            ?: ChapterResult.Unavailable(PassageResult.Reason.NOT_CONFIGURED, "No Bible library.")

    override suspend fun passage(reference: ScriptureReference, versionId: Int) = service.passage(reference, versionId)

    override suspend fun title(bibleId: Int): String? =
        runCatching { (service.bible as? BibleProvider)?.bibles()?.firstOrNull { it.id == bibleId }?.title }.getOrNull()
}

/**
 * Finds the words to speak for each scripture block of a note (docs/mvp/READ_TO_ME_PLAN.md §16.5).
 * The text is only ever the Bible library's or the person's own typing, never a model's. The
 * translation is the one the passage was saved with, else the person's default, the same rule the
 * scripture card and export follow. A passage that can't be fetched resolves to null; nothing is
 * substituted from another translation.
 */
class ScriptureResolver(private val source: ScriptureSource) {

    /** Block id to what to speak, for every SCRIPTURE block in [blocks]. */
    suspend fun resolve(blocks: List<NoteBlock>, refs: List<ScriptureRef>): Map<String, ResolvedScripture?> {
        val byId = refs.associateBy { it.id }
        val chapters = mutableMapOf<Triple<Int, String, Int>, ChapterResult>()
        val titles = mutableMapOf<Int, String?>()
        val result = mutableMapOf<String, ResolvedScripture?>()
        for (block in blocks) {
            if (block.type != NoteBlockType.SCRIPTURE) continue
            val ref = block.payload[NoteBlock.PAYLOAD_SCRIPTURE_REF_ID]?.let(byId::get)
            val reference = ref?.toReference() ?: block.payload["reference"]?.let(ScriptureReferenceParser::parse)
            if (reference == null) { result[block.id] = null; continue }
            val spoken = SpeechScript.spoken(reference)

            val typed = block.payload[NoteBlock.PAYLOAD_USER_TEXT]?.takeIf { it.isNotBlank() }
            if (typed != null) {
                result[block.id] = ResolvedScripture(spoken, block.payload[NoteBlock.PAYLOAD_USER_LABEL], text = typed, userTyped = true)
                continue
            }

            val versionId = ref?.versionId ?: source.defaultVersionId()
            val name = titles.getOrPut(versionId) { source.title(versionId) }
            result[block.id] = resolveFromLibrary(reference, spoken, versionId, name, chapters)
        }
        return result
    }

    private suspend fun resolveFromLibrary(
        reference: ScriptureReference,
        spoken: String,
        versionId: Int,
        title: String?,
        chapters: MutableMap<Triple<Int, String, Int>, ChapterResult>
    ): ResolvedScripture? {
        val key = Triple(versionId, reference.book.usfm, reference.chapter)
        val chapter = chapters.getOrPut(key) { source.chapter(versionId, reference.book, reference.chapter) }
        if (chapter is ChapterResult.Found) {
            val from = reference.verseStart ?: 1
            val to = reference.verseEnd ?: reference.verseStart ?: Int.MAX_VALUE
            val verses = chapter.content.verses.filter { it.number in from..to }
            if (verses.isNotEmpty()) {
                val c = chapter.content
                return ResolvedScripture(
                    spokenReference = spoken,
                    translationName = title ?: c.abbreviation,
                    verses = verses.map { ResolvedScripture.Verse(it.number, it.text) },
                    openForCloud = BibleLicensing.openForCloud(versionId, c.abbreviation, c.attribution)
                )
            }
        }
        // The chapter wasn't available as verses; the flattened passage still has the right words, just no verse numbers.
        return when (val p = source.passage(reference, versionId)) {
            is PassageResult.Found -> ResolvedScripture(
                spokenReference = spoken,
                translationName = title ?: p.passage.versionAbbreviation,
                text = p.passage.text,
                openForCloud = p.passage.openForCloud
            )
            is PassageResult.Unavailable -> null
        }
    }
}
