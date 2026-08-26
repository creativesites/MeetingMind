package com.example.feature.meetingdetail.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.Line
import com.example.ui.theme.Speaker3

/** One word-diff run: a span of text and whether the diff engine marked it changed. */
data class DiffRun(val text: String, val changed: Boolean)

/**
 * Renders a word-diff'd proposal (docs/recording-page-implementation.md §2.7): unchanged text at
 * [baseStyle], changed runs additionally carrying a 2dp [Speaker3] underline independent of text
 * colour (Compose's built-in [androidx.compose.ui.text.style.TextDecoration] always colours the
 * line with the text colour, which can't express this, hence the manual [drawBehind] line).
 */
@Composable
fun DiffProposedText(
    runs: List<DiffRun>,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        runs.forEach { run ->
            BasicText(
                text = "${run.text} ",
                style = baseStyle.copy(color = Ink),
                modifier = if (run.changed) {
                    Modifier.drawBehind { drawUnderline(Speaker3) }
                } else Modifier
            )
        }
    }
}

/**
 * Renders the "before" text when the user taps "Show original" (§2.7): InkMuted text with a
 * [Line]-coloured strikethrough (again independent of text colour, so drawn manually).
 */
@Composable
fun DiffOriginalText(
    text: String,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = text,
        style = baseStyle.copy(color = InkMuted),
        modifier = modifier.drawBehind { drawStrikethrough(Line) }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUnderline(color: Color) {
    val strokeWidth = 2.dp.toPx()
    val y = size.height - strokeWidth
    drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = strokeWidth)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrikethrough(color: Color) {
    val strokeWidth = 1.5.dp.toPx()
    val y = size.height / 2f
    drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = strokeWidth)
}
