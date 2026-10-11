package com.craftflowtechnologies.meetingmind.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** The look of every settings row, built from design-system tokens only. */

@Composable
internal fun SettingsNavRow(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = MM.space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MM.colors.inkMuted, modifier = Modifier.padding(start = MM.space.s))
    }
}

@Composable
internal fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = MM.space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = MM.space.m)) {
            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = MM.colors.accent, checkedThumbColor = MM.colors.onAccent),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
internal fun SettingsRadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = MM.space.m)) {
            Text(title, style = MM.type.body, color = MM.colors.ink)
            Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MM.colors.accent, unselectedColor = MM.colors.inkFaint),
            modifier = Modifier.testTag(testTag)
        )
    }
}

/** A setting whose value is one of a few options: a title, then one radio row each. */
@Composable
internal fun SettingsChoiceBlock(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = MM.space.m)) {
        Text(title, style = MM.type.bodyStrong, color = MM.colors.ink)
        content()
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(color = MM.colors.lineFaint, thickness = MMSize.hairline)
}

/** The quiet "Advanced" row that opens the rest of a group. */
@Composable
internal fun AdvancedToggle(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clickable(role = Role.Button, onClick = onToggle)
            .padding(top = MM.space.l).testTag("settings_advanced_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Advanced", style = MM.type.heading, color = MM.colors.inkSecondary, modifier = Modifier.semantics { heading() })
        Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, if (expanded) "Hide advanced settings" else "Show advanced settings", tint = MM.colors.inkMuted)
    }
}
