package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.Briefing
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.WorkShape

/**
 * The Briefing card: a deep ink gradient (Briefing.Top to Briefing.Bottom) with a soft lavender
 * glow in the top corner, radius 26 dp, and light content. It is always dark, in both themes, by
 * design. Work "Your day" and Pulse both use it, with different content.
 *
 * Content is light text on the gradient ([BriefingLabel], [BriefingButton], [BriefingChip]).
 */
@Composable
fun BriefingCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(WorkShape.brief)
            .background(Brush.linearGradient(listOf(Briefing.Top, Briefing.Bottom)))
            .drawBehind { glow() }
    ) {
        CompositionLocalProvider(LocalContentColor provides FixedWhite) {
            Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
        }
    }
}

/** The soft lavender glow in the top-right corner of [BriefingCard]. */
private fun DrawScope.glow() {
    val radius = GlowRadius.toPx()
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Briefing.Lavender.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(size.width - GlowInset.toPx(), GlowInset.toPx()),
            radius = radius
        )
    )
}

private val GlowRadius: Dp = 120.dp
private val GlowInset: Dp = 50.dp

/** An overline in Lavender, above a Briefing hero or section. */
@Composable
fun BriefingLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, style = MM.type.overline, color = Briefing.Lavender)
}

/**
 * A pill button on the Briefing card. [primary] is white with ink text; the secondary is a glass
 * fill with white text. The visible pill is 38 dp; the touch target is at least 48 dp.
 */
@Composable
fun BriefingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true
) {
    Surface(
        onClick = onClick,
        shape = MM.radius.pill,
        color = if (primary) FixedWhite else FixedWhite.copy(alpha = 0.12f),
        modifier = modifier.defaultMinSize(minHeight = MMSize.minTouch)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = text,
                style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                color = if (primary) Briefing.Top else FixedWhite
            )
        }
    }
}

/**
 * A small glass chip on the Briefing card, for example a count or a "Prepare" shortcut. Clickable
 * when [onClick] is set, in which case its touch target is at least 48 dp.
 */
@Composable
fun BriefingChip(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier.then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick ?: {},
            enabled = onClick != null,
            shape = MM.radius.pill,
            color = FixedWhite.copy(alpha = 0.12f),
            modifier = Modifier.height(28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 11.dp)
            ) {
                Text(
                    text = text,
                    style = MM.type.caption,
                    color = FixedWhite
                )
            }
        }
    }
}
