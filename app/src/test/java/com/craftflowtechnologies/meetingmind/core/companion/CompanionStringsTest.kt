package com.craftflowtechnologies.meetingmind.core.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Comment
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks app/src/main/res/values/strings_companion.xml with a plain XML parser (no Android resources).
 */
class CompanionStringsTest {

    private data class Entry(
        val name: String,
        val tag: String,
        val texts: List<String>,
        val comment: String?,
    )

    private val positional = Regex("%(\\d+)\\\$s")

    private fun load(): List<Entry> {
        val file = File("src/main/res/values/strings_companion.xml")
        assertTrue("missing ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val entries = mutableListOf<Entry>()
        var pendingComment: String? = null
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            when (val node = children.item(i)) {
                is Comment -> pendingComment = node.data.trim()
                is Element -> {
                    if (node.tagName == "string" || node.tagName == "string-array") {
                        val texts = if (node.tagName == "string") {
                            listOf(node.textContent)
                        } else {
                            val items = node.getElementsByTagName("item")
                            (0 until items.length).map { items.item(it).textContent }
                        }
                        entries += Entry(node.getAttribute("name"), node.tagName, texts, pendingComment)
                    }
                    pendingComment = null
                }
            }
        }
        return entries
    }

    /** Text as the app sees it: Android escapes (\' and \") removed. */
    private fun unescape(text: String) = text.replace("\\'", "'").replace("\\\"", "\"")

    @Test
    fun keysAreUnique() {
        val names = load().map { it.name }
        assertTrue("no strings found", names.isNotEmpty())
        val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals("duplicate keys: $duplicates", emptySet<String>(), duplicates)
    }

    @Test
    fun keysUseCompanionOrFormPrefix() {
        load().forEach { entry ->
            assertTrue(
                "key ${entry.name} must start with companion_ or form_",
                entry.name.startsWith("companion_") || entry.name.startsWith("form_"),
            )
        }
    }

    @Test
    fun placeholdersArePositional() {
        load().forEach { entry ->
            entry.texts.forEach { raw ->
                val text = unescape(raw)
                // Every % must be a literal %% or a positional %N$s. Anything else (%s, %d, %1$d) fails.
                val leftover = text.replace(Regex("%%|%\\d+\\\$s"), "")
                assertFalse("${entry.name} has a non-positional placeholder: $text", leftover.contains('%'))
            }
        }
    }

    @Test
    fun positionalArgsAreContiguousFromOne() {
        load().forEach { entry ->
            entry.texts.forEach { raw ->
                val indices = positional.findAll(unescape(raw)).map { it.groupValues[1].toInt() }.toSortedSet()
                if (indices.isNotEmpty()) {
                    assertEquals(
                        "${entry.name} must use %1\$s.. without gaps",
                        (1..indices.max()).toList(),
                        indices.toList(),
                    )
                }
            }
        }
    }

    @Test
    fun noLiteralCompanionOrNameTokens() {
        load().forEach { entry ->
            entry.texts.forEach { raw ->
                val text = unescape(raw)
                assertFalse("${entry.name} contains {companion}", text.contains("{companion}"))
                assertFalse("${entry.name} contains {name}", text.contains("{name}"))
                assertFalse("${entry.name} contains a brace placeholder: $text", text.contains('{') || text.contains('}'))
            }
        }
    }

    @Test
    fun placeholderEntriesDocumentTheirArgs() {
        load().filter { e -> e.texts.any { positional.containsMatchIn(it) } }.forEach { entry ->
            val comment = entry.comment
            assertTrue("${entry.name} has placeholders but no comment above it", comment != null)
            val used = entry.texts.flatMap { t -> positional.findAll(unescape(t)).map { it.groupValues[1] } }.toSet()
            used.forEach { index ->
                assertTrue(
                    "${entry.name}: comment must document %$index\$s",
                    comment!!.contains("%$index\$s"),
                )
            }
        }
    }

    @Test
    fun apostrophesAndQuotesAreEscaped() {
        load().forEach { entry ->
            entry.texts.forEach { raw ->
                assertFalse("${entry.name} has an unescaped apostrophe", raw.contains(Regex("(?<!\\\\)'")))
                assertFalse("${entry.name} has an unescaped double quote", raw.contains(Regex("(?<!\\\\)\"")))
            }
        }
    }

    @Test
    fun arraysHaveTheVariantSlotsFromTheSpec() {
        val arrays = load().filter { it.tag == "string-array" }.associateBy { it.name }
        // Four slots: Everyday, Faith, Work, Study.
        listOf(
            "companion_processing_detecting_speech",
            "companion_processing_diarizing",
            "companion_processing_analyzing",
        ).forEach { key ->
            assertEquals("$key should have 4 variant slots", 4, arrays[key]?.texts?.size)
        }
        assertEquals(2, arrays["companion_notification_note_ready"]?.texts?.size)
    }
}
