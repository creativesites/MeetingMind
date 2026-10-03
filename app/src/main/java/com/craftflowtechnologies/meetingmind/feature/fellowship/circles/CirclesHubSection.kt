package com.craftflowtechnologies.meetingmind.feature.fellowship.circles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipPrimaryButton
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipSecondaryButton
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipSectionTitle
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldInk
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

@Composable
fun CirclesHubSection(
    circles: List<Circle>,
    cachedDisplayName: String,
    onOpenCircle: (String) -> Unit,
    onCreateCircle: (name: String, description: String, displayName: String, emoji: String) -> Unit,
    onJoinCircle: (inviteCodeOrUri: String, displayName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var showJoinSheet by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        FellowshipSectionTitle("Private Circles (Tier 2)", top = 20.dp)

        if (circles.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SurfaceRaised,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(FaithGoldWash),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🕊️", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Private Circles", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink)
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = FaithGoldWash,
                                    border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.3f))
                                ) {
                                    Text("E2EE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FaithGoldInk, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                            Text("Encrypted, invite-only groups (5–15 people)", fontSize = 12.sp, color = InkMuted)
                        }
                    }

                    Text(
                        "Gather a small group, youth leaders, or family. Share prayer requests, tap 'I prayed', celebrate answered prayers, and share sermon discussion guides.",
                        fontSize = 13.sp,
                        lineHeight = 18.5.sp,
                        color = com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FellowshipPrimaryButton(
                            text = "Create circle",
                            onClick = { showCreateSheet = true },
                            icon = Icons.Filled.Add,
                            modifier = Modifier.weight(1f)
                        )
                        FellowshipSecondaryButton(
                            text = "Join with code",
                            onClick = { showJoinSheet = true }
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (circle in circles) {
                    Surface(
                        onClick = { onOpenCircle(circle.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceRaised,
                        border = BorderStroke(1.dp, Line),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(FaithGoldWash),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(circle.avatarEmoji, fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(circle.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink)
                                Text("${circle.memberCount} ${if (circle.memberCount == 1) "member" else "members"} · End-to-end encrypted", fontSize = 12.sp, color = InkMuted)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkFaint, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FellowshipSecondaryButton(
                        text = "Create circle",
                        onClick = { showCreateSheet = true },
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f)
                    )
                    FellowshipSecondaryButton(
                        text = "Join circle",
                        onClick = { showJoinSheet = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateCircleSheet(
            cachedDisplayName = cachedDisplayName,
            onDismiss = { showCreateSheet = false },
            onCreate = { name, desc, dName, emoji ->
                onCreateCircle(name, desc, dName, emoji)
                showCreateSheet = false
            }
        )
    }

    if (showJoinSheet) {
        JoinCircleSheet(
            cachedDisplayName = cachedDisplayName,
            onDismiss = { showJoinSheet = false },
            onJoin = { code, dName ->
                onJoinCircle(code, dName)
                showJoinSheet = false
            }
        )
    }
}
