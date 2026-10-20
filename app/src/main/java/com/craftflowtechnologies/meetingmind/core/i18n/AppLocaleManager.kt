package com.craftflowtechnologies.meetingmind.core.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Handles per-app language configuration and application across Android versions.
 *
 * - On Android 13+ (API 33+), delegates to the system [LocaleManager].
 * - On Android 7.0–12 (API 24–32), uses [LocaleManagerCompat] and configuration context wrapping.
 */
object AppLocaleManager {

    /**
     * Applies the requested interface language tag (e.g. "fr", "sw", "bem").
     * Passing null or blank resets the app to the device system language.
     */
    fun applyLocale(context: Context, languageTag: String?) {
        val normalizedTag = languageTag?.trim()?.takeIf { it.isNotBlank() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                val localeList = if (normalizedTag != null) {
                    LocaleList.forLanguageTags(normalizedTag)
                } else {
                    LocaleList.getEmptyLocaleList()
                }
                localeManager.applicationLocales = localeList
            }
        }

        // Update default JVM locale so SimpleDateFormat and Formatters pick it up
        val targetLocale = if (normalizedTag != null) {
            Locale.forLanguageTag(normalizedTag)
        } else {
            Locale.getDefault()
        }
        Locale.setDefault(targetLocale)

        // For API < 33 update base resources configuration
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(targetLocale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }

    /**
     * Retrieves the currently active application locale tag, or null if following system default.
     */
    fun getActiveLocaleTag(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                return locales[0].toLanguageTag()
            }
        } else {
            val locales = LocaleManagerCompat.getApplicationLocales(context)
            if (!locales.isEmpty) {
                return locales[0]?.toLanguageTag()
            }
        }
        return null
    }

    /**
     * Wraps a base context with the designated language for API < 33 activity attachment.
     */
    fun wrapContext(base: Context, languageTag: String?): Context {
        if (languageTag.isNullOrBlank()) return base
        val locale = Locale.forLanguageTag(languageTag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
