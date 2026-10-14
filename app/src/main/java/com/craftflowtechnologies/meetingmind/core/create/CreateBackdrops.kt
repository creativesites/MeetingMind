package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.core.share.BackgroundPack

/** Curated backgrounds for each vibe, drawn from the built-in painted pack (offline, no licence to credit). */
object CreateBackdrops {
    private val byVibe: Map<CreateVibe, List<String>> = mapOf(
        CreateVibe.PRAYERFUL to listOf("sanctuary", "night", "dusk", "aurora", "slate", "indigo"),
        CreateVibe.PEACEFUL to listOf("ocean", "forest", "sage", "lavender", "sky", "paper"),
        CreateVibe.GRATEFUL to listOf("golden", "dawn", "forest", "paper", "rose", "sky"),
        CreateVibe.JOYFUL to listOf("golden", "sky", "rose", "dawn", "indigo", "ember"),
        CreateVibe.REFLECTIVE to listOf("dusk", "lavender", "slate", "ocean", "night", "sage"),
        CreateVibe.CELEBRATORY to listOf("ember", "golden", "indigo", "dawn", "aurora", "rose"),
        CreateVibe.MOTIVATIONAL to listOf("slate", "ember", "indigo", "night", "golden", "dusk"),
        CreateVibe.FUNNY to listOf("sky", "rose", "golden", "indigo", "sage", "paper"),
        CreateVibe.WISDOM to listOf("sanctuary", "slate", "paper", "forest", "dusk", "ocean"),
        CreateVibe.LOVE to listOf("rose", "dusk", "dawn", "lavender", "ember", "paper"),
        CreateVibe.CUSTOM to listOf("dawn", "night", "paper", "ocean", "forest", "indigo")
    )

    fun forVibe(vibe: CreateVibe): List<String> = byVibe.getValue(vibe)

    fun default(vibe: CreateVibe): CreateBackground = CreateBackground.Pack(forVibe(vibe).first())

    /** The rest of the pack, after the curated ones. */
    fun more(vibe: CreateVibe): List<String> = BackgroundPack.all.map { it.id } - forVibe(vibe).toSet()
}
