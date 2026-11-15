package com.craftflowtechnologies.meetingmind.feature.faith

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.TestimonyColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-story editorial reader sheet for a Testimony: renders the Prayer -> Answer journey timeline,
 * story beats rail, anchor scripture callout, persistent Amen celebration, visual Share Card,
 * Share Text, and one-tap Share to Circle.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TestimonyDetailSheet(
    story: TestimonyStory,
    joinedCircles: List<Circle>,
    onDismiss: () -> Unit,
    onEditInStudio: (noteId: String) -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onCelebrate: (TestimonyStory) -> Unit,
    onShareCard: (ShareRequest) -> Unit,
    onShareToCircle: (circle: Circle, story: TestimonyStory, onDone: (String) -> Unit) -> Unit,
    onOpenScripture: (String) -> Unit
) {
    TestimonyDetailSheetImpl(
        story = story,
        joinedCircles = joinedCircles,
        onDismiss = onDismiss,
        onEditInStudio = onEditInStudio,
        onOpenNote = onOpenNote,
        onCelebrate = onCelebrate,
        onShareCard = onShareCard,
        onShareToCircle = { circle, target, _, onDone -> onShareToCircle(circle, target, onDone) },
        onOpenScripture = onOpenScripture
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TestimonyDetailSheetWithAnon(
    story: TestimonyStory,
    joinedCircles: List<Circle>,
    onDismiss: () -> Unit,
    onEditInStudio: (noteId: String) -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onCelebrate: (TestimonyStory) -> Unit,
    onShareCard: (ShareRequest) -> Unit,
    onShareToCircle: (circle: Circle, story: TestimonyStory, anonymous: Boolean, onDone: (String) -> Unit) -> Unit,
    onOpenScripture: (String) -> Unit
) {
    TestimonyDetailSheetImpl(
        story = story,
        joinedCircles = joinedCircles,
        onDismiss = onDismiss,
        onEditInStudio = onEditInStudio,
        onOpenNote = onOpenNote,
        onCelebrate = onCelebrate,
        onShareCard = onShareCard,
        onShareToCircle = onShareToCircle,
        onOpenScripture = onOpenScripture
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TestimonyDetailSheetImpl(
    story: TestimonyStory,
    joinedCircles: List<Circle>,
    onDismiss: () -> Unit,
    onEditInStudio: (noteId: String) -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onCelebrate: (TestimonyStory) -> Unit,
    onShareCard: (ShareRequest) -> Unit,
    onShareToCircle: (circle: Circle, story: TestimonyStory, anonymous: Boolean, onDone: (String) -> Unit) -> Unit,
    onOpenScripture: (String) -> Unit
) {
    val c = MM.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCirclePicker by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val dateLabel = remember(story.storyDateMs) {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(story.storyDateMs))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.m, vertical = MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = c.ink)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        story.circleName?.let { "Shared in $it" } ?: "Remembrance Story",
                        style = MM.type.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = if (story.circleName != null) TestimonyColors.CircleSage else FaithGold
                    )
                    Text(dateLabel, style = MM.type.caption, color = c.inkSecondary)
                }
                if (story.isPersonal && story.noteId != null) {
                    OutlinedButton(
                        onClick = { onEditInStudio(story.noteId) },
                        shape = MM.radius.pill,
                        border = BorderStroke(1.dp, c.lineSoft)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = c.ink)
                        Spacer(Modifier.width(MM.space.xs))
                        Text("Edit", style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = c.ink)
                    }
                    IconButton(onClick = { onOpenNote(story.noteId) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open full note", tint = c.inkSecondary)
                    }
                }
            }

            HorizontalDivider(color = c.lineSoft)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MM.space.l, vertical = MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                statusBanner?.let { msg ->
                    Surface(
                        shape = MM.radius.small,
                        color = TestimonyColors.CircleSage.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, TestimonyColors.CircleSage.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TestimonyColors.CircleSage, modifier = Modifier.size(16.dp))
                            Text(msg, style = MM.type.secondary, fontWeight = FontWeight.Medium, color = c.ink)
                        }
                    }
                }

                // Meta tags
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    story.category?.let { cat ->
                        val accent = testimonyCategoryAccent(cat)
                        MetaTagPill(text = cat.label, tint = accent)
                    }
                    if (story.hasAudio) {
                        MetaTagPill(text = "Voice note", tint = FaithGold, icon = Icons.Filled.GraphicEq)
                    }
                    if (story.sharedToCircles.isNotEmpty() && story.isPersonal) {
                        story.sharedToCircles.forEach { circleName ->
                            MetaTagPill(
                                text = "In $circleName",
                                tint = TestimonyColors.CircleSage,
                                icon = Icons.Filled.Groups
                            )
                        }
                    }
                }

                // Headline
                Text(
                    story.title.ifBlank { "Testimony of God's Faithfulness" },
                    style = MM.type.title.copy(fontFamily = FontFamily.Serif),
                    color = c.ink
                )

                // Connected 2-node Prayer -> Answer journey timeline
                if (story.linkedPrayerId != null || story.prayerDurationDays != null) {
                    val durationLabel = formatDurationLabel(story.prayerDurationDays) ?: "Prayer answered"
                    val prayedDateLabel = story.prayerStartedAtMs?.let {
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
                    }
                    Surface(
                        onClick = { story.linkedPrayerId?.let(onOpenNote) },
                        enabled = story.linkedPrayerId != null,
                        shape = MM.radius.card,
                        color = if (c.isDark) TestimonyColors.SpotlightTintDark else TestimonyColors.SpotlightTintLight,
                        border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.32f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = FaithGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(MM.space.xs))
                                Text(durationLabel, style = MM.type.bodyStrong, color = FaithGold)
                            }
                            if (story.linkedPrayerTitle != null || prayedDateLabel != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.inkMuted))
                                    Text(
                                        buildString {
                                            if (prayedDateLabel != null) append("Started $prayedDateLabel")
                                            story.linkedPrayerTitle?.let {
                                                if (isNotEmpty()) append(" · ")
                                                append("\"$it\"")
                                            }
                                        },
                                        style = MM.type.secondary,
                                        color = c.inkSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(FaithGold))
                                Text("Answered $dateLabel", style = MM.type.secondary, fontWeight = FontWeight.Medium, color = c.ink)
                            }
                            if (story.linkedPrayerId != null) {
                                Text(
                                    "Tap to revisit the original prayer request →",
                                    style = MM.type.caption,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FaithGold
                                )
                            }
                        }
                    }
                }

                // Story Beats
                val beats = story.beats
                val hasStructuredBeats = beats.situation.isNotBlank() || beats.prayedFor.isNotBlank() ||
                    beats.learned.isNotBlank()
                if (hasStructuredBeats) {
                    if (beats.situation.isNotBlank()) {
                        EditorialBeatSection("THE SITUATION", beats.situation)
                    }
                    if (beats.prayedFor.isNotBlank()) {
                        EditorialBeatSection("WHAT I PRAYED", beats.prayedFor)
                    }
                    if (beats.whatHappened.isNotBlank()) {
                        EditorialBeatSection("HOW GOD MOVED", beats.whatHappened, emphasized = true)
                    }
                    if (beats.learned.isNotBlank()) {
                        EditorialBeatSection("WHAT STAYED WITH ME", beats.learned)
                    }
                } else {
                    Text(
                        story.fullText.ifBlank { story.excerpt },
                        style = MM.type.body,
                        color = c.ink
                    )
                }

                // Anchor Scripture Callout
                story.verseRef?.let { ref ->
                    Surface(
                        onClick = { onOpenScripture(ref) },
                        shape = MM.radius.card,
                        color = if (c.isDark) TestimonyColors.ScriptureWashDark else TestimonyColors.ScriptureWashLight,
                        border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = FaithGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(MM.space.xs))
                                Text(ref, style = MM.type.bodyStrong, color = FaithGold)
                                Spacer(Modifier.weight(1f))
                                Text("Open Bible", style = MM.type.caption, color = FaithGold)
                            }
                            story.verseText?.takeIf { it.isNotBlank() }?.let { verseBody ->
                                Text(
                                    "\"$verseBody\"",
                                    style = MM.type.scripture.copy(fontStyle = FontStyle.Italic),
                                    color = c.ink
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = c.lineSoft)

            // Bottom Celebration & Multi-channel Share Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = MM.space.m, vertical = MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Button(
                        onClick = { onCelebrate(story) },
                        shape = MM.radius.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FaithGold.copy(alpha = if (story.userRejoiced) 0.22f else 0.14f),
                            contentColor = c.ink
                        ),
                        border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (story.userRejoiced) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = FaithGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(MM.space.xs))
                        Text(
                            if (story.rejoiceCount > 0) "Amen · ${story.rejoiceCount}" else "Amen",
                            style = MM.type.secondary,
                            fontWeight = FontWeight.SemiBold,
                            color = c.ink
                        )
                    }

                    Button(
                        onClick = { onShareCard(buildTestimonyShareCardRequest(story)) },
                        shape = MM.radius.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.ink,
                            contentColor = c.onInk
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(MM.space.xs))
                        Text("Visual Card", style = MM.type.secondary, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, story.title.ifBlank { "Testimony" })
                                putExtra(Intent.EXTRA_TEXT, buildTestimonyShareText(story, includeAppLink = true))
                            }
                            context.startActivity(Intent.createChooser(send, "Share testimony"))
                        },
                        shape = MM.radius.small,
                        border = BorderStroke(1.dp, c.lineSoft),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = c.inkSecondary)
                        Spacer(Modifier.width(MM.space.xs))
                        Text("Share Text", style = MM.type.secondary, color = c.ink)
                    }

                    if (story.isPersonal) {
                        OutlinedButton(
                            onClick = { showCirclePicker = true },
                            shape = MM.radius.small,
                            border = BorderStroke(1.dp, TestimonyColors.CircleSage.copy(alpha = 0.55f)),
                            modifier = Modifier.weight(1.2f).testTag("testimony_detail_share_circle")
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp), tint = TestimonyColors.CircleSage)
                            Spacer(Modifier.width(MM.space.xs))
                            Text(
                                if (story.sharedToCircles.isEmpty()) "Share to Circle" else "Share Again",
                                style = MM.type.secondary,
                                fontWeight = FontWeight.SemiBold,
                                color = TestimonyColors.CircleSage
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCirclePicker) {
        TestimonyCircleShareSheet(
            story = story,
            joinedCircles = joinedCircles,
            onDismiss = { showCirclePicker = false },
            onShare = { circle, anonymous ->
                showCirclePicker = false
                onShareToCircle(circle, story, anonymous) { message -> statusBanner = message }
            }
        )
    }
}

