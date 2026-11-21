package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.ui.work.AttentionTone
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

// The Work home's shared parts: a "Needs you" row and the Ask field. The home itself is WorkPageHome.kt.

/** One row of "Needs you": its kind, the title, the source line, the one verb, and where the row opens. */
internal class PulseNeed(
    val key: String,
    val kind: String,
    val title: String,
    val line: String,
    val verb: String,
    val tone: AttentionTone,
    val icon: ImageVector,
    val onVerb: () -> Unit,
    val onOpen: () -> Unit
)

@Composable
internal fun AskSection(onAsk: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
                .background(MM.colors.surface, MM.radius.card)
                .clickable(role = Role.Button, onClick = onAsk)
                .padding(horizontal = MM.space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Icon(Icons.Rounded.Search, null, tint = MM.colors.inkMuted)
            Text("Ask about your work…", style = MM.type.body, color = MM.colors.inkMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            SuggestionChip("What am I waiting for?", onAsk)
            SuggestionChip("What did we decide?", onAsk)
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = MMSize.minTouch)
            .background(MM.colors.accentWash, MM.radius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MM.space.m),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MM.type.caption, color = MM.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
