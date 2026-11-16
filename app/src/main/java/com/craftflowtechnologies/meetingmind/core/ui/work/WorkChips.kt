package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** How sure the app is of a piece of AI output, shown as [TrustMark]. */
enum class TrustKind { CONFIRMED, SUGGESTED, EDITED, TYPED, UNCERTAIN }

/**
 * A play chip that points at the moment a statement came from: a play glyph and a timestamp or
 * page, in an accent wash. The visible chip is 22 dp; the touch target is at least 48 dp.
 *
 * @param label the timestamp or page, for example "10:42" or "p. 4".
 */
@Composable
fun SourceChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MM.colors
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(percent = 50),
            color = colors.accentWash,
            modifier = Modifier
                .height(22.dp)
                .semantics { contentDescription = "Play source, $label" }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 5.dp, end = 8.dp)) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A numbered citation, such as the "1" after a sentence in a Brief or an Ask answer. The circle is
 * 20 dp; the touch target is at least 48 dp.
 */
@Composable
fun CiteChip(n: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MM.colors
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = MM.radius.pill,
            color = colors.accentWash,
            modifier = Modifier
                .size(20.dp)
                .semantics { contentDescription = "Source $n" }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$n",
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent
                )
            }
        }
    }
}

/**
 * The trust state of a row or paragraph: a coloured dot and a caption. [TrustKind.SUGGESTED] is a
 * hollow ring because it has not been reviewed yet.
 */
@Composable
fun TrustMark(kind: TrustKind, modifier: Modifier = Modifier) {
    val colors = MM.colors
    val (label, dot, text) = when (kind) {
        TrustKind.CONFIRMED -> Triple("Confirmed", colors.success, colors.inkMuted)
        TrustKind.SUGGESTED -> Triple("Suggested · not reviewed", colors.accent, colors.accent)
        TrustKind.EDITED -> Triple("Edited by you", colors.inkSecondary, colors.inkMuted)
        TrustKind.TYPED -> Triple("Typed by you", colors.inkSecondary, colors.inkMuted)
        TrustKind.UNCERTAIN -> Triple("Uncertain match", colors.warning, colors.warning)
    }
    val dotModifier = if (kind == TrustKind.SUGGESTED) {
        Modifier.border(1.5f.dp, dot, MM.radius.pill)
    } else {
        Modifier.background(dot, MM.radius.pill)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(MM.radius.pill).then(dotModifier))
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MM.type.caption, color = text)
    }
}

/**
 * Where an AI output was produced: on this phone, or in the MeetingMind cloud. A small pill, so
 * people can see when something left the device.
 */
@Composable
fun WhereItRan(onDevice: Boolean, modifier: Modifier = Modifier) {
    val colors = MM.colors
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = colors.surfaceSunk,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(Modifier.size(6.dp).clip(MM.radius.pill).background(if (onDevice) colors.success else colors.accent))
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (onDevice) "On this phone" else "MeetingMind cloud",
                style = MM.type.caption,
                color = colors.inkSecondary
            )
        }
    }
}
