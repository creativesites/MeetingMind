package com.craftflowtechnologies.meetingmind.feature.work.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.work.PersonAvatar
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors

/**
 * People as a strip of avatars. A badge says where things stand: "you owe" (accent) when you owe them,
 * "owes" (warning) when they owe you. Tapping a person opens their page.
 */
@Composable
internal fun WorkPeopleStrip(
    people: List<WorkPerson>,
    owedToMe: Set<String>,
    iOwe: Set<String>,
    onOpen: (String) -> Unit
) {
    val colors = LocalMMColors.current
    LazyRow(
        Modifier.fillMaxWidth().testTag("work_home_people"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(people.take(12), key = { "pp-" + it.id }) { p ->
            val badge = WorkHomeCopy.peopleBadge(p.id, owedToMe, iOwe)
            Column(
                Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(p.id) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.width(52.dp)) {
                    PersonAvatar(p, 48.dp)
                    if (badge != null) Box(
                        Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(50))
                            .background(if (badge == "you owe") colors.accent else colors.warning)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(badge, color = FixedWhite, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, maxLines = 1)
                    }
                }
                Text(
                    p.firstName, color = Ink, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
