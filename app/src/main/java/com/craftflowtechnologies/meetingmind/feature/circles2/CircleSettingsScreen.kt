package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CircleSettings
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CircleSettingsContent(
    circle: Circle,
    me: Member?,
    onBack: () -> Unit,
    onSave: (name: String, vocab: String, description: String, CircleSettings) -> Unit,
    onMute: (Boolean) -> Unit,
    onLeave: () -> Unit,
    account: AccountUiState? = null,
    onLink: (android.content.Context) -> Unit = {},
    onSwitch: () -> Unit = {},
    onDismissConflict: () -> Unit = {},
    /** The cover photo picked on this phone. Admins only. */
    onCover: (PickedFile) -> Unit = {}
) {
    val admin = me?.role?.isAdmin == true
    var name by rememberSaveable(circle.id, circle.name) { mutableStateOf(circle.name) }
    var vocab by rememberSaveable(circle.id, circle.vocab) { mutableStateOf(circle.vocab) }
    var types by remember(circle.settings) { mutableStateOf(circle.settings.allowedTypes) }
    var description by rememberSaveable(circle.id, circle.description) { mutableStateOf(circle.description) }
    var approval by remember(circle.settings) { mutableStateOf(circle.settings.prayerApproval) }
    var joinApproval by remember(circle.settings) { mutableStateOf(circle.settings.joinApproval) }
    var who by remember(circle.settings) { mutableStateOf(circle.settings.whoCanInvite) }
    var reactions by remember(circle.settings) { mutableStateOf(circle.settings.reactions) }
    var guidelines by remember(circle.settings) { mutableStateOf(circle.settings.guidelines) }
    var confirmLeave by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pickCover = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch { readPickedAsync(ctx, uri)?.let(onCover) }
        }
    }
    val changed = admin && (name.trim() != circle.name || vocab.trim() != circle.vocab || types != circle.settings.allowedTypes ||
        description.trim() != circle.description || joinApproval != circle.settings.joinApproval || approval != circle.settings.prayerApproval || who != circle.settings.whoCanInvite || reactions != circle.settings.reactions || guidelines != circle.settings.guidelines)

    Column(Modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding()) {
        ScreenHeader(
            "Settings", Modifier.padding(horizontal = MM.space.s),
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.inkSecondary) } }
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            if (!admin) Text("Only admins can change these. You can still mute or leave.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            SectionHeader("About")
            val spaceName = circleSpace(circle)
            val spaceTint = when (spaceName) {
                "Work" -> MM.colors.accent
                "Study" -> MM.colors.speaker2
                "Faith" -> MM.colors.gold
                else -> MM.colors.speaker3
            }
            val spaceIcon = when (spaceName) {
                "Work" -> Icons.Rounded.BusinessCenter
                "Study" -> Icons.Rounded.School
                "Faith" -> Icons.Rounded.VolunteerActivism
                else -> Icons.Rounded.Groups
            }
            Surface(
                shape = MM.radius.card,
                color = MM.colors.surfaceRaised,
                border = BorderStroke(MMSize.hairline, MM.colors.lineSoft),
                modifier = Modifier.fillMaxWidth().clip(MM.radius.card)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(CirclesPalette.CoverStart, spaceTint, CirclesPalette.CoverEnd)
                                )
                            )
                    ) {
                        if (circle.coverPath != null) {
                            MediaImage(circle.id, circle.coverPath, Modifier.fillMaxSize(), "Cover photo")
                        } else {
                            Box(Modifier.fillMaxSize().padding(MM.space.l), contentAlignment = Alignment.BottomStart) {
                                InitialsAvatar(circle.name, size = 48.dp)
                            }
                        }
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, MM.colors.scrim.copy(alpha = 0.65f))
                                    )
                                )
                        )
                        Surface(
                            shape = MM.radius.pill,
                            color = MM.colors.scrim,
                            modifier = Modifier.align(Alignment.TopStart).padding(MM.space.s)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(spaceIcon, contentDescription = null, tint = FixedWhite, modifier = Modifier.size(12.dp))
                                Text(
                                    spaceName,
                                    style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                                    color = FixedWhite
                                )
                            }
                        }
                        if (admin) {
                            Surface(
                                shape = MM.radius.pill,
                                color = MM.colors.surfaceRaised.copy(alpha = 0.92f),
                                border = BorderStroke(MMSize.hairline, MM.colors.lineSoft),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(MM.space.s)
                                    .clickable { pickCover.launch("image/*") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = "Change cover photo",
                                        tint = MM.colors.ink,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        if (circle.coverPath != null) "Change cover" else "Add cover",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                        color = MM.colors.ink
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.m, vertical = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Cover photo", style = MM.type.bodyStrong, color = MM.colors.ink)
                            Text("Shown at the top of the circle home.", style = MM.type.caption, color = MM.colors.inkSecondary)
                        }
                        if (admin) {
                            TextAction(if (circle.coverPath != null) "Replace" else "Upload", { pickCover.launch("image/*") })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Circle name", style = MM.type.caption, color = MM.colors.inkSecondary)
                Text("${name.length} / 60", style = MM.type.caption, color = MM.colors.inkMuted)
            }
            CircleField(name, { name = it.take(60) }, "Name")
            val vocabPlaceholder = when {
                circle.isWork -> "Team"
                circle.isLearning -> "Study group"
                else -> "Cell group"
            }
            CircleField(vocab, { vocab = it.take(40) }, "What your group is called", placeholder = vocabPlaceholder)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("About this circle", style = MM.type.caption, color = MM.colors.inkSecondary)
                Text("${description.length} / 300", style = MM.type.caption, color = MM.colors.inkMuted)
            }
            CircleField(description, { description = it.take(300) }, "About this circle (optional)", placeholder = "Breakfast every second Wednesday.", minLines = 2, maxLines = 4)

            SectionHeader("Members and joining")
            MMCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Circle capacity", style = MM.type.bodyStrong, color = MM.colors.ink)
                    Text("${circle.memberCount} of 50", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.accent)
                }
                LinearProgressIndicator(
                    progress = { (circle.memberCount / 50f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(MM.radius.pill),
                    color = MM.colors.accent,
                    trackColor = MM.colors.surfaceSunk
                )
                if (admin) {
                    HorizontalDivider(Modifier.padding(vertical = MM.space.xs), color = MM.colors.line)
                    ToggleRow("Admins let new people in", "When on, people wait until an admin approves them.", joinApproval) { joinApproval = it }
                }
            }

            if (admin) {
                SectionHeader("Posts")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PostType.entries.forEach { t ->
                        MMChip(t.label, t in types, { val next = if (t in types) types - t else types + t; if (next.isNotEmpty()) types = PostType.entries.filter { it in next } })
                    }
                }
                val isFaithSpace = (!circle.isWork && !circle.isLearning) || circle.settings.space.equals("faith", ignoreCase = true)
                if (isFaithSpace) {
                    ToggleRow("Approve prayer requests before posting", "Anonymous requests stay anonymous to admins too.", approval) { approval = it }
                }
                SectionHeader("Reactions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    ReactionKind.entries.forEach { k ->
                        MMChip("${k.emoji} ${k.label}", k in reactions, { val next = if (k in reactions) reactions - k else reactions + k; if (next.isNotEmpty()) reactions = ReactionKind.entries.filter { it in next } })
                    }
                }
                SectionHeader("Who can invite")
                SegmentedControl(WhoCanInvite.entries.map { it.label }, WhoCanInvite.entries.indexOf(who), { who = WhoCanInvite.entries[it] })
                val guidelinesPlaceholder = when {
                    circle.isWork -> "Be respectful. Protect confidential and sensitive information."
                    circle.isLearning -> "Share notes and study materials constructively."
                    else -> "Be kind. What's shared here stays here."
                }
                CircleField(guidelines, { guidelines = it.take(1000) }, "Community guidelines (optional)", placeholder = guidelinesPlaceholder, minLines = 2, maxLines = 6)
                PrimaryButton(
                    "Save changes",
                    {
                        onSave(name, vocab, description.trim(), CircleSettings(
                            allowedTypes = types, whoCanInvite = who, prayerApproval = approval,
                            joinApproval = joinApproval, reactions = reactions, guidelines = guidelines.trim()
                        ))
                    },
                    enabled = changed && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (circle.settings.guidelines.isNotBlank()) {
                SectionHeader("Community guidelines")
                Text(circle.settings.guidelines, style = MM.type.body, color = MM.colors.ink)
            }

            SectionHeader("Permissions")
            MMCard {
                PermissionSummaryRow(Role.Owner, "Full settings, roles, and circle ownership")
                HorizontalDivider(color = MM.colors.line)
                PermissionSummaryRow(Role.Admin, "Approve requests, pin, events, remove members")
                HorizontalDivider(color = MM.colors.line)
                PermissionSummaryRow(Role.Moderator, "Manage events and moderate posts or messages")
                HorizontalDivider(color = MM.colors.line)
                PermissionSummaryRow(Role.Member, "Share posts, chat, react, and vote in polls")
            }

            SectionHeader("For you")
            ToggleRow("Mute this circle", "No notifications from ${circle.name}.", me?.muted == true, onMute)
            if (account != null) {
                SectionHeader("Your account")
                AccountSection(account, onLink, onSwitch, onDismissConflict)
            }
            Surface(
                shape = MM.radius.card,
                color = MM.colors.dangerWash,
                border = BorderStroke(MMSize.hairline, MM.colors.danger.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth().clickable { confirmLeave = true }
            ) {
                Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Leave this ${circle.groupWord.lowercase()}",
                        style = MM.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
                        color = MM.colors.danger
                    )
                }
            }
            Spacer(Modifier.height(MM.space.xl))
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = MM.colors.surfaceRaised,
            title = { Text("Leave ${circle.name}?", style = MM.type.heading, color = MM.colors.ink) },
            text = {
                Text(
                    if (me?.role == Role.Owner) "As the owner, make someone else the owner first, unless you're the only member."
                    else "You'll need a new invite code to come back.", style = MM.type.body, color = MM.colors.inkSecondary
                )
            },
            confirmButton = { TextAction("Leave", { confirmLeave = false; onLeave() }) },
            dismissButton = { TextAction("Stay", { confirmLeave = false }) }
        )
    }
}

@Composable
private fun PermissionSummaryRow(role: Role, summary: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        RoleTag(role)
        Text(summary, style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.weight(1f))
    }
}
