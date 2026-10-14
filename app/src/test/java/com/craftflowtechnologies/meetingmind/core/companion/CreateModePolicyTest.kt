package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.core.companion.CreateMode.CELEBRATORY
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode.GRATEFUL
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode.JOYFUL
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode.PEACEFUL
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode.PRAYERFUL
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode.REFLECTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The §3.2 policy matrix, row by row. */
class CreateModePolicyTest {
    private val all = CreateMode.entries.toSet()

    @Test fun `prayer request - prayerful and peaceful only, whatever the vibe`() {
        for (v in CreateVibe.entries + null) {
            assertEquals(setOf(PRAYERFUL, PEACEFUL), CreateModePolicy.allowed(CreateSource.PRAYER_REQUEST, v))
            assertEquals(PRAYERFUL, CreateModePolicy.suggest(CreateSource.PRAYER_REQUEST, v))
        }
    }

    @Test fun `scripture, sermon quotes and devotionals block joyful and celebratory`() {
        for (src in listOf(CreateSource.SCRIPTURE_VERSE, CreateSource.SERMON_QUOTE, CreateSource.DEVOTIONAL)) {
            assertEquals(setOf(PRAYERFUL, PEACEFUL, GRATEFUL, REFLECTIVE), CreateModePolicy.allowed(src))
            assertEquals(PEACEFUL, CreateModePolicy.suggest(src))
            assertEquals(REFLECTIVE, CreateModePolicy.suggest(src, CreateVibe.REFLECTIVE))
            assertEquals("vibe never overrides the source", PEACEFUL, CreateModePolicy.suggest(src, CreateVibe.CELEBRATION))
        }
    }

    @Test fun `answered prayer and testimony - grateful, all allowed, grief is peaceful only`() {
        for (src in listOf(CreateSource.ANSWERED_PRAYER, CreateSource.TESTIMONY)) {
            assertEquals(all, CreateModePolicy.allowed(src))
            assertEquals(GRATEFUL, CreateModePolicy.suggest(src))
            assertEquals(setOf(PEACEFUL), CreateModePolicy.allowed(src, grief = true))
            assertEquals(PEACEFUL, CreateModePolicy.suggest(src, grief = true))
        }
    }

    @Test fun `achievement and study win - celebratory`() {
        for (src in listOf(CreateSource.ACHIEVEMENT, CreateSource.STUDY_WIN)) {
            assertEquals(all, CreateModePolicy.allowed(src))
            assertEquals(CELEBRATORY, CreateModePolicy.suggest(src))
        }
    }

    @Test fun `custom source follows the vibe`() {
        val expected = mapOf(
            CreateVibe.ENCOURAGING to GRATEFUL, CreateVibe.LOVE to GRATEFUL,
            CreateVibe.MOTIVATIONAL to JOYFUL, CreateVibe.FUNNY to JOYFUL,
            CreateVibe.WISDOM to REFLECTIVE, CreateVibe.REFLECTIVE to REFLECTIVE,
            CreateVibe.CELEBRATION to CELEBRATORY, CreateVibe.CUSTOM to PEACEFUL
        )
        for ((v, m) in expected) assertEquals("$v", m, CreateModePolicy.suggest(CreateSource.CUSTOM, v))
        assertEquals(PEACEFUL, CreateModePolicy.suggest(CreateSource.CUSTOM, null))
    }

    @Test fun `good friday - nothing celebrates`() {
        val gf = CreateModePolicy.allowed(CreateSource.ACHIEVEMENT, goodFriday = true)
        assertFalse(JOYFUL in gf)
        assertFalse(CELEBRATORY in gf)
        assertEquals(PEACEFUL, CreateModePolicy.suggest(CreateSource.ACHIEVEMENT, goodFriday = true))
    }

    @Test fun `the suggestion is always allowed`() {
        for (src in CreateSource.entries) for (v in CreateVibe.entries + null) for (g in listOf(false, true)) for (gf in listOf(false, true)) {
            assertTrue(CreateModePolicy.suggest(src, v, g, gf) in CreateModePolicy.allowed(src, v, g, gf))
        }
    }

    @Test fun `the companion is off by default on every card`() {
        for (src in CreateSource.entries) assertFalse(CreateModePolicy.includeByDefault(src, createSuggest = false))
        assertTrue(CreateModePolicy.includeByDefault(CreateSource.ACHIEVEMENT, createSuggest = true))
        assertTrue(CreateModePolicy.includeByDefault(CreateSource.STUDY_WIN, createSuggest = true))
        assertFalse(CreateModePolicy.includeByDefault(CreateSource.PRAYER_REQUEST, createSuggest = true))
        assertFalse(CreateModePolicy.includeByDefault(CreateSource.SCRIPTURE_VERSE, createSuggest = true))
    }
}
