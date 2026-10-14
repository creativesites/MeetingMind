package com.craftflowtechnologies.meetingmind.feature.devotional

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** "Reading: Morning and Evening" with a quiet "Write one with AI" beside it. Stays quiet: no fill, no border. */
@Composable
fun ReadingStrip(name: String, writing: Boolean, onWriteWithAi: () -> Unit, modifier: Modifier = Modifier) {
    val c = MM.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = MM.space.l).testTag("devotional_reading_strip"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Reading: $name", style = MM.type.caption, color = c.inkSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        TextAction("Write one with AI", onClick = onWriteWithAi, enabled = !writing, modifier = Modifier.testTag("devotional_write_ai"))
    }
}

/** The one-time tip. Dismissed with "Got it" or by writing one with AI; never shown again after that. */
@Composable
fun WriteWithAiTip(planName: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = MM.colors
    InsetPanel(modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.s).testTag("devotional_ai_tip")) {
        Column {
            Text("You're reading $planName", style = MM.type.bodyStrong, color = c.ink)
            Text(
                "Want something fresh? Use Write one with AI above, any time. It's labelled Written with AI, and your plan reading stays in the archive.",
                style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.padding(top = MM.space.xs)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Got it", onClick = onDismiss, modifier = Modifier.testTag("devotional_ai_tip_dismiss"))
            }
        }
    }
}
