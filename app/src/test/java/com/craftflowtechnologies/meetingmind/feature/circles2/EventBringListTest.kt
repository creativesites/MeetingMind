package com.craftflowtechnologies.meetingmind.feature.circles2

import com.craftflowtechnologies.meetingmind.core.circles2.BringItem
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Table: the edit form starts from the event's own items, and the claim line says who is bringing what. */
class EventBringListTest {
    private fun event(vararg items: BringItem) = CircleEvent(
        id = "e1", title = "Dinner", description = "", location = "", onlineUrl = null, startsAt = 1L, endsAt = 2L,
        timeZone = "UTC", status = "scheduled", createdByName = "Ann", bringList = items.toList(),
    )

    @Test fun `the edit form starts from the event's own texts, so saving keeps the list`() {
        val e = event(BringItem("a", "Cake"), BringItem("b", "Plates", "ann", "Ann"))
        assertEquals(listOf("Cake", "Plates"), initialBringTexts(e))
        assertEquals(emptyList<String>(), initialBringTexts(null))
    }

    @Test fun `the draft trims, drops blanks, cuts long items and stops at twenty`() {
        assertEquals(listOf("Cake", "Plates"), draftBringList(listOf("  Cake ", "", "   ", "Plates")))
        assertEquals(80, draftBringList(listOf("x".repeat(100))).single().length)
        assertEquals(20, draftBringList(List(25) { "Item $it" }).size)
    }

    @Test fun `the status line says who has the item`() {
        assertEquals("Nobody yet", bringStatus(BringItem("a", "Cake"), "me"))
        assertEquals("You're bringing this", bringStatus(BringItem("a", "Cake", "me", "Me"), "me"))
        assertEquals("Ann is bringing it", bringStatus(BringItem("a", "Cake", "ann", "Ann"), "me"))
        assertEquals("Someone is bringing it", bringStatus(BringItem("a", "Cake", "ann", null), "me"))
    }
}
