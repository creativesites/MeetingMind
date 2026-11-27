package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.WorkPalette

/**
 * The project colour for [id], in the light or dark variant. The same id always gets the same
 * colour: the choice is `String.hashCode()` modulo the eight palette entries, and `hashCode` is
 * specified by the JDK, so it does not change between runs or devices.
 */
fun projectColor(id: String, dark: Boolean): Color {
    val pairs = WorkPalette.Projects
    val (light, night) = pairs[Math.floorMod(id.hashCode(), pairs.size)]
    return if (dark) night else light
}

/** The project colour for [id] in the active theme (see the two-argument overload). */
@Composable
@ReadOnlyComposable
fun projectColor(id: String): Color = projectColor(id, MM.colors.isDark)

/**
 * The project colour bar used on cards, rows and boards: a narrow rounded bar that fills the
 * height of the row it sits in. Decorative, so it has no semantics of its own.
 */
@Composable
fun ProjectBar(color: Color, modifier: Modifier = Modifier, width: Dp = 3.dp) {
    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .background(color)
    )
}
