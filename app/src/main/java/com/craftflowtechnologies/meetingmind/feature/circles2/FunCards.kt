package com.craftflowtechnologies.meetingmind.feature.circles2

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.craftflowtechnologies.meetingmind.core.create.CreateBackground
import com.craftflowtechnologies.meetingmind.core.create.CreateCardRenderer
import com.craftflowtechnologies.meetingmind.core.create.CreateChatCard
import com.craftflowtechnologies.meetingmind.core.create.CreateDesign
import com.craftflowtechnologies.meetingmind.core.create.CreateFormat
import com.craftflowtechnologies.meetingmind.core.create.CreateRenderInput
import com.craftflowtechnologies.meetingmind.core.create.CreateVibe
import com.craftflowtechnologies.meetingmind.core.create.ResolvedScripture
import com.craftflowtechnologies.meetingmind.core.share.BackgroundPack
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion as CompanionView
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val isQuiz = s.poll.correctOptionId != null
    val revealed = isQuiz && (s.mine.isNotEmpty() || s.poll.closed)
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text(
            if (isQuiz) "Quiz poll" else "Poll",
            style = MM.type.overline,
            color = if (isQuiz) c.accent else c.inkSecondary
        )
        SelectionContainer { Text(s.poll.question.ifBlank { fallbackQuestion }, style = MM.type.bodyStrong, color = c.ink) }
        s.poll.options.forEach { o ->
            val mine = o.id in s.mine
            val frac = s.fraction(o.id)
            val isCorrect = o.id == s.poll.correctOptionId
            val isWrongChoice = mine && !isCorrect

            val barBg = when {
                revealed && isCorrect -> c.success.copy(alpha = 0.2f)
                revealed && isWrongChoice -> c.danger.copy(alpha = 0.2f)
                mine -> c.accentWash
                else -> c.track
            }
            val borderStroke = when {
                revealed && isCorrect -> BorderStroke(1.dp, c.success)
                revealed && isWrongChoice -> BorderStroke(1.dp, c.danger)
                else -> null
            }

            Surface(
                shape = MM.radius.small,
                color = c.surfaceSunk,
                border = borderStroke,
                modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
                    .clickable(enabled = !s.poll.closed, role = Role.Button, onClickLabel = "Vote for ${o.text}") { actions.onVote(s, o.id) }
                    .semantics { contentDescription = "${o.text}, ${s.count(o.id)} votes${if (mine) ", your vote" else ""}" }
            ) {
                Box {
                    Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(barBg)) }
                    Row(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                        if (revealed && isCorrect) {
                            Icon(Icons.Rounded.CheckCircle, "Correct answer", tint = c.success, modifier = Modifier.size(MMSize.iconSmall))
                            Spacer(Modifier.width(MM.space.xs))
                        } else if (revealed && isWrongChoice) {
                            Icon(Icons.Rounded.Close, "Incorrect choice", tint = c.danger, modifier = Modifier.size(MMSize.iconSmall))
                            Spacer(Modifier.width(MM.space.xs))
                        } else if (mine) {
                            Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                            Spacer(Modifier.width(MM.space.xs))
                        }
                        Text(
                            o.text,
                            style = MM.type.body,
                            color = when {
                                revealed && isCorrect -> c.success
                                revealed && isWrongChoice -> c.danger
                                else -> c.ink
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text("${s.count(o.id)}", style = MM.type.caption, color = c.inkSecondary)
                    }
                }
            }
        }
        if (revealed && !s.poll.explanation.isNullOrBlank()) {
            Surface(
                shape = MM.radius.card,
                color = c.accentWash,
                border = BorderStroke(1.dp, c.accent.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MM.space.m),
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs / 2)
                ) {
                    Text(
                        "Answer explanation",
                        style = MM.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = c.accent
                    )
                    Text(
                        s.poll.explanation,
                        style = MM.type.body,
                        color = c.ink
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val deadlineCaption = s.poll.closesAt?.let { closesAt ->
                val remaining = closesAt - System.currentTimeMillis()
                when {
                    remaining <= 0 -> "Deadline passed"
                    remaining < 3600_000L -> "Closes in ${(remaining / 60_000L).coerceAtLeast(1)}m"
                    remaining < 86400_000L -> "Closes in ${remaining / 3600_000L}h"
                    else -> "Closes in ${remaining / 86400_000L}d"
                }
            }
            val statusText = buildString {
                append(if (s.voters == 1) "1 vote" else "${s.voters} votes")
                if (s.poll.multi) append(" · choose any")
                if (s.poll.closed) append(" · closed")
                else if (deadlineCaption != null) append(" · $deadlineCaption")
            }
            Text(
                statusText,
                style = MM.type.caption, color = c.inkSecondary, modifier = Modifier.weight(1f)
            )
            if (!s.poll.closed && (canClose || s.poll.createdBy == s.myUid)) TextAction("Close poll", { actions.onClosePoll(s.poll.id) })
        }
    }
}

