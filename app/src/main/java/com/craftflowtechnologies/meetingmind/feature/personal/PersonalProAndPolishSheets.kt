package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotRecipe
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.personal.GuidedActivityDefinition
import com.craftflowtechnologies.meetingmind.core.personal.RoutineStepKind
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.work.previewOf
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * Full-screen calm stage for Guided Coaching Activities (Track 1):
 * Shows the 3D Breathing Pacer (for breathing activities) or Reading companion, full coaching script,
 * Read-to-Me TTS button, Add-to-Routine shortcut, and an optional post-activity reflection saved
 * straight into Today's Journal Day-Page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuidedActivityStageSheet(
    activity: GuidedActivityDefinition,
    onDismiss: () -> Unit,
    onPlayAudio: () -> Unit,
    onAddToRoutine: () -> Unit,
    onSaveReflection: (String) -> Unit
) {
    val c = MM.colors
    var reflection by remember(activity.id) { mutableStateOf("") }
    var breathingRunning by remember(activity.id) { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
                .padding(bottom = MM.space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${activity.durationMinutes} MIN · ${activity.category.label.uppercase()}",
                        style = MM.type.overline,
                        color = c.accent
                    )
                    Text(activity.title, style = MM.type.title, color = c.ink)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.inkSecondary)
                }
            }

            if (activity.stepKind == RoutineStepKind.BREATHING) {
                Breathing3DPacer(isRunning = breathingRunning)
                TextAction(
                    text = if (breathingRunning) "Pause pacer" else "Resume pacer",
                    onClick = { breathingRunning = !breathingRunning }
                )
            } else {
                ZuriSlot(
                    page = CompanionPage.HOME,
                    state = CompanionState.READING,
                    size = 96.dp
                )
            }

            MMCard {
                Text("GUIDED SCRIPT", style = MM.type.overline, color = c.inkMuted)
                Spacer(Modifier.height(MM.space.xs))
                Text(activity.spokenScript, style = MM.type.body, color = c.ink)
                Spacer(Modifier.height(MM.space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton(
                        text = "Listen with Read-to-Me",
                        onClick = onPlayAudio,
                        leadingIcon = Icons.Rounded.Headphones,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(MM.space.xs))
                TextAction("Add this activity to a Routine", onClick = onAddToRoutine)
            }

            MMCard {
                Text(
                    activity.reflectionPrompt ?: "Keep a quick thought in Today's Journal (optional)",
                    style = MM.type.bodyStrong,
                    color = c.ink
                )
                Spacer(Modifier.height(MM.space.s))
                InsetPanel {
                    BasicTextField(
                        value = reflection,
                        onValueChange = { reflection = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 2),
                        decorationBox = { inner ->
                            Box {
                                if (reflection.isEmpty()) {
                                    Text("Write a reflection to stitch into Today's Journal…", style = MM.type.body, color = c.inkMuted)
                                }
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(MM.space.m))
                PrimaryButton(
                    text = if (reflection.isNotBlank()) "Save Reflection & Done" else "Done",
                    onClick = {
                        if (reflection.isNotBlank()) onSaveReflection(reflection)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Ceremony sheet for unsealing an unlocked 3D Time Capsule or revisiting a Memory,
 * with a 1-tap "Reply to your past self" Story Thread bridge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryUnsealCeremonySheet(
    memoryNote: Note,
    onDismiss: () -> Unit,
    onOpenOriginal: () -> Unit,
    onReplyToPastSelf: (String) -> Unit
) {
    val c = MM.colors
    var replyText by remember(memoryNote.id) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
                .padding(bottom = MM.space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            ZuriSlot(
                page = CompanionPage.HOME,
                state = CompanionState.NOSTALGIC,
                size = 96.dp
            )
            Text("UNSEALED MEMORY", style = MM.type.overline, color = c.gold)
            Text(memoryNote.title.ifBlank { "A note from your past self" }, style = MM.type.title, color = c.ink)

            MMCard {
                Text(
                    memoryNote.plainText.ifBlank { "Open the note to listen to your original recording." },
                    style = MM.type.body,
                    color = c.ink
                )
                Spacer(Modifier.height(MM.space.s))
                TextAction("Open full original note", onClick = onOpenOriginal)
            }

            MMCard {
                Text("REPLY TO YOUR PAST SELF", style = MM.type.overline, color = c.accent)
                Spacer(Modifier.height(MM.space.xs))
                Text("How does reading this feel now? Your reply links directly to this memory.", style = MM.type.secondary, color = c.inkSecondary)
                Spacer(Modifier.height(MM.space.s))
                InsetPanel {
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 2),
                        decorationBox = { inner ->
                            Box {
                                if (replyText.isEmpty()) Text("Dear past me…", style = MM.type.body, color = c.inkMuted)
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(MM.space.m))
                PrimaryButton(
                    text = "Send Reply & Link Thread",
                    onClick = { onReplyToPastSelf(replyText) },
                    enabled = replyText.isNotBlank(),
                    leadingIcon = Icons.Rounded.Reply,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Biometric Sanctuary Lock overlay (Track 1) protecting the Personal space when enabled.
 */
@Composable
internal fun SanctuaryLockGate(
    onUnlockClick: () -> Unit,
    onGoToday: () -> Unit,
    errorMessage: String?
) {
    val c = MM.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(MM.space.xl)
            .testTag("personal_sanctuary_lock_gate"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ZuriSlot(
            page = CompanionPage.HOME,
            state = CompanionState.COZY,
            size = 112.dp
        )
        Spacer(Modifier.height(MM.space.l))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(c.accentWash),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = "Sanctuary locked", tint = c.accent)
        }
        Spacer(Modifier.height(MM.space.m))
        Text("Personal Sanctuary is locked", style = MM.type.title, color = c.ink)
        Spacer(Modifier.height(MM.space.xs))
        Text(
            "Your journals, time capsules, and inner circle stay private even when your phone is unlocked.",
            style = MM.type.secondary,
            color = c.inkSecondary
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(MM.space.s))
            Text(errorMessage, style = MM.type.caption, color = c.danger)
        }
        Spacer(Modifier.height(MM.space.xl))
        PrimaryButton(
            text = "Unlock with Biometrics / PIN",
            onClick = onUnlockClick,
            leadingIcon = Icons.Rounded.Fingerprint,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MM.space.s))
        TextAction("Back to Today", onClick = onGoToday)
    }
}

/**
 * Privacy & sync: the sanctuary lock, the screenshot shield, the Obsidian vault and the Day One import.
 * Free for everyone (vault sync is never gated).
 */
@Composable
internal fun PrivacySyncCards(
    lockEnabled: Boolean,
    recentsShieldEnabled: Boolean,
    obsidianVaultLabel: String?,
    lastSyncLabel: String?,
    onToggleLock: (Boolean) -> Unit,
    onToggleRecentsShield: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onPickObsidianFolder: () -> Unit,
    onSyncObsidianNow: () -> Unit,
    onImportDayOneJson: () -> Unit
) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        // 1. Privacy & Sanctuary Lock
        MMCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(MM.space.s))
                Text("PRIVACY & SANCTUARY LOCK", style = MM.type.overline, color = c.accent)
            }
            Spacer(Modifier.height(MM.space.s))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Require Biometrics / PIN for Personal", style = MM.type.bodyStrong, color = c.ink)
                    Text("Locks journals & capsules separately from Work", style = MM.type.caption, color = c.inkSecondary)
                }
                Switch(checked = lockEnabled, onCheckedChange = onToggleLock)
            }
            Spacer(Modifier.height(MM.space.s))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Blur in Android Recents (FLAG_SECURE)", style = MM.type.bodyStrong, color = c.ink)
                    Text("Prevents screenshots and app-switcher previews", style = MM.type.caption, color = c.inkSecondary)
                }
                Switch(checked = recentsShieldEnabled, onCheckedChange = onToggleRecentsShield)
            }
            if (lockEnabled) {
                Spacer(Modifier.height(MM.space.xs))
                TextAction("Lock Sanctuary now", onClick = onLockNow)
            }
        }
        // 3. Obsidian Vault Sync & Day One Importer
        MMCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = c.speaker3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(MM.space.s))
                Text("SECOND-BRAIN & JOURNAL MIGRATION", style = MM.type.overline, color = c.speaker3)
            }
            Spacer(Modifier.height(MM.space.s))
            Text("Obsidian Markdown Vault Sync", style = MM.type.bodyStrong, color = c.ink)
            Text(
                if (obsidianVaultLabel != null) {
                    "Pinned to $obsidianVaultLabel" + (lastSyncLabel?.let { " · Last synced $it" }.orEmpty())
                } else {
                    "Export Personal journals & ideas as clean .md files with YAML frontmatter and [[Backlinks]]."
                },
                style = MM.type.caption,
                color = c.inkSecondary
            )
            Spacer(Modifier.height(MM.space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                SecondaryButton(
                    text = if (obsidianVaultLabel != null) "Change folder" else "Pin Obsidian Folder",
                    onClick = onPickObsidianFolder
                )
                if (obsidianVaultLabel != null) {
                    PrimaryButton(text = "Sync Now", onClick = onSyncObsidianNow)
                }
            }
            Spacer(Modifier.height(MM.space.m))
            Text("Import Day One Journal (JSON)", style = MM.type.bodyStrong, color = c.ink)
            Text(
                "Bring years of past Day One entries privately onto this phone so On This Day works immediately.",
                style = MM.type.caption,
                color = c.inkSecondary
            )
            Spacer(Modifier.height(MM.space.s))
            SecondaryButton(
                text = "Pick Day One JSON File",
                onClick = onImportDayOneJson,
                leadingIcon = Icons.Rounded.FileUpload
            )
        }
    }
}

