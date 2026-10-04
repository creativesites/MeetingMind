package com.craftflowtechnologies.meetingmind.feature.fellowship.circles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayerStatus
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.core.model.MemberRole
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipEmptyState
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipNote
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipPrimaryButton
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipSecondaryButton
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipSectionTitle
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldInk
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

enum class CircleTab(val label: String) {
    PRAYER("Prayer"),
    TESTIMONIES("Testimonies"),
    STUDY("Study"),
    PEOPLE("People")
}

@Composable
fun CircleScreen(
    circleId: String,
    viewModel: CircleViewModel,
    faithNotes: List<Note> = emptyList(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val circle by androidx.compose.runtime.remember(circleId) { viewModel.circle(circleId) }.collectAsState()
    val members by androidx.compose.runtime.remember(circleId) { viewModel.members(circleId) }.collectAsState()
    val prayers by androidx.compose.runtime.remember(circleId) { viewModel.prayers(circleId) }.collectAsState()
    val testimonies by androidx.compose.runtime.remember(circleId) { viewModel.testimonies(circleId) }.collectAsState()
    val sermons by androidx.compose.runtime.remember(circleId) { viewModel.sermons(circleId) }.collectAsState()
    val cachedName by viewModel.cachedDisplayName.collectAsState()

    var showPostPrayer by remember { mutableStateOf(false) }
    var showPostTestimony by remember { mutableStateOf(false) }
    var testimonyLinkedPrayer by remember { mutableStateOf<CirclePrayer?>(null) }
    var showShareSermon by remember { mutableStateOf(false) }

    if (circle == null) {
        Scaffold(containerColor = SurfaceBase) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading circle...", color = InkMuted, fontSize = 15.sp)
            }
        }
        return
    }

    CircleScreenContent(
        circle = circle!!,
        members = members,
        prayers = prayers,
        testimonies = testimonies,
        sermons = sermons,
        cachedDisplayName = cachedName,
        onNavigateBack = onNavigateBack,
        onPrayFor = { prayerId -> viewModel.prayFor(circleId, prayerId) },
        onMarkAnswered = { prayer ->
            viewModel.markPrayerAnswered(circleId, prayer.id)
            testimonyLinkedPrayer = prayer
            showPostTestimony = true
        },
        onPraiseTestimony = { testimonyId -> viewModel.praiseTestimony(circleId, testimonyId) },
        onOpenPostPrayer = { showPostPrayer = true },
        onOpenPostTestimony = {
            testimonyLinkedPrayer = null
            showPostTestimony = true
        },
        onOpenShareSermon = { showShareSermon = true },
        onLeaveCircle = {
            viewModel.leaveCircle(circleId, onNavigateBack)
        },
        onShareInvite = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Join ${circle!!.name} on MeetingMind")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Join our private fellowship circle '${circle!!.name}':\n${circle!!.inviteCode}"
                )
            }
            context.startActivity(Intent.createChooser(intent, "Invite to ${circle!!.name}"))
        },
        onCopyInvite = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Circle invite", circle!!.inviteCode))
        }
    )

    if (showPostPrayer) {
        PostPrayerSheet(
            authorDisplayName = cachedName.ifBlank { "You" },
            onDismiss = { showPostPrayer = false },
            onPost = { text, isUrgent ->
                viewModel.postPrayer(circleId, text, isUrgent, cachedName)
                showPostPrayer = false
            }
        )
    }

    if (showPostTestimony) {
        PostTestimonySheet(
            authorDisplayName = cachedName.ifBlank { "You" },
            initialTitle = testimonyLinkedPrayer?.let { "Answered: ${it.requestText.take(40)}..." }.orEmpty(),
            prayerRequestId = testimonyLinkedPrayer?.id,
            onDismiss = { showPostTestimony = false },
            onPost = { title, story, scripture ->
                viewModel.postTestimony(circleId, title, story, scripture, testimonyLinkedPrayer?.id, cachedName)
                showPostTestimony = false
            }
        )
    }

    if (showShareSermon) {
        ShareSermonPickerSheet(
            notes = faithNotes,
            onDismiss = { showShareSermon = false },
            onSelectNote = { note ->
                viewModel.shareSermon(
                    circleId = circleId,
                    title = note.title,
                    preacher = note.metadata["speaker"] ?: note.metadata["preacher"],
                    scripturePassage = note.metadata["passage"],
                    sermonDate = note.metadata["date"],
                    discussionGuideJson = "",
                    transcriptSummary = note.plainText.take(500),
                    audioDurationSec = 0L,
                    displayName = cachedName
                )
                showShareSermon = false
            }
        )
    }
}

