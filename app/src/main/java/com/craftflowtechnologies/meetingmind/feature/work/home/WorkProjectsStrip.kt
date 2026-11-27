package com.craftflowtechnologies.meetingmind.feature.work.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.feature.work.ProjectCard
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.today.OutfitFamily
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised

/**
 * Projects as a carousel. Each card has its project colour as a top bar, the organisation, a status
 * pill read from its counts, and its open work. Tapping a card opens the project.
 */
@Composable
internal fun WorkProjectsStrip(projects: List<ProjectCard>, now: Long, onOpen: (String) -> Unit) {
    val colors = LocalMMColors.current
    LazyRow(
        Modifier.fillMaxWidth().testTag("work_home_projects"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(projects, key = { "p-" + it.notebook.id }) { p ->
            val status = WorkHomeCopy.projectStatus(p.openTasks, p.lastActivity, now)
            val pill = when (status) {
                "Needs attention" -> colors.danger
                "Quiet" -> colors.inkMuted
                "Clear" -> colors.success
                else -> colors.accent
            }
            Column(
                Modifier.width(164.dp).clip(RoundedCornerShape(20.dp)).background(SurfaceRaised)
                    .border(1.dp, LineSoft, RoundedCornerShape(20.dp)).clickable { onOpen(p.notebook.id) }
            ) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(projectColor(p.notebook.id)))
                Column(Modifier.padding(14.dp)) {
                    Text(
                        p.org ?: "Project", color = InkMuted, fontFamily = InterFamily, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        p.notebook.name, color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Box(
                        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(pill.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(status, color = pill, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                    Text(
                        "${p.openTasks} open · ${WorkHomeCopy.plural(p.notes, "note")}", color = InkSecondary,
                        fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}