/**
 * The personal autopilot recipes. Part of the paid plans (the same gate as Work's autopilot, which honours
 * closed testing). The "See plans" button only shows when billing is configured.
 */
@Composable
internal fun PersonalAutopilotCard(
    autopilotUnlocked: Boolean,
    isAutopilotRunning: Boolean,
    onRunPersonalAutopilot: (AutopilotRecipe) -> Unit,
    onOpenPaywall: (PaywallTrigger) -> Unit
) {
    val c = MM.colors
    val billingConfigured = Billing.gateway.configured
    MMCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = c.gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(MM.space.s))
            Text("PERSONAL AUTOPILOT", style = MM.type.overline, color = c.gold, modifier = Modifier.weight(1f))
            if (isAutopilotRunning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
            }
        }
        Spacer(Modifier.height(MM.space.xs))
        Text(
            "Synthesize your recent journals, voice rambles, or sparks into structured personal deliverables.",
            style = MM.type.secondary,
            color = c.inkSecondary
        )
        Spacer(Modifier.height(MM.space.m))
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            AutopilotRecipe.PERSONAL_RECIPES.forEach { recipe ->
                InsetPanel(onClick = {
                    if (isAutopilotRunning) return@InsetPanel
                    if (autopilotUnlocked) {
                        onRunPersonalAutopilot(recipe)
                    } else if (billingConfigured) {
                        onOpenPaywall(PaywallTrigger.PREMIUM_ACTION)
                    }
                }) {
                    Text(recipe.label, style = MM.type.bodyStrong, color = c.ink)
                    Text(recipe.subtitle, style = MM.type.caption, color = c.inkSecondary)
                }
            }
        }
        if (!autopilotUnlocked) {
            Spacer(Modifier.height(MM.space.s))
            Text("Part of the paid plans.", style = MM.type.caption, color = c.inkSecondary)
            if (billingConfigured) {
                TextAction("See plans", onClick = { onOpenPaywall(PaywallTrigger.PREMIUM_ACTION) })
            }
        }
    }
}

