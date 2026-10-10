package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.companion.CreateSource
import com.craftflowtechnologies.meetingmind.core.companion.CreateVibe as PolicyVibe
import com.craftflowtechnologies.meetingmind.core.share.ShareFont
import java.util.UUID

/** Where a Create card's words come from (FAITH_V2 §3). */
enum class CreateSourceKind(val label: String, val faith: Boolean, val verbatim: Boolean, val policy: CreateSource) {
    FREE_PROMPT("Idea", false, false, CreateSource.CUSTOM),
    SELECTED_TEXT("Selected text", false, true, CreateSource.CUSTOM),
    DEVOTIONAL("Devotional", true, false, CreateSource.DEVOTIONAL),
    VERSE("Verse", true, false, CreateSource.SCRIPTURE_VERSE),
    SERMON_NOTE("Sermon note", true, true, CreateSource.SERMON_QUOTE),
    PRAYER("Prayer", true, false, CreateSource.PRAYER_REQUEST),
    TESTIMONY("Testimony", true, false, CreateSource.TESTIMONY),
    ACHIEVEMENT("Achievement", false, false, CreateSource.ACHIEVEMENT);
}

/**
 * The card's vibe: the six companion moods (faith) plus the general ones. A mood vibe always carries
 * the faith contract; the general vibes carry it only when the source is faith (a verse, a sermon).
 */
enum class CreateVibe(val label: String, val mood: CreateMode?, val policyVibe: PolicyVibe?) {
    PRAYERFUL("Prayerful", CreateMode.PRAYERFUL, PolicyVibe.ENCOURAGING),
    PEACEFUL("Peaceful", CreateMode.PEACEFUL, PolicyVibe.ENCOURAGING),
    GRATEFUL("Grateful", CreateMode.GRATEFUL, PolicyVibe.ENCOURAGING),
    JOYFUL("Joyful", CreateMode.JOYFUL, PolicyVibe.CELEBRATION),
    REFLECTIVE("Reflective", CreateMode.REFLECTIVE, PolicyVibe.REFLECTIVE),
    CELEBRATORY("Celebratory", CreateMode.CELEBRATORY, PolicyVibe.CELEBRATION),
    MOTIVATIONAL("Motivational", null, PolicyVibe.MOTIVATIONAL),
    FUNNY("Funny", null, PolicyVibe.FUNNY),
    WISDOM("Wisdom", null, PolicyVibe.WISDOM),
    LOVE("Love", null, PolicyVibe.LOVE),
    CUSTOM("Custom", null, PolicyVibe.CUSTOM);

    val isMood: Boolean get() = mood != null

    companion object {
        fun fromMood(mode: CreateMode): CreateVibe = entries.first { it.mood == mode }
        fun parse(raw: String?): CreateVibe? = raw?.trim()?.let { r -> entries.firstOrNull { it.name.equals(r, true) || it.label.equals(r, true) } }
    }
}

/** The three shareable shapes. Story fits WhatsApp status and Instagram stories. */
enum class CreateFormat(val label: String, val hint: String, val width: Int, val height: Int) {
    STORY("Story", "9:16 · WhatsApp status, Instagram story", 1080, 1920),
    SQUARE("Square", "1:1 · feed posts", 1080, 1080),
    PORTRAIT("Portrait", "4:5 · feed posts", 1080, 1350);

    val ratio: Float get() = width.toFloat() / height
}

/** Curated type pairs: a serif for scripture, Inter or Outfit for the rest. */
enum class CreateFontPair(val label: String, val body: ShareFont, val scripture: ShareFont) {
    CLASSIC("Classic", ShareFont.LORA, ShareFont.LORA),
    EDITORIAL("Editorial", ShareFont.PLAYFAIR, ShareFont.LORA_ITALIC),
    MODERN("Modern", ShareFont.OUTFIT, ShareFont.LORA),
    PLAIN("Plain", ShareFont.INTER, ShareFont.LORA)
}

/** What sits behind the words: a built-in painted pack, or a photo (the person's own, usually). */
sealed interface CreateBackground {
    data class Pack(val id: String) : CreateBackground
    data class Photo(val path: String) : CreateBackground
}

/** How the card looks. Watermark and companion are both off until the person turns them on. */
data class CreateDesign(
    val background: CreateBackground = CreateBackground.Pack("dawn"),
    val fontPair: CreateFontPair = CreateFontPair.CLASSIC,
    val centered: Boolean = true,
    val includeCompanion: Boolean = false,
    val watermark: Boolean = false,
    /** Relative text size, 0.7–1.4. */
    val textScale: Float = 1f
)

/** One state of the words. Remixes and edits each add one; undo steps back. */
data class CardVersion(
    val text: String,
    val scriptureRef: String? = null,
    val label: String = "Original"
)

/** A card being made, or saved in "My creations". Scripture text is never held here, only the reference. */
data class CreateCard(
    val id: String = UUID.randomUUID().toString(),
    val source: CreateSourceKind = CreateSourceKind.FREE_PROMPT,
    /** What the person typed, selected or was seeded with. Bounded. */
    val sourceText: String = "",
    /** The verse reference for a verse source, or a note id for a sermon note. */
    val sourceRef: String? = null,
    val vibe: CreateVibe = CreateVibe.PEACEFUL,
    val current: CardVersion = CardVersion(""),
    /** Older versions, newest last. */
    val past: List<CardVersion> = emptyList(),
    val scriptureVersionId: Int? = null,
    val format: CreateFormat = CreateFormat.STORY,
    val design: CreateDesign = CreateDesign(),
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    val text: String get() = current.text
    val scriptureRef: String? get() = current.scriptureRef
    val canUndo: Boolean get() = past.isNotEmpty()

    /** Pushes [next] as the current version. Identical wording is not a new version. */
    fun withVersion(next: CardVersion): CreateCard =
        if (next.text == current.text && next.scriptureRef == current.scriptureRef) this
        else copy(current = next, past = (past + current).takeLast(MAX_VERSIONS), updatedAt = System.currentTimeMillis())

    fun undo(): CreateCard =
        if (past.isEmpty()) this else copy(current = past.last(), past = past.dropLast(1), updatedAt = System.currentTimeMillis())

    /** Whether this card falls under the faith contract (a mood, or a faith source). */
    val faithContract: Boolean get() = vibe.isMood || source.faith

    companion object { const val MAX_VERSIONS = 20; const val MAX_SOURCE = 2_000 }
}

/** What an entry point hands the studio. Everything is optional: a blank seed opens an empty studio. */
data class CreateSeed(
    val source: CreateSourceKind = CreateSourceKind.FREE_PROMPT,
    val text: String = "",
    val reference: String? = null,
    val vibe: CreateVibe? = null,
    /** The source note is tagged grief, funeral or memorial: Peaceful only. */
    val grief: Boolean = false
)
