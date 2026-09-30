package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors

/**
 * What a piece of work looks like in a list: a small glass tile tinted for its kind, with a drawn
 * icon that says what it is (a recording, a client call, a decision record), in place of the same
 * grey document on every row.
 */
internal fun workIconFor(type: RecordingType, title: String = ""): ImageVector = when {
    // Notes MeetingMind writes from a recording are filed as General, so their title says what they are.
    title.startsWith("Action items", ignoreCase = true) -> Icons.Filled.Checklist
    title.startsWith("Summary", ignoreCase = true) -> Icons.Filled.Summarize
    title.startsWith("Transcript", ignoreCase = true) -> Icons.Filled.Subtitles
    else -> when (type) {
        RecordingType.MEETING -> Icons.Filled.GraphicEq
        RecordingType.STANDUP -> Icons.Filled.Groups
        RecordingType.ONE_ON_ONE -> Icons.Filled.Forum
        RecordingType.CLIENT_CALL -> Icons.Filled.Handshake
        RecordingType.INTERVIEW -> Icons.Filled.RecordVoiceOver
        RecordingType.CONSULTATION -> Icons.Filled.MedicalServices
        RecordingType.PROJECT_BRIEF -> Icons.Filled.Layers
        RecordingType.DECISION_RECORD -> Icons.Filled.Gavel
        RecordingType.WEEKLY_REVIEW -> Icons.Filled.Insights
        RecordingType.RESEARCH -> Icons.Filled.Science
        RecordingType.BRAINSTORM, RecordingType.IDEA -> Icons.Filled.Lightbulb
        RecordingType.VOICE_MEMO, RecordingType.DICTATION -> Icons.Filled.Mic
        else -> Icons.Filled.EditNote
    }
}

@Composable
internal fun WorkTypeTile(type: RecordingType, title: String = "", size: Dp = 42.dp) {
    val tint = when {
        title.startsWith("Action items", ignoreCase = true) -> Accent
        title.startsWith("Summary", ignoreCase = true) -> LocalMMColors.current.speaker2
        else -> tintFor(type)
    }
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        Modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.24f), tint.copy(alpha = 0.06f))))
            .border(1.dp, tint.copy(alpha = 0.24f), shape),
        contentAlignment = Alignment.Center
    ) { Icon(workIconFor(type, title), null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}
