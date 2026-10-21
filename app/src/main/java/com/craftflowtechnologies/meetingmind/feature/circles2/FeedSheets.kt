package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.rounded.Videocam
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Comment
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.scripture.rememberPassage
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** Which kind of post to write. Admin-only kinds are hidden from members. */
@Composable
fun PostTypeSheet(circle: Circle, isAdmin: Boolean, onPick: (PostType) -> Unit, onDismiss: () -> Unit) {
    CircleSheet(onDismiss) {
        Text("New post", style = MM.type.title, color = MM.colors.ink)
        // Free-form is always offered; the circle's own kinds follow.
        (listOf(PostType.Note) + circle.settings.allowedTypes.filter { !it.adminOnly || isAdmin }).distinct().forEach { t ->
            ListRow(title = t.verb, subtitle = t.label, onClick = { onPick(t) })
        }
    }
}

@Composable
fun ComposerSheet(
    circle: Circle,
    state: ComposerState,
    onBody: (String) -> Unit,
    onVerse: (String) -> Unit,
    onAnonymous: (Boolean) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    onRemovePhoto: (MediaAttachment) -> Unit = {},
    onTypeChange: (PostType) -> Unit = {},
    onPhoto: (PickedFile) -> Unit = {},
    onPhotoRefused: () -> Unit = {}
) {
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) readPicked(context, uri)?.let { onPhoto(it) } ?: onPhotoRefused()
    }
    val type = state.type ?: return
    val availableTypes = remember(circle) {
        (listOf(PostType.Note) + circle.settings.allowedTypes).distinct()
    }
    val parsedRef = remember(state.verse) {
        if (state.verse.isNotBlank()) ScriptureReferenceParser.parse(state.verse.trim()) else null
    }
    val passageResult = parsedRef?.let { rememberPassage(it) }

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(type.verb, style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = MM.colors.ink)
                Text(
                    "Posting to ${circle.name}",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Cancel", tint = MM.colors.inkSecondary)
            }
        }

        // Type switcher chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            availableTypes.forEach { t ->
                MMChip(t.label, selected = t == type, onClick = { onTypeChange(t) })
            }
        }

        if (state.fromAnswered) {
            StatusLine(StatusKind.Info, "Thank God with the group! Edit this testimony however you like.")
        }

        Column(Modifier.fillMaxWidth()) {
            CircleField(
                state.body,
                { onBody(it.take(4000)) },
                label = when (type) {
                    PostType.Prayer -> "What would you like prayer for?"
                    PostType.Testimony -> "What did God do?"
                    PostType.Encouragement -> "Encouraging word or reflection"
                    else -> "Write something to share"
                },
                placeholder = when (type) {
                    PostType.Prayer -> "Share your prayer request with details or specific prayer points…"
                    PostType.Testimony -> "Tell the story of how God moved or answered prayer…"
                    PostType.Encouragement -> "Share a thought, verse reflection, or word of encouragement…"
                    else -> "Share an update, question, or note with the circle…"
                },
                minLines = 4,
                maxLines = 10
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "${state.body.length} / 4000",
                    style = MM.type.caption.copy(fontSize = 11.sp),
                    color = if (state.body.length > 3800) CirclesPalette.RoseInk else MM.colors.inkMuted
                )
            }
        }

        // Photos gallery
        if (state.attachments.isNotEmpty()) {
            Text("Attached (${state.attachments.size})", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.inkSecondary)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.attachments.forEach { a ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MM.colors.surfaceSunk,
                        border = BorderStroke(1.dp, MM.colors.line),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (a.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Image,
                                contentDescription = null, tint = CirclesPalette.Accent, modifier = Modifier.size(16.dp)
                            )
                            Text(
                                listOfNotNull(a.durationMs?.takeIf { it > 0 }?.let(::formatDuration), formatBytes(a.bytes)).joinToString(" · "),
                                style = MM.type.caption.copy(fontWeight = FontWeight.Medium), color = MM.colors.ink
                            )
                            IconButton(onClick = { onRemovePhoto(a) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Close, "Remove attachment", tint = MM.colors.inkSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Scripture verse section with live preview
        if (type != PostType.Announcement) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CircleField(
                    state.verse,
                    onVerse,
                    label = "Scripture verse (optional)",
                    placeholder = "e.g. Romans 8:28, Psalm 23:1, John 3:16"
                )
                if (parsedRef != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CirclesPalette.WarmBg,
                        border = BorderStroke(1.dp, CirclesPalette.WarmBorder),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = CirclesPalette.WarmInk,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        parsedRef.display(),
                                        style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                        color = CirclesPalette.WarmInk
                                    )
                                }
                                (passageResult as? PassageResult.Found)?.let { found ->
                                    Text(
                                        found.passage.versionAbbreviation,
                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                        color = CirclesPalette.WarmInk.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            when (passageResult) {
                                is PassageResult.Found -> {
                                    Text(
                                        "“${passageResult.passage.text.trim().trim('“', '”')}”",
                                        style = MM.type.scripture.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                                        color = MM.colors.ink,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    if (passageResult.passage.attribution.isNotBlank()) {
                                        Text(
                                            passageResult.passage.attribution,
                                            style = MM.type.caption.copy(fontSize = 10.5.sp),
                                            color = MM.colors.inkMuted,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                null -> Text("Looking up scripture passage…", style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = 4.dp))
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }

        // Add photo launcher button
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(
                "Add photo",
                { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                leadingIcon = Icons.Rounded.Image,
                modifier = Modifier.weight(1f)
            )
        }

        // Anonymous toggle for prayer requests
        if (type == PostType.Prayer) {
            ToggleRow("Post anonymously", "Nobody sees who asked, admins included.", state.anonymous, onAnonymous)
            if (circle.settings.prayerApproval) {
                StatusLine(StatusKind.Info, "An admin reviews prayer requests before they appear in the circle.")
            }
        }

        state.error?.let { StatusLine(StatusKind.Error, it) }

        PrimaryButton(
            if (state.sending) "Posting..." else if (type == PostType.Prayer && circle.settings.prayerApproval) "Send for approval" else "Share with circle",
            onSend,
            enabled = !state.sending && (state.body.isNotBlank() || state.attachments.isNotEmpty()),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** One text box used for editing a post and for posting an update. */
@Composable
fun TextEditSheet(title: String, initial: String, actionLabel: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    CircleSheet(onDismiss) {
        Text(title, style = MM.type.title, color = MM.colors.ink)
        CircleField(text, { text = it.take(4000) }, "Your words", minLines = 3, maxLines = 8)
        PrimaryButton(actionLabel, { onSave(text); onDismiss() }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ReportSheet(onReport: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    CircleSheet(onDismiss) {
        Text("Report this", style = MM.type.title, color = MM.colors.ink)
        Text("The admins will see the report. They won't be told who sent it.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        CircleField(reason, { reason = it.take(500) }, "What's wrong? (optional)", minLines = 2, maxLines = 5)
        PrimaryButton("Send report", { onReport(reason); onDismiss() }, modifier = Modifier.fillMaxWidth())
    }
}

/** The invite code and a share button. The code is always written out so a pasted message works anywhere. */
@Composable
fun InviteSheet(state: InviteUiState, onCreate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    CircleSheet(onDismiss) {
        Text("Invite people", style = MM.type.title, color = MM.colors.ink)
        val invite = state.invite
        when {
            state.busy -> StatusLine(StatusKind.Info, "Making a code...")
            invite != null && state.shareText != null -> {
                InsetPanel { SelectionContainer { Text(invite.code, style = MM.type.title, color = MM.colors.ink) } }
                Text("Works for one week and up to ${invite.maxUses ?: 50} people. Anyone with the code can join, so share it only with people you know.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                PrimaryButton("Share invite", {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, state.shareText)
                    runCatching { context.startActivity(Intent.createChooser(send, "Share invite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, modifier = Modifier.fillMaxWidth())
                SecondaryButton("Copy message", { clipboard.setText(AnnotatedString(state.shareText)) }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                state.error?.let { StatusLine(StatusKind.Error, it) }
                Text("Make a code to share on WhatsApp, SMS or in person.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                PrimaryButton(if (state.error != null) "Try again" else "Make a code", onCreate, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
