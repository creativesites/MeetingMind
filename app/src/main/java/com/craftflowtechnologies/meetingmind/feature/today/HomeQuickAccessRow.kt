package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppLook
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted

data class QuickAccessItem(val id: String, val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** A quiet, single-accent set of shortcuts for lower-priority Home actions. */
@Composable
fun HomeQuickAccessRow(
    onOpenFaith: () -> Unit,
    onOpenWork: () -> Unit,
    onOpenDevotional: () -> Unit,
    onOpenPrayer: () -> Unit,
    onOpenWord: () -> Unit,
    onOpenLearning: (() -> Unit)? = null,
    onOpenTestimonies: (() -> Unit)? = null,
    onOpenSpark: (() -> Unit)? = null,
    showWork: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Work remains represented by its richer Work & Projects card; devotional and notes already
    // have stronger entry points elsewhere on Today.
    val items = buildList {
        add(QuickAccessItem("faith", "Faith", Icons.Filled.Church, onOpenFaith))
        onOpenSpark?.let { add(QuickAccessItem("spark", "Spark", Icons.Filled.Bolt, it)) }
        if (showWork) add(QuickAccessItem("work", "Work", Icons.Filled.Work, onOpenWork))
        onOpenLearning?.let { add(QuickAccessItem("learning", "Learning", Icons.Filled.School, it)) }
        add(QuickAccessItem("prayer", "Prayer", Icons.Filled.Favorite, onOpenPrayer))
        add(QuickAccessItem("word", "Word", Icons.AutoMirrored.Filled.AutoStories, onOpenWord))
        onOpenTestimonies?.let { add(QuickAccessItem("testimonies", "Stories", Icons.Filled.Celebration, it)) }
    }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp)
    ) {
        items(items, key = { it.id }) { QuickAccessButton(it) }
    }
}

@Composable
private fun QuickAccessButton(item: QuickAccessItem, modifier: Modifier = Modifier) {
    val look = LocalAppLook.current
    Column(
        modifier.width(60.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = item.onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(look.accentSoft), contentAlignment = Alignment.Center) {
            Icon(item.icon, item.label, tint = look.accent, modifier = Modifier.size(22.dp))
        }
        Text(
            item.label, color = InkMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
