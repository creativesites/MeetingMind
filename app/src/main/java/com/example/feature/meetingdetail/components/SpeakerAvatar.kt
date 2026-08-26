package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A round speaker avatar: solid speaker-colour fill, first-initial label, white text.
 * Sized per call site — 20/22/26/34dp across the redesign's screens
 * (docs/recording-page-implementation.md §1.4). Colour should come from the speaker's persisted
 * identity, not be re-derived per render, so it never shuffles.
 */
@Composable
fun SpeakerAvatar(
    initial: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.take(1).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
