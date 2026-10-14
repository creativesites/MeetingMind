package com.craftflowtechnologies.meetingmind.core.ui.mm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(MMSize.iconSmall))
        Spacer(Modifier.width(MM.space.s))
    }
    Text(text, style = MM.type.bodyStrong)
}

/** The screen's main action: accent fill, pill, 48 dp tall. One per screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val c = MM.colors
    Button(
        onClick = onClick, enabled = enabled, shape = MM.radius.pill,
        modifier = modifier.heightIn(min = MMSize.minTouch),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent, contentColor = c.onAccent,
            disabledContainerColor = c.track, disabledContentColor = c.inkMuted
        ),
        contentPadding = PaddingValues(horizontal = MM.space.xl, vertical = MM.space.s)
    ) { ButtonLabel(text, leadingIcon) }
}

/** A quiet alternative: line outline, ink text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val c = MM.colors
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = MM.radius.pill,
        modifier = modifier.heightIn(min = MMSize.minTouch),
        border = BorderStroke(MMSize.hairline, c.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink, disabledContentColor = c.inkMuted),
        contentPadding = PaddingValues(horizontal = MM.space.xl, vertical = MM.space.s)
    ) { ButtonLabel(text, leadingIcon) }
}

/** A text-only action in the accent colour ("Retry", "All"). */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = MM.colors
    TextButton(
        onClick = onClick, enabled = enabled, shape = MM.radius.pill,
        modifier = modifier.heightIn(min = MMSize.minTouch),
        colors = ButtonDefaults.textButtonColors(contentColor = c.accent, disabledContentColor = c.inkMuted),
        contentPadding = PaddingValues(horizontal = MM.space.m, vertical = MM.space.s)
    ) { Text(text, style = MM.type.bodyStrong) }
}
