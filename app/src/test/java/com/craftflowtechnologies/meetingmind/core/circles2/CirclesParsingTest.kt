package com.craftflowtechnologies.meetingmind.core.circles2

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CirclesParsingTest {
    @Test fun `an anonymous post never shows an author even if the document carries one`() {
        val p = CirclesParsing.post("p1", mapOf("type" to "prayer", "body" to "pray", "anonymous" to true, "authorUid" to "leak", "authorName" to "Leak", "createdAt" to Timestamp(Date(5))))
        assertNull(p?.authorUid); assertNull(p?.authorName); assertTrue(p?.anonymous == true)
        assertEquals(5L, p?.createdAt)
    }

    @Test fun `a named post keeps its author`() {
        val p = CirclesParsing.post("p1", mapOf("type" to "testimony", "body" to "x", "authorUid" to "u1", "authorName" to "Ann"))
        assertEquals("Ann", p?.authorName); assertEquals("u1", p?.authorUid)
    }

    @Test fun `malformed documents become null or defaults and never throw`() {
        assertNull(CirclesParsing.post("p", mapOf("type" to "banana")))
        assertNull(CirclesParsing.post("p", mapOf("body" to 7)))
        assertNull(CirclesParsing.circle("c", null))
        assertNull(CirclesParsing.circle("c", mapOf("name" to "x", "closed" to true)))
        assertNull(CirclesParsing.circle("c", mapOf("name" to 5)))
        assertNull(CirclesParsing.message("m", mapOf("text" to "no author")))
        assertNull(CirclesParsing.comment("c", mapOf("body" to "no author")))
        assertNull(CirclesParsing.poll("p", mapOf("question" to "q", "options" to "nope")))
        assertNull(CirclesParsing.slot("x", mapOf("authorUid" to "u")))
        val c = CirclesParsing.circle("c", mapOf("name" to "Cell", "settings" to "garbage", "memberCount" to "NaN"))
        assertEquals(CircleTemplate.byId(null).types, c?.settings?.allowedTypes)
        assertEquals(0, c?.memberCount)
        val m = CirclesParsing.message("m", mapOf("authorUid" to "u", "kind" to "from-the-future", "createdAt" to "yesterday"))
        assertEquals(MessageKind.Unknown, m?.kind); assertEquals(0L, m?.createdAt)
    }

    @Test fun `counts clamp and reactions map`() {
        val p = CirclesParsing.post("p", mapOf("type" to "prayer", "counts" to mapOf("prayed" to -3, "comments" to 2L, "reactions" to mapOf("amen" to 4, "heart" to 0, "weird" to 9))))
        assertEquals(0, p?.counts?.prayed); assertEquals(2, p?.counts?.comments)
        assertEquals(mapOf(ReactionKind.Amen to 4), p?.counts?.reactions)
    }

    @Test fun `answered and deleted flags`() {
        val p = CirclesParsing.post("p", mapOf("type" to "prayer", "status" to "answered", "deleted" to true))
        assertTrue(p?.answered == true); assertTrue(p?.deleted == true)
    }

    @Test fun `card messages parse`() {
        val m = CirclesParsing.message("m", mapOf("authorUid" to "u", "kind" to "card", "text" to "", "card" to mapOf("templateId" to "gold", "text" to "Be still", "mood" to "calm")))
        assertEquals(CardPayload("gold", "Be still", "calm"), m?.card)
    }

    @Test fun `poll results count distinct voters and fractions`() {
        val poll = Poll("p", "Night?", listOf(PollOption("o0", "Tue"), PollOption("o1", "Thu")), multi = false, closed = false, createdBy = "u")
        val votes = CirclesParsing.votes(listOf("a" to mapOf("choices" to listOf("o0", "o0")), "b" to mapOf("choices" to listOf("o1")), "c" to mapOf("choices" to emptyList<String>()), "me" to mapOf("choices" to listOf("o0"))))
        val s = PollState(poll, votes, "me")
        assertEquals(3, s.voters); assertEquals(2, s.count("o0")); assertEquals(2f / 3f, s.fraction("o0"), 0.001f)
        assertEquals(listOf("o0"), s.mine)
        assertEquals(0f, PollState(poll, emptyMap(), "me").fraction("o0"), 0f)
    }

    @Test fun `chain progress counts distinct hours`() {
        val chain = Chain("ch", "For Sam", null, startsAt = 0L, endsAt = 24 * 3_600_000L, hours = 24, createdByName = "Ann")
        val s = ChainState(chain, listOf(Slot(1, "a", "A"), Slot(1, "b", "B"), Slot(5, "me", "Me")), "me", now = 7 * 3_600_000L)
        assertEquals(2, s.claimed); assertEquals(2f / 24f, s.progress, 0.001f)
        assertFalse(s.ended); assertEquals(7, s.currentHour)
        assertTrue(ChainState(chain, emptyList(), "me", now = 25 * 3_600_000L).ended)
    }

    @Test fun `reaction chips are grouped, ordered and mark mine`() {
        val chips = CirclesParsing.reactionChips(mapOf("a" to "❤️", "b" to "🙏", "me" to "🙏"), "me")
        assertEquals(listOf("🙏", "❤️"), chips.map { it.emoji })
        assertEquals(2, chips[0].count); assertTrue(chips[0].mine); assertFalse(chips[1].mine)
    }

    @Test fun `templates match the spec`() {
        assertEquals(7, CircleTemplate.all.size)
        assertEquals(listOf("Cell group", "Small group", "Home fellowship", "Life group"), CircleTemplate.byId("small_group").vocab)
        assertEquals(PostType.entries.toList(), CircleTemplate.byId("youth").types)
        assertEquals("custom", CircleTemplate.byId("unknown").id)
    }
}
