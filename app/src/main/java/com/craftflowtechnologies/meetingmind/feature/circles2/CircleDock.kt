package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
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
 * It is the app's own bottom bar, inside a circle: a floating pill on the surface with one soft shadow, the current
 * place as an accent-wash chip with its label, the others as icons, and the ＋ as the one accent action. It clears the
 * navigation bar (3-button or gesture) itself. The ＋ starts a post and is hidden on Chat, where the message bar sits
 * above the dock.
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
    Box(
        modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = MM.space.l)
            .padding(top = MM.space.xs, bottom = MM.space.s),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MM.radius.pill,
            color = MM.colors.surfaceRaised,
            border = BorderStroke(MMSize.hairline, MM.colors.line),
            shadowElevation = MM.elevation.floating,
            modifier = Modifier.wrapContentWidth()
        ) {
            Row(
                Modifier.padding(MM.space.xs + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DockItem(CircleTab.Home, tab, isWork, if (isWork) Icons.Rounded.Dashboard else Icons.Rounded.Home, 0, onTab)
                DockItem(CircleTab.Updates, tab, isWork, Icons.Rounded.ViewAgenda, 0, onTab)
                if (onCompose != null && tab != CircleTab.Chat) {
                    Surface(
                        onClick = onCompose,
                        shape = MM.radius.pill,
                        color = MM.colors.accent,
                        modifier = Modifier.height(DockHeight).semantics { contentDescription = if (isWork) "New post for the team" else "New post" }
                    ) {
                        Box(Modifier.padding(horizontal = MM.space.m), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = MM.colors.onAccent, modifier = Modifier.size(MMSize.iconSmall))
                        }
                    }
                }
                DockItem(CircleTab.Chat, tab, isWork, Icons.Rounded.ChatBubbleOutline, chatUnread, onTab)
                DockItem(CircleTab.People, tab, isWork, Icons.Rounded.Groups, 0, onTab)
            }
        }
    }
}

/** The dock's item height: 44 dp inside the bar, full-width 48 dp hit areas from the bar padding. */
private val DockHeight = 44.dp

@Composable
private fun DockItem(
    item: CircleTab,
    current: CircleTab,
    isWork: Boolean,
    icon: ImageVector,
    badge: Int,
    onTab: (CircleTab) -> Unit
) {
    val selected = item == current
    val pill by animateColorAsState(if (selected) MM.colors.accentWash else Color.Transparent, MM.motion.quick(), label = "dockPill")
    val tint by animateColorAsState(if (selected) MM.colors.accent else MM.colors.inkSecondary, MM.motion.quick(), label = "dockTint")
    val label = item.label(isWork)
    val spoken = if (badge > 0) "$label, $badge unread" else label
    Surface(
        onClick = { onTab(item) },
        color = pill,
        shape = MM.radius.pill,
        modifier = Modifier
            .height(DockHeight)
            .animateContentSize(MM.motion.spring())
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Tab
                this.selected = selected
            }
    ) {
        Row(
            Modifier.padding(horizontal = if (selected) MM.space.m else MM.space.m - 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(icon, null, tint = tint, modifier = Modifier.size(MMSize.iconSmall))
                if (badge > 0) {
                    Surface(
                        color = MM.colors.danger,
                        shape = MM.radius.pill,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp)
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
            if (selected) {
                Text(
                    label,
                    style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                    color = MM.colors.ink,
                    maxLines = 1,
                    modifier = Modifier.padding(start = MM.space.s - 2.dp)
                )
            }
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
