package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Shares a faith artifact (devotional excerpt or verse card) to a Faith Circle.
 * Per agents/PRODUCT.md:
 * - Only Faith circles (!it.isWork) are offered.
 * - Devotionals are shared as an excerpt of about 600 characters plus a link to the reading.
 */
@Composable
fun ShareArtifactToCircleSheet(
    title: String,
    rawText: String,
    verseRef: String? = null,
    readingDate: String? = null,
    onDismiss: () -> Unit,
    onNavigateCreateCircle: () -> Unit,
    onShared: (circleName: String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val circlesState by repo.circles().collectAsState(CirclesListState.Loading)
    val scope = rememberCoroutineScope()

    var selectedCircleId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Enforce 600 character excerpt limit
    val excerpt = remember(rawText) {
        val trimmed = rawText.trim()
        if (trimmed.length <= 600) trimmed
        else trimmed.take(597) + "…"
    }

    val link = readingDate?.let { "Daily reading: $it" } ?: "Shared from MeetingMind"
    val postBody = "$title\n\n$excerpt\n\n$link"

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Share with Faith Circle",
                    style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MM.colors.ink
                )
                Text(
                    "Share this reflection with your small group or prayer partners",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }

        when (val state = circlesState) {
            CirclesListState.Loading -> {
                StatusLine(StatusKind.Info, "Loading your circles…")
            }
            CirclesListState.NotConnected -> {
                EmptyState(
                    title = "Circles not connected",
                    body = "Faith Circles allow your group to share devotionals, prayer requests and studies in real-time.",
                    illustration = { Icon(Icons.Rounded.Groups, null, tint = MM.colors.inkMuted) }
                )
            }
            is CirclesListState.Ready -> {
                val faithCircles = state.circles.filter { !it.isWork }
                if (faithCircles.isEmpty()) {
                    Column(
                        Modifier.padding(vertical = MM.space.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Text(
                            "You don't have any Faith Circles yet. Start a small group or prayer circle to share daily encouragement.",
                            style = MM.type.body,
                            color = MM.colors.inkSecondary
                        )
                        PrimaryButton("Start a circle", {
                            onDismiss()
                            onNavigateCreateCircle()
                        })
                    }
                } else {
                    val defaultPick = selectedCircleId?.takeIf { id -> faithCircles.any { it.id == id } } ?: faithCircles.first().id
                    if (selectedCircleId == null) {
                        selectedCircleId = defaultPick
                    }
                    val currentCircle = faithCircles.firstOrNull { it.id == selectedCircleId } ?: faithCircles.first()

                    SectionHeader("Target Circle")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        faithCircles.take(3).forEach { c ->
                            val isSelected = c.id == currentCircle.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) CirclesPalette.AccentSoft else MM.colors.surfaceSunk,
                                border = BorderStroke(1.dp, if (isSelected) CirclesPalette.Accent else MM.colors.line),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedCircleId = c.id }
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        c.name,
                                        style = MM.type.bodyStrong.copy(fontSize = 13.5.sp),
                                        color = if (isSelected) CirclesPalette.AccentDeep else MM.colors.ink,
                                        maxLines = 1
                                    )
                                    Text(
                                        c.groupWord,
                                        style = MM.type.caption,
                                        color = if (isSelected) CirclesPalette.AccentDeep.copy(alpha = 0.8f) else MM.colors.inkSecondary
                                    )
                                }
                            }
                        }
                    }

                    SectionHeader("Preview")
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MM.colors.surfaceSunk,
                        border = BorderStroke(1.dp, MM.colors.line),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink)
                            Text(excerpt, style = MM.type.body, color = MM.colors.inkSecondary)
                            if (!verseRef.isNullOrBlank()) {
                                Text(verseRef, style = MM.type.caption, color = CirclesPalette.AccentDeep)
                            }
                        }
                    }

                    errorMsg?.let { StatusLine(StatusKind.Error, it) }

                    PrimaryButton(
                        if (sending) "Sharing…" else "Share with ${currentCircle.name}",
                        onClick = {
                            sending = true
                            errorMsg = null
                            scope.launch {
                                val result = repo.createPost(
                                    circleId = currentCircle.id,
                                    body = postBody,
                                    type = PostType.Devotional,
                                    anonymous = false,
                                    verseRef = verseRef,
                                    requestId = UUID.randomUUID().toString()
                                )
                                sending = false
                                if (result.isSuccess) {
                                    onShared(currentCircle.name)
                                    onDismiss()
                                } else {
                                    errorMsg = "Could not share post. Please try again."
                                }
                            }
                        },
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
