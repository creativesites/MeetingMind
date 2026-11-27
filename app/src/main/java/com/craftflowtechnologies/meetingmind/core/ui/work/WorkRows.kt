package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** The colour family of an [AttentionRow]: what kind of attention it asks for. */
enum class AttentionTone { ACCENT, WARNING, DANGER, NEUTRAL }

/**
 * One item in Pulse's "Needs you" list: a leading icon in a tinted circle, a kind label, the title
 * and subtitle, and one verb on the right. Tapping the row opens the item; tapping the verb does
 * the action. Each target is at least 48 dp.
 */
@Composable
fun AttentionRow(
    kindLabel: String,
    title: String,
    subtitle: String,
    verb: String,
    onVerb: () -> Unit,
    onClick: () -> Unit,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    tone: AttentionTone = AttentionTone.ACCENT
) {
    val colors = MM.colors
    val toneColor = when (tone) {
        AttentionTone.ACCENT -> colors.accent
        AttentionTone.WARNING -> colors.warning
        AttentionTone.DANGER -> colors.danger
        AttentionTone.NEUTRAL -> colors.inkSecondary
    }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MM.space.l, vertical = MM.space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(MM.radius.pill)
                .background(toneColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = toneColor,
                modifier = Modifier.size(MMSize.iconSmall)
            )
        }
        Spacer(Modifier.width(MM.space.m))
        Column(Modifier.weight(1f)) {
            Text(text = kindLabel, style = MM.type.overline, color = toneColor)
            Text(
                text = title,
                style = MM.type.bodyStrong,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MM.type.secondary,
                color = colors.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(MM.space.s))
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .clickable(onClick = onVerb),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = verb,
                style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent
            )
        }
    }
}

/**
 * Pulse's header card: a key and value on each line, such as "You owe", "They owe", "Open",
 * "Decided" and "Next". The key is muted and fixed-width; the value takes the rest.
 *
 * @param rows each key with the composable that draws its value.
 */
@Composable
fun PulseHeaderCard(
    rows: List<Pair<String, @Composable () -> Unit>>,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    MMCard(modifier = modifier, contentPadding = MM.space.m) {
        rows.forEachIndexed { index, (key, value) ->
            if (index > 0) {
                HorizontalDivider(color = colors.lineSoft, thickness = MMSize.hairline)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = MM.space.s),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = key,
                    style = MM.type.caption,
                    color = colors.inkMuted,
                    modifier = Modifier.width(92.dp)
                )
                Box(Modifier.weight(1f)) { value() }
            }
        }
    }
}

/**
 * A before and after sentence: the old value struck through, an arrow, and the new value in a
 * wash. [label] is the caption above it, such as "Credentials" or "Since yesterday".
 */
@Composable
fun ChangeLine(label: String, old: String, new: String, modifier: Modifier = Modifier) {
    val colors = MM.colors
    Column(modifier) {
        Text(text = label, style = MM.type.caption, color = colors.inkMuted)
        Spacer(Modifier.size(MM.space.xs))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Text(
                text = old,
                style = MM.type.secondary,
                color = colors.inkMuted,
                textDecoration = TextDecoration.LineThrough
            )
            Text(text = "→", style = MM.type.secondary, color = colors.inkMuted)
            Box(
                Modifier
                    .clip(MM.radius.small)
                    .background(colors.accentWash)
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    text = new,
                    style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink
                )
            }
        }
    }
}