@Composable
fun PollBubble(state: PollState, canClose: Boolean = false, actions: ChatActions) {
    PollBody(s = state, canClose = canClose, actions = actions)
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
fun PollSheet(
    draft: PollDraft,
    vm: PollSheetActions,
    onDismiss: () -> Unit,
    space: String = "faith",
    onClosesAt: (Long?) -> Unit = {}
) {
    val questionPlaceholder = when (space) {
        "work" -> "What should we prioritize next?"
        "learning", "study" -> "Which topic should we cover next?"
        "faith" -> "Which night works for Bible study?"
        else -> "What should we do for our next hangout?"
    }
    var selectedDeadline by remember { mutableStateOf("Open ended") }
    var quizMode by remember { mutableStateOf(draft.correctOptionIndex != null) }
    LaunchedEffect(draft.multi) {
        if (draft.multi && quizMode) {
            quizMode = false
            vm.onCorrectOption(null)
        }
    }
    CircleSheet(onDismiss) {
        Text("Start a poll", style = MM.type.title, color = MM.colors.ink)
        if (space == "work") {
            Text("Templates", style = MM.type.overline, color = MM.colors.inkSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                MMChip(
                    label = "Meeting time",
                    selected = false,
                    onClick = {
                        vm.onQuestion("Preferred meeting time?")
                        vm.onOption(0, "Tomorrow 10:00 AM")
                        if (draft.options.size > 1) vm.onOption(1, "Tomorrow 2:00 PM")
                        if (draft.options.size < 3) vm.onAdd() else vm.onOption(2, "Tomorrow 4:30 PM")
                        vm.onMulti(true)
                    }
                )
                MMChip(
                    label = "Go / No-Go",
                    selected = false,
                    onClick = {
                        vm.onQuestion("Release Go / No-Go")
                        vm.onOption(0, "Ship (Go)")
                        if (draft.options.size > 1) vm.onOption(1, "Hold (No-Go)")
                        if (draft.options.size < 3) vm.onAdd() else vm.onOption(2, "Needs review")
                        vm.onMulti(false)
                    }
                )
            }
        }
        CircleField(draft.question, vm.onQuestion, "Question", placeholder = questionPlaceholder)
        draft.options.forEachIndexed { i, o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (quizMode) {
                    val isCorrect = draft.correctOptionIndex == i
                    IconButton(onClick = { vm.onCorrectOption(if (isCorrect) null else i) }) {
                        Icon(
                            imageVector = if (isCorrect) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isCorrect) "Option ${i + 1} is correct answer" else "Mark option ${i + 1} as correct",
                            tint = if (isCorrect) MM.colors.accent else MM.colors.inkSecondary
                        )
                    }
                }
                CircleField(o, { vm.onOption(i, it) }, "Option ${i + 1}", modifier = Modifier.weight(1f))
                if (draft.options.size > 2) IconButton(onClick = {
                    if (draft.correctOptionIndex == i) vm.onCorrectOption(null)
                    else if (draft.correctOptionIndex != null && draft.correctOptionIndex > i) vm.onCorrectOption(draft.correctOptionIndex - 1)
                    vm.onRemove(i)
                }) { Icon(Icons.Rounded.Close, "Remove option ${i + 1}", tint = MM.colors.inkSecondary) }
            }
        }
        if (draft.options.size < 6) TextAction("Add an option", vm.onAdd)
        ToggleRow("Quiz poll (mark the correct answer)", null, quizMode, onChange = { enabled ->
            quizMode = enabled
            if (enabled) {
                vm.onMulti(false)
                if (draft.correctOptionIndex == null && draft.options.isNotEmpty()) {
                    vm.onCorrectOption(0)
                }
            } else {
                vm.onCorrectOption(null)
                vm.onExplanation("")
            }
        })
        if (quizMode) {
            CircleField(
                value = draft.explanation,
                onValueChange = vm.onExplanation,
                label = "Explanation (shown after voting)",
                placeholder = "Why this is correct / learning context"
            )
        }
        if (space == "work") {
            Text("Deadline", style = MM.type.overline, color = MM.colors.inkSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                listOf("Open ended", "2h", "24h", "3 days").forEach { label ->
                    MMChip(
                        label = label,
                        selected = selectedDeadline == label,
                        onClick = {
                            selectedDeadline = label
                            val closesAt = when (label) {
                                "2h" -> System.currentTimeMillis() + 2 * 3600_000L
                                "24h" -> System.currentTimeMillis() + 24 * 3600_000L
                                "3 days" -> System.currentTimeMillis() + 3 * 86400_000L
                                else -> null
                            }
                            onClosesAt(closesAt)
                        }
                    )
                }
            }
        }
        ToggleRow("Allow more than one choice", null, draft.multi, onChange = { multi ->
            if (multi && quizMode) {
                quizMode = false
                vm.onCorrectOption(null)
            }
            vm.onMulti(multi)
        })
        draft.error?.let { StatusLine(StatusKind.Error, it) }
        PrimaryButton(if (draft.busy) "Posting..." else "Post poll", vm.onCreate, enabled = draft.canCreate, modifier = Modifier.fillMaxWidth())
    }
}

