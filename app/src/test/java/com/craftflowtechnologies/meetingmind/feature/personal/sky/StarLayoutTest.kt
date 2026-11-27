package com.craftflowtechnologies.meetingmind.feature.personal.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class StarLayoutTest {

    private val width = 360f
    private val height = 520f

    private fun ids(n: Int) = (1..n).map { "entry-$it" }

    @Test fun `the same reflections always draw the same sky`() {
        assertEquals(StarLayout.place(ids(9), width, height), StarLayout.place(ids(9), width, height))
    }

    @Test fun `the order the ids arrive in does not move the stars`() {
        val forward = StarLayout.place(ids(9), width, height)
        val backward = StarLayout.place(ids(9).reversed(), width, height)
        assertEquals(forward, backward)
    }

    @Test fun `stars keep off the top fifteen percent and the bottom thirty percent`() {
        val spots = StarLayout.place(ids(30), width, height)
        val top = height * StarLayout.TOP_FRACTION
        val bottom = height * (1f - StarLayout.BOTTOM_FRACTION)
        spots.values.forEach { p ->
            assertTrue("y ${p.y} above the header band", p.y >= top - 0.001f)
            assertTrue("y ${p.y} below the card band", p.y <= bottom + 0.001f)
        }
    }

    @Test fun `stars stay inside the sides`() {
        StarLayout.place(ids(30), width, height).values.forEach { p ->
            assertTrue(p.x >= 0f && p.x <= width)
        }
    }

    @Test fun `stars are at least twenty-four dp apart where there is room`() {
        val spots = StarLayout.place(ids(12), width, height).values.toList()
        for (i in spots.indices) for (j in i + 1 until spots.size) {
            val a = spots[i]
            val b = spots[j]
            val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
            assertTrue("stars $i and $j are $d dp apart", d >= StarLayout.MIN_GAP_DP)
        }
    }

    @Test fun `every id gets exactly one position and duplicates are merged`() {
        val spots = StarLayout.place(listOf("a", "b", "a", "c"), width, height)
        assertEquals(listOf("a", "b", "c"), spots.keys.toList())
    }

    @Test fun `no reflections draw no stars`() {
        assertEquals(emptyMap<String, StarLayout.Point>(), StarLayout.place(emptyList(), width, height))
    }

    @Test fun `unit values are in range and repeat for the same key`() {
        val a = StarLayout.unitsFor("key", 16)
        assertEquals(16, a.size)
        assertTrue(a.all { it >= 0f && it < 1f })
        assertEquals(a.toList(), StarLayout.unitsFor("key", 16).toList())
    }
}
