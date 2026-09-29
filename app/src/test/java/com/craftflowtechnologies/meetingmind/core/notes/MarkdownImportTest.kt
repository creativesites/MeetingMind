package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownImportTest {

    private val chatGptAnswer = """
        # Launch plan

        Here's a plan that keeps **scope tight** and ships by *Friday*.
        It fits the budget we discussed.

        ## Decisions

        - Use **PostgreSQL** for storage
        - Move deployment to Friday
          because QA needs Thursday
            - Nested: tell the client
        1. Draft the API
        2. Review with `John`

        ## Action items

        - [ ] Winston: update the API
        - [x] John: review deployment

        > Ship small, ship often.

        | Owner | Task | Due |
        |---|:---:|---|
        | Winston | **API** | Fri |
        | John | Review | Thu |

        ```kotlin
        fun main() {
            println("hi")
        }
        ```

        ---

        ![Architecture](https://example.com/arch.png)

        https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s

        See [the docs](https://example.com/docs) and ~~old notes~~ ==this==.
    """.trimIndent()

    private val blocks = MarkdownImport.parse(chatGptAnswer, "n")

    @Test
    fun `an AI answer becomes the right blocks in order`() {
        assertEquals(
            listOf(
                NoteBlockType.HEADING_1, NoteBlockType.PARAGRAPH, NoteBlockType.HEADING_2,
                NoteBlockType.BULLET, NoteBlockType.BULLET, NoteBlockType.BULLET, NoteBlockType.NUMBERED, NoteBlockType.NUMBERED,
                NoteBlockType.HEADING_2, NoteBlockType.CHECKLIST, NoteBlockType.CHECKLIST,
                NoteBlockType.QUOTE, NoteBlockType.TABLE, NoteBlockType.CODE, NoteBlockType.DIVIDER,
                NoteBlockType.EMBED, NoteBlockType.EMBED, NoteBlockType.PARAGRAPH
            ),
            blocks.map { it.type }
        )
    }

    @Test
    fun `a wrapped paragraph stays one block, and no block holds a line break`() {
        assertEquals("Here's a plan that keeps scope tight and ships by Friday. It fits the budget we discussed.", blocks[1].content.text)
        assertTrue(blocks.filter { it.type != NoteBlockType.CODE }.none { '\n' in it.content.text })
    }

    @Test
    fun `inline styles land on the right words`() {
        val p = blocks[1].content
        assertTrue(p.hasStyle(InlineStyle.BOLD, p.text.indexOf("scope"), p.text.indexOf("scope") + "scope tight".length))
        assertTrue(p.hasStyle(InlineStyle.ITALIC, p.text.indexOf("Friday"), p.text.indexOf("Friday") + 6))
        assertFalse(p.hasStyle(InlineStyle.BOLD, 0, 4))
        val last = blocks.last().content
        assertEquals("See the docs and old notes this.", last.text)
        assertEquals("https://example.com/docs", last.linkAt(last.text.indexOf("docs"))!!.url)
        assertTrue(last.hasStyle(InlineStyle.STRIKETHROUGH, last.text.indexOf("old"), last.text.indexOf("old") + 9))
        assertTrue(last.hasStyle(InlineStyle.HIGHLIGHT, last.text.indexOf("this"), last.text.indexOf("this") + 4))
        val code = blocks[7].content
        assertTrue(code.hasStyle(InlineStyle.CODE, code.text.indexOf("John"), code.text.indexOf("John") + 4))
    }

    @Test
    fun `lists keep nesting, continuation lines and check state`() {
        assertEquals("Move deployment to Friday because QA needs Thursday", blocks[4].content.text)
        assertEquals(1, blocks[5].indent)
        assertFalse(blocks[9].checked)
        assertTrue(blocks[10].checked)
    }

    @Test
    fun `tables, code, pictures and videos keep their details`() {
        val table = MarkdownImport.Table.fromJson(blocks[12].payload[NoteBlock.PAYLOAD_TABLE])!!
        assertEquals(listOf("Owner", "Task", "Due"), table.header)
        assertEquals(listOf("Winston", "**API**", "Fri"), table.rows[0])
        assertEquals("kotlin", blocks[13].payload[NoteBlock.PAYLOAD_LANGUAGE])
        assertEquals("fun main() {\n    println(\"hi\")\n}", blocks[13].content.text)
        assertEquals(NoteBlock.EMBED_IMAGE, blocks[15].payload[NoteBlock.PAYLOAD_EMBED_KIND])
        assertEquals("https://example.com/arch.png", blocks[15].payload[NoteBlock.PAYLOAD_URL])
        assertEquals(NoteBlock.EMBED_YOUTUBE, blocks[16].payload[NoteBlock.PAYLOAD_EMBED_KIND])
        assertEquals("dQw4w9WgXcQ", YouTube.idOf(blocks[16].payload[NoteBlock.PAYLOAD_URL]!!))
        assertEquals(42, YouTube.startSeconds(blocks[16].payload[NoteBlock.PAYLOAD_URL]!!))
    }

    @Test
    fun `writing the blocks back out and reading them again changes nothing`() {
        val again = MarkdownImport.parse(NoteText.markdown(blocks), "n")
        assertEquals(blocks.map { it.type to it.content }, again.map { it.type to it.content })
        assertEquals(blocks.map { it.indent to it.checked }, again.map { it.indent to it.checked })
        assertEquals(blocks.map { it.payload }, again.map { it.payload })
    }

    @Test
    fun `snake_case, lone stars and prices are left alone`() {
        val t = MarkdownImport.inline("use snake_case_name, 2 * 3 = 6 and costs \$5*")
        assertEquals("use snake_case_name, 2 * 3 = 6 and costs \$5*", t.text)
        assertTrue(t.spans.isEmpty())
    }

    @Test
    fun `plain text is not mistaken for Markdown`() {
        assertFalse(MarkdownImport.looksLikeMarkdown("Call John tomorrow\nBuy milk\nThe meeting moved to 3pm"))
        assertTrue(MarkdownImport.looksLikeMarkdown("Notes\n- one\n- two"))
        assertTrue(MarkdownImport.looksLikeMarkdown("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `copies for other apps keep the formatting in their own terms`() {
        val wa = NoteText.whatsApp(blocks.take(4))
        assertTrue(wa.contains("*Launch plan*"))
        assertTrue(wa.contains("*scope tight*"))
        assertTrue(wa.contains("• Use *PostgreSQL* for storage"))
        val html = NoteText.html(blocks)
        assertTrue(html.contains("<h1>Launch plan</h1>"))
        assertTrue(html.contains("<strong>scope tight</strong>"))
        assertTrue(html.contains("<table"))
    }
}
