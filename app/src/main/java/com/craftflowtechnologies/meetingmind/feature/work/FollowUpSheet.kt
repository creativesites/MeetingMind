package com.craftflowtechnologies.meetingmind.feature.work

import com.craftflowtechnologies.meetingmind.ui.theme.Briefing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.craftflowtechnologies.meetingmind.core.work.Channel
import com.craftflowtechnologies.meetingmind.core.work.ChannelChooser
import com.craftflowtechnologies.meetingmind.core.work.OutgoingMessage
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.Send
import com.craftflowtechnologies.meetingmind.core.work.Tone
import com.craftflowtechnologies.meetingmind.core.work.WorkSettings
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary

/**
 * The composer (docs/PLAN_PROFESSIONAL.md §6.5): one draft, rendered for the channel it's going
 * out on, editable before it goes. The channel offered first adapts to the people and the region;
 * the others are one tap away. MeetingMind never sends by itself: the person's own app does.
 *
 * [compose] renders the draft for a channel and tone. [onSent] runs when the person confirms it
 * went, so the follow-up can be marked sent and the channel remembered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpSheet(
    title: String,
    recipients: List<WorkPerson>,
    settings: WorkSettings,
    compose: (Channel, Tone) -> OutgoingMessage,
    onDismiss: () -> Unit,
    onSent: (Channel) -> Unit,
    onAddContact: ((WorkPerson) -> Unit)? = null
) {
    val context = LocalContext.current
    val primary = remember(recipients, settings) { ChannelChooser.pick(recipients, settings) }
    var channel by remember { mutableStateOf(primary) }
    var tone by remember { mutableStateOf(settings.tone) }
    val draft = remember(channel, tone) { compose(channel, tone) }
    var body by remember(channel, tone) { mutableStateOf(draft.body) }
    var awaiting by remember { mutableStateOf<Channel?>(null) }
    var confirm by remember { mutableStateOf<Channel?>(null) }

    // Back from WhatsApp or email: ask once whether it went (§4.3).
    LifecycleResumeEffect(awaiting) {
        if (awaiting != null) { confirm = awaiting; awaiting = null }
        onPauseOrDispose { }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            val names = recipients.filter { !it.isSelf }
            Text(
                if (names.isEmpty()) "No one to address yet" else "To " + names.joinToString(", ") { it.name },
                fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp)
            )
            val unreachable = names.filter { it.emails.isEmpty() && it.phones.isEmpty() }
            if (onAddContact != null && unreachable.isNotEmpty()) {
                TextButton(onClick = { onAddContact(unreachable.first()) }) {
                    Text("Add ${unreachable.first().name.substringBefore(' ')}'s email or number")
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tone.entries.forEach { t -> Chip(t.label, tone == t, Ink) { tone = t } }
            }
            if (channel == Channel.EMAIL) Text("Subject: " + draft.subject, fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 200.dp, max = 360.dp).verticalScroll(rememberScrollState()),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink)
            )
            Text("Written from what you confirmed — nothing added. Edit anything before it goes.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))

            fun go(c: Channel) {
                val message = OutgoingMessage(draft.subject, if (c == channel) body else compose(c, tone).body)
                if (Send.send(context, c, message, recipients)) awaiting = c
            }
            Button(
                onClick = { go(channel) }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (channel == Channel.WHATSAPP) Briefing.WhatsApp else Ink, contentColor = if (channel == Channel.WHATSAPP) Briefing.OnBrief else com.craftflowtechnologies.meetingmind.ui.theme.OnInk)
            ) { Text("Send on " + channel.label, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Channel.entries.filter { it != channel }.forEach { c ->
                    TextButton(onClick = { channel = c }) { Text(c.label) }
                }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase,
            title = { Text("Did it go?") },
            text = { Text("Mark the follow-up as sent on ${c.label}? Next time, ${c.label} is offered first for these people.") },
            confirmButton = { TextButton(onClick = { confirm = null; onSent(c) }) { Text("Yes, sent") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Not yet") } }
        )
    }
}

/** "Just checking in on…" for something someone owes (Waiting on → Nudge). */
@Composable
internal fun NudgeSheet(task: com.craftflowtechnologies.meetingmind.core.work.WorkTask, viewModel: WorkViewModel, onDismiss: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val self by viewModel.self.collectAsState()
    val recipient by androidx.compose.runtime.produceState<WorkPerson?>(null, task.id) { value = viewModel.ownerOf(task) }
    FollowUpSheet(
        title = "Nudge", recipients = listOfNotNull(recipient), settings = settings,
        compose = { c, t -> com.craftflowtechnologies.meetingmind.core.work.FollowUpWriter.nudge(viewModel.nudgeLine(task), self?.name?.takeIf { it != "Me" }, t, c) },
        onDismiss = onDismiss,
        onSent = { c -> recipient?.let { viewModel.rememberChannel(it, c) }; onDismiss() }
    )
}
