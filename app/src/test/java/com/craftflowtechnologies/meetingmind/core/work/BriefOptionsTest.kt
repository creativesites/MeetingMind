package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefOptionsTest {

    private fun line(text: String, item: String) = BriefLine(text, item)

    private val full = Brief(
        target = BriefTarget.weekly,
        title = "Acme",
        generatedAt = 0L,
        status = BriefStatus.ATTENTION,
        progress = null,
        executive = listOf(BriefSentence("Picture.", listOf("i1"))),
        changes = listOf(line("c1", "i1"), line("c2", "i2"), line("c3", "i3")),
        decisions = listOf(line("d1", "i4")),
        youOwe = listOf(line("y1", "i5")),
        theyOwe = listOf(line("t1", "i6")),
        risks = listOf(line("r1", "i7")),
        questions = listOf(line("q1", "i8")),
        next7 = listOf(line("n1", "i9")),
        decisionsRequired = listOf(line("dr1", "i10")),
        recommended = listOf(BriefSentence("Next.", listOf("i1"))),
        evidence = listOf(
            BriefEvidence("i1", "e1", "quote one", null, null, null, null),
            BriefEvidence("i9", "e9", "quote nine", null, null, null, null),
            BriefEvidence("i2", "e2", "quote two", null, null, null, null)
        ),
        confidential = false
    )

    @Test fun `the default options keep every section and every quote`() {
        val shaped = BriefShaper.shape(full, BriefOptions.DEFAULT)
        assertEquals(3, shaped.changes.size)
        assertEquals(listOf("d1"), shaped.decisions.map { it.text })
        assertEquals(listOf("dr1"), shaped.decisionsRequired.map { it.text })
        assertEquals(1, shaped.recommended.size)
        assertEquals(3, shaped.evidence.size)
    }

    @Test fun `headline caps each section at two lines and drops the second paragraph`() {
        val shaped = BriefShaper.shape(full, BriefOptions(length = BriefLength.HEADLINE))
        assertEquals(listOf("c1", "c2"), shaped.changes.map { it.text })
        assertTrue(shaped.recommended.isEmpty())
        assertEquals(1, shaped.executive.size)
    }

    @Test fun `detailed shows every line`() {
        val many = full.copy(changes = (1..9).map { line("c$it", "i$it") })
        assertEquals(9, BriefShaper.shape(many, BriefOptions(length = BriefLength.DETAILED)).changes.size)
        assertEquals(5, BriefShaper.shape(many, BriefOptions(length = BriefLength.ONE_PAGE)).changes.size)
    }

    @Test fun `a section that is off prints nothing and the others are untouched`() {
        val shaped = BriefShaper.shape(full, BriefOptions(sections = setOf(BriefSection.RISKS, BriefSection.QUESTIONS)))
        assertTrue(shaped.changes.isEmpty())
        assertTrue(shaped.executive.isEmpty())
        assertTrue(shaped.youOwe.isEmpty())
        assertEquals(listOf("r1"), shaped.risks.map { it.text })
        assertEquals(listOf("q1"), shaped.questions.map { it.text })
    }

    @Test fun `switching the evidence appendix off removes the quotes behind the lines`() {
        val shaped = BriefShaper.shape(full, BriefOptions(sections = BriefSection.entries.toSet() - BriefSection.EVIDENCE))
        assertTrue(shaped.evidence.isEmpty())
        assertEquals(3, shaped.changes.size)
    }

    @Test fun `shaping never edits a line`() {
        val shaped = BriefShaper.shape(full, BriefOptions(length = BriefLength.HEADLINE))
        val original = (full.changes + full.decisions + full.decisionsRequired).associateBy { it.itemId }
        shaped.changes.forEach { assertEquals(original[it.itemId]?.text, it.text) }
    }

    @Test fun `audience, tone and language change the style, length and sections do not`() {
        val base = BriefOptions.DEFAULT
        assertFalse(base.styleKey == base.copy(audience = BriefAudience.EXECUTIVE).styleKey)
        assertFalse(base.styleKey == base.copy(tone = BriefTone.WARM).styleKey)
        assertFalse(base.styleKey == base.copy(language = "French").styleKey)
        assertEquals(base.styleKey, base.copy(length = BriefLength.HEADLINE).styleKey)
        assertEquals(base.styleKey, base.copy(sections = setOf(BriefSection.RISKS)).styleKey)
        assertFalse(base.optionsKey == base.copy(length = BriefLength.HEADLINE).optionsKey)
    }

    @Test fun `the style block states the audience, tone and language`() {
        val block = BriefPrompt.styleBlock(BriefOptions(audience = BriefAudience.EXECUTIVE, tone = BriefTone.FORMAL, language = "French"))
        assertTrue(block.contains("an executive with little time"))
        assertTrue(block.contains("Tone: formal, measured sentences"))
        assertTrue(block.contains("Write in French."))
    }

    @Test fun `the prompt keeps the fidelity contract and the citation rule`() {
        val prompt = BriefPrompt.build("Client brief", "Acme", BriefOptions(tone = BriefTone.WARM), "facts here")
        assertTrue(prompt.contains(TranscriptToolPrompts.FIDELITY_CONTRACT.trim()))
        assertTrue(prompt.contains("Every sentence must cite the ids"))
        assertTrue(prompt.contains("Tone: warm and encouraging"))
        assertTrue(prompt.endsWith("facts here"))
    }

    @Test fun `length and sections never reach the prompt`() {
        val a = BriefPrompt.build("Client brief", "Acme", BriefOptions.DEFAULT, "facts")
        val b = BriefPrompt.build("Client brief", "Acme", BriefOptions.DEFAULT.copy(length = BriefLength.HEADLINE, sections = setOf(BriefSection.RISKS)), "facts")
        assertEquals(a, b)
    }

    @Test fun `the plain copy follows the footer option and the html copy escapes text`() {
        val withFooter = BriefCopy.plain(full, BriefOptions(footer = true))
        val withoutFooter = BriefCopy.plain(full, BriefOptions(footer = false))
        assertTrue(withFooter.contains("Prepared with MeetingMind"))
        assertFalse(withoutFooter.contains("Prepared with MeetingMind"))
        val html = BriefCopy.html(full.copy(changes = listOf(line("a < b", "i1"))), BriefOptions.DEFAULT)
        assertTrue(html.contains("a &lt; b"))
        assertFalse(html.contains("a < b"))
    }
}
