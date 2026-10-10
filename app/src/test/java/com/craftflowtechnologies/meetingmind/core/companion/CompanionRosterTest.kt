package com.craftflowtechnologies.meetingmind.core.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionRosterTest {
    @Test fun `all four forms ship, in order`() {
        assertEquals(listOf(CompanionForm.ZURI, CompanionForm.NAS, CompanionForm.WREN, CompanionForm.PAGE), CompanionRoster.enabled)
    }

    @Test fun `parse skips unknown names and never returns empty`() {
        assertEquals(listOf(CompanionForm.ZURI, CompanionForm.NAS), CompanionRoster.parse(" zuri, NAS ,bogus,NAS"))
        assertEquals(listOf(CompanionForm.ZURI), CompanionRoster.parse(""))
    }

    @Test fun `a stored form outside the roster falls back to Zuri`() {
        val roster = listOf(CompanionForm.ZURI, CompanionForm.NAS)
        assertEquals(CompanionForm.ZURI, CompanionRoster.resolve(CompanionForm.WREN, roster))
        assertEquals(CompanionForm.NAS, CompanionRoster.resolve(CompanionForm.NAS, roster))
        assertNull(CompanionRoster.resolve(null, roster))
    }

    @Test fun `testing flags`() {
        assertEquals(AskGate.NONE, CompanionFlags.askGate)
        assertFalse(CompanionFlags.volumeLayer)
    }

    @Test fun `tier by size`() {
        assertEquals(CompanionTier.T0, CompanionTier.forSizeDp(24f))
        assertEquals(CompanionTier.T0, CompanionTier.forSizeDp(47.9f))
        assertEquals(CompanionTier.T1, CompanionTier.forSizeDp(48f))
        assertEquals(CompanionTier.T1, CompanionTier.forSizeDp(143f))
        assertEquals(CompanionTier.T2, CompanionTier.forSizeDp(144f))
    }
}
