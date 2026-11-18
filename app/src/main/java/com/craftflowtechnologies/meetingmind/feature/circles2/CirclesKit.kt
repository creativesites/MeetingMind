package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay

/*
 * The Circles kit: the few pieces every Circles page shares, built on the same blocks as Home and Work
 * (MM tokens, ScreenHeader's anatomy, MMCard, the ListRow rhythm). Nothing here has a Circles-only look.
 */

/**
 * The page header every Circles page uses: back, an optional leading mark (a cover or avatar), the title in
 * `MM.type.title` with one quiet subtitle line, and plain icon actions. No outlined icon circles, no tag pills:
 * the same anatomy as Work's ScreenHeader.
 */
@Composable
fun CirclesTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = MM.space.xs, end = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.ink) }
        } else {
            Spacer(Modifier.width(MM.space.m))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(MM.space.m))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MM.type.title, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(subtitle, style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A plain header action: a 48 dp icon button in ink, like Work's header. */
@Composable
fun CirclesHeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, description, tint = MM.colors.ink) }
}

/** The section title used across Circles pages: `heading` type, optional trailing text action. */
@Composable
fun CirclesSectionTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = MM.space.s, bottom = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title, style = MM.type.heading, color = MM.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (trailing != null) trailing()
    }
}

/** A hairline between rows inside a card, inset to line up with the row text. */
@Composable
fun CirclesRowDivider(startInset: Dp = MM.space.l) {
    HorizontalDivider(Modifier.padding(start = startInset), thickness = MMSize.hairline, color = MM.colors.lineSoft)
}

/**
 * The bottom padding a scrolling Circles page needs so its last item clears the navigation bar (3-button or gesture),
 * plus [extra] for anything floating above it. Use as the list's bottom contentPadding.
 */
@Composable
fun circlesBottomInset(extra: Dp = 0.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues(LocalDensity.current).calculateBottomPadding() + MM.space.xl + extra

/** List content padding for Circles pages: the screen gutter, and a bottom that clears the navigation bar. */
@Composable
fun circlesListPadding(top: Dp = MM.space.s, clearNavigationBar: Boolean = true, extraBottom: Dp = 0.dp): PaddingValues =
    PaddingValues(
        start = MM.space.l, end = MM.space.l, top = top,
        bottom = if (clearNavigationBar) circlesBottomInset(extraBottom) else MM.space.xl + extraBottom
    )

/**
 * A page section arriving: fades in and rises 10 dp, 30 ms apart per [index] (capped at five), once.
 * Reduced motion (`MM.motion.reduced`) shows the end state at once.
 */
@Composable
fun CirclesReveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = MM.motion
    // Saved, so a section scrolled away and back (or a rotated screen) doesn't play its entrance again.
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (motion.reduced || played) 1f else 0f) }
    val density = LocalDensity.current.density
    LaunchedEffect(Unit) {
        if (!motion.reduced && progress.value < 1f) {
            delay(index.coerceIn(0, 5) * 30L)
            progress.animateTo(1f, motion.enter())
        }
        played = true
    }
    Box(modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 10f * density
    }) { content() }
}

/** The colours of one chat bubble. Theme tokens only, so it works in light and dark. */
@Immutable
data class ChatBubbleColors(
    val container: Color,
    val content: Color,
    val secondary: Color,
    val quoteFill: Color,
    val quoteBar: Color,
    val link: Color
)

/**
 * Chat bubbles without the blue: your own messages are a charcoal ink bubble in light (a raised graphite one in dark),
 * everyone else's sit on the plain surface. The accent is kept for links and mentions only.
 */
@Composable
@ReadOnlyComposable
fun chatBubbleColors(mine: Boolean): ChatBubbleColors {
    val c = MM.colors
    return if (mine) {
        if (c.isDark) {
            ChatBubbleColors(
                container = lerp(c.surfaceRaised, c.ink, 0.12f),
                content = c.ink,
                secondary = c.inkSecondary,
                quoteFill = c.ink.copy(alpha = 0.08f),
                quoteBar = c.inkSecondary,
                link = c.ink
            )
        } else {
            ChatBubbleColors(
                container = lerp(c.canvas, c.ink, 0.92f),
                content = c.onInk,
                secondary = c.onInk.copy(alpha = 0.72f),
                quoteFill = c.onInk.copy(alpha = 0.12f),
                quoteBar = c.onInk.copy(alpha = 0.72f),
                link = c.onInk
            )
        }
    } else {
        ChatBubbleColors(
            container = if (c.isDark) c.surfaceRaised else c.surface,
            content = c.ink,
            secondary = c.inkMuted,
            quoteFill = c.track.copy(alpha = 0.7f),
            quoteBar = c.inkMuted,
            link = c.accent
        )
    }
}

/** A label beside the title in [CirclesTopBar]: "Fellowship · 12 members", plus "Admin" for admins. */
fun circleSubtitle(c: com.craftflowtechnologies.meetingmind.core.circles2.Circle, isAdmin: Boolean): String = buildString {
    append(c.groupWord)
    append(" · ")
    append(if (c.memberCount == 1) "1 member" else "${c.memberCount} members")
    if (isAdmin) append(" · Admin")
    if (c.template == "client_engagement") append(" · External")
}

/** Space between a list and the pinned control under it, so nothing hides behind a floating action. */
val CirclesFabClearance: Dp = 88.dp
