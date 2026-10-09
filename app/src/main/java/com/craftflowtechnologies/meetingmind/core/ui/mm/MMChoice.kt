package com.craftflowtechnologies.meetingmind.core.ui.mm

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** A filter chip. The visual is compact; the touch target is still 48 dp. A check mark shows selection, not colour alone. */
@Composable
fun MMChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val fill = animateColorAsState(if (selected) c.accentWash else c.surfaceSunk, MM.motion.quick(), label = "chipFill")
    Box(
        modifier.heightIn(min = MMSize.minTouch)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MM.radius.small, color = fill.value,
            border = if (selected) BorderStroke(MMSize.hairline, c.accent) else null
        ) {
            Row(
                Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(MMSize.iconSmall), tint = c.accent)
                    Spacer(Modifier.width(MM.space.xs))
                }
                Text(label, style = MM.type.caption, color = if (selected) c.accent else c.inkSecondary)
            }
        }
    }
}

/** A scrolling row of multi-select chips. Add the screen gutter with [Modifier.padding] or by placing it full width. */
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() }
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        options.forEach { option ->
            MMChip(label(option), selected = option in selected, onClick = { onToggle(option) })
        }
    }
}

/** Two to four mutually exclusive options on a track. Each segment is at least 48 dp tall. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    Surface(modifier.fillMaxWidth(), shape = MM.radius.pill, color = c.track) {
        Row(Modifier.selectableGroup().padding(MM.space.xs)) {
            options.forEachIndexed { i, text ->
                val isSel = i == selectedIndex
                val fill = animateColorAsState(if (isSel) c.surfaceRaised else c.track, MM.motion.quick(), label = "segFill")
                Surface(
                    shape = MM.radius.pill, color = fill.value,
                    modifier = Modifier.weight(1f).heightIn(min = MMSize.minTouch)
                        .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(i) })
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text, style = if (isSel) MM.type.bodyStrong else MM.type.body,
                            color = if (isSel) c.ink else c.inkSecondary
                        )
                    }
                }
            }
        }
    }
}
