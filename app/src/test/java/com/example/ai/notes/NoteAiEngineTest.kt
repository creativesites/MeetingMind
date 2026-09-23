package com.example.ai.notes

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.core.model.BlockSource
import com.example.core.model.Note
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NoteStatus
import com.example.core.model.RecordingType
import com.example.core.notes.RichText
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class NoteAiEngineTest {

    private class Recording(private val reply: String) : LanguageModel {
        var prompt = ""
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> { this.prompt = prompt; return AiResult.Success(reply) }
    }

    private val passages = listOf(
        SourcePassage("b1", "Budget review moved to 14 March. Sarah will send the figures.", "Team sync · Notes", "n1"),
        SourcePassage("b2", "We agreed to hire two designers this quarter.", "Team sync · Notes", "n1"),
        SourcePassage("b3", "Maybe look into a new office someday.", "Team sync · Ideas", "n1")
    )

    private suspend fun run(tool: NoteAiTool, reply: String, faith: Boolean = false, question: String? = null, sections: List<SectionSpec> = emptyList()): Pair<NoteAiOutcome, String> {
        val model = Recording(reply)
        val r = NoteAiEngine(model).run(tool, passages, faith, question, sections) as AiResult.Success
        return r.value to model.prompt
    }

    @Test
    fun `every prompt carries the contract, cites by short ids, and Faith adds its clause`() = runBlocking {
        val (_, plain) = run(NoteAiTool.SUMMARIZE, """{"points":[]}""")
        assertTrue(plain.contains(NoteAiPrompts.NOTE_CONTRACT.trim()))
        assertFalse(plain.contains(NoteAiPrompts.FAITH_NOTE_CONTRACT.trim()))
        assertTrue(plain.contains("[p1] (Team sync · Notes) Budget review"))
        assertFalse(plain.contains("b1")) // real ids never reach the model

        val (_, faith) = run(NoteAiTool.SUMMARIZE, """{"points":[]}""", faith = true)
        assertTrue(faith.contains(NoteAiPrompts.FAITH_NOTE_CONTRACT.trim()))
        NoteAiTool.entries.forEach { tool ->
            val p = NoteAiPrompts.build(tool, mapOf("p1" to passages[0]), faith = true, question = "q", sections = listOf(SectionSpec("a", "A", null)))
            assertTrue(p.contains(NoteAiPrompts.NOTE_CONTRACT.trim()) && p.contains(NoteAiPrompts.FAITH_NOTE_CONTRACT.trim()))
        }
    }

    @Test
    fun `points must cite real passages and keep numbers true`() = runBlocking {
        val (outcome, _) = run(
            NoteAiTool.SUMMARIZE,
            """{"points":[
                {"text":"Budget review is on 14 March","sources":["p1"]},
                {"text":"Budget review is on 15 March","sources":["p1"]},
                {"text":"Two designers will be hired","sources":["p9"]},
                {"text":"Uncited claim","sources":[]},
                {"text":"The team agreed to hire designers","sources":["p2","p2","zz"]}
            ]}"""
        )
        val items = (outcome as NoteAiOutcome.Points).items
        assertEquals(listOf("Budget review is on 14 March", "The team agreed to hire designers"), items.map { it.text })
        assertEquals(listOf("b1"), items[0].sourceIds) // mapped back to the block id
        assertEquals(listOf("b2"), items[1].sourceIds)
    }

    @Test
    fun `actions keep the person's words and only details the notes state`() = runBlocking {
        val (outcome, _) = run(
            NoteAiTool.EXTRACT_ACTIONS,
            """{"actions":[
                {"text":"Send the figures","detail":"Sarah","sources":["p1"]},
                {"text":"Negotiate a better lease with the landlord","detail":"","sources":["p3"]},
                {"text":"Hire two designers","detail":"Tom","sources":["p2"]}
            ]}"""
        )
        val items = (outcome as NoteAiOutcome.Points).items
        assertEquals(listOf("Send the figures", "Hire two designers"), items.map { it.text })
        assertEquals("Sarah", items[0].detail)
        assertEquals(null, items[1].detail) // "Tom" isn't in the notes
    }

    @Test
    fun `an answer without real citations is no answer`() = runBlocking {
        val (good, prompt) = run(NoteAiTool.ASK, """{"found":true,"answer":"It moved to 14 March.","sources":["p1"]}""", question = "When is the budget review?")
        assertTrue(prompt.contains("When is the budget review?"))
        assertEquals(listOf("b1"), (good as NoteAiOutcome.Answer).sourceIds)

        val (uncited, _) = run(NoteAiTool.ASK, """{"found":true,"answer":"Probably April.","sources":[]}""", question = "When?")
        assertFalse((uncited as NoteAiOutcome.Answer).found)
        val (wrongNumber, _) = run(NoteAiTool.ASK, """{"found":true,"answer":"On 20 March.","sources":["p1"]}""", question = "When?")
        assertFalse((wrongNumber as NoteAiOutcome.Answer).found)
    }

    @Test
    fun `organizing only moves the person's sentences into known sections`() = runBlocking {
        val sections = listOf(SectionSpec("decisions", "Decisions", null), SectionSpec("ideas", "Ideas", null))
        val (outcome, _) = run(
            NoteAiTool.ORGANIZE,
            """{"sections":[
                {"key":"ideas","items":[{"text":"Maybe look into a new office someday.","sources":["p3"]}]},
                {"key":"decisions","items":[{"text":"We agreed to hire two designers this quarter.","sources":["p2"]},
                                            {"text":"Leadership strongly endorsed aggressive expansion","sources":["p2"]}]},
                {"key":"invented","items":[{"text":"x","sources":["p1"]}]}
            ]}""",
            sections = sections
        )
        val o = outcome as NoteAiOutcome.Sections
        assertEquals(listOf("decisions", "ideas"), o.sections.map { it.key }) // template order, unknown key dropped
        assertEquals(1, o.sections[0].items.size) // the rewritten sentence isn't the person's words
        assertEquals(listOf("b1"), o.leftOver)
    }

    @Test
    fun `unreadable output and empty input fail honestly`() = runBlocking {
        assertTrue(NoteAiEngine(Recording("sorry, I can't")).run(NoteAiTool.SUMMARIZE, passages, false) is AiResult.Failed)
        assertTrue(NoteAiEngine(Recording("{}")).run(NoteAiTool.SUMMARIZE, emptyList(), false) is AiResult.Failed)
        assertTrue(NoteAiEngine(Recording("{}")).run(NoteAiTool.ASK, passages, false, question = " ") is AiResult.Failed)
    }

    // ---------------------------------------------------------------- sources

    private fun note(id: String = "n1", title: String = "Sunday") = Note(id, title, RecordingType.SERMON, null, 0, 0, null, false, false, NoteStatus.OPEN, null, emptyMap())
    private fun block(id: String, type: NoteBlockType, text: String, checked: Boolean = false) =
        NoteBlock(id, "n1", 0, type, RichText.plain(text), source = BlockSource.USER, checked = checked)

    @Test
    fun `passages carry their section and skip empty and structural blocks`() {
        val p = NoteSources.passagesOf(note(), listOf(
            block("h", NoteBlockType.HEADING_2, "Key points"),
            block("a", NoteBlockType.BULLET, "Grace is a gift"),
            block("e", NoteBlockType.PARAGRAPH, "  "),
            block("c", NoteBlockType.CHECKLIST, "Call Mum", checked = true),
            block("s", NoteBlockType.SCRIPTURE, "Ephesians 2:8")
        ))
        assertEquals(listOf("a", "c", "s"), p.map { it.id })
        assertEquals("Sunday · Key points", p[0].label)
        assertEquals("[done] Call Mum", p[1].text)
        assertEquals("Scripture: Ephesians 2:8", p[2].text)
    }

    @Test
    fun `fitting stops at the budget and relevance picks the passages a question needs`() {
        val many = (1..50).map { SourcePassage("b$it", "Filler passage number $it about nothing much at all", "L", "n1") }
        val (kept, left) = NoteSources.fit(many, 500)
        assertTrue(kept.size in 5..9)
        assertEquals(50 - kept.size, left)

        val withAnswer = many + SourcePassage("x", "The retreat budget is four hundred dollars", "L", "n1")
        val chosen = NoteSources.relevant(withAnswer, "What is the retreat budget?", 400)
        assertTrue(chosen.any { it.id == "x" })
        assertTrue(NoteSources.budgetChars(4096, 1536) in 5000..8000)
    }

    // ---------------------------------------------------------------- related

    private fun signals(id: String, text: String, tags: Set<String> = emptySet(), passages: Set<String> = emptySet(), linked: Set<String> = emptySet()) =
        NoteSignals(id, text, tags, passages, passages.associateWith { it }, linked)

    @Test
    fun `related notes come with their reasons, strongest first`() {
        val target = signals("t", "Sermon on grace and forgiveness from Ephesians", tags = setOf("grace"), passages = setOf("EPH 2"))
        val result = RelatedNotes.find(target, listOf(
            target,
            signals("verse", "Morning devotional", passages = setOf("EPH 2")),
            signals("tag", "Thinking about mercy", tags = setOf("Grace")),
            signals("words", "Forgiveness and grace in my family"),
            signals("link", "Prayer request", linked = setOf("t")),
            signals("none", "Quarterly budget spreadsheet review")
        ))
        val ids = result.map { it.noteId }
        assertFalse("none" in ids)
        assertFalse("t" in ids)
        assertEquals(setOf("verse", "tag", "words", "link"), ids.toSet())
        assertEquals("link", ids.first())
        assertTrue(result.first { it.noteId == "verse" }.reasons.any { it.startsWith("Both cite") })
        assertTrue(result.first { it.noteId == "tag" }.reasons.contains("#grace"))
        assertTrue(result.first { it.noteId == "words" }.reasons.any { it.startsWith("Similar words") })
    }

    // ---------------------------------------------------------------- applying

    private val noteBlocks = listOf(
        block("r", NoteBlockType.RECORDING, ""),
        block("h", NoteBlockType.HEADING_2, "Notes"),
        block("a", NoteBlockType.PARAGRAPH, "We agreed to hire two designers."),
        block("b", NoteBlockType.PARAGRAPH, "Maybe a new office."),
        block("c", NoteBlockType.PARAGRAPH, "Text the model never saw."),
        block("end", NoteBlockType.PARAGRAPH, "")
    )

    @Test
    fun `a new summary replaces the old one and nothing else`() {
        val once = NoteAiApply.withSummary(noteBlocks, "n1", listOf(CitedItem("One", listOf("a"))))
        val twice = NoteAiApply.withSummary(once, "n1", listOf(CitedItem("Two", listOf("a")), CitedItem("Three", listOf("b"))))
        assertEquals(listOf("Summary", "Two", "Three"), twice.take(3).map { it.content.text })
        assertEquals(noteBlocks.map { it.id }, twice.drop(3).map { it.id })
    }

    @Test
    fun `actions land as a checklist and aren't added twice`() {
        val first = NoteAiApply.withActions(noteBlocks, "n1", listOf(CitedItem("Send figures", listOf("a"), "Sarah")))
        assertEquals("", first.last().content.text) // the trailing line stays last
        assertEquals(NoteBlockType.CHECKLIST, first[first.size - 2].type)
        assertEquals("Send figures — Sarah", first[first.size - 2].content.text)
        val again = NoteAiApply.withActions(first, "n1", listOf(CitedItem("Send figures", listOf("a"), "Sarah"), CitedItem("Book room", listOf("b"))))
        assertEquals(1, again.size - first.size)
    }

    @Test
    fun `organizing keeps media first and every unused sentence`() {
        val result = NoteAiOutcome.Sections(listOf(SectionDraft("decisions", "Decisions", listOf(CitedItem("We agreed to hire two designers.", listOf("a"))))), listOf("b"))
        val out = NoteAiApply.organized(noteBlocks, "n1", result)
        assertEquals("r", out.first().id)
        val texts = out.map { it.content.text }
        assertTrue(texts.containsAll(listOf("Decisions", "We agreed to hire two designers.", "Other notes", "Maybe a new office.", "Text the model never saw.")))
        assertFalse(out.any { it.id == "a" }) // moved, not duplicated
    }

    @Test
    fun `results survive a round trip through the job row`() {
        val result = NoteAiResult(
            NoteAiOutcome.Sections(listOf(SectionDraft("k", "K", listOf(CitedItem("t", listOf("b1"), "d")))), listOf("b2")),
            mapOf("b1" to passages[0]), "This note"
        )
        assertEquals(result, NoteAiCodec.decode(JSONObject(NoteAiCodec.encode(result).toString())))
        val answer = NoteAiResult(NoteAiOutcome.Answer("a", listOf("b1"), true), mapOf("b1" to passages[0]), "s")
        assertEquals(answer, NoteAiCodec.decode(NoteAiCodec.encode(answer)))
    }
}
