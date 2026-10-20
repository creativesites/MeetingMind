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

    private const val PREFS_NAME = "mm_locale_prefs"
    private const val KEY_LOCALE_TAG = "selected_locale_tag"

    /**
     * Checks whether two language tags are functionally equivalent (e.g. "sn" and "sn-ZW", or both null/blank).
     */
    fun areLocalesEquivalent(tag1: String?, tag2: String?): Boolean {
        val t1 = tag1?.trim()?.replace('_', '-')?.takeIf { it.isNotBlank() }
        val t2 = tag2?.trim()?.replace('_', '-')?.takeIf { it.isNotBlank() }
        if (t1 == null && t2 == null) return true
        if (t1 == null || t2 == null) return false
        if (t1.equals(t2, ignoreCase = true)) return true

        return runCatching {
            val lang1 = Locale.forLanguageTag(t1).language
            val lang2 = Locale.forLanguageTag(t2).language
            lang1.isNotBlank() && lang1.equals(lang2, ignoreCase = true)
        }.getOrDefault(false)
    }

    /**
     * Applies the requested interface language tag (e.g. "fr", "sw", "bem", "sn").
     * Passing null or blank resets the app to the device system language.
     *
     * Protected against redundant execution: if the requested locale is already active,
     * this returns immediately without mutating system state or triggering activity recreation.
     */
    fun applyLocale(context: Context, languageTag: String?) {
        val normalizedTag = languageTag?.trim()?.takeIf { it.isNotBlank() }

        // Guard against duplicate execution: setting applicationLocales triggers activity recreation on API 33+.
        val currentTag = getActiveLocaleTag(context)
        if (areLocalesEquivalent(normalizedTag, currentTag)) {
            return
        }

        // Persist synchronously to SharedPreferences for fast sync retrieval during attachBaseContext
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .apply {
                    if (normalizedTag != null) {
                        putString(KEY_LOCALE_TAG, normalizedTag)
                    } else {
                        remove(KEY_LOCALE_TAG)
                    }
                }
                .apply()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                val targetLocales = if (normalizedTag != null) {
                    LocaleList.forLanguageTags(normalizedTag)
                } else {
                    LocaleList.getEmptyLocaleList()
                }
                // Avoid redundant setApplicationLocales calls
                val existingLocales = localeManager.applicationLocales
                val existingTag = if (existingLocales.isEmpty) null else existingLocales[0].toLanguageTag()
                if (!areLocalesEquivalent(normalizedTag, existingTag)) {
                    localeManager.applicationLocales = targetLocales
                }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val res = context.resources
            val config = Configuration(res.configuration)
            config.setLocale(targetLocale)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
        }
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
            val savedTag = runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_LOCALE_TAG, null)
            }.getOrNull()
            if (!savedTag.isNullOrBlank()) {
                return savedTag
            }
            val locales = LocaleManagerCompat.getApplicationLocales(context)
            if (!locales.isEmpty) {
                return locales[0]?.toLanguageTag()
            }
        }
        return null
    }

    /**
     * Wraps a base context with the designated language for API < 33 activity attachment.
     * On API 33+, Android's LocaleManager handles configuration natively before attachBaseContext.
     */
    fun wrapContext(base: Context, languageTag: String?): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val tag = languageTag ?: getActiveLocaleTag(base)
        if (tag.isNullOrBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
