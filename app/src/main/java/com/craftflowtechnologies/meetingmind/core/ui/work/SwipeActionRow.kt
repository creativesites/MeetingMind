package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * A row that answers a swipe. Swipe right to complete (green), swipe left to schedule (accent).
 * Each swipe fires its action once the row is dragged past the threshold, with a haptic tick, and
 * the row then snaps back to rest. The row itself never leaves the list: the caller removes or
 * moves the item through its own state.
 *
 * Both backgrounds show an icon and a label while the row is dragged, so the action is visible
 * before it is taken.
 *
 * @param onSwipeRight the complete action (swipe right).
 * @param onSwipeLeft the schedule action (swipe left).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionRow(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    completeLabel: String = "Complete",
    scheduleLabel: String = "Schedule",
    completeIcon: ImageVector = Icons.Filled.Check,
    scheduleIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    content: @Composable () -> Unit
) {
    val colors = MM.colors
    val haptic = LocalHapticFeedback.current
    val currentRight by rememberUpdatedState(onSwipeRight)
    val currentLeft by rememberUpdatedState(onSwipeLeft)

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentRight()
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentLeft()
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Never leave the row dismissed: it always settles back, so the list keeps its items.
            false
        }
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val direction = state.dismissDirection
            val complete = direction == SwipeToDismissBoxValue.StartToEnd
            val scheduling = direction == SwipeToDismissBoxValue.EndToStart
            val tint = if (complete) colors.success else colors.accent
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (complete || scheduling) tint.copy(alpha = 0.14f) else colors.surface),
                contentAlignment = if (scheduling) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (complete || scheduling) {
                    Row(
                        Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = if (complete) completeIcon else scheduleIcon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (complete) completeLabel else scheduleLabel,
                            style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                            color = tint
                        )
                    }
                }
            }
        }
    ) {
        Box(Modifier.background(colors.surface)) { content() }
    }
}
