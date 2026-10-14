package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** A text field in tokens: sunk fill, small radius, accent line when focused. */
@Composable
fun CircleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) 8 else 1,
    singleLine: Boolean = maxLines == 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
) {
    val c = MM.colors
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
        label = { Text(label, style = MM.type.caption) },
        placeholder = placeholder?.let { { Text(it, style = MM.type.body, color = c.inkMuted) } },
        textStyle = MM.type.body.copy(color = c.ink), shape = MM.radius.small,
        minLines = minLines, maxLines = maxLines, singleLine = singleLine, keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.surfaceSunk, unfocusedContainerColor = c.surfaceSunk, disabledContainerColor = c.surfaceSunk,
            focusedIndicatorColor = c.accent, unfocusedIndicatorColor = c.line,
            focusedLabelColor = c.accent, unfocusedLabelColor = c.inkSecondary, cursorColor = c.accent,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink
        )
    )
}

/** Sheet surface (DESIGN_SYSTEM section 7): raised fill, top card radius. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.xl),
            verticalArrangement = Arrangement.spacedBy(MM.space.m), content = content
        )
    }
}

/** Initials in a wash circle. Decorative: the name is always next to it. */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(
        modifier.size(MMSize.minTouch).background(MM.colors.accentWash, CircleShape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) { Text(initials, style = MM.type.bodyStrong, color = MM.colors.accent) }
}

/** A quiet label row used above groups of fields. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MM.type.caption, color = MM.colors.inkSecondary, modifier = modifier.padding(top = MM.space.xs))
}

@Composable
fun RowSpaced(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** "just now", "5m", "3h", "2d", then a date. Pure, so tests pass [now]. */
fun relativeTime(now: Long, then: Long): String {
    if (then <= 0L) return ""
    val s = (now - then) / 1000
    return when {
        s < 60 -> "just now"
        s < 3_600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3_600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(then))
    }
}
