package com.craftflowtechnologies.meetingmind.core.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class AiPasteCleanupTest {

    private val flattened = """
        Worked for 51s
        Yes. I went through the current transcription architecture.
        1. The target: fast transcription
        The substrate comes first.
        enum class SceneKind {
            SPEECH, SONG, PRAYER
        }
        data class Scene(val kind: SceneKind, val startMs: Long)
        {
          "scenes": [
            { "kind": "SONG", "startMs": 0 }
          ]
        }
        ┌───────────┐
        │  Audio    │
        └─────┬─────┘
              ▼
        Model      Local     Gemini
        WER        11.2%     5.1%
        Speed      1.0x      4.2x
        Copy code
        That's the plan.
    """.trimIndent()

    private val r = AiPasteCleanup.clean(flattened)

    @Test fun `chat app lines go, prose stays word for word`() {
        assertFalse(r.markdown.contains("Worked for 51s"))
        assertFalse(r.markdown.lines().any { it.trim() == "Copy code" })
        assertTrue(r.markdown.contains("Yes. I went through the current transcription architecture."))
        assertTrue(r.markdown.contains("That's the plan."))
        assertEquals("ChatGPT", r.source)
    }

    @Test fun `code and JSON get fences back`() {
        assertTrue(r.markdown, r.markdown.contains("```kotlin\nenum class SceneKind {"))
        assertTrue(r.markdown.contains("data class Scene(val kind: SceneKind, val startMs: Long)"))
        assertTrue(r.markdown, r.markdown.contains("```json\n{\n  \"scenes\": ["))
        val shown = MarkdownImport.parse(r.markdown, "n")
        assertTrue(shown.count { it.type == com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.CODE } >= 2)
    }

    @Test fun `diagrams stay monospace and aligned columns become a table`() {
        assertTrue(r.markdown, r.markdown.contains("```\n┌───────────┐"))
        assertTrue(r.markdown, r.markdown.contains("| Model | Local | Gemini |"))
        assertTrue(r.markdown.contains("| WER | 11.2% | 5.1% |"))
    }

    @Test fun `ordinary prose is left alone`() {
        val prose = "We met on Tuesday.\nThe budget was approved.\nJohn will send the notes."
        assertEquals(prose, AiPasteCleanup.clean(prose).markdown)
        assertFalse(AiPasteCleanup.looksLikeAiAnswer(prose))
    }

    @Test fun `long text splits at headings, never inside code`() {
        val md = (1..40).joinToString("\n\n") { "## Section $it\n\n" + "Words ".repeat(80) + "\n\n```\ncode $it\nmore\n```" }
        val parts = PasteTool.chunks(md, 4000)
        assertTrue(parts.size > 3)
        parts.forEach { p -> assertEquals(0, Regex("```").findAll(p).count() % 2) }
        assertEquals(md.replace(Regex("\\s+"), " ").trim(), parts.joinToString("\n\n").replace(Regex("\\s+"), " ").trim())
    }
}
