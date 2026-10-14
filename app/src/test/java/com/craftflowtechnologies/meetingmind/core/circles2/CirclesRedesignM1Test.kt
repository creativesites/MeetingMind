package com.craftflowtechnologies.meetingmind.core.circles2

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Parsing and request bodies for the redesign's backend slice (joinApproval, description, titles, pins, events). */
class CirclesRedesignM1Test {
    @Test fun `settings: join approval is read, and off when missing`() {
        assertTrue(CirclesParsing.settings(mapOf("joinApproval" to true), "small_group").joinApproval)
        assertFalse(CirclesParsing.settings(emptyMap(), "small_group").joinApproval)
    }

    @Test fun `circle: description and pinned post are read, blank pin is none`() {
        val c = CirclesParsing.circle("c1", mapOf("name" to "Cell", "template" to "custom", "description" to "Bring a question.", "pinnedPostId" to "p9"))
        assertEquals("Bring a question.", c?.description)
        assertEquals("p9", c?.pinnedPostId)
        val blank = CirclesParsing.circle("c2", mapOf("name" to "Cell", "template" to "custom", "pinnedPostId" to ""))
        assertEquals("", blank?.description)
        assertNull(blank?.pinnedPostId)
    }

    @Test fun `member: title is a label, blank means none`() {
        assertEquals("Pastor", CirclesParsing.member("u1", mapOf("displayName" to "Dan", "role" to "admin", "title" to "Pastor")).title)
        assertNull(CirclesParsing.member("u2", mapOf("displayName" to "Ann", "role" to "member", "title" to "")).title)
        assertEquals(Role.Admin, CirclesParsing.member("u1", mapOf("role" to "admin", "title" to "Pastor")).role, "title never changes the role")
    }

    @Test fun `post type: note is the free-form type`() {
        assertEquals(PostType.Note, PostType.from("note"))
        assertFalse(PostType.Note.adminOnly)
    }

    @Test fun `event: fields are read, a missing title is refused`() {
        val e = CirclesParsing.event("e1", mapOf(
            "title" to "Breakfast", "description" to "Café Lumen", "onlineUrl" to "", "startsAt" to 1_800_000_000_000L,
            "endsAt" to 1_800_003_600_000L, "timeZone" to "America/New_York", "status" to "cancelled", "createdByName" to "Dana"
        ))
        assertEquals("Breakfast", e?.title)
        assertNull(e?.onlineUrl)
        assertTrue(e?.cancelled == true)
        assertNull(CirclesParsing.event("e2", mapOf("startsAt" to 1L)))
    }

    @Test fun `rsvp: answer is read, unknown answer is ignored`() {
        val r = CirclesParsing.rsvp("u1", mapOf("status" to "going", "displayName" to "Ann"))
        assertEquals(RsvpAnswer.Going, r?.answer)
        assertNull(CirclesParsing.rsvp("u2", mapOf("status" to "yes please")))
    }
}

@RunWith(RobolectricTestRunner::class)
class CirclesRedesignM1JsonTest {
    @Test fun `settings body carries join approval`() {
        val json = CirclesJson.settings(CircleSettings(listOf(PostType.Prayer), joinApproval = true))
        assertTrue(json.getBoolean("joinApproval"))
    }

    @Test fun `event body sends an empty link as empty, times as numbers`() {
        val json: JSONObject = CirclesJson.event("c1", EventDraft("Breakfast", "", "", null, 1_800_000_000_000L, 1_800_003_600_000L, "America/New_York"))
        assertEquals("", json.getString("onlineUrl"))
        assertEquals(1_800_000_000_000L, json.getLong("startsAt"))
        assertEquals("America/New_York", json.getString("timeZone"))
    }
}
