package com.craftflowtechnologies.meetingmind.core.faith

import com.craftflowtechnologies.meetingmind.core.share.BackgroundSpec
import com.craftflowtechnologies.meetingmind.core.share.ShareCardContent
import com.craftflowtechnologies.meetingmind.core.share.ShareFont
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import java.util.UUID

/**
 * The emotional tone of the generated Spark piece.
 */
enum class SparkVibe(
    val label: String,
    val emoji: String,
    val description: String,
    val defaultFont: ShareFont,
    val defaultPackId: String
) {
    DEEP(
        label = "Deep",
        emoji = "🌊",
        description = "Soul-stirring reflection & perspective shifts",
        defaultFont = ShareFont.PLAYFAIR,
        defaultPackId = "sanctuary"
    ),
    FUNNY(
        label = "Funny & Witty",
        emoji = "⚡",
        description = "Clean humor, dry irony & relatable realities",
        defaultFont = ShareFont.OUTFIT,
        defaultPackId = "ember"
    ),
    FIRE(
        label = "Fire & Drive",
        emoji = "🔥",
        description = "High agency, discipline, bold momentum",
        defaultFont = ShareFont.INTER,
        defaultPackId = "slate"
    ),
    REAL(
        label = "Real & Raw",
        emoji = "💎",
        description = "Honest, vulnerability without the fake gloss",
        defaultFont = ShareFont.LORA_ITALIC,
        defaultPackId = "dusk"
    ),
    CALM(
        label = "Peace & Calm",
        emoji = "🌿",
        description = "Decompressing anxiety & resting in quiet trust",
        defaultFont = ShareFont.LORA,
        defaultPackId = "ocean"
    ),
    GRATEFUL(
        label = "Joy & Praise",
        emoji = "✨",
        description = "Celebrating wins, wonder & thankful heart",
        defaultFont = ShareFont.LORA,
        defaultPackId = "golden"
    )
}

/**
 * Format of the Spark piece.
 */
enum class SparkFormat(
    val label: String,
    val badge: String,
    val promptDescription: String
) {
    ONE_LINER(
        label = "One-Liner",
        badge = "Mic-Drop",
        promptDescription = "A single unforgettable, punchy sentence. Max 25 words."
    ),
    PERSPECTIVE_FLIP(
        label = "Perspective Shift",
        badge = "Shift",
        promptDescription = "A contrast starting with what people think vs what is actually true."
    ),
    MINI_STORY(
        label = "Mini-Story",
        badge = "Story",
        promptDescription = "2-4 sentences showing a real moment with an unexpected twist."
    ),
    HOT_TAKE(
        label = "Hot Take",
        badge = "Take",
        promptDescription = "A bold counter-intuitive truth that challenges status quo."
    ),
    VERSE_TWIST(
        label = "Verse + Twist",
        badge = "Scripture",
        promptDescription = "A timeless Scripture verse paired with a sharp, modern application."
    )
}

/**
 * How explicitly faith/Scripture is woven in.
 */
enum class FaithLevel(val label: String, val hint: String) {
    SUBTLE("Subtle", "Grounded in timeless spiritual wisdom without preachy jargon"),
    CLEAR("Clear Faith", "Explicit Scripture and prayerful anchoring"),
    NONE("Pure Life", "Universal human truth, humor, mindset — zero religious framing")
}

/**
 * Quick contextual moments/situations.
 */
enum class SparkMoment(val label: String, val emoji: String, val promptHint: String) {
    MONDAY("Monday Grind", "💼", "tackling Monday morning, high workload, mental focus"),
    LATE_NIGHT("Late Night", "🌙", "late night overthinking, insomnia, quiet thoughts"),
    GYM("Gym & Workout", "🏋️", "physical training, push through fatigue, self-discipline"),
    PRE_MEETING("Big Meeting", "🎯", "walking into an intimidating room, interview, high stakes"),
    WEEKEND("Weekend Reset", "☕", "slowing down, recharging soul, letting work rest"),
    HEARTBREAK("Healing Heart", "❤️‍🩹", "letting go of someone, grief, emotional healing, self-worth"),
    MILESTONE("New Chapter", "🚀", "stepping into unfamiliar territory, leveling up, courage")
}

/**
 * Live remixing actions for a card.
 */
enum class RemixAction(val label: String, val emoji: String, val instruction: String) {
    FUNNIER("Funnier", "😂", "Make it funnier, with witty observation or self-aware irony"),
    DEEPER("Deeper", "🧠", "Make it deeper and more thought-provoking"),
    SHORTER("Punchier", "✂️", "Make it shorter and more punchy (cut unnecessary words)"),
    BOLDER("More Fire", "🔥", "Make it bolder, tougher love, higher urgency"),
    CALMER("Calmer", "🕊️", "Make it calmer, more comforting and peaceful"),
    ADD_VERSE("Add Verse", "📖", "Pair this exact thought with a complementary Bible verse"),
    REMOVE_VERSE("Text Only", "✨", "Strip any Bible verse citation and make it pure standalone text")
}

/**
 * An individual share-worthy piece.
 */
data class SparkPiece(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val verseRef: String? = null,
    val verseText: String? = null,
    val vibe: SparkVibe,
    val format: SparkFormat,
    val history: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Converts to a ShareRequest ready for the 9:16 vertical ShareStudio.
     */
    fun toShareRequest(customBackground: BackgroundSpec? = null): ShareRequest {
        val bg = customBackground ?: BackgroundSpec.Pack(vibe.defaultPackId)
        val fullCaption = buildString {
            append(text)
            if (!verseRef.isNullOrBlank()) {
                append("\n\n— ")
                append(verseRef)
                if (!verseText.isNullOrBlank()) {
                    append("\n“")
                    append(verseText)
                    append("”")
                }
            }
        }

        return ShareRequest(
            content = ShareCardContent(
                eyebrow = vibe.label.uppercase(),
                text = text,
                reference = verseRef,
                attribution = if (verseRef != null) "MeetingMind Spark" else null,
                quoted = format == SparkFormat.ONE_LINER || format == SparkFormat.HOT_TAKE
            ),
            theme = "${vibe.label}, $text",
            background = bg,
            caption = fullCaption
        )
    }

    fun withTextUpdate(newText: String): SparkPiece {
        val updatedHistory = if (newText != text) history + text else history
        return copy(text = newText, history = updatedHistory)
    }

    fun canUndo(): Boolean = history.isNotEmpty()

    fun undo(): SparkPiece {
        if (history.isEmpty()) return this
        val previous = history.last()
        return copy(text = previous, history = history.dropLast(1))
    }
}
