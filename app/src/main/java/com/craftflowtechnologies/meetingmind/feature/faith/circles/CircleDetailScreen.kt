package com.craftflowtechnologies.meetingmind.feature.faith.circles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayerStatus
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldInk
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CircleDetailScreen(
    circleId: String,
    viewModel: CirclesViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val circle by viewModel.observeCircle(circleId).collectAsState()
    val members by viewModel.observeMembers(circleId).collectAsState()
    val prayers by viewModel.observePrayers(circleId).collectAsState()
    val testimonies by viewModel.observeTestimonies(circleId).collectAsState()
    val sermons by viewModel.observeSermons(circleId).collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showPostPrayerDialog by remember { mutableStateOf(false) }
    var showPostTestimonyDialog by remember { mutableStateOf(false) }
    var linkedPrayerIdForTestimony by remember { mutableStateOf<String?>(null) }
    var showShareSermonDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Prayer Wall", "Testimonies", "Sermons", "Members")

    Scaffold(
        containerColor = SurfaceBase.forTheme(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBase.forTheme())
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnInk.forTheme()
                        )
                    }
                    Text(
                        text = circle?.name ?: "Fellowship Circle",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = OnInk.forTheme(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    IconButton(onClick = {
                        circle?.inviteCode?.let { code ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Circle Invite", code))
                            Toast.makeText(context, "Invite link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share Invite",
                            tint = FaithGoldInk
                        )
                    }
                }

                SecondaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = SurfaceBase.forTheme(),
                    contentColor = FaithGoldInk
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> PrayerWallTab(
                    prayers = prayers,
                    onPrayFor = { viewModel.prayFor(it) },
                    onPostPrayer = { showPostPrayerDialog = true },
                    onAnswered = { prayerId ->
                        linkedPrayerIdForTestimony = prayerId
                        showPostTestimonyDialog = true
                    }
                )
                1 -> TestimoniesTab(
                    testimonies = testimonies,
                    onPraise = { viewModel.praiseTestimony(it) },
                    onPostTestimony = {
                        linkedPrayerIdForTestimony = null
                        showPostTestimonyDialog = true
                    }
                )
                2 -> SermonsTab(
                    sermons = sermons,
                    onShareSermon = { showShareSermonDialog = true }
                )
                3 -> MembersTab(
                    circle = circle,
                    members = members,
                    onLeaveCircle = {
                        viewModel.leaveCircle(circleId)
                        onNavigateBack()
                    }
                )
            }
        }
    }

    if (showPostPrayerDialog) {
        PostPrayerDialog(
            onDismiss = { showPostPrayerDialog = false },
            onPost = { text, isUrgent, author ->
                showPostPrayerDialog = false
                viewModel.postPrayer(circleId, text, isUrgent, author)
            }
        )
    }

    if (showPostTestimonyDialog) {
        PostTestimonyDialog(
            prayerRequestId = linkedPrayerIdForTestimony,
            onDismiss = {
                showPostTestimonyDialog = false
                linkedPrayerIdForTestimony = null
            },
            onPost = { title, story, scripture, author ->
                showPostTestimonyDialog = false
                viewModel.postTestimony(
                    circleId = circleId,
                    title = title,
                    storyText = story,
                    scriptureRef = scripture,
                    prayerRequestId = linkedPrayerIdForTestimony,
                    authorName = author
                )
                linkedPrayerIdForTestimony = null
            }
        )
    }

    if (showShareSermonDialog) {
        ShareSermonDialog(
            onDismiss = { showShareSermonDialog = false },
            onShare = { title, preacher, passage, summary, guideJson ->
                showShareSermonDialog = false
                viewModel.shareSermon(
                    circleId = circleId,
                    title = title,
                    preacher = preacher,
                    scripturePassage = passage,
                    transcriptSummary = summary,
                    discussionGuideJson = guideJson
                )
            }
        )
    }
}

// ---------------------------------------------------------------------------
// 1. Prayer Wall Tab
// ---------------------------------------------------------------------------

@Composable
private fun PrayerWallTab(
    prayers: List<CirclePrayer>,
    onPrayFor: (String) -> Unit,
    onPostPrayer: () -> Unit,
    onAnswered: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE PRAYER WALL (${prayers.count { it.status == CirclePrayerStatus.ACTIVE }})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = OnInk.forTheme().copy(alpha = 0.6f)
                )
                Button(
                    onClick = onPostPrayer,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "Ask for Prayer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (prayers.isEmpty()) {
            item {
                Surface(
                    color = OnInk.forTheme().copy(alpha = 0.02f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Line.forTheme())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🙏", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No prayer requests yet",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = OnInk.forTheme()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Share what's on your heart so your circle can pray for you.",
                            fontSize = 12.sp,
                            color = OnInk.forTheme().copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            items(prayers) { prayer ->
                PrayerCard(
                    prayer = prayer,
                    onPray = { onPrayFor(prayer.id) },
                    onAnswered = { onAnswered(prayer.id) }
                )
            }
        }
    }
}

