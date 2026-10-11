package com.craftflowtechnologies.meetingmind.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Experience first, note after" (docs/mvp/ONE_PLACE.md §1): a prominent button starts the
 * experience its label names, and a button that only creates a note says "Write …" and is quiet.
 * The screens read their labels from [EntryPoints], so this guards what people actually see.
 */
class EntryPointContractTest {
    private val all = EntryPoints.all

    @Test
    fun `a WRITE entry is never prominent and always says Write`() {
        val bad = all.filter { it.kind == EntryKind.WRITE }.filter { it.prominent || !it.label.startsWith("Write") }
        assertTrue("WRITE entries must be QUIET and start with \"Write\": $bad", bad.isEmpty())
    }

    @Test
    fun `a label that starts with Write is a WRITE entry`() {
        val bad = all.filter { it.label.startsWith("Write") && it.kind != EntryKind.WRITE }
        assertTrue("These say Write but do something else: $bad", bad.isEmpty())
    }

    @Test
    fun `ids are unique and every surface is covered`() {
        assertEquals("duplicate ids", all.size, all.map { it.id }.toSet().size)
        EntryScreen.entries.forEach { s -> assertTrue("no entries for $s", all.any { it.screen == s }) }
    }

    @Test
    fun `no entry is named like a template`() {
        val banned = setOf("Bible study", "Study", "Word", "Stories", "New note", "Note")
        val bad = all.filter { it.label in banned }
        assertTrue("Entries named like templates or old names: $bad", bad.isEmpty())
    }

    @Test
    fun `Faith home leads with experiences and keeps the six writing tiles exact`() {
        val faith = all.filter { it.screen == EntryScreen.FAITH_HOME }
        assertEquals(
            listOf("Write a prayer", "Write a prayer request", "Write down a blessing", "Write a testimony", "Write a reflection", "Write a devotional"),
            EntryPoints.faithWriteTiles.map { it.label }
        )
        assertEquals("the screen's tiles are the registry's tiles", EntryPoints.faithWriteTiles, com.craftflowtechnologies.meetingmind.feature.faith.FaithWriteTiles.map { it.first })
        assertTrue(faith.filter { it.prominent }.all { it.kind == EntryKind.EXPERIENCE })
        assertEquals(listOf("Bible", "Today's devotional", "Pray with me", "Circles"), listOf(EntryPoints.faithBible, EntryPoints.faithDevotional, EntryPoints.faithPray, EntryPoints.faithCircles).map { it.label })
        assertTrue("Bible is the one hero", faith.count { it.style == EntryStyle.HERO } == 1)
    }

    @Test
    fun `the home quick row says Devotional and Testimonies`() {
        assertEquals("Devotional", EntryPoints.quickDevotional.label)
        assertEquals("Testimonies", EntryPoints.quickTestimonies.label)
    }

    /** The screens must take their labels from the registry, not retype them, or the contract guards nothing. */
    @Test
    fun `screens read entry labels from the registry`() {
        val root = File("src/main/java/com/craftflowtechnologies/meetingmind")
        val files = mapOf(
            EntryScreen.FAITH_HOME to "feature/faith/FaithHome.kt",
            EntryScreen.TODAY_QUICK_ROW to "feature/today/HomeQuickAccessRow.kt",
            EntryScreen.EVERYDAY_HOME to "feature/today/EverydayHome.kt",
            EntryScreen.BIBLE_HOME to "feature/bible/BibleHomeScreen.kt"
        )
        val problems = files.flatMap { (screen, path) ->
            val src = File(root, path).also { assertTrue("missing $path", it.exists()) }.readText()
            assertTrue("$path doesn't use EntryPoints", "EntryPoints." in src)
            all.filter { it.screen == screen }.mapNotNull { e ->
                // A label typed as a string literal in a call (not through the registry) can drift from the contract.
                if (Regex("""(Button|TextAction|ListRow|Text)\(\s*(title\s*=\s*)?"${Regex.escape(e.label)}"""").containsMatchIn(src)) "$path retypes \"${e.label}\"" else null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
