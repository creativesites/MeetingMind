package com.craftflowtechnologies.meetingmind.core.ui.mm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Screen title row: optional leading (back) slot, `title` text, trailing actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(MM.space.s)) }
        Text(
            title, style = MM.type.title, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Home greeting: `display` greeting, `secondary` subtitle, a leading slot for the companion, actions. */
@Composable
fun HomeHeader(
    greeting: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(MM.space.m)) }
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MM.type.display, color = MM.colors.ink, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Section header: "Tasks · 5" and a single "All →" action. [actionLabel] and [onAction] go
 * together; with no action the header is just the title and count.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
        val text = if (count != null) "$title · $count" else title
        Text(
            text, style = MM.type.heading, color = MM.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (actionLabel != null && onAction != null) {
            Row(
                Modifier.heightIn(min = MMSize.minTouch)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(actionLabel, style = MM.type.bodyStrong, color = MM.colors.accent)
                Spacer(Modifier.width(MM.space.xs))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(MMSize.iconSmall), tint = MM.colors.accent)
            }
        }
    }
}