@Composable
internal fun CircleScreenContent(
    circle: Circle,
    members: List<CircleMember>,
    prayers: List<CirclePrayer>,
    testimonies: List<CircleTestimony>,
    sermons: List<CircleSermon>,
    cachedDisplayName: String,
    onNavigateBack: () -> Unit,
    onPrayFor: (String) -> Unit,
    onMarkAnswered: (CirclePrayer) -> Unit,
    onPraiseTestimony: (String) -> Unit,
    onOpenPostPrayer: () -> Unit,
    onOpenPostTestimony: () -> Unit,
    onOpenShareSermon: () -> Unit,
    onLeaveCircle: () -> Unit,
    onShareInvite: () -> Unit,
    onCopyInvite: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(CircleTab.PRAYER) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SurfaceBase,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Text(circle.avatarEmoji, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            circle.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Serif,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = FaithGoldWash,
                            border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "E2EE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FaithGoldInk,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "${members.size} ${if (members.size == 1) "member" else "members"}",
                        fontSize = 12.sp,
                        color = InkMuted
                    )
                }
                IconButton(onClick = onShareInvite) {
                    Icon(Icons.Filled.Share, contentDescription = "Invite", tint = Ink)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Ink)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy invite link") },
                            onClick = {
                                menuOpen = false
                                onCopyInvite()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Leave circle", color = Danger) },
                            onClick = {
                                menuOpen = false
                                onLeaveCircle()
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Segment row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (tab in CircleTab.entries) {
                    val isSelected = tab == selectedTab
                    val count = when (tab) {
                        CircleTab.PRAYER -> prayers.size
                        CircleTab.TESTIMONIES -> testimonies.size
                        CircleTab.STUDY -> sermons.size
                        CircleTab.PEOPLE -> members.size
                    }
                    Surface(
                        onClick = { selectedTab = tab },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) FaithGoldWash else SurfaceSunk,
                        border = BorderStroke(1.dp, if (isSelected) FaithGold else Line),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tab.label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) FaithGoldInk else InkSecondary
                            )
                            if (count > 0) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    count.toString(),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) FaithGoldInk else InkMuted
                                )
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                CircleTab.PRAYER -> {
                    PrayerTabContent(
                        prayers = prayers,
                        currentDisplayName = cachedDisplayName,
                        onPrayFor = onPrayFor,
                        onMarkAnswered = onMarkAnswered,
                        onOpenPostPrayer = onOpenPostPrayer
                    )
                }
                CircleTab.TESTIMONIES -> {
                    TestimoniesTabContent(
                        testimonies = testimonies,
                        onPraiseTestimony = onPraiseTestimony,
                        onOpenPostTestimony = onOpenPostTestimony
                    )
                }
                CircleTab.STUDY -> {
                    StudyTabContent(
                        sermons = sermons,
                        onOpenShareSermon = onOpenShareSermon
                    )
                }
                CircleTab.PEOPLE -> {
                    PeopleTabContent(
                        members = members,
                        circle = circle,
                        onShareInvite = onShareInvite,
                        onCopyInvite = onCopyInvite,
                        onLeaveCircle = onLeaveCircle
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerTabContent(
    prayers: List<CirclePrayer>,
    currentDisplayName: String,
    onPrayFor: (String) -> Unit,
    onMarkAnswered: (CirclePrayer) -> Unit,
    onOpenPostPrayer: () -> Unit
) {
    if (prayers.isEmpty()) {
        FellowshipEmptyState(
            icon = Icons.Filled.VolunteerActivism,
            title = "No prayer requests yet",
            body = "Be the first to share a prayer request with your circle. Only members can see and pray for it.",
            actionLabel = "Share prayer request",
            onAction = onOpenPostPrayer
        )
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FellowshipSectionTitle("Prayer wall", modifier = Modifier.weight(1f))
                    FellowshipSecondaryButton(
                        text = "Ask for prayer",
                        onClick = onOpenPostPrayer,
                        icon = Icons.Filled.Add
                    )
                }
            }
            items(prayers, key = { it.id }) { prayer ->
                val isAuthor = prayer.authorName == currentDisplayName || prayer.authorId.isNotBlank()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceRaised,
                    border = BorderStroke(1.dp, Line),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(FaithGoldWash),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = prayer.authorName.take(2).uppercase()
                                Text(initials, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FaithGoldInk)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(prayer.authorName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(Formatters.formatDateRelative(prayer.createdAt), fontSize = 11.5.sp, color = InkMuted)
                            }
                            if (prayer.isUrgent) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Danger.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        "URGENT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Danger,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (prayer.status == CirclePrayerStatus.ANSWERED) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = FaithGoldWash,
                                    border = BorderStroke(1.dp, FaithGold)
                                ) {
                                    Text(
                                        "ANSWERED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FaithGoldInk,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            prayer.requestText,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = Ink,
                            modifier = Modifier.padding(top = 12.dp, bottom = 14.dp)
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { onPrayFor(prayer.id) },
                                shape = RoundedCornerShape(50),
                                color = if (prayer.prayedByMe) FaithGoldWash else SurfaceSunk,
                                border = BorderStroke(1.dp, if (prayer.prayedByMe) FaithGold else Line)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (prayer.prayedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (prayer.prayedByMe) FaithGold else InkMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (prayer.prayedByMe) "Prayed (${prayer.prayerCount})" else "I prayed (${prayer.prayerCount})",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (prayer.prayedByMe) FaithGoldInk else InkSecondary
                                    )
                                }
                            }

                            if (prayer.status == CirclePrayerStatus.ACTIVE) {
                                Text(
                                    "Mark answered",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Accent,
                                    modifier = Modifier.clickable { onMarkAnswered(prayer) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestimoniesTabContent(
    testimonies: List<CircleTestimony>,
    onPraiseTestimony: (String) -> Unit,
    onOpenPostTestimony: () -> Unit
) {
    if (testimonies.isEmpty()) {
        FellowshipEmptyState(
            icon = Icons.Filled.Church,
            title = "No testimonies yet",
            body = "Share what God has done or celebrate an answered prayer. Encourage your fellow brothers and sisters.",
            actionLabel = "Share testimony",
            onAction = onOpenPostTestimony
        )
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FellowshipSectionTitle("Testimonies & Praise", modifier = Modifier.weight(1f))
                    FellowshipSecondaryButton(
                        text = "Share praise",
                        onClick = onOpenPostTestimony,
                        icon = Icons.Filled.Add
                    )
                }
            }
            items(testimonies, key = { it.id }) { testimony ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceRaised,
                    border = BorderStroke(1.dp, Line),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(FaithGoldWash),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = testimony.authorName.take(2).uppercase()
                                Text(initials, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FaithGoldInk)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(testimony.authorName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(Formatters.formatDateRelative(testimony.createdAt), fontSize = 11.5.sp, color = InkMuted)
                            }
                        }

                        Text(
                            testimony.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Serif,
                            color = Ink,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )

                        Text(
                            testimony.storyText,
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = InkSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (!testimony.scriptureRef.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceSunk,
                                border = BorderStroke(1.dp, Line),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Text(
                                    testimony.scriptureRef,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FaithGoldInk,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Surface(
                                onClick = { onPraiseTestimony(testimony.id) },
                                shape = RoundedCornerShape(50),
                                color = if (testimony.praisedByMe) FaithGoldWash else SurfaceSunk,
                                border = BorderStroke(1.dp, if (testimony.praisedByMe) FaithGold else Line)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🙌 Amen (${testimony.praiseCount})",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (testimony.praisedByMe) FaithGoldInk else InkSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyTabContent(
    sermons: List<CircleSermon>,
    onOpenShareSermon: () -> Unit
) {
    if (sermons.isEmpty()) {
        FellowshipEmptyState(
            icon = Icons.Filled.Church,
            title = "No studies shared yet",
            body = "Record Sunday's sermon or a Bible study on MeetingMind and share the discussion questions with your circle.",
            actionLabel = "Share a sermon study",
            onAction = onOpenShareSermon
        )
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FellowshipSectionTitle("Sermon studies", modifier = Modifier.weight(1f))
                    FellowshipSecondaryButton(
                        text = "Share study",
                        onClick = onOpenShareSermon,
                        icon = Icons.Filled.Add
                    )
                }
            }
            items(sermons, key = { it.id }) { sermon ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceRaised,
                    border = BorderStroke(1.dp, Line),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            sermon.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Serif,
                            color = Ink
                        )
                        val meta = listOfNotNull(sermon.preacher, sermon.scripturePassage, sermon.sermonDate).joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(meta, fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                        }
                        if (sermon.transcriptSummary.isNotBlank()) {
                            Text(
                                sermon.transcriptSummary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = InkSecondary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleTabContent(
    members: List<CircleMember>,
    circle: Circle,
    onShareInvite: () -> Unit,
    onCopyInvite: () -> Unit,
    onLeaveCircle: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            FellowshipSectionTitle("Invite to circle", top = 12.dp)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceRaised,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Private group invite link", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        "Anyone with this link can join. Circles are encrypted and designed for 5–15 close members.",
                        fontSize = 12.5.sp,
                        color = InkMuted,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FellowshipPrimaryButton(
                            text = "Share invite link",
                            onClick = onShareInvite,
                            icon = Icons.Filled.Share,
                            modifier = Modifier.weight(1f)
                        )
                        FellowshipSecondaryButton(
                            text = "Copy link",
                            onClick = onCopyInvite,
                            icon = Icons.Filled.ContentCopy
                        )
                    }
                }
            }
        }

        item {
            FellowshipSectionTitle("Members (${members.size})")
        }

        items(members, key = { it.id }) { member ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceSunk,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(FaithGoldWash),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = member.displayName.take(2).uppercase()
                        Text(initials, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FaithGoldInk)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            if (member.isSelf) {
                                Spacer(Modifier.width(6.dp))
                                Text("(You)", fontSize = 12.sp, color = InkMuted)
                            }
                        }
                        Text("Joined ${Formatters.formatDateRelative(member.joinedAt)}", fontSize = 12.sp, color = InkMuted)
                    }
                    if (member.role == MemberRole.ADMIN) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = FaithGoldWash,
                            border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Leader",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FaithGoldInk,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onLeaveCircle,
                shape = RoundedCornerShape(14.dp),
                color = SurfaceRaised,
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Leave this circle", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
