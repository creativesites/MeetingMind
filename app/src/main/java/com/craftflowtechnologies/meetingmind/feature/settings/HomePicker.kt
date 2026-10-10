package com.craftflowtechnologies.meetingmind.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** The order the picker lists the homes in: the default first, then the one the app had, then the rest. */
val HomePickerOrder: List<HomeStyle> =
    listOf(HomeStyle.EVERYDAY, HomeStyle.TODAY, HomeStyle.PROFESSIONAL, HomeStyle.CALM, HomeStyle.FOCUS)

/** "Everyday (default)", "Today", "Work"… */
fun homePickerLabel(style: HomeStyle): String =
    if (style == HomeStyle.DEFAULT_FOR_NEW_INSTALLS) style.label + " (default)" else style.label

/**
 * Settings → Look and home: pick which home opens. One row per home with a small preview, the current
 * one marked. Each row is a single 48 dp-plus target.
 */
@Composable
fun HomePicker(selected: HomeStyle, onSelect: (HomeStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag("home_picker"), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        HomePickerOrder.forEach { style ->
            val on = style == selected
            val row: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
                Row(
                    Modifier.selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(style) }).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeThumbnail(style)
                    Spacer(Modifier.width(MM.space.m))
                    Column(Modifier.weight(1f)) {
                        Text(homePickerLabel(style), style = MM.type.bodyStrong, color = MM.colors.ink)
                        Text(style.description, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 2)
                    }
                    if (on) {
                        Spacer(Modifier.width(MM.space.s))
                        Icon(Icons.Rounded.Check, "Selected", tint = MM.colors.accent, modifier = Modifier.size(MMSize.icon))
                    }
                }
            }
            val tag = Modifier.testTag("home_${style.name.lowercase()}")
            if (on) HeroCard(tag, contentPadding = MM.space.m, content = row) else MMCard(tag, contentPadding = MM.space.m, content = row)
        }
    }
}

private val ThumbWidth = 56.dp
private val ThumbHeight = 72.dp
private val Bar = 4.dp

/** A miniature of each home's shape, drawn with theme colours only. */
@Composable
private fun HomeThumbnail(style: HomeStyle) {
    val c = MM.colors
    Column(
        Modifier.size(ThumbWidth, ThumbHeight).clip(MM.radius.small).background(c.background).padding(MM.space.xs),
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        when (style) {
            HomeStyle.EVERYDAY -> {
                Block(c.accentWash, 18.dp)
                Line(); Line(); Line()
            }
            HomeStyle.TODAY -> {
                Block(c.accent, 24.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) { repeat(3) { Dot() } }
                Line(); Line()
            }
            HomeStyle.PROFESSIONAL -> {
                Block(c.ink, 20.dp)
                Line(); Line(); Line()
            }
            HomeStyle.CALM -> {
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) { repeat(3) { Dot() } }
                Block(c.surface, 14.dp)
                Line(); Line()
            }
            HomeStyle.FOCUS -> {
                Block(c.surface, 16.dp)
                Box(Modifier.align(Alignment.CenterHorizontally).size(20.dp).background(c.accent, MM.radius.pill))
            }
        }
    }
}

@Composable
private fun Block(color: androidx.compose.ui.graphics.Color, height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height).background(color, MM.radius.small))
}

@Composable
private fun Line() {
    Box(Modifier.fillMaxWidth().height(Bar).background(MM.colors.line, MM.radius.pill))
}

@Composable
private fun Dot() {
    Box(Modifier.size(10.dp).background(MM.colors.accentWash, MM.radius.pill))
}
