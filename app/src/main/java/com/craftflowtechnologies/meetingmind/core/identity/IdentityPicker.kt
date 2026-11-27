package com.craftflowtechnologies.meetingmind.core.identity

import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.OfferedNotebookSpaces
import com.craftflowtechnologies.meetingmind.core.navigation.SpaceStyle
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role

/**
 * Which spaces the app shows: one row per space with a switch, so on and off are unmistakable in
 * light and dark. A change applies at once everywhere (Home, the bar, the menu, Notes, Record).
 * At least one space stays on; its switch is disabled with a reason rather than silently ignored.
 */
@Composable
fun SpacesPicker(selected: Set<NotebookSpace>, onChange: (Set<NotebookSpace>) -> Unit) {
    val colors = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SpaceStyle.ORDER.filter { it in OfferedNotebookSpaces }.forEach { space ->
            val on = space in selected
            val last = on && selected.size == 1
            val tint = SpaceStyle.tint(space)
            val toggle = {
                val next = if (on) selected - space else selected + space
                if (next.isNotEmpty()) onChange(next)
            }
            Surface(
                shape = MM.radius.card,
                color = if (on) colors.surfaceRaised else colors.surfaceSunk,
                border = BorderStroke(if (on) 1.5.dp else 1.dp, if (on) tint else colors.line),
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = on, enabled = !last, role = Role.Switch, onValueChange = { toggle() })
                    .testTag("space_${space.name.lowercase()}")
            ) {
                Row(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.m), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (on) tint.copy(alpha = 0.16f) else colors.track),
                        contentAlignment = Alignment.Center
                    ) { Icon(SpaceStyle.icon(space), contentDescription = null, tint = if (on) tint else colors.inkMuted, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = MM.space.m)) {
                        Text(SpaceStyle.title(space), style = MM.type.heading, color = colors.ink)
                        Text(SpaceStyle.line(space), style = MM.type.secondary, color = colors.inkSecondary)
                        Text(
                            if (last) "Keep at least one space on" else if (on) SpaceStyle.adds(space).replaceFirst("Adds", "Shows") else SpaceStyle.adds(space),
                            style = MM.type.caption, color = if (on) tint else colors.inkMuted,
                            modifier = Modifier.padding(top = MM.space.xs)
                        )
                    }
                    // The row is the toggle; the switch only shows its state (no second click target).
                    Switch(
                        checked = on, onCheckedChange = null, enabled = !last,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onAccent, checkedTrackColor = tint, checkedBorderColor = tint,
                            uncheckedThumbColor = colors.inkMuted, uncheckedTrackColor = colors.track, uncheckedBorderColor = colors.line,
                            disabledCheckedThumbColor = colors.onAccent, disabledCheckedTrackColor = tint.copy(alpha = 0.6f), disabledCheckedBorderColor = tint.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
        Text(
            "Turning a space off hides it everywhere: Home, the bar, the menu, Record and Notes. Your notes are kept, and turning it back on brings everything back.",
            style = MM.type.caption, color = colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs)
        )
    }
}

/** How the app feels. */
@Composable
fun LookPicker(selected: LookAndFeel, onChange: (LookAndFeel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LookAndFeel.entries.forEach { look ->
            val palette = AppLook.of(look)
            val on = look == selected
            Surface(
                onClick = { onChange(look) }, shape = RoundedCornerShape(18.dp), color = SurfaceBase,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) palette.accent else Line),
                modifier = Modifier.fillMaxWidth().testTag("look_${look.name.lowercase()}")
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 54.dp, height = 40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(palette.heroTop, palette.heroBottom)))) {
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(12.dp).clip(CircleShape).background(palette.warm))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(look.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, fontFamily = palette.headingFont)
                        Text(look.description, fontSize = 12.sp, color = InkMuted)
                    }
                    if (on) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = palette.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
