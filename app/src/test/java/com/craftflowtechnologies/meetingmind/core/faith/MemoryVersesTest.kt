package com.craftflowtechnologies.meetingmind.core.faith

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryVersesTest {
    private val v = "I can do all things through Christ, who strengthens me."
    @Test fun `practice hides more each level, keeping punctuation`() {
        assertEquals(v, MemoryVerses.mask(v, 0))
        assertEquals("I can __ all things _______ Christ, who __________ me.", MemoryVerses.mask(v, 1))
        assertEquals("I ___ do ___ things _______ Christ, ___ strengthens __.", MemoryVerses.mask(v, 2))
        assertEquals("I c__ d_ a__ t_____ t______ C_____, w__ s________ m_.", MemoryVerses.mask(v, 3))
    }
    @Test fun `the verse of the day turns through the collection`() {
        assertEquals(listOf(0, 1, 2, 0), (0L..3L).map { MemoryVerses.indexFor(it, 3) })
        assertEquals(-1, MemoryVerses.indexFor(5, 0))
    }
}
