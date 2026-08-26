package com.example.core.model

/**
 * The single persisted speaker-colour palette (Phase 15 §2 audit — this file replaces three
 * divergent sources: the pipeline's old ad-hoc hex list, and two Compose call sites that
 * hardcoded `Accent` for every speaker regardless of identity). Hex values match ui.theme's
 * Accent/Speaker2/Speaker3/Speaker4 Compose colors exactly. This file has no Compose dependency
 * — it's used from the non-UI pipeline when persisting [com.example.core.database.SpeakerEntity]
 * — so the correspondence is kept in sync by hand; see SpeakerColorsTest for the pinned values
 * and ui.theme.Color.kt for the Compose-side definitions this must keep matching.
 */
object SpeakerColors {
    val PALETTE_HEX = listOf("#6366F1", "#A855F7", "#10B981", "#F59E0B")

    /** Non-negative modulo indexing so this never throws for any speakerIndex. */
    fun forIndex(speakerIndex: Int): String = PALETTE_HEX[speakerIndex.mod(PALETTE_HEX.size)]
}
