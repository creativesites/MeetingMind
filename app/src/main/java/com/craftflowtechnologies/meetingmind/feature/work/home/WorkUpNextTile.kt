package com.craftflowtechnologies.meetingmind.feature.work.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.today.OutfitFamily
import com.craftflowtechnologies.meetingmind.feature.today.home.Reveal
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Briefing
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised

/**
 * The floating "Up next" tile, over the bottom edge of the sky hero. One line of what is next, and one
 * verb: Prepare for a meeting, Review for what is waiting. The whole tile opens the same thing as its verb.
 */
@Composable
internal fun WorkUpNextTile(
    overline: String,
    title: String,
    subtitle: String?,
    icon: ImageVector,
    buttonLabel: String,
    onTile: () -> Unit,
    onButton: () -> Unit,
    index: Int,
    modifier: Modifier = Modifier
) {
    Reveal(index) {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceRaised)
                .border(1.dp, LineSoft, RoundedCornerShape(26.dp))
                .clickable(onClick = onTile)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("work_home_upnext"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Accent, Briefing.Indigo))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = FixedWhite, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    overline, color = InkMuted, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    title, color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) Text(
                    subtitle, color = InkSecondary, fontFamily = InterFamily, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Accent).clickable(onClick = onButton)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(buttonLabel, color = FixedWhite, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
