package com.craftflowtechnologies.meetingmind.feature.faith

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
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.share.ShareCardContent
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme
import java.util.UUID

/**
 * Model representing a shared testimony of answered prayer and God's faithfulness.
 */
data class SharedTestimony(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String,
    val dateText: String,
    val text: String,
    val scripture: String? = null,
    val isPersonal: Boolean = false,
    val noteId: String? = null
)

/**
 * Curated uplifting starter testimonies to inspire faith immediately.
 */
val CuratedTestimonies = listOf(
    SharedTestimony(
        id = "t-1",
        title = "Miraculous Peace & Healing",
        author = "Grace M.",
        dateText = "Answered Prayer",
        text = "After weeks of anxiety following unexpected medical tests, I surrendered the worry in prayer. The follow-up scan came back completely clear! God's peace guarded my heart when fear felt overwhelming.",
        scripture = "Philippians 4:6-7"
    ),
    SharedTestimony(
        id = "t-2",
        title = "Provision at Just the Right Time",
        author = "David K.",
        dateText = "Answered Prayer",
        text = "Our family faced an unexpected shortfall right as tuition was due. We prayed together around the table. That exact week, an old client reached out with an unexpected contract that covered everything to the dollar.",
        scripture = "Philippians 4:19"
    ),
    SharedTestimony(
        id = "t-3",
        title = "Reconciliation & Restored Joy",
        author = "Sarah B.",
        dateText = "Answered Prayer",
        text = "A two-year silence with my sister felt impossible to mend. During morning devotional prayer, God prompted me to send a gentle message without demands. We spoke on the phone that evening and wept together in forgiveness.",
        scripture = "Ephesians 4:32"
    ),
    SharedTestimony(
        id = "t-4",
        title = "Open Doors in Career Direction",
        author = "Michael T.",
        dateText = "Answered Prayer",
        text = "After feeling stagnant and praying for clear guidance, an opportunity opened up in a role where I can truly use my gifts to serve others. God's timing was so much better than my initial rush.",
        scripture = "Proverbs 3:5-6"
    )
)

@Composable
fun TestimoniesFeedScreen(
    viewModel: FaithViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onNewTestimony: () -> Unit,
    onShare: (ShareRequest) -> Unit
) {
    val notes by viewModel.faithNotes.collectAsState()
    val rejoiced = remember { mutableStateMapOf<String, Boolean>() }

    // Map user's testimonies and answered prayer requests
    val userTestimonies = remember(notes) {
        notes.filter { it.workflow == RecordingType.TESTIMONY }.map { n ->
            SharedTestimony(
                id = n.id,
                title = n.title.ifBlank { "Answered Prayer & Praise" },
                author = "You",
                dateText = "Your Testimony",
                text = n.plainText.ifBlank { "Praise report of God's faithfulness." },
                isPersonal = true,
                noteId = n.id
            )
        }
    }

    val allTestimonies = remember(userTestimonies) {
        userTestimonies + CuratedTestimonies
    }

    Scaffold(
        containerColor = SurfaceBase,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Testimonies & Praise",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    Text(
                        "Stories of answered prayers and God's faithfulness",
                        fontSize = 12.sp,
                        color = InkSecondary
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTestimony,
                containerColor = Ink,
                contentColor = OnInk,
                shape = CircleShape,
                modifier = Modifier.testTag("testimonies_new_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Share your testimony")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TestimoniesHeroBanner(onNewTestimony = onNewTestimony)
            }

            items(allTestimonies, key = { it.id }) { testimony ->
                val isRejoiced = rejoiced[testimony.id] == true
                TestimonyCard(
                    testimony = testimony,
                    isRejoiced = isRejoiced,
                    onToggleRejoice = { rejoiced[testimony.id] = !isRejoiced },
                    onOpenNote = { testimony.noteId?.let(onOpenNote) },
                    onShareStory = {
                        val shareReq = ShareRequest(
                            content = ShareCardContent(
                                eyebrow = "TESTIMONY",
                                text = "${testimony.title}\n\n${testimony.text.take(240)}",
                                reference = testimony.scripture ?: "God is faithful",
                                quoted = true
                            ),
                            theme = "${testimony.title}, praise report",
                            caption = "${testimony.title}\n\n${testimony.text}\n\n#MeetingMind #Testimony"
                        )
                        onShare(shareReq)
                    }
                )
            }

            item {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun TestimoniesHeroBanner(onNewTestimony: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1B1530).forTheme(),
                            Color(0xFF2E2248).forTheme(),
                            Color(0xFF4A3423).forTheme()
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FaithGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = FaithGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Overcoming by Testimony",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontFamily = FontFamily.Serif
                    )
                }
                Text(
                    "“And they overcame by the blood of the Lamb and by the word of their testimony.” — Rev 12:11",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Surface(
                    onClick = onNewTestimony,
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Celebration,
                            contentDescription = null,
                            tint = FaithGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Share What God Has Done",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TestimonyCard(
    testimony: SharedTestimony,
    isRejoiced: Boolean,
    onToggleRejoice: () -> Unit,
    onOpenNote: () -> Unit,
    onShareStory: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceRaised,
        border = BorderStroke(1.dp, LineSoft),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = testimony.isPersonal, onClick = onOpenNote)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (testimony.isPersonal) FaithGold.copy(alpha = 0.18f) else SurfaceSunk
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (testimony.isPersonal) Icons.Filled.Star else Icons.Filled.Celebration,
                        contentDescription = null,
                        tint = if (testimony.isPersonal) FaithGold else InkSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        testimony.author,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    Text(
                        testimony.dateText,
                        fontSize = 12.sp,
                        color = InkMuted
                    )
                }
                if (testimony.scripture != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = FaithGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.25f))
                    ) {
                        Text(
                            testimony.scripture,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = FaithGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                testimony.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
                color = Ink,
                modifier = Modifier.padding(top = 10.dp)
            )

            Text(
                testimony.text,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = InkSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = onToggleRejoice,
                    shape = RoundedCornerShape(50),
                    color = if (isRejoiced) FaithGold.copy(alpha = 0.15f) else SurfaceSunk,
                    border = BorderStroke(1.dp, if (isRejoiced) FaithGold else LineSoft)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isRejoiced) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isRejoiced) FaithGold else InkSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isRejoiced) "Amen! 🙌" else "Amen",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isRejoiced) FaithGold else InkSecondary
                        )
                    }
                }

                Surface(
                    onClick = onShareStory,
                    shape = RoundedCornerShape(50),
                    color = SurfaceSunk,
                    border = BorderStroke(1.dp, LineSoft)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = InkSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Share Story",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkSecondary
                        )
                    }
                }
            }
        }
    }
}
