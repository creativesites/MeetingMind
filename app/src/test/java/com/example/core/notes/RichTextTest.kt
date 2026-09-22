package com.example.core.notes

import com.example.core.notes.InlineStyle.BOLD
import com.example.core.notes.InlineStyle.CODE
import com.example.core.notes.InlineStyle.ITALIC
import com.example.core.notes.InlineStyle.LINK
import com.example.core.notes.InlineStyle.UNDERLINE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    private val hello = RichText.plain("Hello brave world")

    @Test
    fun `applying and toggling a style covers exactly the selection`() {
        val bold = hello.toggleStyle(BOLD, 6, 11)
        assertEquals(listOf(InlineSpan(6, 11, BOLD)), bold.spans)
        assertTrue(bold.hasStyle(BOLD, 6, 11))
        assertFalse(bold.hasStyle(BOLD, 5, 11))

        val off = bold.toggleStyle(BOLD, 6, 11)
        assertTrue(off.spans.isEmpty())
    }

    @Test
    fun `toggling part of a partly styled range applies to the whole range`() {
        val text = hello.applyStyle(BOLD, 0, 5).toggleStyle(BOLD, 0, 11)
        assertEquals(listOf(InlineSpan(0, 11, BOLD)), text.spans)
    }

    @Test
    fun `removing the middle of a span splits it`() {
        val text = hello.applyStyle(ITALIC, 0, 17).removeStyle(ITALIC, 6, 11)
        assertEquals(listOf(InlineSpan(0, 6, ITALIC), InlineSpan(11, 17, ITALIC)), text.spans)
    }

    @Test
    fun `adjacent spans of the same style merge`() {
        val text = hello.applyStyle(BOLD, 0, 5).applyStyle(BOLD, 5, 11)
        assertEquals(listOf(InlineSpan(0, 11, BOLD)), text.spans)
    }

    @Test
    fun `adjacent links to different targets stay separate`() {
        val text = hello.applyStyle(LINK, 0, 5, "https://a.test").applyStyle(LINK, 5, 11, "https://b.test")
        assertEquals(2, text.spans.size)
    }

    @Test
    fun `a new link replaces the link it overlaps`() {
        val text = hello.applyStyle(LINK, 0, 11, "https://a.test").applyStyle(LINK, 6, 17, "https://b.test")
        assertEquals(
            listOf(InlineSpan(0, 6, LINK, "https://a.test"), InlineSpan(6, 17, LINK, "https://b.test")),
            text.spans
        )
    }

    @Test
    fun `a collapsed cursor reports the style of the character before it`() {
        val text = hello.applyStyle(BOLD, 0, 5)
        assertTrue(text.hasStyle(BOLD, 5, 5))
        assertFalse(text.hasStyle(BOLD, 6, 6))
        assertFalse(text.hasStyle(BOLD, 0, 0))
    }

    @Test
    fun `typing after a bold word continues the bold`() {
        val text = RichText.plain("Hello").applyStyle(BOLD, 0, 5).withEditedText("Hello there")
        assertEquals(listOf(InlineSpan(0, 11, BOLD)), text.spans)
    }

    @Test
    fun `typing after a link does not extend the link`() {
        val text = RichText.plain("site").applyStyle(LINK, 0, 4, "https://a.test").withEditedText("site now")
        assertEquals(listOf(InlineSpan(0, 4, LINK, "https://a.test")), text.spans)
    }

    @Test
    fun `typing after code does not extend the code`() {
        val text = RichText.plain("x").applyStyle(CODE, 0, 1).withEditedText("x y")
        assertEquals(listOf(InlineSpan(0, 1, CODE)), text.spans)
    }

    @Test
    fun `a suppressed style stops continuing`() {
        val text = RichText.plain("Hello").applyStyle(BOLD, 0, 5)
            .withEditedText("Hello there", suppressedStyles = setOf(BOLD))
        assertEquals(listOf(InlineSpan(0, 5, BOLD)), text.spans)
    }

    @Test
    fun `pending styles apply to the inserted text only`() {
        val text = RichText.plain("Hello").withEditedText("Hello there", pendingStyles = setOf(ITALIC))
        assertEquals(listOf(InlineSpan(5, 11, ITALIC)), text.spans)
    }

    @Test
    fun `text typed before a span shifts it without taking its style`() {
        val text = hello.applyStyle(BOLD, 6, 11).withEditedText("Oh Hello brave world")
        assertEquals(listOf(InlineSpan(9, 14, BOLD)), text.spans)
    }

    @Test
    fun `text typed at the start of a span does not take its style`() {
        val text = hello.applyStyle(BOLD, 6, 11).withEditedText("Hello very brave world")
        assertEquals(listOf(InlineSpan(11, 16, BOLD)), text.spans)
    }

    @Test
    fun `typing inside a span keeps it whole`() {
        val text = hello.applyStyle(BOLD, 6, 11).withEditedText("Hello bravest world")
        assertEquals(listOf(InlineSpan(6, 13, BOLD)), text.spans)
    }

    @Test
    fun `deleting across a span shrinks it and deleting all of it removes it`() {
        val styled = hello.applyStyle(BOLD, 6, 11)
        assertEquals(listOf(InlineSpan(6, 8, BOLD)), styled.withEditedText("Hello brworld").spans)
        assertTrue(styled.withEditedText("Hello world").spans.isEmpty())
    }

    @Test
    fun `replacing a selection that starts a span keeps the style`() {
        val text = hello.applyStyle(BOLD, 6, 11).withEditedText("Hello bold world")
        assertEquals(listOf(InlineSpan(6, 10, BOLD)), text.spans)
    }

    @Test
    fun `repeated characters are handled by the prefix and suffix match`() {
        val text = RichText.plain("aaaa").applyStyle(BOLD, 2, 4).withEditedText("aaaaa")
        // Whichever 'a' is considered new, the styled range stays two characters or grows by one.
        assertTrue(text.spans.single().length in 2..3)
        assertEquals(5, text.text.length)
    }

    @Test
    fun `split and append are inverse`() {
        val text = hello.applyStyle(BOLD, 3, 9).applyStyle(UNDERLINE, 0, 17)
        val (left, right) = text.splitAt(6)
        assertEquals("Hello ", left.text)
        assertEquals(listOf(InlineSpan(0, 3, BOLD)), right.spans.filter { it.style == BOLD })
        assertEquals(text, left.append(right))
    }

    @Test
    fun `runs cut the text where styling changes`() {
        val runs = hello.applyStyle(BOLD, 0, 11).applyStyle(ITALIC, 6, 17).runs()
        assertEquals(listOf("Hello ", "brave", " world"), runs.map { it.text })
        assertEquals(setOf(BOLD), runs[0].styles)
        assertEquals(setOf(BOLD, ITALIC), runs[1].styles)
        assertEquals(setOf(ITALIC), runs[2].styles)
    }

    @Test
    fun `markdown hugs words and escapes specials`() {
        val md = RichText.plain("Read *this* now").applyStyle(BOLD, 5, 12).toMarkdown()
        assertEquals("Read **\\*this\\*** now", md)
    }

    @Test
    fun `markdown renders links around styled text`() {
        val md = RichText.plain("see docs").applyStyle(LINK, 4, 8, "https://x.test").applyStyle(ITALIC, 4, 8).toMarkdown()
        assertEquals("see [*docs*](https://x.test)", md)
    }

    @Test
    fun `spans survive an encode decode round trip including awkward urls`() {
        val text = hello.applyStyle(BOLD, 0, 5).applyStyle(LINK, 6, 11, "https://x.test/a,b;c?d=1")
        assertEquals(text, RichText.decode(text.text, text.encodeSpans()))
    }

    @Test
    fun `malformed or out of range stored spans are dropped not fatal`() {
        val text = RichText.decode("abc", "BOLD,0,2;NOPE,0,1;ITALIC,x,2;UNDERLINE,1,99;LINK,0,1")
        assertEquals(listOf(InlineSpan(0, 2, BOLD), InlineSpan(1, 3, UNDERLINE)), text.spans)
    }
}
