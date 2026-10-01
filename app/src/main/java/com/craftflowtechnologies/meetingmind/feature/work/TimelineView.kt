package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.work.TimelineItem
import com.craftflowtechnologies.meetingmind.core.work.TimelineItemType
import com.craftflowtechnologies.meetingmind.core.work.TimelineSection
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft

@Composable
fun TimelineView(
    sections: List<TimelineSection>,
    onItemClick: (TimelineItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (sections.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No dated activity recorded for this project.",
                fontSize = 13.sp,
                color = InkMuted
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = section.title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (section.title == "Overdue") Danger else InkMuted
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    section.items.forEach { item ->
                        TimelineRow(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    item: TimelineItem,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (item.type) {
        TimelineItemType.MEETING -> Icons.Default.Mic
        TimelineItemType.DECISION -> Icons.Default.Gavel
        TimelineItemType.RISK -> Icons.Default.Warning
        TimelineItemType.COMMITMENT, TimelineItemType.TASK -> Icons.Default.CheckCircle
    }

    val iconTint: Color = when {
        item.isOverdue -> Danger
        item.type == TimelineItemType.DECISION -> Accent
        item.type == TimelineItemType.RISK -> Danger
        else -> InkSecondary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, LineSoft, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Pulse.shortDate(item.at),
                        fontSize = 11.sp,
                        color = if (item.isOverdue) Danger else InkMuted
                    )
                    item.counterparty?.let { cp ->
                        Text(text = "• $cp", fontSize = 11.sp, color = InkSecondary)
                    }
                }
            }

            item.status?.let { status ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LineSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkSecondary
                    )
                }
            }
        }
    }
}
