package com.craftflowtechnologies.meetingmind.feature.notes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.MarkdownChunks
import com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

/** How much of a long Markdown block shows while it is folded. */
internal const val MARKDOWN_FOLDED_ITEMS = 8
internal const val PAYLOAD_EXPANDED = "expanded"

/** Whether a Markdown block is long enough to fold. */
internal fun isLongMarkdown(items: Int, chars: Int) = items > MARKDOWN_FOLDED_ITEMS + 2 || chars > 1600

/**
 * A whole pasted document in one block. It reads as formatted text — headings, lists, tables,
 * code, pictures — and a long one folds to its first lines with "Show all". Tapping it opens the
 * Markdown source to edit; Done (or tapping elsewhere) shows it formatted again.
 */
@Composable
internal fun MarkdownBlock(
    block: NoteBlock,
    serif: Boolean,
    editing: Boolean,
    onEdit: (Boolean) -> Unit,
    onText: (String) -> Unit,
    onToggleExpanded: () -> Unit
) {
    if (editing) {
        MarkdownSource(block.content.text, onText) { onEdit(false) }
        return
    }
    // The pasted document as pieces (a paragraph, a list, a table, a code fence). Tapping a piece
    // edits just that piece, in place, while the rest stays formatted; the whole-block source editor
    // stays in the block's menu. While a piece is edited the list is held still, so typing a blank
    // line in it can't shuffle what is being edited.
    var editingPiece by remember { mutableStateOf<Int?>(null) }
    var frozen by remember { mutableStateOf<List<String>?>(null) }
    val current = remember(block.content.text) { MarkdownChunks.split(block.content.text) }
    val pieces = frozen ?: current
    if (pieces.isEmpty()) {
        Text("Empty Markdown — tap to write", color = InkMuted, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable { onEdit(true) }.padding(vertical = 10.dp))
        return
    }
    val parsed = remember(pieces) { pieces.map { MarkdownImport.parse(it, block.noteId) } }
    val totalItems = parsed.sumOf { it.size }
    val long = isLongMarkdown(totalItems, block.content.text.length)
    val expanded = !long || block.payload[PAYLOAD_EXPANDED] == "1" || editingPiece != null
    // Folded: the first pieces, until about eight items are showing.
    var shownPieces = pieces.size
    if (!expanded) {
        var n = 0; shownPieces = 0
        while (shownPieces < pieces.size && n < MARKDOWN_FOLDED_ITEMS) { n += parsed[shownPieces].size; shownPieces++ }
    }
    val colors = LocalMMColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box {
            Column(Modifier.fillMaxWidth()) {
                for (i in 0 until shownPieces) {
                    if (editingPiece == i) {
                        MarkdownSource(pieces[i], label = "Editing this part", onText = { t ->
                            val next = MarkdownChunks.replace(pieces, i, t)
                            frozen = next
                            onText(MarkdownChunks.join(next))
                        }) { editingPiece = null; frozen = null }
                    } else {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { frozen = pieces; editingPiece = i }) {
                            parsed[i].forEachIndexed { k, item -> MarkdownItem(item, parsed[i], k, serif, onTap = { frozen = pieces; editingPiece = i }) }
                        }
                    }
                }
            }
            if (!expanded) {
                // A soft fade into the page says there's more below.
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background))))
            }
        }
        if (long && editingPiece == null) {
            val hidden = if (expanded) 0 else parsed.drop(shownPieces).sumOf { it.size }
            Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(SurfaceSunk).clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (expanded) "Show less" else "Show all · $hidden more", fontSize = 13.sp, color = InkSecondary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Markdown shown formatted, read-only — for previews. */
@Composable
internal fun MarkdownPreview(items: List<NoteBlock>, serif: Boolean = false) {
    Column { items.forEachIndexed { i, item -> MarkdownItem(item, items, i, serif, onTap = {}) } }
}

/** One heading, paragraph, list item, table or picture inside a Markdown block, read-only. */
@Composable
private fun MarkdownItem(item: NoteBlock, all: List<NoteBlock>, index: Int, serif: Boolean, onTap: () -> Unit) {
    val colors = LocalMMColors.current
    when (item.type) {
        NoteBlockType.DIVIDER -> HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
        NoteBlockType.CODE -> CodeBlock(item, onTap = onTap) {}
        NoteBlockType.TABLE -> TableBlock(item)
        NoteBlockType.EMBED -> EmbedBlock(item)
        else -> Row(
            Modifier.fillMaxWidth().padding(top = blockTopPadding(item.type), bottom = 2.dp).padding(start = (item.indent * 22).dp),
            verticalAlignment = Alignment.Top
        ) {
            when (item.type) {
                NoteBlockType.BULLET -> Box(Modifier.width(24.dp).height(25.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (item.indent % 2 == 0) Ink else InkMuted))
                }
                NoteBlockType.NUMBERED -> Text("${BlockEditing.numberFor(all, index)}.", modifier = Modifier.width(26.dp),
                    style = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, color = InkSecondary, fontWeight = FontWeight.Medium))
                NoteBlockType.CHECKLIST -> Box(Modifier.width(30.dp).height(25.dp), contentAlignment = Alignment.CenterStart) {
                    Surface(shape = RoundedCornerShape(6.dp), color = if (item.checked) Accent else colors.surface,
                        border = if (item.checked) null else androidx.compose.foundation.BorderStroke(1.5.dp, InkMuted), modifier = Modifier.size(19.dp)) {
                        if (item.checked) Icon(Icons.Filled.Check, contentDescription = "Done", tint = colors.onAccent, modifier = Modifier.padding(2.dp))
                    }
                }
                NoteBlockType.QUOTE -> Box(Modifier.padding(end = 12.dp).width(3.dp).height(25.dp).background(Accent, RoundedCornerShape(2.dp)))
                else -> Unit
            }
            Text(styled(item.content, colors), style = blockTextStyle(item.type, serif), modifier = Modifier.weight(1f))
        }
    }
}

/** The Markdown source, to edit as text. */
@Composable
private fun MarkdownSource(text: String, onText: (String) -> Unit, label: String = "Markdown", onDone: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceSunk)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = InkMuted, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("**bold**  # heading  - list", fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(end = 8.dp))
            Surface(onClick = onDone, shape = RoundedCornerShape(50), color = AccentWash) {
                Text("Done", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        BasicTextField(
            value = value,
            onValueChange = { v -> value = v; if (v.text != text) onText(v.text) },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp, color = Ink),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(14.dp).focusRequester(focus)
        )
    }
}

/** The chevron on a heading that folds its section away. */
@Composable
internal fun FoldChevron(folded: Boolean, hidden: Int, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (folded && hidden > 0) Text("$hidden", fontSize = 12.sp, color = InkMuted)
        Icon(
            if (folded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (folded) "Unfold section" else "Fold section",
            tint = InkMuted, modifier = Modifier.size(20.dp)
        )
    }
}
