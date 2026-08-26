package com.example.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the exact hex values [SpeakerColors] must keep matching ui.theme's
 * Accent/Speaker2/Speaker3/Speaker4 Compose colors — see
 * feature.meetingdetail.components.SpeakerPalette, which is the Compose-side half of this
 * correspondence. If either list changes without the other, speaker colors silently diverge
 * between the persisted identity and what's rendered.
 */
class SpeakerColorsTest {

    @Test
    fun `palette matches the four theme speaker colors in order`() {
        assertEquals(listOf("#6366F1", "#A855F7", "#10B981", "#F59E0B"), SpeakerColors.PALETTE_HEX)
    }

    @Test
    fun `forIndex cycles through the palette`() {
        assertEquals("#6366F1", SpeakerColors.forIndex(0))
        assertEquals("#A855F7", SpeakerColors.forIndex(1))
        assertEquals("#10B981", SpeakerColors.forIndex(2))
        assertEquals("#F59E0B", SpeakerColors.forIndex(3))
        assertEquals("#6366F1", SpeakerColors.forIndex(4))
    }

    @Test
    fun `forIndex never throws for a negative index`() {
        // mod() (not %) so a stray -1 from an unfound-index lookup elsewhere in the app can never
        // crash this — it just wraps to a valid palette entry.
        assertEquals("#F59E0B", SpeakerColors.forIndex(-1))
    }
}
