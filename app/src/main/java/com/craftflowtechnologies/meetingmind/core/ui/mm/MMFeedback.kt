package com.craftflowtechnologies.meetingmind.core.ui.mm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** An empty screen (never an empty section: those render nothing). The illustration slot takes the companion. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(MM.space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        if (illustration != null) illustration()
        Text(title, style = MM.type.heading, color = MM.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = MM.type.secondary, color = MM.colors.inkSecondary, textAlign = TextAlign.Center)
        if (action != null) action()
    }
}

enum class StatusKind { Info, Warning, Error }

/**
 * The honest fallback line: one plain sentence and, where possible, a Retry. The icon shape differs
 * per kind so the meaning never rests on colour alone.
 */
@Composable
fun StatusLine(
    kind: StatusKind,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = MM.colors
    val (icon, tint) = when (kind) {
        StatusKind.Info -> Icons.Rounded.Info to c.inkMuted
        StatusKind.Warning -> Icons.Rounded.WarningAmber to c.warning
        StatusKind.Error -> Icons.Rounded.ErrorOutline to c.danger
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(MMSize.iconSmall))
        Spacer(Modifier.width(MM.space.s))
        Text(text, style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) TextAction(actionLabel, onAction)
    }
}
