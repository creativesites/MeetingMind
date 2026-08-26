package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Ink
import com.example.ui.theme.SurfaceTrack

/**
 * A pill-tracked N-way segmented control (docs/recording-page-implementation.md §2.6's
 * Verbatim/Readable/Polished cleanup-mode control, and reusable anywhere the design calls for the
 * same shape). The selected segment is white with Ink text on a [SurfaceTrack] track.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceTrack, RoundedCornerShape(13.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option) }
                    .background(if (active) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (active) Ink else com.example.ui.theme.InkSecondary
                )
            }
        }
    }
}
