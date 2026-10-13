package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.ScriptureOrigin
import com.craftflowtechnologies.meetingmind.core.model.ScriptureRef
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.scripture.BibleBook
import com.craftflowtechnologies.meetingmind.core.scripture.BibleBooks
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterContent
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterResult
import com.craftflowtechnologies.meetingmind.core.scripture.ChapterVerse
import com.craftflowtechnologies.meetingmind.core.scripture.Passage
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSource(
    var default: Int = 3034,
    val chapters: MutableMap<Pair<Int, Int>, ChapterResult> = mutableMapOf(),
    val passages: MutableMap<Int, PassageResult> = mutableMapOf(),
    val titles: Map<Int, String> = mapOf(3034 to "Berean Standard Bible", 111 to "New International Version")
) : ScriptureSource {
    val chapterCalls = mutableListOf<Pair<Int, String>>()
    override suspend fun defaultVersionId() = default
    override suspend fun chapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult {
        chapterCalls += bibleId to "${book.usfm}.$chapter"
        return chapters[bibleId to chapter] ?: ChapterResult.Unavailable(PassageResult.Reason.OFFLINE, "offline")
    }
    override suspend fun passage(reference: ScriptureReference, versionId: Int) =
        passages[versionId] ?: PassageResult.Unavailable(PassageResult.Reason.OFFLINE, "offline")
    override suspend fun title(bibleId: Int) = titles[bibleId]
}

class ScriptureResolverTest {
    private val john = BibleBooks.byUsfm("JHN")!!

    private fun chapter(bibleId: Int, abbr: String, attribution: String = "Public domain") = ChapterResult.Found(
        ChapterContent(
            bibleId, abbr, attribution, john, 3,
            (14..18).map { ChapterVerse(it, "Verse $it text.", paragraph = false, poetry = false) },
            fromDevice = true
        )
    )

    private fun block(id: String, reference: String, refId: String? = null, extra: Map<String, String> = emptyMap()) = NoteBlock(
        id = id, noteId = "n", position = 0, type = NoteBlockType.SCRIPTURE, content = RichText.plain(reference),
        payload = buildMap { put("reference", reference); refId?.let { put(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID, it) }; putAll(extra) },
        source = BlockSource.SCRIPTURE
    )

    private fun ref(id: String, version: Int?, start: Int? = 16, end: Int? = 17) = ScriptureRef(id, "n", null, "JHN", 3, start, end, version, ScriptureOrigin.USER, createdAt = 0)

    private fun resolve(source: FakeSource, blocks: List<NoteBlock>, refs: List<ScriptureRef> = emptyList()) =
        runBlocking { ScriptureResolver(source).resolve(blocks, refs) }

    @Test fun `verses in the range come back with their numbers and untouched text`() {
        val src = FakeSource(chapters = mutableMapOf((3034 to 3) to chapter(3034, "BSB")))
        val r = resolve(src, listOf(block("b1", "John 3:16-17", "r1")), listOf(ref("r1", null)))["b1"]!!
        assertEquals(listOf(16, 17), r.verses!!.map { it.number })
        assertEquals("Verse 16 text.", r.verses!![0].text)
        assertEquals("John chapter 3, verses 16 to 17", r.spokenReference)
        assertEquals("Berean Standard Bible", r.translationName)
        assertTrue(r.openForCloud)
    }

    @Test fun `a single verse and a whole chapter`() {
        val src = FakeSource(chapters = mutableMapOf((3034 to 3) to chapter(3034, "BSB")))
        val single = resolve(src, listOf(block("b1", "John 3:16", "r1")), listOf(ref("r1", null, 16, null)))["b1"]!!
        assertEquals(listOf(16), single.verses!!.map { it.number })
        val whole = resolve(src, listOf(block("b2", "John 3", "r2")), listOf(ref("r2", null, null, null)))["b2"]!!
        assertEquals(listOf(14, 15, 16, 17, 18), whole.verses!!.map { it.number })
    }

    @Test fun `the translation the passage was saved with wins over the default`() {
        val src = FakeSource(chapters = mutableMapOf((3034 to 3) to chapter(3034, "BSB"), (111 to 3) to chapter(111, "NIV", "© Biblica")))
        val r = resolve(src, listOf(block("b1", "John 3:16-17", "r1")), listOf(ref("r1", 111)))["b1"]!!
        assertEquals("New International Version", r.translationName)
        assertFalse(r.openForCloud) // licensed: phone voice only
        assertEquals(listOf(111 to "JHN.3"), src.chapterCalls)
    }

    @Test fun `a reference with no stored row is parsed from the block`() {
        val src = FakeSource(chapters = mutableMapOf((3034 to 3) to chapter(3034, "BSB")))
        val r = resolve(src, listOf(block("b1", "John 3:16-17")))["b1"]!!
        assertEquals(listOf(16, 17), r.verses!!.map { it.number })
    }

    @Test fun `typed text is the person's own and is never fetched`() {
        val src = FakeSource()
        val b = block("b1", "John 3:16", "r1", mapOf(NoteBlock.PAYLOAD_USER_TEXT to "my own wording", NoteBlock.PAYLOAD_USER_LABEL to "My paraphrase"))
        val r = resolve(src, listOf(b), listOf(ref("r1", null, 16, null)))["b1"]!!
        assertTrue(r.userTyped)
        assertEquals("my own wording", r.text)
        assertTrue(src.chapterCalls.isEmpty())
    }

    @Test fun `when the chapter is unavailable the flattened passage is used without verse numbers`() {
        val passage = Passage(ScriptureReference(john, 3, 16, 17), "For God so loved the world.", 3034, "BSB", "Public domain")
        val src = FakeSource(passages = mutableMapOf(3034 to PassageResult.Found(passage)))
        val r = resolve(src, listOf(block("b1", "John 3:16-17", "r1")), listOf(ref("r1", null)))["b1"]!!
        assertNull(r.verses)
        assertEquals("For God so loved the world.", r.text)
        assertTrue(r.openForCloud)
    }

    @Test fun `an unavailable passage resolves to nothing, never to another translation`() {
        val src = FakeSource(chapters = mutableMapOf((3034 to 3) to chapter(3034, "BSB")), default = 111)
        // default is NIV (111) which is unavailable; BSB being available must not be used instead
        assertNull(resolve(src, listOf(block("b1", "John 3:16-17", "r1")), listOf(ref("r1", null)))["b1"])
        assertEquals(listOf(111 to "JHN.3"), src.chapterCalls)
    }

    @Test fun `a reference that cannot be read resolves to nothing`() {
        assertNull(resolve(FakeSource(), listOf(block("b1", "not a verse")))["b1"])
    }

    @Test fun `each chapter is fetched once for several passages`() {
        val src = FakeSource(chapters = mutableMapOf((3034 to 3) to chapter(3034, "BSB")))
        val blocks = listOf(block("b1", "John 3:14", "r1"), block("b2", "John 3:16-17", "r2"))
        val out = resolve(src, blocks, listOf(ref("r1", null, 14, null), ref("r2", null)))
        assertNotNull(out["b1"]); assertNotNull(out["b2"])
        assertEquals(1, src.chapterCalls.size)
    }

    @Test fun `only scripture blocks are resolved`() {
        val para = block("p", "x").copy(type = NoteBlockType.PARAGRAPH)
        assertTrue(resolve(FakeSource(), listOf(para)).isEmpty())
    }
}
