package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
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
    /** A new cover: a pack picture or a photo picked on this phone. Admins only. */
    onCover: (com.craftflowtechnologies.meetingmind.core.circles2.CoverChoice) -> Unit = {},
    /** True while a cover is saving; [coverNote] is the last outcome, shown under the cover (this screen has no snackbar). */
    coverBusy: Boolean = false,
    coverNote: CoverNote? = null
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
    var choosingCover by remember { mutableStateOf(false) }
    var unreadablePhoto by remember { mutableStateOf(false) }
    val pickPhoto = rememberCoverPhotoPicker { picked ->
        unreadablePhoto = picked == null
        if (picked != null) {
            choosingCover = false
            onCover(com.craftflowtechnologies.meetingmind.core.circles2.CoverChoice.Photo(picked.bytes, picked.mime, picked.name))
        }
    }
    if (choosingCover && admin) {
        CoverChooserSheet(
            template = circle.template, space = circle.space, currentCover = circle.coverPath, busy = coverBusy,
            onPickPack = { id -> choosingCover = false; unreadablePhoto = false; onCover(com.craftflowtechnologies.meetingmind.core.circles2.CoverChoice.Pack(id)) },
            onUpload = pickPhoto,
            onDismiss = { choosingCover = false }
        )
    }
    val changed = admin && (name.trim() != circle.name || vocab.trim() != circle.vocab || types != circle.settings.allowedTypes ||
        description.trim() != circle.description || joinApproval != circle.settings.joinApproval || approval != circle.settings.prayerApproval || who != circle.settings.whoCanInvite || reactions != circle.settings.reactions || guidelines != circle.settings.guidelines)

    val save: () -> Unit = {
        onSave(name, vocab, description.trim(), CircleSettings(
            allowedTypes = types, whoCanInvite = who, prayerApproval = approval,
            joinApproval = joinApproval, reactions = reactions, guidelines = guidelines.trim()
        ))
    }
    val canSave = changed && name.isNotBlank()
    Column(Modifier.fillMaxSize().background(MM.colors.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        CirclesTopBar(
            title = "Settings",
            onBack = onBack,
            subtitle = circle.name,
            actions = { if (admin) TextAction("Save", save, enabled = canSave) }
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l).padding(top = MM.space.s, bottom = if (canSave) MM.space.xl else circlesBottomInset()),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            if (!admin) Text("Only admins can change these. You can still mute or leave.", style = MM.type.secondary, color = MM.colors.inkSecondary)

            // ---- the cover, as the circle shows it ----
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Box(Modifier.fillMaxWidth().height(156.dp).clip(MM.radius.card).background(MM.colors.surfaceSunk)) {
                    CircleCoverImage(circle.coverPath, circle.template, circle.space, circle.id, Modifier.fillMaxSize(), contentDescription = "Cover")
                    if (coverBusy) {
                        Box(Modifier.matchParentSize().background(MM.colors.scrim), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = FixedWhite)
                        }
                    }
                    if (admin) {
                        Surface(
                            onClick = { if (!coverBusy) choosingCover = true },
                            shape = MM.radius.pill,
                            color = MM.colors.surfaceRaised,
                            shadowElevation = MM.elevation.floating,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(MM.space.m)
                        ) {
                            Row(
                                Modifier.heightIn(min = 36.dp).padding(horizontal = MM.space.m),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                Icon(Icons.Rounded.AddPhotoAlternate, null, tint = MM.colors.ink, modifier = Modifier.size(16.dp))
                                Text(if (coverBusy) "Saving…" else "Change cover", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.ink)
                            }
                        }
                    }
                }
                val note = when {
                    unreadablePhoto -> CoverNote("That photo couldn't be read. Try another, or pick one of ours.", failed = true)
                    else -> coverNote
                }
                if (note != null && !coverBusy) StatusLine(if (note.failed) StatusKind.Warning else StatusKind.Info, note.text)
            }

            // ---- about ----
            SettingsGroup("About") {
                CircleField(name, { name = it.take(60) }, "Name")
                val vocabPlaceholder = when {
                    circle.isWork -> "Team"
                    circle.isLearning -> "Study group"
                    else -> "Cell group"
                }
                CircleField(vocab, { vocab = it.take(40) }, "What your group is called", placeholder = vocabPlaceholder)
                CircleField(description, { description = it.take(300) }, "About this circle (optional)", placeholder = "Breakfast every second Wednesday.", minLines = 2, maxLines = 4)
            }

            // ---- members and joining ----
            SettingsGroup("Members and joining") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Members", style = MM.type.bodyStrong, color = MM.colors.ink, modifier = Modifier.weight(1f))
                    Text("${circle.memberCount} of 50", style = MM.type.secondary, color = MM.colors.inkSecondary)
                }
                LinearProgressIndicator(
                    progress = { (circle.memberCount / 50f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(MM.radius.pill),
                    color = MM.colors.ink,
                    trackColor = MM.colors.track
                )
                if (admin) {
                    ToggleRow("Admins let new people in", "When on, people wait until an admin approves them.", joinApproval) { joinApproval = it }
                    Text("Who can invite", style = MM.type.caption, color = MM.colors.inkSecondary)
                    SegmentedControl(WhoCanInvite.entries.map { it.label }, WhoCanInvite.entries.indexOf(who), { who = WhoCanInvite.entries[it] })
                }
            }

            if (admin) {
                SettingsGroup("Posts") {
                    Text("What members can share", style = MM.type.caption, color = MM.colors.inkSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        PostType.entries.forEach { t ->
                            MMChip(t.label, t in types, { val next = if (t in types) types - t else types + t; if (next.isNotEmpty()) types = PostType.entries.filter { it in next } })
                        }
                    }
                    val isFaithSpace = (!circle.isWork && !circle.isLearning) || circle.settings.space.equals("faith", ignoreCase = true)
                    if (isFaithSpace) {
                        ToggleRow("Approve prayer requests before posting", "Anonymous requests stay anonymous to admins too.", approval) { approval = it }
                    }
                    Text("Reactions", style = MM.type.caption, color = MM.colors.inkSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        ReactionKind.entries.forEach { k ->
                            MMChip("${k.emoji} ${k.label}", k in reactions, { val next = if (k in reactions) reactions - k else reactions + k; if (next.isNotEmpty()) reactions = ReactionKind.entries.filter { it in next } })
                        }
                    }
                }
                SettingsGroup("Community guidelines") {
                    val guidelinesPlaceholder = when {
                        circle.isWork -> "Be respectful. Protect confidential and sensitive information."
                        circle.isLearning -> "Share notes and study materials constructively."
                        else -> "Be kind. What's shared here stays here."
                    }
                    CircleField(guidelines, { guidelines = it.take(1000) }, "Guidelines (optional)", placeholder = guidelinesPlaceholder, minLines = 2, maxLines = 6)
                }
            } else if (circle.settings.guidelines.isNotBlank()) {
                SettingsGroup("Community guidelines") {
                    Text(circle.settings.guidelines, style = MM.type.body, color = MM.colors.ink)
                }
            }

            SettingsGroup("Who can do what") {
                PermissionSummaryRow(Role.Owner, "Full settings, roles, and circle ownership")
                CirclesRowDivider(startInset = 0.dp)
                PermissionSummaryRow(Role.Admin, "Approve requests, pin, events, remove members")
                CirclesRowDivider(startInset = 0.dp)
                PermissionSummaryRow(Role.Moderator, "Manage events and moderate posts or messages")
                CirclesRowDivider(startInset = 0.dp)
                PermissionSummaryRow(Role.Member, "Share posts, chat, react, and vote in polls")
            }

            SettingsGroup("For you") {
                ToggleRow("Mute this circle", "No notifications from ${circle.name}.", me?.muted == true, onMute)
            }
            if (account != null) {
                SettingsGroup("Your account") { AccountSection(account, onLink, onSwitch, onDismissConflict) }
            }
            MMCard(onClick = { confirmLeave = true }, contentPadding = MM.space.l) {
                Text(
                    "Leave this ${circle.groupWord.lowercase()}",
                    style = MM.type.bodyStrong,
                    color = MM.colors.danger
                )
            }
        }
        // Unsaved changes: one pinned Save above the navigation bar, so it is never under 3-button navigation.
        AnimatedVisibility(visible = admin && canSave, enter = fadeIn(MM.motion.quick()) + expandVertically(MM.motion.standard()), exit = fadeOut(MM.motion.quick()) + shrinkVertically(MM.motion.standard())) {
            Column(Modifier.fillMaxWidth().background(MM.colors.surface).navigationBarsPadding().imePadding()) {
                HorizontalDivider(thickness = MMSize.hairline, color = MM.colors.lineSoft)
                PrimaryButton("Save changes", save, modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.m))
            }
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

/** The outcome of the last cover change, shown in place. */
data class CoverNote(val text: String, val failed: Boolean)

/** A titled group of settings in one card, the way Work's settings group their rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        CirclesSectionTitle(title)
        MMCard(contentPadding = MM.space.l) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m), content = content)
        }
    }
}
