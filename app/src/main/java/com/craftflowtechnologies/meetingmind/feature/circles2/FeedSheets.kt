package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Comment
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Which kind of post to write. Admin-only kinds are hidden from members. */
@Composable
fun PostTypeSheet(circle: Circle, isAdmin: Boolean, onPick: (PostType) -> Unit, onDismiss: () -> Unit) {
    CircleSheet(onDismiss) {
        Text("New post", style = MM.type.title, color = MM.colors.ink)
        circle.settings.allowedTypes.filter { !it.adminOnly || isAdmin }.forEach { t ->
            ListRow(title = t.verb, subtitle = t.label, onClick = { onPick(t) })
        }
    }
}

@Composable
fun ComposerSheet(circle: Circle, state: ComposerState, onBody: (String) -> Unit, onVerse: (String) -> Unit, onAnonymous: (Boolean) -> Unit, onSend: () -> Unit, onDismiss: () -> Unit) {
    val type = state.type ?: return
    CircleSheet(onDismiss) {
        Text(type.verb, style = MM.type.title, color = MM.colors.ink)
        if (state.fromAnswered) Text("Thank God with the group. Edit this however you like.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        CircleField(
            state.body, onBody,
            label = when (type) { PostType.Prayer -> "What would you like prayer for?"; PostType.Testimony -> "What did God do?"; else -> "Write something" },
            minLines = 4, maxLines = 10
        )
        if (type != PostType.Announcement) CircleField(state.verse, onVerse, "Verse (optional)", placeholder = "Psalm 23:1")
        if (type == PostType.Prayer) {
            ToggleRow("Post anonymously", "Nobody sees who asked, admins included.", state.anonymous, onAnonymous)
            if (circle.settings.prayerApproval) {
                StatusLine(StatusKind.Info, "An admin reviews prayer requests before they appear in the circle.")
            }
        }
        state.error?.let { StatusLine(StatusKind.Error, it) }
        PrimaryButton(
            if (state.sending) "Sending..." else if (type == PostType.Prayer && circle.settings.prayerApproval) "Send for approval" else "Post",
            onSend, enabled = !state.sending && state.body.isNotBlank(), modifier = Modifier.fillMaxWidth()
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

/** A post with its updates and comments. */
@Composable
fun ThreadSheet(
    post: Post?, thread: ThreadUiState?, myUid: String?, isAdmin: Boolean, now: Long,
    onSend: (String) -> Unit, onDeleteComment: (Comment) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    CircleSheet(onDismiss) {
        Text("Comments", style = MM.type.title, color = MM.colors.ink)
        if (post != null) {
            InsetPanel { SelectionContainer { Text(post.body, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 4) } }
        }
        thread?.failure?.let { StatusLine(StatusKind.Warning, it.message) }
        if (thread != null) {
            thread.updates.forEach { u ->
                InsetPanel {
                    Text("Update from the author · ${relativeTime(now, u.createdAt)}", style = MM.type.caption, color = MM.colors.inkSecondary)
                    SelectionContainer { Text(u.body, style = MM.type.body, color = MM.colors.ink) }
                }
            }
            if (thread.comments.isEmpty() && !thread.loading) Text("No comments yet. Be the first to encourage.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            LazyColumn(Modifier.heightIn(max = MMSize.minTouch * 6), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                items(thread.comments, key = { it.id }) { cm ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${cm.authorName} · ${relativeTime(now, cm.createdAt)}", style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.weight(1f))
                            if ((cm.authorUid != null && cm.authorUid == myUid) || isAdmin) TextAction("Delete", { onDeleteComment(cm) })
                        }
                        SelectionContainer { Text(cm.body, style = MM.type.body, color = MM.colors.ink) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            CircleField(text, { text = it.take(2000) }, "Add a comment", modifier = Modifier.weight(1f), maxLines = 4)
            IconButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank()) {
                Icon(Icons.Rounded.Send, "Send comment", tint = if (text.isNotBlank()) MM.colors.accent else MM.colors.inkMuted)
            }
        }
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
