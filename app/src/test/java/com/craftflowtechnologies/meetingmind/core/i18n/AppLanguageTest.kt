package com.craftflowtechnologies.meetingmind.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `exactly nine supported languages including source English`() {
        assertEquals(9, AppLanguage.SUPPORTED.size)
        assertEquals(AppLanguage.ENGLISH, AppLanguage.DEFAULT)
        val tags = AppLanguage.SUPPORTED.map { it.tag }
        val expectedTags = listOf("en", "fr", "es", "pt", "sw", "ny", "sn", "zu", "bem")
        // Verify all 9 target languages + source are present
        expectedTags.forEach { tag ->
            assertTrue("Expected tag $tag to be present in supported list", tags.contains(tag))
        }
    }

    @Test
    fun `fromTag resolves exact and case insensitive matches`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("FR"))
        assertEquals(AppLanguage.SWAHILI, AppLanguage.fromTag("sw"))
        assertEquals(AppLanguage.BEMBA, AppLanguage.fromTag("bem"))
        assertEquals(AppLanguage.SHONA, AppLanguage.fromTag("sn"))
        assertEquals(AppLanguage.NYANJA, AppLanguage.fromTag("ny"))
        assertEquals(AppLanguage.ZULU, AppLanguage.fromTag("zu"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es"))
    }

    @Test
    fun `fromTag returns null for invalid or blank tags`() {
        assertNull(AppLanguage.fromTag(null))
        assertNull(AppLanguage.fromTag(""))
        assertNull(AppLanguage.fromTag("   "))
        assertNull(AppLanguage.fromTag("de")) // German is not in scope
        assertNull(AppLanguage.fromTag("unknown"))
    }

    @Test
    fun `fromTag resolves regional variants accurately`() {
        assertEquals(AppLanguage.SHONA, AppLanguage.fromTag("sn-ZW"))
        assertEquals(AppLanguage.SHONA, AppLanguage.fromTag("sn_zw"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTag("pt-BR"))
        assertEquals(AppLanguage.SWAHILI, AppLanguage.fromTag("sw-KE"))
        assertEquals(AppLanguage.NYANJA, AppLanguage.fromTag("ny-MW"))
        assertEquals(AppLanguage.ZULU, AppLanguage.fromTag("zu-ZA"))
        assertEquals(AppLanguage.BEMBA, AppLanguage.fromTag("bem-ZM"))
    }

    @Test
    fun `areLocalesEquivalent matches base and regional variants`() {
        assertTrue(AppLocaleManager.areLocalesEquivalent("sn", "sn-ZW"))
        assertTrue(AppLocaleManager.areLocalesEquivalent("sn-ZW", "sn"))
        assertTrue(AppLocaleManager.areLocalesEquivalent("sn", "sn"))
        assertTrue(AppLocaleManager.areLocalesEquivalent(null, null))
        assertTrue(AppLocaleManager.areLocalesEquivalent("", null))
        assertTrue(AppLocaleManager.areLocalesEquivalent("pt-BR", "pt-PT"))
        
        org.junit.Assert.assertFalse(AppLocaleManager.areLocalesEquivalent("sn", "en"))
        org.junit.Assert.assertFalse(AppLocaleManager.areLocalesEquivalent("sn", null))
        org.junit.Assert.assertFalse(AppLocaleManager.areLocalesEquivalent(null, "sn"))
    }

    @Test
    fun `saved language is not applied before preferences load`() {
        // Before DataStore emits, the saved value is unknown (null), not "follow system".
        // The old check (!areLocalesEquivalent(null, "fr")) returned true here and reset the app.
        assertFalse(AppLocaleManager.shouldApplySavedLanguage(prefsLoaded = false, savedTag = null, activeTag = "fr"))
        assertFalse(AppLocaleManager.shouldApplySavedLanguage(prefsLoaded = false, savedTag = "fr", activeTag = null))
    }

    @Test
    fun `saved language is applied once loaded when it differs from the active one`() {
        assertTrue(AppLocaleManager.shouldApplySavedLanguage(prefsLoaded = true, savedTag = "fr", activeTag = null))
        assertTrue(AppLocaleManager.shouldApplySavedLanguage(prefsLoaded = true, savedTag = "sw", activeTag = "fr"))
    }

    @Test
    fun `loaded system default resets a language the app still has active`() {
        assertTrue(AppLocaleManager.shouldApplySavedLanguage(prefsLoaded = true, savedTag = null, activeTag = "fr"))
    }

    @Test
    fun `no apply when the saved and active languages already match`() {
        assertFalse(AppLocaleManager.shouldApplySavedLanguage(prefsLoaded = true, savedTag = "fr", activeTag = "fr"))
        assertFalse(AppLocaleManager.shouldApplySavedLanguage(prefsLoaded = true, savedTag = "sn", activeTag = "sn-ZW"))
        assertFalse(AppLocaleManager.shouldApplySavedLanguage(prefsLoaded = true, savedTag = null, activeTag = null))
    }

    @Test
    fun `every language has non-empty native and english display names`() {
        AppLanguage.SUPPORTED.forEach { lang ->
            assertTrue("${lang.tag} has blank native name", lang.nativeName.isNotBlank())
            assertTrue("${lang.tag} has blank english name", lang.englishName.isNotBlank())
        }
    }
}
