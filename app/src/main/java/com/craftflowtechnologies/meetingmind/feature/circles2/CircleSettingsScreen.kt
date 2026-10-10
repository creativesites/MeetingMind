package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CircleSettings
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CircleSettingsContent(
    circle: Circle,
    me: Member?,
    onBack: () -> Unit,
    onSave: (name: String, vocab: String, CircleSettings) -> Unit,
    onMute: (Boolean) -> Unit,
    onLeave: () -> Unit
) {
    val admin = me?.role?.isAdmin == true
    var name by rememberSaveable(circle.id, circle.name) { mutableStateOf(circle.name) }
    var vocab by rememberSaveable(circle.id, circle.vocab) { mutableStateOf(circle.vocab) }
    var types by remember(circle.settings) { mutableStateOf(circle.settings.allowedTypes) }
    var approval by remember(circle.settings) { mutableStateOf(circle.settings.prayerApproval) }
    var who by remember(circle.settings) { mutableStateOf(circle.settings.whoCanInvite) }
    var reactions by remember(circle.settings) { mutableStateOf(circle.settings.reactions) }
    var guidelines by remember(circle.settings) { mutableStateOf(circle.settings.guidelines) }
    var confirmLeave by remember { mutableStateOf(false) }
    val changed = admin && (name.trim() != circle.name || vocab.trim() != circle.vocab || types != circle.settings.allowedTypes ||
        approval != circle.settings.prayerApproval || who != circle.settings.whoCanInvite || reactions != circle.settings.reactions || guidelines != circle.settings.guidelines)

    Column(Modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding()) {
        ScreenHeader(
            "Settings", Modifier.padding(horizontal = MM.space.s),
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.inkSecondary) } }
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            if (!admin) Text("Only admins can change these. You can still mute or leave.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            SectionHeader("About")
            CircleField(name, { name = it.take(60) }, "Name")
            CircleField(vocab, { vocab = it.take(40) }, "What your group is called", placeholder = "Cell group")
            if (admin) {
                SectionHeader("Posts")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PostType.entries.forEach { t ->
                        MMChip(t.label, t in types, { val next = if (t in types) types - t else types + t; if (next.isNotEmpty()) types = PostType.entries.filter { it in next } })
                    }
                }
                ToggleRow("Admins approve prayer requests first", "Anonymous requests stay anonymous to admins too.", approval) { approval = it }
                SectionHeader("Reactions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    ReactionKind.entries.forEach { k ->
                        MMChip("${k.emoji} ${k.label}", k in reactions, { val next = if (k in reactions) reactions - k else reactions + k; if (next.isNotEmpty()) reactions = ReactionKind.entries.filter { it in next } })
                    }
                }
                SectionHeader("Who can invite")
                SegmentedControl(WhoCanInvite.entries.map { it.label }, WhoCanInvite.entries.indexOf(who), { who = WhoCanInvite.entries[it] })
                CircleField(guidelines, { guidelines = it.take(1000) }, "Community guidelines (optional)", placeholder = "Be kind. What's shared here stays here.", minLines = 2, maxLines = 6)
                PrimaryButton("Save changes", { onSave(name, vocab, CircleSettings(types, who, approval, reactions, guidelines.trim())) }, enabled = changed && name.isNotBlank(), modifier = Modifier.fillMaxWidth())
            } else if (circle.settings.guidelines.isNotBlank()) {
                SectionHeader("Community guidelines")
                Text(circle.settings.guidelines, style = MM.type.body, color = MM.colors.ink)
            }
            SectionHeader("For you")
            ToggleRow("Mute this circle", "No notifications from ${circle.name}.", me?.muted == true, onMute)
            TextAction("Leave this ${circle.groupWord.lowercase()}", { confirmLeave = true })
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
