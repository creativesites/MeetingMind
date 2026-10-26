package com.craftflowtechnologies.meetingmind.feature.faith

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.FeatureGate
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.feature.circles2.CircleSheet
import com.craftflowtechnologies.meetingmind.feature.circles2.CirclesPalette
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Ministry Sermon-to-Circle Pack Sheet.
 * Available exclusively on the MeetingMind Church plan ($49.99/mo).
 *
 * Allows ministry leaders and pastors to package:
 * 1. Sermon Summary & Key Points
 * 2. Scripture References & Memory Verses
 * 3. Small-Group Discussion Questions
 * and publish them to their church's Faith Circles with a single confirmation.
 */
@Composable
fun SermonCirclePackSheet(
    sermonTitle: String,
    preacher: String?,
    summaryText: String?,
    scriptures: List<String>,
    discussionQuestions: List<String>,
    onDismiss: () -> Unit,
    onNavigatePaywall: () -> Unit,
    onNavigateCreateCircle: () -> Unit,
    onPublished: (circleName: String, count: Int) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val circlesState by repo.circles().collectAsState(CirclesListState.Loading)
    val scope = rememberCoroutineScope()

    val hasChurchCapability = remember { FeatureGate.can(Capability.CHURCH_ORG_WORKFLOWS) }

    var publishSummary by remember { mutableStateOf(true) }
    var publishScriptures by remember { mutableStateOf(scriptures.isNotEmpty()) }
    var publishQuestions by remember { mutableStateOf(discussionQuestions.isNotEmpty()) }

    var selectedCircleId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Sermon Ministry Pack",
                    style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MM.colors.ink
                )
                Text(
                    "Publish sermon takeaways, verses and study questions to circles",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }

        if (!hasChurchCapability) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CirclesPalette.AccentSoft,
                border = BorderStroke(1.dp, CirclesPalette.Accent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Church Plan Ministry Workflow",
                        style = MM.type.bodyStrong,
                        color = CirclesPalette.AccentDeep
                    )
                    Text(
                        "The Sermon-to-Circle Pack is an organizational capability included with MeetingMind Church. It automatically transforms sermon messages into small-group guides and distributes them across your ministry circles.",
                        style = MM.type.body,
                        color = MM.colors.ink
                    )
                    Spacer(Modifier.height(4.dp))
                    PrimaryButton("Learn about Church plan", onClick = {
                        onDismiss()
                        onNavigatePaywall()
                    })
                }
            }
            return@CircleSheet
        }

        when (val state = circlesState) {
            CirclesListState.Loading -> {
                StatusLine(StatusKind.Info, "Loading ministry circles…")
            }
            CirclesListState.NotConnected -> {
                EmptyState(
                    title = "Circles not connected",
                    body = "Connect to Circles to share sermon study guides with your church members.",
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
                            "No Faith Circles found. Create a circle for your congregation or small group to begin publishing.",
                            style = MM.type.body,
                            color = MM.colors.inkSecondary
                        )
                        PrimaryButton("Create a ministry circle", {
                            onDismiss()
                            onNavigateCreateCircle()
                        })
                    }
                } else {
                    val defaultPick = selectedCircleId?.takeIf { id -> faithCircles.any { it.id == id } } ?: faithCircles.first().id
                    if (selectedCircleId == null) selectedCircleId = defaultPick
                    val currentCircle = faithCircles.firstOrNull { it.id == selectedCircleId } ?: faithCircles.first()

                    SectionHeader("Destination Circle")
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

                    SectionHeader("Pack Contents")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!summaryText.isNullOrBlank()) {
                            MMCard(onClick = { publishSummary = !publishSummary }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = publishSummary,
                                        onCheckedChange = { publishSummary = it },
                                        colors = CheckboxDefaults.colors(checkedColor = CirclesPalette.Accent)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Sermon Takeaways & Message", style = MM.type.bodyStrong, color = MM.colors.ink)
                                        Text(summaryText.take(100) + "…", style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (scriptures.isNotEmpty()) {
                            MMCard(onClick = { publishScriptures = !publishScriptures }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = publishScriptures,
                                        onCheckedChange = { publishScriptures = it },
                                        colors = CheckboxDefaults.colors(checkedColor = CirclesPalette.Accent)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Scripture References (${scriptures.size})", style = MM.type.bodyStrong, color = MM.colors.ink)
                                        Text(scriptures.joinToString(", "), style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (discussionQuestions.isNotEmpty()) {
                            MMCard(onClick = { publishQuestions = !publishQuestions }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = publishQuestions,
                                        onCheckedChange = { publishQuestions = it },
                                        colors = CheckboxDefaults.colors(checkedColor = CirclesPalette.Accent)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Small Group Study Guide (${discussionQuestions.size} questions)", style = MM.type.bodyStrong, color = MM.colors.ink)
                                        Text(discussionQuestions.first(), style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    errorMsg?.let { StatusLine(StatusKind.Error, it) }

                    val count = (if (publishSummary) 1 else 0) + (if (publishScriptures) 1 else 0) + (if (publishQuestions) 1 else 0)

                    PrimaryButton(
                        if (sending) "Publishing Pack…" else "Publish Ministry Pack to ${currentCircle.name}",
                        onClick = {
                            if (count == 0) return@PrimaryButton
                            sending = true
                            errorMsg = null
                            scope.launch {
                                var published = 0
                                val speakerLine = preacher?.let { " · Speaker: $it" } ?: ""
                                if (publishSummary && !summaryText.isNullOrBlank()) {
                                    val content = "📖 Sermon: $sermonTitle$speakerLine\n\n$summaryText"
                                    repo.createPost(
                                        circleId = currentCircle.id,
                                        body = content,
                                        type = PostType.Devotional,
                                        anonymous = false,
                                        requestId = UUID.randomUUID().toString()
                                    )
                                    published++
                                }
                                if (publishScriptures && scriptures.isNotEmpty()) {
                                    val verseContent = "📜 Scriptures Cited:\n" + scriptures.joinToString("\n") { "• $it" }
                                    repo.createPost(
                                        circleId = currentCircle.id,
                                        body = verseContent,
                                        type = PostType.Study,
                                        anonymous = false,
                                        requestId = UUID.randomUUID().toString()
                                    )
                                    published++
                                }
                                if (publishQuestions && discussionQuestions.isNotEmpty()) {
                                    val questionsContent = "💬 Small Group Discussion Guide:\n" +
                                            discussionQuestions.mapIndexed { idx, q -> "${idx + 1}. $q" }.joinToString("\n\n")
                                    repo.createPost(
                                        circleId = currentCircle.id,
                                        body = questionsContent,
                                        type = PostType.Study,
                                        anonymous = false,
                                        requestId = UUID.randomUUID().toString()
                                    )
                                    published++
                                }
                                sending = false
                                onPublished(currentCircle.name, published)
                                onDismiss()
                            }
                        },
                        enabled = !sending && count > 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