/** The Privacy & sync page: the lock and vault sync cards in one scrolling column. */
@Composable
internal fun PrivacySyncPage(
    lockEnabled: Boolean,
    recentsShieldEnabled: Boolean,
    obsidianVaultLabel: String?,
    lastSyncLabel: String?,
    onToggleLock: (Boolean) -> Unit,
    onToggleRecentsShield: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onPickObsidianFolder: () -> Unit,
    onSyncObsidianNow: () -> Unit,
    onImportDayOneJson: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MM.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MM.space.l)
            .padding(bottom = MM.space.xl)
            .testTag("garden_privacy_page")
    ) {
        PrivacySyncCards(
            lockEnabled = lockEnabled,
            recentsShieldEnabled = recentsShieldEnabled,
            obsidianVaultLabel = obsidianVaultLabel,
            lastSyncLabel = lastSyncLabel,
            onToggleLock = onToggleLock,
            onToggleRecentsShield = onToggleRecentsShield,
            onLockNow = onLockNow,
            onPickObsidianFolder = onPickObsidianFolder,
            onSyncObsidianNow = onSyncObsidianNow,
            onImportDayOneJson = onImportDayOneJson
        )
    }
}

/**
 * Dialog to add a loved one or friend to the private Inner Circle (Track 2).
 */
