package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.core.circles2.CardPayload
import com.craftflowtechnologies.meetingmind.core.share.BackgroundPack

/**
 * Create studio card <-> chat card. A card in chat travels as a template id + words + mood (+ verse), never as an
 * image, so every phone draws it from the same renderer (FAITH_V2 §2.2b). The template id is `create:<background pack id>`;
 * older simple-sheet cards use `dawn`, `stone`, `gold`, `night` and keep their simple look.
 */
object CreateChatCard {
    const val PREFIX = "create:"
    const val MAX_TEXT = 600
    const val MAX_VERSE_REF = 64

    /** The payload for a finished studio card, or null when it has no words and no verse (nothing to send). */
    fun toPayload(card: CreateCard, scripture: ResolvedScripture?): CardPayload? {
        val words = card.text.trim()
        val verseWords = scripture?.text?.trim()?.trim('“', '”', '"')?.takeIf { it.isNotEmpty() }
        val text = words.ifEmpty { verseWords.orEmpty() }.take(MAX_TEXT)
        if (text.isEmpty()) return null
        // A card with only a verse puts the verse in the main text, so it is not shown twice.
        val showVerse = words.isNotEmpty() && verseWords != null && scripture != null
        val verseRef = scripture?.let { s -> if (s.versionAbbreviation.isBlank()) s.reference else "${s.reference} · ${s.versionAbbreviation}" }
            ?: card.scriptureRef
        return CardPayload(
            templateId = PREFIX + backgroundId(card),
            text = text,
            mood = card.vibe.name.lowercase(),
            verseRef = verseRef?.take(MAX_VERSE_REF)?.takeIf { it.isNotBlank() },
            verseText = if (showVerse) verseWords?.take(MAX_TEXT) else null
        )
    }

    /** Photos stay on the sender's phone, so a photo card is sent on its vibe's default painted background. */
    private fun backgroundId(card: CreateCard): String = when (val b = card.design.background) {
        is CreateBackground.Pack -> b.id.takeIf { id -> BackgroundPack.all.any { it.id == id } }
        is CreateBackground.Photo -> null
    } ?: (CreateBackdrops.default(card.vibe) as CreateBackground.Pack).id

    /** What to draw for [payload] with the Create renderer, or null when it isn't a studio card (draw the simple card instead). */
    fun renderInput(payload: CardPayload): CreateRenderInput? {
        val id = payload.templateId.removePrefix(PREFIX).takeIf { payload.templateId.startsWith(PREFIX) } ?: return null
        if (BackgroundPack.all.none { it.id == id } || payload.text.isBlank()) return null
        val verse = payload.verseText?.takeIf { it.isNotBlank() }?.let {
            ResolvedScripture(reference = payload.verseRef.orEmpty(), text = it, versionId = 0, versionAbbreviation = "", attribution = "")
        }
        return CreateRenderInput(
            text = payload.text, verse = verse, format = CreateFormat.SQUARE,
            design = CreateDesign(background = CreateBackground.Pack(id))
        )
    }

    /** "Prayerful", "Peaceful"… for the line under the card. Older cards store a short mood id too. */
    fun moodLabel(payload: CardPayload): String? {
        val m = payload.mood ?: return null
        return CreateVibe.parse(m)?.label ?: m.replaceFirstChar { it.uppercase() }.takeIf { it.length <= 24 }
    }
}
