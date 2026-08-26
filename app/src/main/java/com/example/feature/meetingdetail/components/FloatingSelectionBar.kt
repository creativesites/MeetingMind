package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Ink

/**
 * The dark pill toolbar that appears above a text selection in Transcript editing
 * (docs/recording-page-implementation.md §2.5): a handful of tools that suit a selection, plus a
 * trailing "more" (⋯) that opens the rest as an inline panel rather than a new surface.
 */
@Composable
fun FloatingSelectionBar(
    items: List<String>,
    onItemClick: (Int) -> Unit,
    moreLabel: String,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Ink.copy(alpha = 0.28f),
                spotColor = Ink.copy(alpha = 0.28f)
            )
            .background(Ink, RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { index, label ->
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onItemClick(index) }
                    .background(Color.Transparent, RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
        Text(
            text = moreLabel,
            color = Color.White,
            fontSize = 14.sp,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clickable(onClick = onMoreClick)
                .background(Color.Transparent, RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}
