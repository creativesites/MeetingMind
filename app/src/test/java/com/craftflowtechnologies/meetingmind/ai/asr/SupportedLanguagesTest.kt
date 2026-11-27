package com.craftflowtechnologies.meetingmind.ai.asr

import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLanguagesTest {

    @Test
    fun `priority 1 Zimbabwean and Zambian languages are recognized as African languages`() {
        val priority1 = listOf(
            SupportedLanguages.SHONA,
            SupportedLanguages.NORTH_NDEBELE,
            SupportedLanguages.BEMBA,
            SupportedLanguages.NYANJA,
            SupportedLanguages.TONGA,
            SupportedLanguages.LOZI,
            SupportedLanguages.LUVALE,
            SupportedLanguages.LUNDA,
            SupportedLanguages.KAONDE
        )

        for (lang in priority1) {
            assertTrue("Expected $lang to be recognized as African language", SupportedLanguages.isAfricanLanguage(lang))
            assertEquals(
                "Expected $lang to resolve to Omnilingual ASR CTC 300M",
                ModelCatalog.omnilingualAsr300mInt8.id,
                SupportedLanguages.defaultModelIdForLanguage(lang)
            )
        }
    }

    @Test
    fun `priority 2 Southern African languages are recognized`() {
        val priority2 = listOf(
            SupportedLanguages.ZULU,
            SupportedLanguages.XHOSA,
            SupportedLanguages.AFRIKAANS,
            SupportedLanguages.SESOTHO,
            SupportedLanguages.SETSWANA,
            SupportedLanguages.SWAHILI
        )

        for (lang in priority2) {
            assertTrue("Expected $lang to be recognized as African language", SupportedLanguages.isAfricanLanguage(lang))
            assertEquals(
                "Expected $lang to resolve to Omnilingual ASR CTC 300M",
                ModelCatalog.omnilingualAsr300mInt8.id,
                SupportedLanguages.defaultModelIdForLanguage(lang)
            )
        }
    }

    @Test
    fun `English and European languages resolve to Parakeet TDT V3`() {
        assertFalse(SupportedLanguages.isAfricanLanguage(SupportedLanguages.ENGLISH))
        assertEquals(
            ModelCatalog.parakeetTdtV3Int8.id,
            SupportedLanguages.defaultModelIdForLanguage(SupportedLanguages.ENGLISH)
        )
        assertEquals(
            ModelCatalog.parakeetTdtV3Int8.id,
            SupportedLanguages.defaultModelIdForLanguage(SupportedLanguages.SPANISH)
        )
        assertEquals(
            ModelCatalog.parakeetTdtV3Int8.id,
            SupportedLanguages.defaultModelIdForLanguage(SupportedLanguages.FRENCH)
        )
    }

    @Test
    fun `Asian and Arabic languages resolve to Whisper Turbo`() {
        val whisperLangs = listOf(
            SupportedLanguages.JAPANESE,
            SupportedLanguages.KOREAN,
            SupportedLanguages.MANDARIN,
            SupportedLanguages.HINDI,
            SupportedLanguages.ARABIC,
            SupportedLanguages.INDONESIAN,
            SupportedLanguages.VIETNAMESE
        )
        for (lang in whisperLangs) {
            assertTrue("Expected $lang to be Whisper preferred", SupportedLanguages.isWhisperPreferredLanguage(lang))
            assertEquals(
                "Expected $lang to resolve to Whisper Turbo",
                ModelCatalog.whisperTurboInt8.id,
                SupportedLanguages.defaultModelIdForLanguage(lang)
            )
        }
    }

    @Test
    fun `displayName returns clear local names for UI display`() {
        assertEquals("Shona (chiShona)", SupportedLanguages.displayName("sna"))
        assertEquals("Ndebele (isiNdebele)", SupportedLanguages.displayName("nde"))
        assertEquals("Bemba (iciBemba)", SupportedLanguages.displayName("bem"))
        assertEquals("Nyanja / Chichewa", SupportedLanguages.displayName("nya"))
        assertEquals("English", SupportedLanguages.displayName("en"))
        assertEquals("Spanish (Español)", SupportedLanguages.displayName("es"))
        assertEquals("French (Français)", SupportedLanguages.displayName("fr"))
        assertEquals("Japanese (日本語)", SupportedLanguages.displayName("ja"))
        assertEquals("Arabic (العربية)", SupportedLanguages.displayName("ar"))
    }
}
