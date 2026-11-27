package com.craftflowtechnologies.meetingmind.feature.work.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * Quick access for the Work home: Record, Task, Inbox, Ask, Brief, Review, and New (Note and Import
 * live in the New sheet). One tap each. Inbox shows its count as a badge.
 */
@Composable
internal fun WorkQuickAccess(
    onRecord: () -> Unit,
    onTask: () -> Unit,
    onInbox: () -> Unit,
    onAsk: () -> Unit,
    onBrief: () -> Unit,
    onReview: () -> Unit,
    onNew: () -> Unit,
    inboxCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("work_home_quick"),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        QuickTile(Modifier.weight(1f), Icons.Filled.Mic, "Record", colors.recording, 0, onRecord)
        QuickTile(Modifier.weight(1f), Icons.Filled.AddTask, "Task", colors.speaker3, 0, onTask)
        QuickTile(Modifier.weight(1f), Icons.Rounded.Inbox, "Inbox", colors.speaker4, inboxCount, onInbox)
        QuickTile(Modifier.weight(1f), Icons.Outlined.HelpOutline, "Ask", Accent, 0, onAsk)
        QuickTile(Modifier.weight(1f), Icons.Filled.Summarize, "Brief", colors.speaker2, 0, onBrief)
        QuickTile(Modifier.weight(1f), Icons.Filled.Insights, "Review", colors.gold, 0, onReview)
        QuickTile(Modifier.weight(1f), Icons.Filled.Add, "New", InkSecondary, 0, onNew)
    }
}

@Composable
private fun QuickTile(modifier: Modifier, icon: ImageVector, label: String, tint: Color, badge: Int, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                .background(tint.copy(alpha = if (MM.colors.isDark) 0.18f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
            if (badge > 0) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).clip(RoundedCornerShape(50))
                    .background(MM.colors.danger).padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("$badge", color = FixedWhite, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        Text(
            label, color = InkSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
