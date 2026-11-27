package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.feature.circles2.CircleSheet
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

@Composable
fun ShareStudyPackToCircleSheet(
    title: String,
    cramSheetMarkdown: String,
    onDismiss: () -> Unit,
    onPublished: (circleName: String) -> Unit,
    onNavigateCreateCircle: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val circlesState by repo.circles().collectAsState(CirclesListState.Loading)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedCircleId by remember { mutableStateOf<String?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs / 2), modifier = Modifier.weight(1f)) {
                Text(
                    text = if (title.isNotBlank()) "Share: $title" else "Share Study Pack to Circle",
                    style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                    color = MM.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Publish cram sheet & high-yield concepts to your Study Circle",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }

        Spacer(Modifier.height(MM.space.m))

        when (val state = circlesState) {
            is CirclesListState.Loading -> {
                val transition = rememberInfiniteTransition(label = "circles_skeleton_pulse")
                val alpha by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "circles_skeleton_alpha"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MM.space.xs),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    repeat(3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MMSize.minTouch)
                                .padding(vertical = MM.space.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(MMSize.iconSmall)
                                    .clip(MM.radius.pill)
                                    .background(MM.colors.surfaceSunk.copy(alpha = alpha))
                            )
                            Spacer(Modifier.width(MM.space.s))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .height(MM.space.m)
                                        .clip(MM.radius.small)
                                        .background(MM.colors.surfaceSunk.copy(alpha = alpha))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(MM.space.s + MM.space.xs)
                                        .clip(MM.radius.small)
                                        .background(MM.colors.surfaceSunk.copy(alpha = alpha))
                                )
                            }
                        }
                    }
                }
            }
            is CirclesListState.NotConnected -> {
                Text("Circles is not connected yet.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
            is CirclesListState.Ready -> {
                val learningCircles = state.circles.filter { it.space == "learning" || it.space == null }
                if (learningCircles.isEmpty()) {
                    MMCard(contentPadding = MM.space.m) {
                        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            Text("No Study Circles Found", style = MM.type.bodyStrong, color = MM.colors.ink)
                            Text("Create a study group or course cohort circle to share study packs with peers.", style = MM.type.caption, color = MM.colors.inkSecondary)
                            PrimaryButton(
                                text = "Create Study Circle",
                                onClick = {
                                    onDismiss()
                                    onNavigateCreateCircle()
                                },
                                modifier = Modifier.testTag("create_study_circle_btn")
                            )
                        }
                    }
                } else {
                    Text("Select Target Circle", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = MM.colors.inkSecondary)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = MM.space.xxl * 8)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        learningCircles.forEach { circle ->
                            val isSelected = circle.id == selectedCircleId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MM.radius.small)
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            if (selectedCircleId != circle.id) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedCircleId = circle.id
                                            }
                                        }
                                    )
                                    .padding(vertical = MM.space.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Spacer(Modifier.width(MM.space.s))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = circle.name,
                                        style = MM.type.bodyStrong,
                                        color = MM.colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    circle.description?.let { desc ->
                                        Text(
                                            text = desc,
                                            style = MM.type.caption,
                                            color = MM.colors.inkSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(MM.space.m))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MM.type.caption,
                            color = MM.colors.danger
                        )
                        Spacer(Modifier.height(MM.space.xs))
                    }

                    PrimaryButton(
                        text = if (isPublishing) "Publishing..." else "Publish to Circle",
                        enabled = selectedCircleId != null && !isPublishing,
                        onClick = {
                            val circleId = selectedCircleId ?: return@PrimaryButton
                            val targetCircle = learningCircles.firstOrNull { it.id == circleId } ?: return@PrimaryButton
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isPublishing = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    repo.createPost(
                                        circleId = circleId,
                                        body = cramSheetMarkdown,
                                        type = PostType.Study
                                    )
                                    isPublishing = false
                                    onPublished(targetCircle.name)
                                    onDismiss()
                                } catch (e: Exception) {
                                    isPublishing = false
                                    errorMessage = e.message ?: "Failed to publish study pack. Please try again."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("publish_to_circle_button")
                    )
                }
            }
        }
    }
}
