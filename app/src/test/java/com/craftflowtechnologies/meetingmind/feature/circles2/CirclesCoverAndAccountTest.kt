package com.craftflowtechnologies.meetingmind.feature.circles2

import com.craftflowtechnologies.meetingmind.core.circles2.CircleTemplate
import com.craftflowtechnologies.meetingmind.core.circles2.CoverChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CirclesCoverAndAccountTest {
    private val bibleStudy = CircleTemplate.allTemplates.first { it.id == "bible_study" }

    @Test fun `pack ids map to their asset and thumbnail`() {
        assertEquals("backgrounds/bg_206.webp", packAsset("photo_206", thumb = false))
        assertEquals("backgrounds/bg_1056_thumb.webp", packAsset("photo_1056", thumb = true))
    }

    @Test fun `creating saves nothing for the type's own picture, a reference for another, and the photo when chosen`() {
        val base = CreateUiState(template = bibleStudy)
        assertEquals("photo_510", base.shownCoverPack)
        assertNull(coverToSave(base))
        assertNull(coverToSave(base.copy(coverPack = "photo_510")))
        assertEquals(CoverChoice.Pack("photo_206"), coverToSave(base.copy(coverPack = "photo_206")))
        val photo = coverToSave(base.copy(coverPack = "photo_206", coverPhoto = PickedFile(ByteArray(3), "image/jpeg", "a.jpg")))
        assertTrue(photo is CoverChoice.Photo)
    }

    @Test fun `signed out shows Sign in up front, and says how to get earlier circles back`() {
        val fresh = accountCardCopy(linked = false, email = null, earlier = 0, busy = false)
        assertEquals("Sign in to Circles", fresh.title)
        assertEquals("Sign in with Google", fresh.action)
        val back = accountCardCopy(linked = false, email = null, earlier = 2, busy = false)
        assertEquals("Sign in to get your circles back", back.title)
        assertTrue(back.body.startsWith("2 circles from an earlier sign-in aren't showing."))
        assertTrue(accountCardCopy(false, null, 1, false).body.startsWith("A circle from an earlier sign-in isn't showing."))
        assertNull(accountCardCopy(false, null, 0, busy = true).action)
        val linked = accountCardCopy(linked = true, email = "ann@example.com", earlier = 0, busy = false)
        assertEquals("Signed in as ann@example.com", linked.title)
        assertNull(linked.action)
    }
}
