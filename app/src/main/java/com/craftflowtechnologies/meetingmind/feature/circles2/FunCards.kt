package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.COMPANION_IDS
import com.craftflowtechnologies.meetingmind.core.circles2.CardPayload
import com.craftflowtechnologies.meetingmind.core.circles2.CelebrationKind
import com.craftflowtechnologies.meetingmind.core.circles2.ChainState
import com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.PollState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion as CompanionView
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

// ───────────────────────────── Poll ─────────────────────────────

@Composable
fun PollCard(m: ChatMessage, actions: ChatActions) {
    val pollId = m.pollId ?: return
    val state by remember(pollId) { actions.pollFor(pollId) }.collectAsState(DataState.Loading)
    when (val s = state) {
        DataState.Loading -> StatusLine(StatusKind.Info, "Loading poll...")
        is DataState.Failed -> StatusLine(StatusKind.Warning, s.failure.message)
        is DataState.Ready -> PollBody(s.value, canClose = false, actions = actions, fallbackQuestion = m.text, creatorName = m.authorName)
    }
}

@Composable
fun PollBody(s: PollState, canClose: Boolean, actions: ChatActions, fallbackQuestion: String = "", creatorName: String = "") {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text("Poll", style = MM.type.overline, color = c.inkSecondary)
        SelectionContainer { Text(s.poll.question.ifBlank { fallbackQuestion }, style = MM.type.bodyStrong, color = c.ink) }
        s.poll.options.forEach { o ->
            val mine = o.id in s.mine
            val frac = s.fraction(o.id)
            Surface(
                shape = MM.radius.small, color = c.surfaceSunk,
                modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
                    .clickable(enabled = !s.poll.closed, role = Role.Button, onClickLabel = "Vote for ${o.text}") { actions.onVote(s, o.id) }
                    .semantics { contentDescription = "${o.text}, ${s.count(o.id)} votes${if (mine) ", your vote" else ""}" }
            ) {
                Box {
                    Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(if (mine) c.accentWash else c.track)) }
                    Row(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                        if (mine) { Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall)); Spacer(Modifier.width(MM.space.xs)) }
                        Text(o.text, style = MM.type.body, color = c.ink, modifier = Modifier.weight(1f))
                        Text("${s.count(o.id)}", style = MM.type.caption, color = c.inkSecondary)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (s.voters == 1) "1 vote" else "${s.voters} votes") + (if (s.poll.multi) " · choose any" else "") + (if (s.poll.closed) " · closed" else ""),
                style = MM.type.caption, color = c.inkSecondary, modifier = Modifier.weight(1f)
            )
            if (!s.poll.closed && (canClose || s.poll.createdBy == s.myUid)) TextAction("Close poll", { actions.onClosePoll(s.poll.id) })
        }
    }
}

// ───────────────────────────── Prayer chain ─────────────────────────────

@Composable
fun ChainCard(m: ChatMessage, actions: ChatActions) {
    val chainId = m.chainId ?: return
    val state by remember(chainId) { actions.chainFor(chainId) }.collectAsState(DataState.Loading)
    when (val s = state) {
        DataState.Loading -> StatusLine(StatusKind.Info, "Loading prayer chain...")
        is DataState.Failed -> StatusLine(StatusKind.Warning, s.failure.message)
        is DataState.Ready -> ChainBody(s.value, actions)
    }
}

