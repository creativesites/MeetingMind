package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.feature.circles2.CircleSheet
import com.craftflowtechnologies.meetingmind.ui.theme.MM
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

    var selectedCircleId by remember { mutableStateOf<String?>(null) }
    var isPublishing by remember { mutableStateOf(false) }

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Share Study Pack to Circle",
                    style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MM.colors.ink
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
                Text("Loading study circles...", style = MM.type.secondary, color = MM.colors.inkSecondary)
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
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        learningCircles.forEach { circle ->
                            val isSelected = circle.id == selectedCircleId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedCircleId = circle.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedCircleId = circle.id }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(circle.name, style = MM.type.bodyStrong, color = MM.colors.ink)
                                    circle.description?.let {
                                        Text(it, style = MM.type.caption, color = MM.colors.inkSecondary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(MM.space.m))

                    PrimaryButton(
                        text = if (isPublishing) "Publishing..." else "Publish to Circle",
                        enabled = selectedCircleId != null && !isPublishing,
                        onClick = {
                            val circleId = selectedCircleId ?: return@PrimaryButton
                            val targetCircle = learningCircles.firstOrNull { it.id == circleId } ?: return@PrimaryButton
                            isPublishing = true
                            scope.launch {
                                repo.createPost(
                                    circleId = circleId,
                                    body = cramSheetMarkdown,
                                    type = PostType.Study
                                )
                                isPublishing = false
                                onPublished(targetCircle.name)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("publish_to_circle_button")
                    )
                }
            }
        }
    }
}
