package com.craftflowtechnologies.meetingmind.core.ui.mm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Shared body for the four surface kinds (DESIGN_SYSTEM §7). */
@Composable
private fun MMSurface(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    color: Color,
    shape: Shape,
    border: BorderStroke?,
    padding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val inner: @Composable () -> Unit = { Column(Modifier.padding(padding), content = content) }
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
            shape = shape, color = color, border = border, content = inner
        )
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border, content = inner)
    }
}

/** Card: grouped content. `surface` fill, `card` radius, a line border in light and none in dark. */
@Composable
fun MMCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = MM.space.l,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MM.colors
    MMSurface(
        modifier, onClick, colors.surface, MM.radius.card,
        if (colors.isDark) null else BorderStroke(MMSize.hairline, colors.line),
        contentPadding, content
    )
}

/** Hero: the one primary element of a screen. `accentWash` fill, `card` radius, no border. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = MM.space.l,
    content: @Composable ColumnScope.() -> Unit
) = MMSurface(modifier, onClick, MM.colors.accentWash, MM.radius.card, null, contentPadding, content)

/** Inset: fields and nested panels inside a card. `surfaceSunk`, `small` radius. */
@Composable
fun InsetPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = MM.space.m,
    content: @Composable ColumnScope.() -> Unit
) = MMSurface(modifier, onClick, MM.colors.surfaceSunk, MM.radius.small, null, contentPadding, content)