@Composable
internal fun AddInnerCirclePersonDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, relationship: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val c = MM.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        title = { Text("Add to Inner Circle", style = MM.type.heading, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Text("Stays 100% local. Mentioning their name in a journal or voice memo surfaces gift ideas & memories here.", style = MM.type.caption, color = c.inkSecondary)
                InsetPanel {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        textStyle = MM.type.bodyStrong.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> Box { if (name.isEmpty()) Text("Name (e.g. Sarah)", style = MM.type.body, color = c.inkMuted); inner() } }
                    )
                }
                InsetPanel {
                    BasicTextField(
                        value = relationship,
                        onValueChange = { relationship = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> Box { if (relationship.isEmpty()) Text("Relationship (e.g. Sister, Close friend)", style = MM.type.body, color = c.inkMuted); inner() } }
                    )
                }
                InsetPanel {
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
                        decorationBox = { inner -> Box { if (notes.isEmpty()) Text("Favorites, birthday, gift notes (optional)", style = MM.type.body, color = c.inkMuted); inner() } }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, relationship, notes) }, enabled = name.isNotBlank()) {
                Text("Save", style = MM.type.bodyStrong, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MM.type.body, color = c.inkSecondary) }
        }
    )
}

/**
 * Inner Circle & Gift Radar card rendered inside the Growth → Chapters & People section (Track 2).
 */
@Composable
internal fun InnerCircleSectionCard(
    people: List<PersonEntity>,
    mentionsByPersonId: Map<String, List<Pair<Note, String>>>,
    onAddPerson: () -> Unit,
    onOpenNote: (String) -> Unit
) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader(
            title = "Inner Circle & Gift Radar",
            count = people.size,
            actionLabel = "Add person",
            onAction = onAddPerson
        )
        if (people.isEmpty()) {
            MMCard(onClick = onAddPerson) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = c.accent)
                    Spacer(Modifier.width(MM.space.m))
                    Column(Modifier.weight(1f)) {
                        Text("Remember what matters to the people you love", style = MM.type.bodyStrong, color = c.ink)
                        Text("Add family or close friends so journal mentions & gift ideas surface automatically.", style = MM.type.caption, color = c.inkSecondary)
                    }
                }
            }
        } else {
            people.forEach { person ->
                val mentions = mentionsByPersonId[person.id].orEmpty()
                MMCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(person.name, style = MM.type.heading, color = c.ink)
                            person.relationship?.takeIf { it.isNotBlank() }?.let { rel ->
                                Text(rel, style = MM.type.caption, color = c.speaker3)
                            }
                        }
                        if (mentions.isNotEmpty()) {
                            Text("${mentions.size} ${if (mentions.size == 1) "mention" else "mentions"}", style = MM.type.caption, color = c.accent)
                        }
                    }
                    if (person.notes.isNotBlank()) {
                        Spacer(Modifier.height(MM.space.xs))
                        Text(person.notes, style = MM.type.secondary, color = c.inkSecondary)
                    }
                    mentions.take(2).forEach { (note, line) ->
                        Spacer(Modifier.height(MM.space.xs))
                        InsetPanel(onClick = { onOpenNote(note.id) }, contentPadding = MM.space.s) {
                            Text("“$line”", style = MM.type.caption, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("From ${note.title.ifBlank { "Journal" }}", style = MM.type.overline, color = c.inkMuted)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Personal Life Lists card (Track 2): calm quick-add and check-off view over Personal-space tasks
 * stored in [com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository].
 */
@Composable
internal fun PersonalLifeListsCard(
    tasks: List<Task>,
    onAddTask: (String) -> Unit,
    onToggleTask: (String) -> Unit
) {
    val c = MM.colors
    var draft by remember { mutableStateOf("") }
    val openTasks = remember(tasks) { tasks.filter { !it.done } }
    val recentlyDone = remember(tasks) { tasks.filter { it.done }.take(3) }

    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader(
            title = "Personal Life Lists",
            count = openTasks.size
        )
        MMCard {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                InsetPanel(modifier = Modifier.weight(1f), contentPadding = MM.space.s) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) Text("Add a personal errand, book, or life item…", style = MM.type.body, color = c.inkMuted)
                                inner()
                            }
                        }
                    )
                }
                IconButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotBlank()) {
                            onAddTask(trimmed)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add personal item", tint = if (draft.isNotBlank()) c.accent else c.inkMuted)
                }
            }

            if (openTasks.isEmpty() && recentlyDone.isEmpty()) {
                Spacer(Modifier.height(MM.space.s))
                Text(
                    "Keep reading lists, bucket-list wishes, and personal errands separate from work.",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            } else {
                Spacer(Modifier.height(MM.space.s))
                (openTasks.take(10) + recentlyDone).forEach { task ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onToggleTask(task.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (task.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (task.done) "Mark open" else "Mark done",
                                tint = if (task.done) c.speaker3 else c.inkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(MM.space.xs))
                        Text(
                            task.title,
                            style = MM.type.body.copy(
                                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = if (task.done) c.inkMuted else c.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
