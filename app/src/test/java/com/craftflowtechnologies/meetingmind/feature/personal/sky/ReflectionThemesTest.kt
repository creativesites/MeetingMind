package com.craftflowtechnologies.meetingmind.feature.personal.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionThemesTest {

    @Test fun `a word in two reflections makes a constellation named by the word`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "Inbox zero was hard today", 100),
                ReflectionEntry("b", "The inbox kept filling up again", 200)
            )
        )
        assertEquals(1, found.size)
        assertEquals("Inbox", found[0].name)
        assertEquals(listOf("a", "b"), found[0].entryIds)
        assertEquals(100L, found[0].firstAt)
    }

    @Test fun `a word that appears in only one reflection is not a constellation`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "Gardening helped", 1),
                ReflectionEntry("b", "Walking helped", 2)
            )
        )
        assertTrue(found.none { it.name.lowercase() == "gardening" })
        assertEquals(emptyList<Constellation>(), ReflectionThemes.find(listOf(ReflectionEntry("a", "Gardening", 1))))
    }

    @Test fun `stopwords and filler words never become themes`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "I really just feel like today was a thing with the day", 1),
                ReflectionEntry("b", "Really, today I just felt things about the day", 2)
            )
        )
        assertEquals(emptyList<String>(), found.map { it.name })
    }

    @Test fun `words under four letters are dropped`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "cat dog sun", 1),
                ReflectionEntry("b", "cat dog sun", 2)
            )
        )
        assertTrue(found.isEmpty())
    }

    @Test fun `simple plurals merge into one theme`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "Three inboxes to clear", 1),
                ReflectionEntry("b", "The inbox again", 2),
                ReflectionEntry("c", "Two more inbox threads", 3)
            )
        )
        val inbox = found.single()
        assertEquals("Inboxes", inbox.name) // the first form seen is the name shown
        assertEquals(listOf("a", "b", "c"), inbox.entryIds)
    }

    @Test fun `mornings and morning are one theme`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "Mornings are quiet", 1),
                ReflectionEntry("b", "A good morning walk", 2)
            )
        )
        assertEquals(listOf("a", "b"), found.single().entryIds)
    }

    @Test fun `tags count as themes too`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "Quiet before the day started", 1, tags = listOf("Family")),
                ReflectionEntry("b", "Dinner was loud", 2, tags = listOf("family"))
            )
        )
        val family = found.single()
        assertEquals("Family", family.name)
        assertEquals(listOf("a", "b"), family.entryIds)
    }

    @Test fun `a tag and a word that match are counted together`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "Busy week with money worries", 1, tags = listOf("money")),
                ReflectionEntry("b", "Money came in late", 2)
            )
        )
        assertEquals(listOf("a", "b"), found.single().entryIds)
    }

    @Test fun `themes rank by how many reflections carry them, then by recency`() {
        val found = ReflectionThemes.find(
            listOf(
                ReflectionEntry("a", "Walking and reading", 10),
                ReflectionEntry("b", "Walking again", 20),
                ReflectionEntry("c", "Walking and reading more", 30),
                ReflectionEntry("d", "Sleeping well", 40),
                ReflectionEntry("e", "Sleeping late again", 50)
            )
        )
        // Walking appears three times; sleeping and reading each appear twice, sleeping is more recent.
        assertEquals(listOf("Walking", "Sleeping", "Reading"), found.map { it.name })
    }

    @Test fun `at most five constellations are returned`() {
        val words = listOf("alpha", "bravo", "charlie", "delta", "echoes", "foxtrot", "golfer")
        val entries = words.flatMapIndexed { i, w ->
            listOf(ReflectionEntry("x$i-1", "$w one", (i * 10 + 1).toLong()), ReflectionEntry("x$i-2", "$w two", (i * 10 + 2).toLong()))
        }
        assertEquals(ReflectionThemes.MAX_CONSTELLATIONS, ReflectionThemes.find(entries).size)
    }

    @Test fun `the same reflections always give the same constellations`() {
        val entries = listOf(
            ReflectionEntry("a", "Calls with the team", 1),
            ReflectionEntry("b", "Team lunch was good", 2),
            ReflectionEntry("c", "The team shipped", 3)
        )
        assertEquals(ReflectionThemes.find(entries), ReflectionThemes.find(entries))
    }

    @Test fun `no reflections give no constellations`() {
        assertEquals(emptyList<Constellation>(), ReflectionThemes.find(emptyList()))
    }

    @Test fun `plural rules only touch words that are long enough`() {
        assertEquals("inbox", ReflectionThemes.singular("inboxes"))
        assertEquals("list", ReflectionThemes.singular("lists"))
        assertEquals("grass", ReflectionThemes.singular("grass"))
        assertEquals("focus", ReflectionThemes.singular("focus"))
        assertEquals("this", ReflectionThemes.singular("this"))
    }
}
