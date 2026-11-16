package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * The round checkbox for a task, ringed in its priority colour: 1 is the most urgent (red),
 * 2 amber, 3 accent, 4 faint. A checked box fills with its ring colour and pops once, from 60%
 * to full size. The pop is skipped under "Remove animations" and never runs at first composition.
 *
 * The touch target is at least 48 dp; the visible circle is 22 dp.
 *
 * @param priority 1..4; any other value is clamped to that range.
 * @param onToggle called with the new checked state.
 */
@Composable
fun TaskCheck(
    checked: Boolean,
    priority: Int,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val motion = MM.motion
    val ring = when (priority.coerceIn(1, 4)) {
        1 -> colors.danger
        2 -> colors.warning
        3 -> colors.accent
        else -> colors.inkFaint
    }

    val pop = remember { Animatable(1f) }
    var seenFirst by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (!seenFirst) {
            seenFirst = true
            return@LaunchedEffect
        }
        if (checked && !motion.reduced) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, motion.spring<Float>())
        }
    }

    Box(
        modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = "Priority $priority task" }
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(MM.radius.pill)
                .background(if (checked) ring else Color.Transparent)
                .border(1.5f.dp, ring, MM.radius.pill),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = FixedWhite,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
