package com.example.core.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlToMarkdownTest {

    @Test
    fun `a ChatGPT answer copied as HTML keeps its structure`() {
        val html = """
            <meta charset="utf-8"><h3>Steps</h3>
            <ol><li><p><strong>Plan</strong> the week</p></li><li><p>Ship it</p>
            <ul><li>Tell <em>John</em></li></ul></li></ol>
            <blockquote><p>Keep it simple.</p></blockquote>
            <pre><code class="language-kotlin">fun main() {
                println("hi")
            }</code></pre>
            <p>See <a href="https://example.com/docs">the docs</a> &amp; <code>README</code>.</p>
            <table><thead><tr><th>Who</th><th>What</th></tr></thead><tbody><tr><td>Ann</td><td>API</td></tr></tbody></table>
        """.trimIndent()
        val expected = """
            ### Steps

            1. **Plan** the week
            2. Ship it
              - Tell *John*

            > Keep it simple.

            ```kotlin
            fun main() {
                println("hi")
            }
            ```

            See [the docs](https://example.com/docs) & `README`.

            | Who | What |
            | --- | --- |
            | Ann | API |
        """.trimIndent()
        assertEquals(expected, HtmlToMarkdown.convert(html))
    }

    @Test
    fun `spaces inside bold move outside the markers`() {
        assertEquals("a **bold** word", HtmlToMarkdown.convert("<p>a<b> bold </b>word</p>"))
    }

    @Test
    fun `line breaks, rules and entities`() {
        assertEquals("one\ntwo\n\n---\n\nit’s “quoted”", HtmlToMarkdown.convert("<div>one<br>two</div><hr><p>it&rsquo;s &ldquo;quoted&rdquo;</p>"))
    }
}
