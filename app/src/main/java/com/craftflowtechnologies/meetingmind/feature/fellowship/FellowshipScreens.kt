package com.craftflowtechnologies.meetingmind.feature.fellowship

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.share.ShareCardContent
import com.craftflowtechnologies.meetingmind.core.share.ShareHelper
import com.craftflowtechnologies.meetingmind.feature.faith.faithIcon
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase

/** How long a card's text can be before the renderer starts shortening it. */
internal const val CARD_TEXT_SOFT_LIMIT = 600

/** Turning a note into the words that go on a card or in a message. Pure, so it can be tested. */
internal object FellowshipText {

    /** The note's own words, tidied: no stray blank lines, and cut at a word if very long. */
    fun draftFor(note: Note, limit: Int = 900): String {
        val body = note.plainText.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").ifBlank { note.title.trim() }
        if (body.length <= limit) return body
        return body.take(limit).substringBeforeLast(' ').trimEnd(',', ';', ':', '-') + "…"
    }

    /** A short line under a note's title in a picker: when it happened, and what kind it is. */
    fun meta(note: Note, now: Long = System.currentTimeMillis()): String {
        val kind = if (note.workflow == RecordingType.PRAYER_REQUEST && note.status == NoteStatus.ANSWERED) "Answered prayer" else note.workflow.displayName
        return kind + " · " + Formatters.formatDateRelative(note.eventDate ?: note.createdAt)
    }
}

/** The Fellowship hub: what you can send to your group, each starting from a note you already wrote. */
@Composable
fun FellowshipHubScreen(
    onNavigateBack: () -> Unit,
    onPick: (FellowshipKind) -> Unit,
    /** The circles section; null until private circles are connected, so nothing is shown in its place. */
    circlesSection: (@Composable () -> Unit)? = null
) {
    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { FellowshipTopBar("Fellowship", "Faith is better together", onNavigateBack) }
            item {
                Spacer(Modifier.height(6.dp))
                FellowshipHero(
                    title = "Share with your group",
                    body = "Everything here starts from your own notes, and nothing leaves your phone until you tap share.",
                    icon = Icons.Filled.Groups
                )
            }
            item { FellowshipSectionTitle("Send to your group", top = 26.dp) }
            items(FellowshipKind.entries.toList(), key = { it.route }) { kind ->
                FellowshipActionCard(
                    title = kind.title, body = kind.line, icon = kind.icon, tint = kind.tint,
                    onClick = { onPick(kind) },
                    modifier = Modifier.padding(bottom = 12.dp).testTag("fellowship_${kind.route}")
                )
            }
            if (circlesSection != null) item { circlesSection() }
            item {
                Spacer(Modifier.height(8.dp))
                FellowshipNote("You review every word before it leaves your phone. Personal prayers, journal entries and devotionals are never listed here.")
            }
        }
    }
}

/** Your own notes this kind can use — or an honest empty state with the next step. */
@Composable
fun FellowshipPickerScreen(
    kind: FellowshipKind,
    notes: List<Note>,
    onNavigateBack: () -> Unit,
    onPick: (Note) -> Unit,
    onCreate: () -> Unit
) {
    val candidates = remember(kind, notes) { FellowshipKind.candidates(kind, notes) }
    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { FellowshipTopBar(kind.pickerTitle, kind.pickerLine, onNavigateBack) }
            if (candidates.isEmpty()) {
                item { FellowshipEmptyState(kind.icon, kind.emptyTitle, kind.emptyBody, Modifier.padding(top = 24.dp), kind.emptyAction, onCreate) }
            } else {
                item { Spacer(Modifier.height(8.dp)) }
                items(candidates, key = { it.id }) { note ->
                    FellowshipNoteRow(
                        title = note.title.ifBlank { note.workflow.displayName },
                        meta = FellowshipText.meta(note),
                        icon = faithIcon(note.workflow),
                        isPrivate = note.isPrivate,
                        onClick = { onPick(note) }
                    )
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    FellowshipNote("Notes marked private are shown so you can choose them on purpose. Nothing is shared until you tap share on the next screen.")
                }
            }
        }
    }
}

/** Review and edit the words, then design a card or send them as text. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FellowshipDraftScreen(
    kind: FellowshipKind,
    note: Note?,
    onNavigateBack: () -> Unit,
    onDesignCard: (ShareRequest) -> Unit
) {
    if (note == null) {
        Scaffold(containerColor = SurfaceBase) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item { FellowshipTopBar(kind.title, null, onNavigateBack) }
                item { FellowshipEmptyState(Icons.Filled.EventBusy, "This note isn't available", "It may have been moved to the trash. Go back and choose another.") }
            }
        }
        return
    }
    val context = LocalContext.current
    var text by remember(note.id) { mutableStateOf(FellowshipText.draftFor(note)) }
    var eyebrowIndex by remember(note.id) { mutableIntStateOf(0) }
    val eyebrow = kind.eyebrows.getOrElse(eyebrowIndex) { kind.title }
    val ready = text.isNotBlank()

    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { FellowshipTopBar(kind.title, "Review every word before it leaves your phone", onNavigateBack) }
            item {
                FellowshipSectionTitle("Label", top = 12.dp)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    kind.eyebrows.forEachIndexed { i, label -> FellowshipChip(label, selected = i == eyebrowIndex, onClick = { eyebrowIndex = i }) }
                }
            }
            item {
                FellowshipSectionTitle("Your words", top = 22.dp)
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("fellowship_draft_text"),
                    minLines = 6, shape = RoundedCornerShape(18.dp), colors = fellowshipFieldColors(),
                    textStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontFamily = FontFamily.Serif)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (text.length > CARD_TEXT_SOFT_LIMIT) "Long text is shortened on a card" else "Edit anything, including names",
                        fontSize = 12.sp, color = InkMuted
                    )
                    Text("${text.length} / $CARD_TEXT_SOFT_LIMIT", fontSize = 12.sp, color = if (text.length > CARD_TEXT_SOFT_LIMIT) Ink else InkMuted)
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                FellowshipNote(
                    when (kind) {
                        FellowshipKind.PRAYER -> "A request can name someone. Check they're happy to be shared, and take out anything private. Card backgrounds are made from a general theme, never from your words."
                        else -> "Take out anything you'd rather keep private. Card backgrounds are made from a general theme, never from your words."
                    }
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FellowshipPrimaryButton(
                        "Design card", enabled = ready, icon = Icons.Filled.Edit, modifier = Modifier.weight(1f).testTag("fellowship_design"),
                        onClick = {
                            onDesignCard(
                                ShareRequest(
                                    content = ShareCardContent(eyebrow = eyebrow, text = text.trim(), reference = null, attribution = null, quoted = false),
                                    theme = kind.backgroundTheme
                                )
                            )
                        }
                    )
                    FellowshipSecondaryButton(
                        "Send as text", icon = Icons.Filled.Share, modifier = Modifier.weight(1f).testTag("fellowship_text"),
                        onClick = { if (ready) ShareHelper.shareText(context, eyebrow, "$eyebrow\n\n${text.trim()}", "Share") }
                    )
                }
            }
        }
    }
}
