package com.example.feature.meetingdetail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.Accent

/**
 * A tappable mono timestamp — the seek affordance used everywhere in the redesign (gutter,
 * decisions, citations, review panels). InkMuted normally, [Accent] when it marks the currently
 * playing position (docs/recording-page-implementation.md §1.2, "every timestamp shown anywhere is
 * tappable and seeks").
 */
@Composable
fun MonoTimestamp(
    label: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = label,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        style = if (active) RecordingPageType.monoTimestampActive.copy(color = Accent) else RecordingPageType.monoTimestamp
    )
}
