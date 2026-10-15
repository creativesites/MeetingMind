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

class CirclesMediaParsingTest {
    @Test fun `member: last-read time is read for read receipts`() {
        assertEquals(1_700L, CirclesParsing.member("u1", mapOf("displayName" to "Ann", "role" to "member", "lastReadAt" to 1_700L)).lastReadAt)
        assertEquals(0L, CirclesParsing.member("u2", mapOf("displayName" to "Bo", "role" to "member")).lastReadAt)
    }

    @Test fun `circle: cover photo and pinned message are read, blank means none`() {
        val c = CirclesParsing.circle("c1", mapOf("name" to "Cell", "template" to "custom", "coverPath" to "circles/c1/media/x", "pinnedMessageId" to "m4"))
        assertEquals("circles/c1/media/x", c?.coverPath)
        assertEquals("m4", c?.pinnedMessageId)
        assertNull(CirclesParsing.circle("c2", mapOf("name" to "Cell", "template" to "custom", "coverPath" to ""))?.coverPath)
    }

    @Test fun `message: attachment, forwarded label and mentions are read`() {
        val m = CirclesParsing.message("m1", mapOf(
            "authorUid" to "u1", "kind" to "image", "text" to "Breakfast", "createdAt" to 5L,
            "attachment" to mapOf("path" to "circles/c1/media/x", "mime" to "image/jpeg", "bytes" to 1200L),
            "forwardedFrom" to "m0", "mentions" to listOf("u2")
        ))
        assertEquals(MessageKind.Image, m?.kind)
        assertEquals("circles/c1/media/x", m?.attachment?.path)
        assertEquals(1200L, m?.attachment?.bytes)
        assertEquals("m0", m?.forwardedFrom)
        assertEquals(listOf("u2"), m?.mentions)
    }

    @Test fun `attachment without a path is no attachment`() {
        assertNull(CirclesParsing.attachment(mapOf("mime" to "image/png")))
    }

    @Test fun `typing: name is read, missing name is Someone`() {
        assertEquals("Ann", CirclesParsing.typingUser("u1", mapOf("displayName" to "Ann", "updatedAt" to 9L)).name)
        assertEquals("Someone", CirclesParsing.typingUser("u2", mapOf("updatedAt" to 9L)).name)
    }

    @Test fun `media kinds accept only their own mimes`() {
        assertTrue(MediaKind.Image.accepts("image/webp"))
        assertFalse(MediaKind.Image.accepts("application/pdf"))
        assertTrue(MediaKind.File.accepts("application/pdf"))
        assertEquals(10_000_000L, MediaKind.Image.maxBytes)
    }
}