/**
 * Reusable Circle picker modal sheet for sharing a personal testimony into any joined Circle.
 * Supports optional anonymous posting and highlights Circles that already received the story.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TestimonyCircleShareSheet(
    story: TestimonyStory,
    joinedCircles: List<Circle>,
    onDismiss: () -> Unit,
    onShare: (circle: Circle, anonymous: Boolean) -> Unit
) {
    val c = MM.colors
    var postAnonymously by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MM.space.l, vertical = MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(TestimonyColors.CircleSage.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = TestimonyColors.CircleSage, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(MM.space.s))
                Column(Modifier.weight(1f)) {
                    Text("Share to a Circle", style = MM.type.heading, color = c.ink)
                    Text(
                        story.title.ifBlank { "Testimony of God's Faithfulness" },
                        style = MM.type.caption,
                        color = c.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = c.inkSecondary)
                }
            }

            Text(
                "Your personal testimony note stays private on your phone. Picking a Circle publishes a testimony post there to encourage members.",
                style = MM.type.secondary,
                color = c.inkSecondary
            )

            if (joinedCircles.isNotEmpty()) {
                Surface(
                    shape = MM.radius.small,
                    color = c.surfaceSunk,
                    border = BorderStroke(1.dp, c.lineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Post anonymously", style = MM.type.bodyStrong, color = c.ink)
                            Text("Share the praise without showing your display name", style = MM.type.caption, color = c.inkSecondary)
                        }
                        Switch(
                            checked = postAnonymously,
                            onCheckedChange = { postAnonymously = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = c.onInk, checkedTrackColor = TestimonyColors.CircleSage)
                        )
                    }
                }
            }

            if (joinedCircles.isEmpty()) {
                Surface(
                    shape = MM.radius.card,
                    color = c.surfaceSunk,
                    border = BorderStroke(1.dp, c.lineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(MM.space.l),
                        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Text("No Circles joined yet", style = MM.type.bodyStrong, color = c.ink)
                        Text(
                            "Join or create a Faith or Community Circle from the Circles tab, then share your testimony in one tap.",
                            style = MM.type.secondary,
                            color = c.inkSecondary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    joinedCircles.forEach { circle ->
                        val alreadyShared = story.sharedToCircles.any { it.equals(circle.name, ignoreCase = true) }
                        Surface(
                            onClick = { onShare(circle, postAnonymously) },
                            shape = MM.radius.small,
                            color = if (alreadyShared) TestimonyColors.CircleSage.copy(alpha = 0.08f) else c.surfaceSunk,
                            border = BorderStroke(1.dp, if (alreadyShared) TestimonyColors.CircleSage.copy(alpha = 0.4f) else c.lineSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.m),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Groups, contentDescription = null, tint = TestimonyColors.CircleSage, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(MM.space.s))
                                Column(Modifier.weight(1f)) {
                                    Text(circle.name, style = MM.type.bodyStrong, color = c.ink)
                                    Text(
                                        "${circle.memberCount} members" + if (alreadyShared) " · Shared ✓" else "",
                                        style = MM.type.caption,
                                        color = if (alreadyShared) TestimonyColors.CircleSage else c.inkSecondary
                                    )
                                }
                                Icon(
                                    if (alreadyShared) Icons.AutoMirrored.Filled.ArrowForward else Icons.Filled.IosShare,
                                    contentDescription = "Share to ${circle.name}",
                                    tint = FaithGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = c.ink)
                }
            }
        }
    }
}

@Composable
private fun MetaTagPill(
    text: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = MM.radius.pill,
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            }
            Text(text, style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}

@Composable
private fun EditorialBeatSection(
    eyebrow: String,
    text: String,
    emphasized: Boolean = false
) {
    val c = MM.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (emphasized) {
                    Modifier
                        .clip(MM.radius.small)
                        .background(FaithGold.copy(alpha = 0.09f))
                        .padding(MM.space.m)
                } else {
                    Modifier.padding(vertical = 2.dp)
                }
            ),
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        Text(
            eyebrow,
            style = MM.type.caption,
            fontWeight = FontWeight.Bold,
            color = FaithGold
        )
        Text(
            text,
            style = MM.type.body,
            color = c.ink
        )
    }
}