@Composable
fun ChainBody(s: ChainState, actions: ChatActions) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            ProgressRing(s.progress, "${s.claimed}/${s.chain.hours}")
            Column(Modifier.weight(1f)) {
                Text("24-hour prayer chain", style = MM.type.overline, color = c.inkSecondary)
                SelectionContainer { Text(s.chain.title, style = MM.type.bodyStrong, color = c.ink) }
                Text(
                    if (s.ended) "Finished · ${s.claimed} of ${s.chain.hours} hours were covered"
                    else "${s.claimed} of ${s.chain.hours} hours covered · started by ${s.chain.createdByName.ifBlank { "a member" }}",
                    style = MM.type.caption, color = c.inkSecondary
                )
            }
        }
        if (!s.ended) {
            Text("Tap an hour to take it. Tap yours again to give it back.", style = MM.type.caption, color = c.inkSecondary)
            val rows = (0 until s.chain.hours).chunked(6)
            rows.forEach { hours ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    hours.forEach { h -> SlotCell(h, s, actions, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SlotCell(hour: Int, s: ChainState, actions: ChatActions, modifier: Modifier) {
    val c = MM.colors
    val slot = s.slotFor(hour)
    val mine = slot?.uid == s.myUid
    val clock = (java.util.Calendar.getInstance().apply { timeInMillis = s.chain.startsAt; add(java.util.Calendar.HOUR_OF_DAY, hour) }).get(java.util.Calendar.HOUR_OF_DAY)
    val label = "%02d".format(clock)
    val enabled = slot == null || mine
    Surface(
        shape = MM.radius.small,
        color = when { mine -> c.accent; slot != null -> c.accentWash; else -> c.surfaceSunk },
        modifier = modifier.heightIn(min = MMSize.minTouch)
            .clickable(enabled = enabled, role = Role.Button) { if (mine) actions.onRelease(s.chain.id, hour) else actions.onClaim(s.chain.id, hour) }
            .semantics {
                contentDescription = when {
                    mine -> "$label:00, yours. Tap to give it back"
                    slot != null -> "$label:00, taken by ${slot.name}"
                    else -> "$label:00, free. Tap to take it"
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = MM.space.xs)) {
            Text(label, style = MM.type.caption, color = if (mine) c.onAccent else c.ink)
            Text(
                slot?.name?.take(3).orEmpty().ifEmpty { "·" }, style = MM.type.caption,
                color = if (mine) c.onAccent else c.inkSecondary, maxLines = 1
            )
        }
    }
}

/** A thin ring that fills clockwise. Static: no idle animation. */
@Composable
fun ProgressRing(progress: Float, label: String, modifier: Modifier = Modifier) {
    val track = MM.colors.track
    val fill = MM.colors.accent
    Box(modifier.size(MMSize.minTouch + MM.space.xl).semantics { contentDescription = "$label hours covered" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(MM.space.xs)) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke, size = Size(size.width, size.height))
            drawArc(fill, -90f, 360f * progress.coerceIn(0f, 1f), false, style = stroke, size = Size(size.width, size.height))
        }
        Text(label, style = MM.type.caption, color = MM.colors.ink)
    }
}

// ───────────────────────────── Celebration ─────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CelebrationMessage(m: ChatMessage, onLongPress: () -> Unit) {
    val c = MM.colors
    val form = CompanionForm.entries.firstOrNull { it.name.equals(m.companion, ignoreCase = true) }
    val what = when (m.celebration) {
        "birthday" -> "has a birthday"
        "answered" -> "had a prayer answered"
        "streak" -> "hit a streak"
        else -> "reached a milestone"
    }
    Surface(
        shape = MM.radius.card, color = c.goldWash,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Message actions")
    ) {
        Row(Modifier.padding(MM.space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            if (form != null) CompanionView(form = form, state = CompanionState.CELEBRATING, size = MMSize.minTouch + MM.space.l)
            else Text("🎉", style = MM.type.display)
            Column(Modifier.weight(1f)) {
                Text("${m.authorName} $what", style = MM.type.bodyStrong, color = c.goldInk)
                if (m.text.isNotBlank()) SelectionContainer { Text(m.text, style = MM.type.body, color = c.goldInk) }
            }
        }
    }
}

// ───────────────────────────── Sheets ─────────────────────────────

@Composable
fun PollSheet(draft: PollDraft, vm: PollSheetActions, onDismiss: () -> Unit) {
    CircleSheet(onDismiss) {
        Text("Start a poll", style = MM.type.title, color = MM.colors.ink)
        CircleField(draft.question, vm.onQuestion, "Question", placeholder = "Which night works for Bible study?")
        draft.options.forEachIndexed { i, o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleField(o, { vm.onOption(i, it) }, "Option ${i + 1}", modifier = Modifier.weight(1f))
                if (draft.options.size > 2) IconButton(onClick = { vm.onRemove(i) }) { Icon(Icons.Rounded.Close, "Remove option ${i + 1}", tint = MM.colors.inkSecondary) }
            }
        }
        if (draft.options.size < 6) TextAction("Add an option", vm.onAdd)
        ToggleRow("Allow more than one choice", null, draft.multi, vm.onMulti)
        draft.error?.let { StatusLine(StatusKind.Error, it) }
        PrimaryButton(if (draft.busy) "Posting..." else "Post poll", vm.onCreate, enabled = draft.canCreate, modifier = Modifier.fillMaxWidth())
    }
}

class PollSheetActions(
    val onQuestion: (String) -> Unit, val onOption: (Int, String) -> Unit, val onAdd: () -> Unit,
    val onRemove: (Int) -> Unit, val onMulti: (Boolean) -> Unit, val onCreate: () -> Unit
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CardShareSheet(onShare: (CardPayload) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(ChatCardTemplate.Dawn) }
    var mood by remember { mutableStateOf<String?>(null) }
    CircleSheet(onDismiss) {
        Text("Share a card", style = MM.type.title, color = MM.colors.ink)
        CircleField(text, { text = it.take(600) }, "Words on the card", minLines = 2, maxLines = 5)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            ChatCardTemplate.entries.forEach { t -> MMChip(t.label, template == t, { template = t }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            CARD_MOODS.forEach { (id, label) -> MMChip(label, mood == id, { mood = if (mood == id) null else id }) }
        }
        if (text.isNotBlank()) SharedCard(CardPayload(template.id, text.trim(), mood))
        PrimaryButton("Send card", { onShare(CardPayload(template.id, text.trim(), mood)); onDismiss() }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ChainSheet(initialTitle: String, onStart: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    CircleSheet(onDismiss) {
        Text("Start a prayer chain", style = MM.type.title, color = MM.colors.ink)
        Text("For the next 24 hours, members each take an hour to pray, so the request is covered around the clock.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        CircleField(title, { title = it.take(120) }, "What are we praying for?", minLines = 2, maxLines = 4)
        PrimaryButton("Start chain", { onStart(title); onDismiss() }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun CelebrateSheet(companionId: String?, onCelebrate: (CelebrationKind, String) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(CelebrationKind.Milestone) }
    var text by remember { mutableStateOf("") }
    val form = CompanionForm.entries.firstOrNull { it.name.equals(companionId, ignoreCase = true) }
    CircleSheet(onDismiss) {
        Text("Celebrate", style = MM.type.title, color = MM.colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            if (form != null) CompanionView(form = form, state = CompanionState.CELEBRATING, size = MMSize.minTouch + MM.space.l)
            Text("Tell the circle something good. ${if (form != null) "Your companion will cheer." else ""}", style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            CelebrationKind.entries.filter { it != CelebrationKind.Answered }.forEach { k -> MMChip(k.label, kind == k, { kind = k }) }
        }
        CircleField(text, { text = it.take(140) }, "A few words (optional)", maxLines = 3)
        PrimaryButton("Celebrate", { onCelebrate(kind, text); onDismiss() }, modifier = Modifier.fillMaxWidth())
    }
}
