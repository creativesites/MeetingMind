package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.craftflowtechnologies.meetingmind.core.circles2.CardPayload
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * Create cards shared into chat travel as a template id + text + mood (no image upload) and are drawn
 * here, on each phone, from design tokens. This is the simple in-chat look; the full Create studio
 * (core/share) renders bitmaps for the share sheet and is not needed for chat.
 */
enum class ChatCardTemplate(val id: String, val label: String) {
    Dawn("dawn", "Dawn"), Stone("stone", "Stone"), Gold("gold", "Gold"), Night("night", "Night");

    companion object { fun from(id: String?): ChatCardTemplate = entries.firstOrNull { it.id == id } ?: Dawn }
}

val CARD_MOODS: List<Pair<String, String>> = listOf(
    "calm" to "Calm", "joyful" to "Joyful", "grateful" to "Grateful", "prayerful" to "Prayerful", "funny" to "Funny"
)

@Composable
fun SharedCard(card: CardPayload, modifier: Modifier = Modifier) {
    val c = MM.colors
    val t = ChatCardTemplate.from(card.templateId)
    val (fill, ink) = when (t) {
        ChatCardTemplate.Dawn -> c.accentWash to c.ink
        ChatCardTemplate.Stone -> c.surfaceSunk to c.ink
        ChatCardTemplate.Gold -> c.goldWash to c.goldInk
        ChatCardTemplate.Night -> c.ink to c.onInk
    }
    val mood = CARD_MOODS.firstOrNull { it.first == card.mood }?.second
    Surface(modifier.fillMaxWidth(), shape = MM.radius.card, color = fill, border = if (t == ChatCardTemplate.Stone) BorderStroke(MMSize.hairline, c.line) else null) {
        Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            SelectionContainer {
                Text(card.text, style = MM.type.scripture, color = ink, textAlign = TextAlign.Start)
            }
            if (mood != null) Text(mood, style = MM.type.caption, color = ink.copy(alpha = 0.8f))
        }
    }
}
