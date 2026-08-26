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
import com.example.ui.theme.Accent
import com.example.ui.theme.Speaker2
import com.example.ui.theme.Speaker3
import com.example.ui.theme.Speaker4

/**
 * The single Compose-side speaker palette (Phase 15 §2 — consolidates what used to be three
 * divergent color sources: this list, a second copy of it inline in two other files, and a
 * completely different hex list in [com.example.ai.pipeline.MeetingProcessingPipeline]). Order
 * and values must stay in sync with [com.example.core.model.SpeakerColors.PALETTE_HEX] — that
 * object is what the pipeline actually persists into `SpeakerEntity.colorHex`, so this list only
 * matters as a fallback for a speaker id not yet reflected in persisted data.
 */
val SpeakerPalette = listOf(Accent, Speaker2, Speaker3, Speaker4)

/**
 * Resolves a speaker's Compose colour: the real persisted [colorHex] when there is one — parsed,
 * never re-derived — falling back to the index-based [SpeakerPalette] only when nothing is
 * persisted yet (e.g. a speaker reassigned moments ago, before Room's Flow has re-emitted). A
 * speaker's colour must never shuffle between renders, which is why every call site should pass
 * the same [fallbackIndex] for the same speaker (its stable position in the meeting's speaker
 * list) rather than a render-order index.
 */
fun speakerColorFor(colorHex: String?, fallbackIndex: Int): Color =
    colorHex
        ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
        ?: SpeakerPalette[fallbackIndex.mod(SpeakerPalette.size)]

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
