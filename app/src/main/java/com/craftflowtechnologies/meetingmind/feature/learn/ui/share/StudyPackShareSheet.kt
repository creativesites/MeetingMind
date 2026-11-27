package com.craftflowtechnologies.meetingmind.feature.learn.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.ui.gate.PaidPlanCard
import com.craftflowtechnologies.meetingmind.feature.learn.ui.gate.StudyGate
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

/** The result of tapping a Circle: the new selection, and whether the tap was refused by the plan. */
data class CircleSelection(val selected: Set<String>, val blocked: Boolean) {
    companion object {
        /**
         * One Circle is free. Choosing another Circle at once needs [multiCircleOpen] (a paid plan); tapping a chosen
         * Circle again always unchooses it.
         */
        fun toggle(current: Set<String>, circleId: String, multiCircleOpen: Boolean): CircleSelection = when {
            circleId in current -> CircleSelection(current - circleId, blocked = false)
            current.isEmpty() || multiCircleOpen -> CircleSelection(current + circleId, blocked = false)
            else -> CircleSelection(current, blocked = true)
        }
    }
}

/**
 * The Study version of "share to a Circle": one sheet for the exam plan's cram sheet and a lesson's study pack.
 * One Circle is free; several at once are part of the paid plans ([Capability.CHURCH_ORG_WORKFLOWS]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPackShareSheet(
    title: String,
    markdown: String,
    onDismiss: () -> Unit,
    onPublished: (circleName: String) -> Unit,
    onNavigateCreateCircle: () -> Unit,
    multiCircleOpen: Boolean = StudyGate.open(Capability.CHURCH_ORG_WORKFLOWS),
    onSeePlans: () -> Unit = {}
) {
    StudyTheme {
        val c = MM.colors
        val context = LocalContext.current
        val repo = remember { Circles2.repository(context) }
        val circlesState by repo.circles().collectAsState(CirclesListState.Loading)
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        var selected by remember { mutableStateOf(emptySet<String>()) }
        var blocked by remember { mutableStateOf(false) }
        var isPublishing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surfaceRaised,
            shape = MM.radius.sheet,
            modifier = Modifier.testTag("study_pack_share_sheet")
        ) {
            Column(Modifier.padding(horizontal = MM.space.xl).padding(bottom = MM.space.xl).navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs / 2)) {
                        Text("Share to a Circle", style = MM.type.heading, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (title.isNotBlank()) title else "Post this to a Study Circle for everyone in it.",
                            style = MM.type.caption,
                            color = c.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(MMSize.minTouch)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.inkSecondary)
                    }
                }
                Spacer(Modifier.height(MM.space.m))

                when (val state = circlesState) {
                    is CirclesListState.Loading -> Text("Loading your circles…", style = MM.type.secondary, color = c.inkSecondary)
                    is CirclesListState.NotConnected -> Text("Circles is not connected yet.", style = MM.type.secondary, color = c.inkSecondary)
                    is CirclesListState.Ready -> {
                        val learningCircles = state.circles.filter { it.space == "learning" || it.space == null }
                        if (learningCircles.isEmpty()) {
                            MMCard(contentPadding = MM.space.m) {
                                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                    Text("No study circles yet", style = MM.type.bodyStrong, color = c.ink)
                                    Text("Make a study group or course circle, then share study packs with peers.", style = MM.type.caption, color = c.inkSecondary)
                                    PrimaryButton(
                                        text = "Create a study circle",
                                        onClick = { onDismiss(); onNavigateCreateCircle() },
                                        modifier = Modifier.testTag("create_study_circle_btn")
                                    )
                                }
                            }
                        } else {
                            Text("Choose a circle", style = MM.type.caption, color = c.inkSecondary)
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = MM.space.xxl * 8).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                learningCircles.forEach { circle ->
                                    val isSelected = circle.id in selected
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = MMSize.minTouch)
                                            .selectable(
                                                selected = isSelected,
                                                role = Role.RadioButton,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    val next = CircleSelection.toggle(selected, circle.id, multiCircleOpen)
                                                    selected = next.selected
                                                    blocked = next.blocked
                                                }
                                            )
                                            .testTag("share_circle_${circle.id}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = isSelected, onClick = null)
                                        Spacer(Modifier.width(MM.space.s))
                                        Column(Modifier.weight(1f)) {
                                            Text(circle.name, style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            circle.description?.let {
                                                Text(it, style = MM.type.caption, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(MM.space.m))
                            if (blocked) {
                                PaidPlanCard(
                                    line = "Sharing to several circles at once is part of the paid plans. One circle is free.",
                                    onSeePlans = onSeePlans,
                                    modifier = Modifier.padding(bottom = MM.space.m)
                                )
                            }
                            errorMessage?.let {
                                Text(it, style = MM.type.caption, color = c.danger)
                                Spacer(Modifier.height(MM.space.xs))
                            }
                            PrimaryButton(
                                text = if (isPublishing) "Sharing…" else if (selected.size > 1) "Share to ${selected.size} circles" else "Share to this circle",
                                enabled = selected.isNotEmpty() && !isPublishing,
                                onClick = {
                                    val targets = learningCircles.filter { it.id in selected }
                                    if (targets.isEmpty()) return@PrimaryButton
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isPublishing = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            targets.forEach { target ->
                                                repo.createPost(circleId = target.id, body = markdown, type = PostType.Study)
                                            }
                                            isPublishing = false
                                            onPublished(targets.joinToString(", ") { it.name })
                                            onDismiss()
                                        } catch (e: Exception) {
                                            isPublishing = false
                                            errorMessage = e.message ?: "The study pack couldn't be shared. Try again."
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
    }
}
