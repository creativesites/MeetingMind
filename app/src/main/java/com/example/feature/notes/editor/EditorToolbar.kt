package com.example.feature.notes.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.NoteBlockType
import com.example.core.notes.InlineStyle
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line

/**
 * The bar above the keyboard while a text block has the caret.
 *
 * One scrolling row: insert, then inline styles, then block types, then indent and undo. A
 * control that is on shows it with the accent wash, the same way the rest of the app marks a
 * selection.
 */
@Composable
internal fun FormattingToolbar(
    isActive: (InlineStyle) -> Boolean,
    blockType: NoteBlockType?,
    canUndo: Boolean,
    canRedo: Boolean,
    onInsert: () -> Unit,
    onInline: (InlineStyle) -> Unit,
    onLink: () -> Unit,
    onBlockType: (NoteBlockType) -> Unit,
    onIndent: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit
) {
    Surface(color = Color.White, shadowElevation = 6.dp) {
        androidx.compose.foundation.layout.Column {
            HorizontalDivider(color = Line, thickness = 0.75.dp)
            Row(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(onClick = onInsert, shape = CircleShape, color = Ink, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Insert", tint = Color.White, modifier = Modifier.padding(7.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    ToolButton(Icons.Filled.FormatBold, "Bold", isActive(InlineStyle.BOLD)) { onInline(InlineStyle.BOLD) }
                    ToolButton(Icons.Filled.FormatItalic, "Italic", isActive(InlineStyle.ITALIC)) { onInline(InlineStyle.ITALIC) }
                    ToolButton(Icons.Filled.FormatUnderlined, "Underline", isActive(InlineStyle.UNDERLINE)) { onInline(InlineStyle.UNDERLINE) }
                    ToolButton(Icons.Filled.FormatStrikethrough, "Strikethrough", isActive(InlineStyle.STRIKETHROUGH)) { onInline(InlineStyle.STRIKETHROUGH) }
                    ToolButton(Icons.Filled.BorderColor, "Highlight", isActive(InlineStyle.HIGHLIGHT)) { onInline(InlineStyle.HIGHLIGHT) }
                    ToolButton(Icons.Filled.Code, "Code", isActive(InlineStyle.CODE)) { onInline(InlineStyle.CODE) }
                    ToolButton(Icons.Filled.Link, "Link", isActive(InlineStyle.LINK), onClick = onLink)
                    Separator()
                    TextTool("H1", blockType == NoteBlockType.HEADING_1) { onBlockType(NoteBlockType.HEADING_1) }
                    TextTool("H2", blockType == NoteBlockType.HEADING_2) { onBlockType(NoteBlockType.HEADING_2) }
                    TextTool("H3", blockType == NoteBlockType.HEADING_3) { onBlockType(NoteBlockType.HEADING_3) }
                    ToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bulleted list", blockType == NoteBlockType.BULLET) { onBlockType(NoteBlockType.BULLET) }
                    ToolButton(Icons.Filled.FormatListNumbered, "Numbered list", blockType == NoteBlockType.NUMBERED) { onBlockType(NoteBlockType.NUMBERED) }
                    ToolButton(Icons.Filled.CheckBox, "Checklist", blockType == NoteBlockType.CHECKLIST) { onBlockType(NoteBlockType.CHECKLIST) }
                    ToolButton(Icons.Filled.FormatQuote, "Quote", blockType == NoteBlockType.QUOTE) { onBlockType(NoteBlockType.QUOTE) }
                    if (blockType?.isListItem == true) {
                        Separator()
                        ToolButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Outdent", false) { onIndent(-1) }
                        ToolButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent", false) { onIndent(1) }
                    }
                    Separator()
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", false, enabled = canUndo, onClick = onUndo)
                    ToolButton(Icons.AutoMirrored.Filled.Redo, "Redo", false, enabled = canRedo, onClick = onRedo)
                }
                ToolButton(Icons.Filled.KeyboardHide, "Done editing", false, onClick = onDone)
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (active) AccentWash else Color.Transparent,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = when { !enabled -> InkFaint; active -> Accent; else -> InkSecondary }, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun TextTool(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (active) AccentWash else Color.Transparent, modifier = Modifier.size(38.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (active) Accent else InkSecondary)
        }
    }
}

@Composable
private fun Separator() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).padding(0.dp)) {
        Surface(color = Line, modifier = Modifier.width(1.dp).height(22.dp)) {}
    }
}

/** When no text has the caret: a slim bar to insert things, with the word count. */
@Composable
internal fun IdleEditorBar(words: Int, onInsert: () -> Unit) {
    Surface(color = Color.White) {
        androidx.compose.foundation.layout.Column {
            HorizontalDivider(color = Line, thickness = 0.75.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(onClick = onInsert, shape = RoundedCornerShape(50), color = Ink) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Insert", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(if (words == 1) "1 word" else "$words words", fontSize = 13.sp, color = InkSecondary)
            }
        }
    }
}

internal val ToolbarBorder = BorderStroke(1.dp, Line)
