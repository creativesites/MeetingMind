package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.core.circles2.CardPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Create studio card -> the chat payload (template id + text + mood + verse), and back to something the renderer can draw. */
class CreateChatCardTest {
    private val psalm = ResolvedScripture("Psalm 46:10", "Be still, and know that I am God.", 3034, "BSB", "Berean Standard Bible")

    private fun card(text: String, vibe: CreateVibe = CreateVibe.PEACEFUL, bg: CreateBackground = CreateBackground.Pack("ocean"), ref: String? = null) =
        CreateCard(vibe = vibe, current = CardVersion(text, ref), design = CreateDesign(background = bg))

    @Test fun `words, mood and background become the payload`() {
        val p = CreateChatCard.toPayload(card("Rest in Him today."), null)!!
        assertEquals("create:ocean", p.templateId)
        assertEquals("Rest in Him today.", p.text)
        assertEquals("peaceful", p.mood)
        assertNull(p.verseRef); assertNull(p.verseText)
    }

    @Test fun `a verse travels as reference and words`() {
        val p = CreateChatCard.toPayload(card("Rest.", ref = "Psalm 46:10"), psalm)!!
        assertEquals("Psalm 46:10 · BSB", p.verseRef)
        assertEquals("Be still, and know that I am God.", p.verseText)
        assertEquals("Rest.", p.text)
    }

    @Test fun `a verse-only card puts the verse in the main text so it shows once`() {
        val p = CreateChatCard.toPayload(card("", ref = "Psalm 46:10"), psalm)!!
        assertEquals("Be still, and know that I am God.", p.text)
        assertNull(p.verseText)
        assertEquals("Psalm 46:10 · BSB", p.verseRef)
    }

    @Test fun `nothing to send gives no payload`() {
        assertNull(CreateChatCard.toPayload(card("   "), null))
    }

    @Test fun `a photo stays on this phone, so the card is sent on its vibe's painted background`() {
        val p = CreateChatCard.toPayload(card("Hello", vibe = CreateVibe.JOYFUL, bg = CreateBackground.Photo("/data/x.jpg")), null)!!
        assertEquals("create:" + (CreateBackdrops.default(CreateVibe.JOYFUL) as CreateBackground.Pack).id, p.templateId)
    }

    @Test fun `payload text and verse stay within what the rules allow`() {
        val p = CreateChatCard.toPayload(card("x".repeat(900)), psalm)!!
        assertTrue(p.text.length <= 600)
        assertTrue(p.templateId.length <= 40 && (p.mood?.length ?: 0) <= 24 && (p.verseRef?.length ?: 0) <= 64)
    }

    @Test fun `a studio payload can be drawn by the Create renderer`() {
        val p = CreateChatCard.toPayload(card("Rest.", ref = "Psalm 46:10"), psalm)!!
        val input = CreateChatCard.renderInput(p)!!
        assertEquals("Rest.", input.text)
        assertEquals(CreateBackground.Pack("ocean"), input.design.background)
        assertEquals("Psalm 46:10 · BSB", input.verse?.reference)
        assertEquals("Be still, and know that I am God.", input.verse?.text)
        assertEquals(CreateFormat.SQUARE, input.format)
    }

    @Test fun `simple sheet cards and unknown templates keep the simple look`() {
        assertNull(CreateChatCard.renderInput(CardPayload("dawn", "Be still", "calm")))
        assertNull(CreateChatCard.renderInput(CardPayload("create:not-a-pack", "Be still")))
        assertNull(CreateChatCard.renderInput(CardPayload("create:ocean", "  ")))
        assertNotNull(CreateChatCard.renderInput(CardPayload("create:night", "Be still")))
    }

    @Test fun `mood label reads for both new and older cards`() {
        assertEquals("Peaceful", CreateChatCard.moodLabel(CardPayload("create:ocean", "x", "peaceful")))
        assertEquals("Calm", CreateChatCard.moodLabel(CardPayload("dawn", "x", "calm")))
        assertNull(CreateChatCard.moodLabel(CardPayload("dawn", "x")))
    }
}
