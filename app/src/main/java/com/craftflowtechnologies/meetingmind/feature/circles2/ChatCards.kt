package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.craftflowtechnologies.meetingmind.core.create.CreateCardRenderer
import com.craftflowtechnologies.meetingmind.core.create.CreateChatCard
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.share.ShareHelper
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * A card in chat. Cards made in the Create studio are drawn with the Create renderer from their fields (template,
 * words, verse), so they look the same as on the sender's phone. Anything else, or if drawing fails, is the simple [SharedCard].
 * Tapping opens [CardDetailSheet] to view in full and share.
 */
@Composable
fun ChatCard(card: CardPayload, modifier: Modifier = Modifier) {
    var showDetail by remember { mutableStateOf(false) }
    val input = remember(card) { CreateChatCard.renderInput(card) }
    val context = LocalContext.current
    val drawn: ImageBitmap? by produceState<ImageBitmap?>(null, input) {
        if (input != null) {
            value = withContext(Dispatchers.Default) {
                runCatching { CreateCardRenderer.render(context, input, 0.3f).asImageBitmap() }.getOrNull()
            }
        }
    }
    val image = drawn

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .clickable { showDetail = true },
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        if (image != null) {
            Image(
                image, contentDescription = null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(MM.radius.card).semantics { contentDescription = "Card: " + card.text.take(120) }
            )
            val caption = listOfNotNull(
                CreateChatCard.moodLabel(card),
                card.verseRef.takeIf { card.verseText == null }
            ).joinToString(" · ")
            if (caption.isNotEmpty()) Text(caption, style = MM.type.caption, color = MM.colors.inkSecondary)
        } else {
            SharedCard(card)
        }
    }

    if (showDetail) {
        CardDetailSheet(card = card, image = image, onDismiss = { showDetail = false })
    }
}

/** Bottom sheet to preview and share a card tapped from chat or feed. */
@Composable
fun CardDetailSheet(card: CardPayload, image: ImageBitmap?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val mood = CARD_MOODS.firstOrNull { it.first == card.mood }?.second
    val caption = listOfNotNull(
        mood,
        card.verseRef.takeIf { card.verseText == null }
    ).joinToString(" · ")

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (mood != null) "$mood Card" else "Shared Card",
                style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = MM.colors.ink
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }

        if (image != null) {
            Image(
                image,
                contentDescription = "Card preview",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MM.radius.card)
            )
        } else {
            SharedCard(card)
        }

        if (caption.isNotEmpty()) {
            Text(
                caption,
                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = MM.colors.inkSecondary
            )
        }

        PrimaryButton(
            "Share card",
            {
                val textToShare = buildString {
                    append(card.text)
                    if (!card.verseRef.isNullOrBlank()) {
                        append("\n\n— ")
                        append(card.verseRef)
                    }
                }
                ShareHelper.shareText(context, "Card", textToShare, "Share Card")
            },
            leadingIcon = Icons.Rounded.Share,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(
                "Copy words",
                {
                    clipboard.setText(AnnotatedString(card.text))
                    onDismiss()
                },
                leadingIcon = Icons.Rounded.ContentCopy,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                "Close",
                onDismiss,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