@Composable
private fun PrayerCard(
    prayer: CirclePrayer,
    onPray: () -> Unit,
    onAnswered: () -> Unit
) {
    val isAnswered = prayer.status == CirclePrayerStatus.ANSWERED

    Surface(
        color = SurfaceBase.forTheme(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (prayer.isUrgent && !isAnswered) FaithGoldInk else Line.forTheme()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = prayer.authorName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = OnInk.forTheme()
                    )
                    if (prayer.isUrgent && !isAnswered) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFD97706).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "URGENT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isAnswered) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ANSWERED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = prayer.requestText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = OnInk.forTheme()
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Prayed button
                Surface(
                    color = if (prayer.prayedByMe) FaithGoldInk.copy(alpha = 0.15f) else OnInk.forTheme().copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (prayer.prayedByMe) FaithGoldInk else Line.forTheme()),
                    modifier = Modifier.clickable(onClick = onPray)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolunteerActivism,
                            contentDescription = null,
                            tint = if (prayer.prayedByMe) FaithGoldInk else OnInk.forTheme().copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (prayer.prayerCount > 0) "Prayed (${prayer.prayerCount})" else "Prayed for this",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (prayer.prayedByMe) FaithGoldInk else OnInk.forTheme()
                        )
                    }
                }

                if (!isAnswered) {
                    TextButton(onClick = onAnswered) {
                        Text(
                            text = "Answered? Share Testimony",
                            fontSize = 12.sp,
                            color = FaithGoldInk,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Testimonies & Praise Tab
// ---------------------------------------------------------------------------

@Composable
private fun TestimoniesTab(
    testimonies: List<CircleTestimony>,
    onPraise: (String) -> Unit,
    onPostTestimony: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRAISE & TESTIMONIES (${testimonies.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = OnInk.forTheme().copy(alpha = 0.6f)
                )
                Button(
                    onClick = onPostTestimony,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "Share Testimony", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (testimonies.isEmpty()) {
            item {
                Surface(
                    color = OnInk.forTheme().copy(alpha = 0.02f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Line.forTheme())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🙌", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No testimonies shared yet",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = OnInk.forTheme()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Celebrate what God has done in your life to encourage your circle brothers and sisters.",
                            fontSize = 12.sp,
                            color = OnInk.forTheme().copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            items(testimonies) { testimony ->
                TestimonyCard(testimony = testimony, onPraise = { onPraise(testimony.id) })
            }
        }
    }
}

@Composable
private fun TestimonyCard(
    testimony: CircleTestimony,
    onPraise: () -> Unit
) {
    Surface(
        color = SurfaceBase.forTheme(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line.forTheme()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🙌 PRAISE REPORT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FaithGoldInk,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "by ${testimony.authorName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnInk.forTheme().copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = testimony.title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = OnInk.forTheme()
            )
            if (!testimony.scriptureRef.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "📖 ${testimony.scriptureRef}",
                    fontSize = 12.sp,
                    color = FaithGoldInk,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = testimony.storyText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = OnInk.forTheme()
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (testimony.praisedByMe) FaithGoldInk.copy(alpha = 0.15f) else OnInk.forTheme().copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (testimony.praisedByMe) FaithGoldInk else Line.forTheme()),
                    modifier = Modifier.clickable(onClick = onPraise)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🙌", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (testimony.praiseCount > 0) "Praise God (${testimony.praiseCount})" else "Praise God",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (testimony.praisedByMe) FaithGoldInk else OnInk.forTheme()
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Sermons & Discussion Guides Tab
// ---------------------------------------------------------------------------

@Composable
private fun SermonsTab(
    sermons: List<CircleSermon>,
    onShareSermon: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SERMON WORKSPACES (${sermons.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = OnInk.forTheme().copy(alpha = 0.6f)
                )
                Button(
                    onClick = onShareSermon,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "Share Sermon", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (sermons.isEmpty()) {
            item {
                Surface(
                    color = OnInk.forTheme().copy(alpha = 0.02f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Line.forTheme())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "📖", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No sermon studies yet",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = OnInk.forTheme()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "One member records Sunday's sermon; the group shares the discussion guide while personal reflections remain sealed.",
                            fontSize = 12.sp,
                            color = OnInk.forTheme().copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            items(sermons) { sermon ->
                Surface(
                    color = SurfaceBase.forTheme(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Line.forTheme()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = sermon.title,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = OnInk.forTheme()
                        )
                        if (!sermon.preacher.isNullOrBlank() || !sermon.scripturePassage.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = listOfNotNull(sermon.preacher, sermon.scripturePassage).joinToString(" • "),
                                fontSize = 12.sp,
                                color = FaithGoldInk,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (sermon.transcriptSummary.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = sermon.transcriptSummary,
                                fontSize = 13.sp,
                                color = OnInk.forTheme().copy(alpha = 0.8f)
                            )
                        }
                        if (sermon.discussionGuideJson.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Discussion Guide", sermon.discussionGuideJson))
                                    Toast.makeText(context, "Discussion guide copied for WhatsApp!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, FaithGoldInk)
                            ) {
                                Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, tint = FaithGoldInk, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Discussion Guide", fontSize = 12.sp, color = FaithGoldInk)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Members & Settings Tab
// ---------------------------------------------------------------------------

@Composable
private fun MembersTab(
    circle: Circle?,
    members: List<CircleMember>,
    onLeaveCircle: () -> Unit
) {
    val context = LocalContext.current
    var showLeaveConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Invite Link Card
        item {
            Surface(
                color = OnInk.forTheme().copy(alpha = 0.03f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Line.forTheme())
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "INVITE MEMBERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FaithGoldInk,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Share this link with fellowship members. It securely exchanges the AES-256 room key so only invited members can participate.",
                        fontSize = 12.sp,
                        color = OnInk.forTheme().copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            circle?.inviteCode?.let { code ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Circle Invite", code))
                                Toast.makeText(context, "Invite link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Invite Link", fontSize = 12.sp)
                    }
                }
            }
        }

        // Member list
        item {
            Text(
                text = "CIRCLE MEMBERS (${members.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = OnInk.forTheme().copy(alpha = 0.6f)
            )
        }

        items(members) { member ->
            Surface(
                color = SurfaceBase.forTheme(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Line.forTheme()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = FaithGoldInk.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = member.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = FaithGoldInk)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.displayName + if (member.isSelf) " (You)" else "",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = OnInk.forTheme()
                        )
                        Text(
                            text = member.role.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            color = OnInk.forTheme().copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Leave Circle
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showLeaveConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Leave Fellowship Circle")
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Circle?") },
            text = { Text("You will no longer receive prayer updates or shared sermon guides from this group.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        onLeaveCircle()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Dialogs: Post Prayer, Post Testimony, Share Sermon
// ---------------------------------------------------------------------------

@Composable
private fun PostPrayerDialog(
    onDismiss: () -> Unit,
    onPost: (text: String, isUrgent: Boolean, author: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isUrgent by remember { mutableStateOf(false) }
    var author by remember { mutableStateOf("Me") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask for Prayer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What can your circle pray for?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = isUrgent,
                        onCheckedChange = { isUrgent = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Mark as Urgent Prayer Request", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onPost(text, isUrgent, author) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk)
            ) {
                Text("Share with Circle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PostTestimonyDialog(
    prayerRequestId: String?,
    onDismiss: () -> Unit,
    onPost: (title: String, story: String, scripture: String?, author: String) -> Unit
) {
    var title by remember { mutableStateOf(if (prayerRequestId != null) "Answered Prayer!" else "") }
    var story by remember { mutableStateOf("") }
    var scripture by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("Me") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Praise Report", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (prayerRequestId != null) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🎉 Marking linked prayer request as ANSWERED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Testimony Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = story,
                    onValueChange = { story = it },
                    label = { Text("What did God do?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scripture,
                    onValueChange = { scripture = it },
                    label = { Text("Scripture Reference (Optional, e.g. Psalm 103:2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank() && story.isNotBlank()) onPost(title, story, scripture.takeIf { it.isNotBlank() }, author) },
                enabled = title.isNotBlank() && story.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk)
            ) {
                Text("Share Praise")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShareSermonDialog(
    onDismiss: () -> Unit,
    onShare: (title: String, preacher: String?, passage: String?, summary: String, guide: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var preacher by remember { mutableStateOf("") }
    var passage by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var guide by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Sermon Study", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Sermon Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = preacher,
                    onValueChange = { preacher = it },
                    label = { Text("Preacher / Speaker (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = passage,
                    onValueChange = { passage = it },
                    label = { Text("Scripture Passage (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Main Idea / Summary") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = guide,
                    onValueChange = { guide = it },
                    label = { Text("Discussion Guide Markdown") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onShare(title, preacher.takeIf { it.isNotBlank() }, passage.takeIf { it.isNotBlank() }, summary, guide) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk)
            ) {
                Text("Share with Circle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
