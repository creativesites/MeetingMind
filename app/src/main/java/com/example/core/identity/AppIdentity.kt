package com.example.core.identity

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.model.Workflows

/** How the app looks and speaks (PLAN_V2 F0). One engine; the face changes. */
enum class LookAndFeel(val label: String, val description: String) {
    PROFESSIONAL("Professional", "Clean and focused — ink, indigo, crisp type"),
    SANCTUARY("Sanctuary", "Warm and calm — gold light, serif headings, faith-first"),
    MINIMAL("Minimal", "Quiet — greys, fewer flourishes")
}

/**
 * Who the app is for, as the person told us: which spaces they use and how it should feel.
 * Everything that differs between "a faith app" and "a work app" reads this — nothing branches on
 * a vertical directly (PLAN_V1 §9 rule 2).
 */
data class AppIdentity(
    val spaces: Set<NotebookSpace> = NotebookSpace.entries.toSet(),
    val look: LookAndFeel = LookAndFeel.PROFESSIONAL,
    val displayName: String? = null,
    val avatarPath: String? = null
) {
    val showsFaith: Boolean get() = NotebookSpace.FAITH in spaces
    /** Faith is what this person mostly comes for. */
    val faithFirst: Boolean get() = showsFaith && (look == LookAndFeel.SANCTUARY || spaces == setOf(NotebookSpace.FAITH) || spaces == setOf(NotebookSpace.FAITH, NotebookSpace.PERSONAL))
    val firstName: String? get() = displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
    val initials: String get() = displayName?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }.orEmpty().ifEmpty { "M" }

    /** Whether a workflow belongs to a space this person uses. */
    fun allows(type: RecordingType): Boolean = Workflows.space(type) in spaces

    companion object {
        fun parseSpaces(raw: Set<String>?): Set<NotebookSpace> =
            raw?.mapNotNull { runCatching { NotebookSpace.valueOf(it) }.getOrNull() }?.toSet()?.ifEmpty { null } ?: NotebookSpace.entries.toSet()
    }
}

/** The look's tokens, for screens that adapt to it (Today, Faith, stories, share cards). */
data class AppLook(
    val look: LookAndFeel,
    val accent: Color,
    val accentSoft: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val warm: Color,
    val headingFont: FontFamily
) {
    companion object {
        fun of(look: LookAndFeel): AppLook = when (look) {
            LookAndFeel.PROFESSIONAL -> AppLook(look, Color(0xFF4F46E5), Color(0x144F46E5), Color(0xFF3B5BDB), Color(0xFF111A3A), Color(0xFFF2C94C), FontFamily.SansSerif)
            LookAndFeel.SANCTUARY -> AppLook(look, Color(0xFFB7791F), Color(0x14B7791F), Color(0xFF7A4E2D), Color(0xFF1F1530), Color(0xFFF2C94C), FontFamily.Serif)
            LookAndFeel.MINIMAL -> AppLook(look, Color(0xFF111827), Color(0x10111827), Color(0xFF374151), Color(0xFF0B0F17), Color(0xFFE5E7EB), FontFamily.SansSerif)
        }
    }
}

val LocalAppIdentity = staticCompositionLocalOf { AppIdentity() }
val LocalAppLook = staticCompositionLocalOf { AppLook.of(LookAndFeel.PROFESSIONAL) }
