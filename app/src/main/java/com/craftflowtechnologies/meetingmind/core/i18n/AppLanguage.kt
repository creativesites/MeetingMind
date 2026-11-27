package com.craftflowtechnologies.meetingmind.core.i18n

/**
 * Supported UI interface languages for MeetingMind.
 *
 * NOTE: UI language is strictly independent of speech recognition (ASR) language,
 * AI prompt response language, and transcript translation language.
 */
enum class AppLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String
) {
    ENGLISH("en", "English", "English (Source)"),
    FRENCH("fr", "Français", "French"),
    SPANISH("es", "Español", "Spanish"),
    PORTUGUESE("pt", "Português", "Portuguese"),
    SWAHILI("sw", "Kiswahili", "Swahili"),
    NYANJA("ny", "Chichewa / Chinyanja", "Nyanja / Chichewa"),
    SHONA("sn", "ChiShona", "Shona"),
    ZULU("zu", "isiZulu", "Zulu"),
    BEMBA("bem", "Ichibemba / Chibemba", "Bemba / Chibemba");

    companion object {
        /** Fallback and source language */
        val DEFAULT = ENGLISH

        /** All supported languages in display order */
        val SUPPORTED = entries.toList()

        /** Find language by BCP-47 tag (including regional variants like sn-ZW, pt-BR), or null if unknown or system default */
        fun fromTag(tag: String?): AppLanguage? {
            if (tag.isNullOrBlank()) return null
            val normalized = tag.lowercase().trim().replace('_', '-')
            return entries.firstOrNull { it.tag.lowercase() == normalized }
                ?: entries.firstOrNull { normalized.startsWith(it.tag.lowercase() + "-") }
                ?: runCatching {
                    val langCode = java.util.Locale.forLanguageTag(normalized).language.lowercase()
                    entries.firstOrNull { it.tag.lowercase() == langCode }
                }.getOrNull()
        }
    }
}
