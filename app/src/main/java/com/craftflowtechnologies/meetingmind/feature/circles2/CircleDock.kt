package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** The four places inside a Circle. Work's Home is the Overview. */
enum class CircleTab { Home, Updates, Chat, People }

/** Labels for a tab in this kind of circle. */
fun CircleTab.label(isWork: Boolean): String = when (this) {
    CircleTab.Home -> if (isWork) "Overview" else "Home"
    CircleTab.Updates -> "Updates"
    CircleTab.Chat -> "Chat"
    CircleTab.People -> "People"
}

/**
 * The thumb dock (founder decision D1): Home|Overview · Updates · ＋ · Chat · People, within thumb reach.
 * The ＋ starts a post. It is hidden on Chat, where the message bar sits just above the dock.
 */
@Composable
fun CircleDock(
    tab: CircleTab,
    isWork: Boolean,
    chatUnread: Int,
    onTab: (CircleTab) -> Unit,
    onCompose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(color = MM.colors.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MM.colors.lineSoft)
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = MM.space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DockItem(CircleTab.Home, tab, isWork, if (isWork) Icons.Rounded.Dashboard else Icons.Rounded.Home, 0, onTab, Modifier.weight(1f))
                DockItem(CircleTab.Updates, tab, isWork, Icons.Rounded.ViewAgenda, 0, onTab, Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (onCompose != null && tab != CircleTab.Chat) {
                        Surface(
                            onClick = onCompose,
                            shape = CircleShape,
                            color = MM.colors.accent,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = if (isWork) "New post for the team" else "New post" }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, null, tint = MM.colors.onAccent, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
                DockItem(CircleTab.Chat, tab, isWork, Icons.Rounded.ChatBubbleOutline, chatUnread, onTab, Modifier.weight(1f))
                DockItem(CircleTab.People, tab, isWork, Icons.Rounded.Groups, 0, onTab, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DockItem(
    item: CircleTab,
    current: CircleTab,
    isWork: Boolean,
    icon: ImageVector,
    badge: Int,
    onTab: (CircleTab) -> Unit,
    modifier: Modifier
) {
    val selected = item == current
    val pill by animateColorAsState(if (selected) MM.colors.accentWash else Color.Transparent, tween(250), label = "dockPill")
    val tint by animateColorAsState(if (selected) MM.colors.accent else MM.colors.inkSecondary, tween(250), label = "dockTint")
    val label = item.label(isWork)
    val spoken = if (badge > 0) "$label, $badge unread" else label
    Surface(
        onClick = { onTab(item) },
        color = Color.Transparent,
        shape = MM.radius.pill,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Tab
                this.selected = selected
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box {
                Box(Modifier.background(pill, MM.radius.pill).padding(horizontal = 14.dp, vertical = 3.dp)) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                }
                if (badge > 0) {
                    Surface(
                        color = MM.colors.danger,
                        shape = MM.radius.pill,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp)
                    ) {
                        Text(
                            if (badge > 99) "99+" else badge.toString(),
                            style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = MM.colors.onAccent,
                            modifier = Modifier.padding(horizontal = 5.dp)
                        )
                    }
                }
            }
            Text(
                label,
                style = MM.type.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = tint,
                maxLines = 1
            )
        }
    }
}

/**
 * A generated cover: soft concentric rings whose centre and spacing come from the circle's id, so every Circle
 * looks like itself without anyone uploading a photo. One draw, no animation.
 */
@Composable
fun RippleCover(seed: String, modifier: Modifier = Modifier, base: Color = MM.colors.accent) {
    val h = remember(seed) { seed.fold(7) { acc, ch -> acc * 31 + ch.code } }
    val cx = remember(h) { 0.2f + ((h ushr 3) and 0xFF) / 255f * 0.6f }
    val cy = remember(h) { 0.25f + ((h ushr 11) and 0xFF) / 255f * 0.5f }
    val gap = remember(h) { 18f + ((h ushr 19) and 0x1F) }
    val deep = base.copy(alpha = 1f)
    Canvas(modifier) {
        drawRect(Brush.linearGradient(listOf(deep, deep.copy(alpha = 0.78f))))
        val centre = Offset(size.width * cx, size.height * cy)
        val maxR = size.maxDimension * 1.1f
        var r = gap.dp.toPx()
        var i = 0
        while (r < maxR && i < 14) {
            drawCircle(
                color = Color.White.copy(alpha = if (i % 2 == 0) 0.16f else 0.08f),
                radius = r,
                center = centre,
                style = Stroke(width = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx())
            )
            r += gap.dp.toPx() * (1f + i * 0.12f)
            i++
        }
        drawCircle(Color.White.copy(alpha = 0.14f), radius = gap.dp.toPx() * 0.6f, center = centre)
    }
}
