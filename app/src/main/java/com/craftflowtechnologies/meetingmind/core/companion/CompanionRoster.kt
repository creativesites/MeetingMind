package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.BuildConfig

/**
 * Which forms ship (§10.7). The founder's switch is `COMPANION_FORMS` in `app/build.gradle.kts`
 * (currently all four: "ZURI,NAS,WREN,PAGE"). Order is display order.
 */
object CompanionRoster {

    val enabled: List<CompanionForm> = parse(BuildConfig.COMPANION_FORMS)

    /** Parses a comma-separated roster; unknown names are skipped, and an empty result falls back to Zuri. */
    fun parse(csv: String): List<CompanionForm> =
        csv.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            .mapNotNull { name -> CompanionForm.entries.firstOrNull { it.name == name } }
            .distinct()
            .ifEmpty { listOf(CompanionForm.ZURI) }

    /**
     * The form to draw for a stored choice. Null stays null ("No companion"); a form that is no
     * longer in the roster falls back to Zuri with no prompt.
     */
    fun resolve(stored: CompanionForm?, roster: List<CompanionForm> = enabled): CompanionForm? = when {
        stored == null -> null
        stored in roster -> stored
        else -> CompanionForm.ZURI
    }

    /**
     * The form's proper name, used until the companion strings land (Z-18 adds `form_*` string
     * resources; these are names, not copy).
     */
    fun defaultName(form: CompanionForm): String = when (form) {
        CompanionForm.ZURI -> "Zuri"
        CompanionForm.NAS -> "Nas"
        CompanionForm.WREN -> "Wren"
        CompanionForm.PAGE -> "Page"
    }
}
