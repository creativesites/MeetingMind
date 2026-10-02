package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised

data class QuickAccessItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val onClick: () -> Unit
)

/**
 * High-end circular quick-access row on Home (Faith, Work, Learning, Devotional, Prayer, Word, Testimonies).
 * Designed with glowing gradient rings and minimalistic typography.
 */
@Composable
fun HomeQuickAccessRow(
    onOpenFaith: () -> Unit,
    onOpenWork: () -> Unit,
    onOpenLearning: () -> Unit,
    onOpenDevotional: () -> Unit,
    onOpenPrayer: () -> Unit,
    onOpenWord: () -> Unit,
    onOpenTestimonies: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        QuickAccessItem(
            id = "faith",
            label = "Faith",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
            onClick = onOpenFaith
        ),
        QuickAccessItem(
            id = "work",
            label = "Work",
            icon = Icons.Filled.Work,
            gradientColors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
            onClick = onOpenWork
        ),
        QuickAccessItem(
            id = "learning",
            label = "Learning",
            icon = Icons.Filled.School,
            gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669)),
            onClick = onOpenLearning
        ),
        QuickAccessItem(
            id = "devotional",
            label = "Devotional",
            icon = Icons.Filled.WbSunny,
            gradientColors = listOf(Color(0xFFF97316), Color(0xFFEA580C)),
            onClick = onOpenDevotional
        ),
        QuickAccessItem(
            id = "prayer",
            label = "Prayer",
            icon = Icons.Filled.Favorite,
            gradientColors = listOf(Color(0xFFF43F5E), Color(0xFFBE123C)),
            onClick = onOpenPrayer
        ),
        QuickAccessItem(
            id = "word",
            label = "Word",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF0284C7)),
            onClick = onOpenWord
        ),
        QuickAccessItem(
            id = "testimonies",
            label = "Testimonies",
            icon = Icons.Filled.Celebration,
            gradientColors = listOf(Color(0xFFA855F7), Color(0xFF7E22CE)),
            onClick = onOpenTestimonies
        )
    )

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
    ) {
        items(items, key = { it.id }) { item ->
            QuickAccessButton(item = item)
        }
    }
}

@Composable
private fun QuickAccessButton(
    item: QuickAccessItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(62.dp)
            .clickable(onClick = item.onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outer gradient ring with surface background
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(item.gradientColors),
                    shape = CircleShape
                )
                .background(SurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            // Subtle tinted glow inside
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(item.gradientColors.first().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = item.gradientColors.first(),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = item.label,
            color = Ink,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
