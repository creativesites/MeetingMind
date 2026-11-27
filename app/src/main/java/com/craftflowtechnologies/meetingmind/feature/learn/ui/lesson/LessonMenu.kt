package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * The lesson's top-bar overflow: the secondary actions the Learning studio's menu had.
 * "Open full notes" and "Pause reviews" / "Resume reviews". Ask and Listen are on the bottom bar.
 */
@Composable
fun LessonOverflowMenu(
    reviewsPaused: Boolean,
    onOpenNotes: () -> Unit,
    onTogglePauseReviews: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val c = MM.colors
    Box(modifier) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.testTag("lesson_menu_button")
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Lesson options", tint = c.inkSecondary)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = c.surfaceRaised,
            shape = MM.radius.card,
            modifier = Modifier.testTag("lesson_menu")
        ) {
            DropdownMenuItem(
                text = { Text("Open full notes", style = MM.type.body, color = c.ink) },
                leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null, tint = c.inkSecondary) },
                onClick = { open = false; onOpenNotes() },
                modifier = Modifier.testTag("lesson_menu_open_notes")
            )
            DropdownMenuItem(
                text = { Text(if (reviewsPaused) "Resume reviews" else "Pause reviews", style = MM.type.body, color = c.ink) },
                leadingIcon = {
                    Icon(
                        if (reviewsPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = c.inkSecondary
                    )
                },
                onClick = { open = false; onTogglePauseReviews() },
                modifier = Modifier.testTag("lesson_menu_pause_reviews")
            )
        }
    }
}
