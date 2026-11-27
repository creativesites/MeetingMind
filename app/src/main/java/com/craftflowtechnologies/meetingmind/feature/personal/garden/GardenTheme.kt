package com.craftflowtechnologies.meetingmind.feature.personal.garden

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import com.craftflowtechnologies.meetingmind.feature.personal.GardenSerif
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.gardenMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPalette
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import java.time.LocalTime


/**
 * Wraps a garden page or sheet host in the garden's colours for the current time of day and the app's
 * theme. Do not nest it: the inner call would read the garden's own dark flag as the theme.
 */
@Composable
fun GardenTheme(
    phase: GardenPhase = GardenTime.phase(LocalTime.now()),
    content: @Composable () -> Unit
) {
    val palette = GardenColors.palette(phase, MM.colors.isDark)
    CompositionLocalProvider(LocalMMColors provides gardenMMColors(palette), content = content)
}

/** A garden page's top bar: a 48 dp back button, the page title in the garden serif, and optional actions. */
@Composable
fun GardenPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = MM.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = MM.space.s, vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(MMSize.minTouch)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to your garden", tint = c.ink)
        }
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MM.type.title.copy(fontFamily = GardenSerif),
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}