class PollSheetActions(
    val onQuestion: (String) -> Unit, val onOption: (Int, String) -> Unit, val onAdd: () -> Unit,
    val onRemove: (Int) -> Unit, val onMulti: (Boolean) -> Unit, val onCreate: () -> Unit,
    val onCorrectOption: (Int?) -> Unit = {}, val onExplanation: (String) -> Unit = {}
)

@Composable
fun ChatCardComposerSheet(
    space: String = "faith",
    onShare: (CardPayload) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isFaith = space == "faith"
    var text by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var activeWorkPreset by remember { mutableStateOf<String?>(null) }
    var selectedPack by remember { mutableStateOf(BackgroundPack.all.first()) }
    val initialVibe = if (isFaith) CreateVibe.PEACEFUL else CreateVibe.MOTIVATIONAL
    var selectedVibe by remember { mutableStateOf(initialVibe) }

    val vibes = remember(space) {
        if (space == "faith") {
            CreateVibe.entries.toList()
        } else {
            listOf(
                CreateVibe.MOTIVATIONAL,
                CreateVibe.WISDOM,
                CreateVibe.CELEBRATORY,
                CreateVibe.GRATEFUL,
                CreateVibe.PEACEFUL,
                CreateVibe.REFLECTIVE,
                CreateVibe.JOYFUL,
                CreateVibe.FUNNY,
                CreateVibe.LOVE,
                CreateVibe.PRAYERFUL,
                CreateVibe.CUSTOM
            )
        }
    }

    val previewText = text.ifBlank {
        if (isFaith) "Be still, and know that I am God." else "Small steps every day lead to big results."
    }
    val resolvedScripture = remember(reference) {
        if (reference.isNotBlank()) {
            ResolvedScripture(
                reference = reference.trim(),
                text = "",
                versionId = 0,
                versionAbbreviation = "",
                attribution = ""
            )
        } else null
    }
    val renderInput = remember(previewText, resolvedScripture, selectedPack, selectedVibe) {
        CreateRenderInput(
            text = previewText,
            verse = resolvedScripture,
            format = CreateFormat.SQUARE,
            design = CreateDesign(
                background = CreateBackground.Pack(selectedPack.id),
                centered = true
            ),
            eyebrow = selectedVibe.label.uppercase()
        )
    }
    val renderedBitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, renderInput) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                CreateCardRenderer.render(context, renderInput, 0.35f).asImageBitmap()
            }.getOrNull()
        }
    }

    CircleSheet(onDismiss) {
        Text("Share a card", style = MM.type.title, color = MM.colors.ink)

        if (space == "work") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                listOf("Decision", "Launch Win", "Team Shoutout").forEach { preset ->
                    MMChip(
                        label = preset,
                        selected = activeWorkPreset == preset,
                        onClick = {
                            activeWorkPreset = preset
                            when (preset) {
                                "Decision" -> {
                                    text = "Decision: \n\nRationale: "
                                    selectedVibe = CreateVibe.WISDOM
                                }
                                "Launch Win" -> {
                                    text = "Launch Win: \n\nKey Result: "
                                    selectedVibe = CreateVibe.CELEBRATORY
                                }
                                "Team Shoutout" -> {
                                    text = "Team Shoutout to: \n\nFor: "
                                    selectedVibe = CreateVibe.GRATEFUL
                                }
                            }
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MM.radius.card),
            contentAlignment = Alignment.Center
        ) {
            val bmp = renderedBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Card preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(MM.radius.card)
                )
            } else {
                SharedCard(
                    card = CardPayload(
                        templateId = selectedPack.id,
                        text = previewText,
                        mood = selectedVibe.name.lowercase(),
                        verseRef = reference.takeIf { it.isNotBlank() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        CircleField(
            value = text,
            onValueChange = { text = it.take(600) },
            label = "Words on the card",
            placeholder = if (isFaith) "Type an inspiring thought or scripture…" else "Type a thought, win, or takeaway…",
            minLines = 2,
            maxLines = 4
        )

        val refLabel = when {
            isFaith -> "Verse or source (optional)"
            space == "work" -> "Attribute / source"
            else -> "Key takeaway or attribution (optional)"
        }
        val refPlaceholder = when {
            isFaith -> "e.g. Psalm 23:1"
            space == "work" && activeWorkPreset == "Decision" -> "Decision owner or meeting"
            space == "work" && activeWorkPreset == "Launch Win" -> "Milestone / date"
            space == "work" && activeWorkPreset == "Team Shoutout" -> "Team member or team"
            space == "work" -> "Decision owner or meeting"
            else -> "e.g. Maya Angelou"
        }
        CircleField(
            value = reference,
            onValueChange = { reference = it.take(64) },
            label = refLabel,
            placeholder = refPlaceholder,
            singleLine = true
        )

        Text("Background", style = MM.type.overline, color = MM.colors.inkSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            BackgroundPack.all.forEach { pack ->
                val selected = selectedPack.id == pack.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(56.dp)
                        .clickable(role = Role.Button) { selectedPack = pack }
                        .semantics { contentDescription = pack.name + if (selected) ", selected" else "" }
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 76.dp)
                            .clip(MM.radius.small)
                            .then(
                                if (selected) Modifier.border(2.dp, MM.colors.accent, MM.radius.small)
                                else Modifier
                            )
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawIntoCanvas {
                                BackgroundPack.draw(it.nativeCanvas, pack, size.width.toInt(), size.height.toInt())
                            }
                        }
                    }
                    Spacer(Modifier.height(MM.space.xs))
                    Text(
                        pack.name,
                        style = MM.type.caption,
                        color = if (selected) MM.colors.accent else MM.colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text("Vibe", style = MM.type.overline, color = MM.colors.inkSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            vibes.forEach { vibe ->
                MMChip(
                    label = vibe.label,
                    selected = selectedVibe == vibe,
                    onClick = { selectedVibe = vibe }
                )
            }
        }

        PrimaryButton(
            text = "Send to chat",
            onClick = {
                val payload = CardPayload(
                    templateId = CreateChatCard.PREFIX + selectedPack.id,
                    text = text.trim(),
                    mood = selectedVibe.name.lowercase(),
                    verseRef = reference.trim().takeIf { it.isNotBlank() },
                    verseText = null
                )
                onShare(payload)
                onDismiss()
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CardShareSheet(onShare: (CardPayload) -> Unit, onDismiss: () -> Unit) {
    ChatCardComposerSheet(space = "faith", onShare = onShare, onDismiss = onDismiss)
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
fun CelebrateSheet(companionId: String?, onCelebrate: (CelebrationKind, String) -> Unit, onDismiss: () -> Unit, space: String = "faith") {
    val isFaith = space.equals("faith", ignoreCase = true)
    var kind by remember { mutableStateOf(CelebrationKind.Milestone) }
    var text by remember { mutableStateOf("") }
    val form = CompanionForm.entries.firstOrNull { it.name.equals(companionId, ignoreCase = true) }
    val kinds = remember(space) {
        if (isFaith) CelebrationKind.entries
        else CelebrationKind.entries.filter { it != CelebrationKind.Answered }
    }
    val cheeringText = if (form != null) " Your companion will cheer." else ""
    val subtitle = if (isFaith) "Tell the circle something good.$cheeringText" else "Share a win with the group.$cheeringText"
    val quickKudos = remember(space) {
        when (space.lowercase()) {
            "work" -> listOf(
                "Shipped it!" to CelebrationKind.Milestone,
                "Great assist" to CelebrationKind.Milestone,
                "Unblocked the team" to CelebrationKind.Milestone
            )
            "study", "learning" -> listOf(
                "Study Streak" to CelebrationKind.Streak,
                "Exam Ready" to CelebrationKind.Milestone,
                "Concept Clicked" to CelebrationKind.Milestone
            )
            "faith" -> listOf(
                "Answered Prayer" to CelebrationKind.Answered,
                "Grateful Today" to CelebrationKind.Milestone,
                "Keep Believing" to CelebrationKind.Streak
            )
            else -> listOf(
                "Big Milestone" to CelebrationKind.Milestone,
                "Appreciate You" to CelebrationKind.Milestone,
                "Count Me In" to CelebrationKind.Streak
            )
        }
    }
    CircleSheet(onDismiss) {
        Text("Celebrate", style = MM.type.title, color = MM.colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            if (form != null) CompanionView(form = form, state = CompanionState.CELEBRATING, size = MMSize.minTouch + MM.space.l)
            Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            kinds.forEach { k -> MMChip(k.label, kind == k, { kind = k }) }
        }
        Text("Quick kudos", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.inkSecondary)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            quickKudos.forEach { (label, pickKind) ->
                val isSelected = text == label && kind == pickKind
                Surface(
                    shape = MM.radius.pill,
                    color = if (isSelected) MM.colors.accentWash else MM.colors.surfaceSunk,
                    border = BorderStroke(MMSize.hairline, if (isSelected) MM.colors.accent else MM.colors.lineSoft),
                    modifier = Modifier.clickable {
                        kind = pickKind
                        text = if (text.isBlank()) label else if (text == label) label else "$text · $label"
                    }
                ) {
                    Text(
                        label,
                        style = MM.type.caption.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (isSelected) MM.colors.accent else MM.colors.ink,
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                    )
                }
            }
        }
        CircleField(text, { text = it.take(140) }, "A few words (optional)", maxLines = 3)
        PrimaryButton("Celebrate", { onCelebrate(kind, text); onDismiss() }, modifier = Modifier.fillMaxWidth())
    }
}
