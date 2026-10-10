package com.craftflowtechnologies.meetingmind.core.circles2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The old app crashed when a code was pasted. Parsing must accept anything and never throw. */
class InviteCodesTest {
    @Test fun `a plain code`() = assertEquals("GRACE-7K2Q", InviteCodes.extract("GRACE-7K2Q"))

    @Test fun `lowercase and surrounding spaces`() {
        assertEquals("GRACE-7K2Q", InviteCodes.extract("  grace-7k2q  "))
        assertEquals("HOPE-BBBB", InviteCodes.extract("\n\t hope-bbbb \n"))
    }

    @Test fun `a space or typographic dash instead of the hyphen`() {
        assertEquals("GRACE-7K2Q", InviteCodes.extract("grace 7k2q"))
        assertEquals("GRACE-7K2Q", InviteCodes.extract("GRACE–7K2Q"))
        assertEquals("GRACE-7K2Q", InviteCodes.extract("GRACE‑7K2Q"))
    }

    @Test fun `a whole WhatsApp message`() {
        val msg = "‎[10/10/26, 09:41] Pastor Mark: Join our Cell group \"Tuesday Night\" on MeetingMind.\n" +
            "Code: GRACE-7K2Q\nmeetingmind://c/GRACE-7K2Q\nOpen MeetingMind, go to Circles, tap Join and paste this message.\n🙏🙏"
        assertEquals("GRACE-7K2Q", InviteCodes.extract(msg))
        assertEquals(listOf("GRACE-7K2Q"), InviteCodes.extractAll(msg))
    }

    @Test fun `a link`() {
        assertEquals("GRACE-7K2Q", InviteCodes.fromLink("meetingmind://c/GRACE-7K2Q"))
        assertEquals("OLIVE-2XVD", InviteCodes.fromLink("https://example.org/c/olive-2xvd?utm=share"))
        assertNull(InviteCodes.fromLink("https://example.org/nothing"))
    }

    @Test fun `garbage emoji and empty input give null and never throw`() {
        listOf(
            null, "", "   ", "\n\n", "🙏🙏🙏", "\uD83D", "GRACE-", "-7K2Q", "GRACE-7K2",
            "GRACE-7K2QQ", "xGRACE-7K2Q", "GRACE-7K2Qx", "GRACE-0O1I", "self-care", "well-known", "NOTAWORD-7K2Q",
            "<script>alert(1)</script>", "'; DROP TABLE circles;--", "\u0000\u0001\u0002", "\\\\\\", "%00%0A", "GRACÉ-7K2Q"
        ).forEach { assertNull("should not parse: $it", InviteCodes.extract(it)) }
    }

    @Test fun `a huge paste is handled quickly`() {
        val big = "lorem ipsum ".repeat(200_000) + " GRACE-7K2Q"
        val t = System.nanoTime()
        // Only the first 2000 characters are scanned, like the server, so a code at the very end is not found.
        assertNull(InviteCodes.extract(big))
        assertTrue("took too long", (System.nanoTime() - t) / 1_000_000 < 500)
    }

    @Test fun `several codes keep order and drop duplicates`() {
        assertEquals(listOf("HOPE-BBBB", "GRACE-7K2Q"), InviteCodes.extractAll("hope-bbbb? no: GRACE-7K2Q then GRACE-7K2Q"))
        assertEquals(1, InviteCodes.extractAll("GRACE-7K2Q grace-7k2q").size)
        assertEquals(3, InviteCodes.extractAll("GRACE-7K2Q HOPE-BBBB LAMP-2222 SALT-3333", max = 3).size)
    }

    @Test fun `every word and alphabet character is accepted`() {
        InviteCodes.WORDS.forEach { w -> assertEquals("$w-2345", InviteCodes.extract("code: $w-2345!")) }
        assertEquals("GRACE-BCDF", InviteCodes.extract("grace-bcdf"))
    }

    @Test fun `share text always contains the plain code`() {
        val text = InviteCodes.shareText("Tuesday Night", "Cell group", "GRACE-7K2Q")
        assertEquals("GRACE-7K2Q", InviteCodes.extract(text))
        assertTrue(text.contains("Tuesday Night"))
    }
}
