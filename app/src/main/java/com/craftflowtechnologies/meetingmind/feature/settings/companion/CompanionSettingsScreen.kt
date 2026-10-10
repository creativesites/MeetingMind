package com.craftflowtechnologies.meetingmind.feature.settings.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsRules
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.Presence
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.NameField
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionRiveBudget
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.LocalCompanionRiveBudget
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

/** The route of the Companion page, opened from the Settings row. */
const val CompanionSettingsRoute = "settings/companion"

/** The poses a founder can step through on a preview, in this order (tap to cycle). */
val CompanionPreviewPoses: List<CompanionState> = listOf(
    CompanionState.IDLE, CompanionState.LISTENING, CompanionState.THINKING,
    CompanionState.CELEBRATING, CompanionState.SLEEPY, CompanionState.WORRIED
)

/** What the page can change; one method per setting in §7.1. */
interface CompanionSettingsActions {
    fun setForm(form: CompanionForm?)
    fun rename(raw: String)
    fun setPresence(presence: Presence)
    fun setOnRecordButton(on: Boolean)
    fun setQuietSermons(on: Boolean)
    fun setCreateSuggest(on: Boolean)
}

/** Settings → Companion, wired to the real settings store. */
@Composable
fun CompanionSettingsScreen(onNavigateBack: () -> Unit) {
    val source = rememberCompanionSettingsSource()
    val settings by rememberCompanionSettings()
    val scope = rememberCoroutineScope()
    val actions = remember(source) {
        object : CompanionSettingsActions {
            override fun setForm(form: CompanionForm?) { scope.launch { source.setForm(form) } }
            override fun rename(raw: String) { scope.launch { source.setName(raw) } }
            override fun setPresence(presence: Presence) { scope.launch { source.setPresence(presence) } }
            override fun setOnRecordButton(on: Boolean) { scope.launch { source.setOnRecordButton(on) } }
            override fun setQuietSermons(on: Boolean) { scope.launch { source.setQuietSermons(on) } }
            override fun setCreateSuggest(on: Boolean) { scope.launch { source.setCreateSuggest(on) } }
        }
    }
    CompanionSettingsContent(settings, actions, onNavigateBack)
}

/**
 * The page itself. Basic: form (each shown live, tap to cycle its poses), name, how often.
 * Advanced: record button, quiet during sermons, Create suggestions (§7.1).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionSettingsContent(
    settings: CompanionSettings,
    actions: CompanionSettingsActions,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    roster: List<CompanionForm> = CompanionRoster.enabled
) {
    val form = settings.form
    val activeName = form?.let { settings.name ?: stringResource(CompanionRoster.displayName(it)) }
    var nameText by rememberSaveable(form) { mutableStateOf(settings.name ?: "") }

    // This page is where the founder compares them, so every face may run live Rive at once.
    CompositionLocalProvider(LocalCompanionRiveBudget provides remember { CompanionRiveBudget(max = roster.size) }) {
        Column(modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding().testTag("companion_settings")) {
            ScreenHeader(
                stringResource(R.string.companion_settings_title),
                Modifier.padding(horizontal = MM.space.s),
                leading = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.companion_settings_back), tint = MM.colors.ink) } }
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(horizontal = MM.space.l).padding(bottom = MM.space.xxl),
                verticalArrangement = Arrangement.spacedBy(MM.space.xl)
            ) {
                // ---- Basic
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    SectionHeader(stringResource(R.string.companion_settings_basic))
                    Text(stringResource(R.string.companion_settings_tap_hint), style = MM.type.secondary, color = MM.colors.inkSecondary)
                    FlowRow(
                        Modifier.fillMaxWidth().selectableGroup(), maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.m)
                    ) {
                        roster.forEach { f ->
                            FormCard(
                                f, selected = f == form, nameOverride = if (f == form) settings.name else null,
                                onChoose = { actions.setForm(f) }, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    NoCompanionRow(selected = form == null, onChoose = { actions.setForm(null) })
                }

                // ---- Name
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Text(stringResource(R.string.companion_name_label), style = MM.type.heading, color = MM.colors.ink)
                    NameField(nameText, activeName ?: "", enabled = form != null) { raw ->
                        nameText = raw.take(CompanionSettingsRules.MaxNameLength)
                        actions.rename(nameText)
                    }
                    Text(stringResource(R.string.companion_name_supporting), style = MM.type.caption, color = MM.colors.inkMuted)
                }

                // ---- Presence
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text(stringResource(R.string.companion_presence_label), style = MM.type.heading, color = MM.colors.ink)
                    SegmentedControl(
                        listOf(
                            stringResource(R.string.companion_presence_around),
                            stringResource(R.string.companion_presence_moments_long),
                            stringResource(R.string.companion_presence_off)
                        ),
                        settings.presence.ordinal, { actions.setPresence(Presence.entries[it]) }
                    )
                    Text(
                        stringResource(
                            when (settings.presence) {
                                Presence.AROUND -> R.string.companion_presence_around_body
                                Presence.MOMENTS -> R.string.companion_presence_moments_body
                                Presence.OFF -> R.string.companion_presence_off_body
                            }
                        ),
                        style = MM.type.secondary, color = MM.colors.inkSecondary
                    )
                }

                // ---- Advanced
                Column {
                    SectionHeader(stringResource(R.string.companion_advanced))
                    SwitchRow(
                        stringResource(R.string.companion_advanced_record_button, activeName ?: stringResource(R.string.companion_settings_none_title)),
                        stringResource(if (form == null) R.string.companion_advanced_record_button_disabled else R.string.companion_advanced_record_button_body),
                        settings.onRecordButton && form != null, enabled = form != null, onChange = actions::setOnRecordButton, tag = "companion_record_button"
                    )
                    SwitchRow(
                        stringResource(R.string.companion_advanced_quiet_sermons), stringResource(R.string.companion_advanced_quiet_sermons_body),
                        settings.quietSermons, enabled = true, onChange = actions::setQuietSermons, tag = "companion_quiet_sermons"
                    )
                    SwitchRow(
                        stringResource(R.string.companion_advanced_create_suggest, activeName ?: stringResource(R.string.companion_settings_none_title)),
                        stringResource(R.string.companion_advanced_create_suggest_body),
                        settings.createSuggest && form != null, enabled = form != null, onChange = actions::setCreateSuggest, tag = "companion_create_suggest"
                    )
                }
            }
        }
    }
}

/** One roster form: a live preview that cycles poses on tap, and a separate Choose control. */
@Composable
private fun FormCard(form: CompanionForm, selected: Boolean, nameOverride: String?, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    var poseIndex by rememberSaveable(form) { mutableStateOf(0) }
    val pose = CompanionPreviewPoses[poseIndex % CompanionPreviewPoses.size]
    val formName = nameOverride ?: stringResource(CompanionRoster.displayName(form))
    val poseName = stringResource(poseLabel(pose))
    val cycle = stringResource(R.string.companion_settings_preview_description, formName, poseName)
    MMCard(
        modifier.testTag("companion_form_${form.name.lowercase()}"),
        contentPadding = MM.space.m
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            Box(
                Modifier.heightIn(min = PreviewSize).fillMaxWidth()
                    .clickable(onClickLabel = cycle, role = Role.Button) { poseIndex = (poseIndex + 1) % CompanionPreviewPoses.size }
                    .semantics { contentDescription = cycle },
                contentAlignment = Alignment.Center
            ) {
                Companion(form = form, state = pose, size = PreviewSize)
            }
            Text(formName, style = MM.type.heading, color = MM.colors.ink, maxLines = 1)
            Text(poseName, style = MM.type.caption, color = MM.colors.inkMuted)
            MMChip(
                stringResource(if (selected) R.string.companion_settings_chosen else R.string.companion_settings_choose),
                selected, onChoose, Modifier.semantics { role = Role.RadioButton }
            )
        }
    }
}

private val PreviewSize = 96.dp

private fun poseLabel(state: CompanionState): Int = when (state) {
    CompanionState.IDLE -> R.string.companion_pose_idle
    CompanionState.LISTENING -> R.string.companion_pose_listening
    CompanionState.THINKING -> R.string.companion_pose_thinking
    CompanionState.CELEBRATING -> R.string.companion_pose_celebrating
    CompanionState.SLEEPY -> R.string.companion_pose_sleepy
    CompanionState.WORRIED -> R.string.companion_pose_worried
    else -> R.string.companion_pose_other
}

@Composable
private fun NoCompanionRow(selected: Boolean, onChoose: () -> Unit) {
    Surface(
        shape = MM.radius.card, color = if (selected) MM.colors.accentWash else MM.colors.surfaceSunk,
        border = if (selected) BorderStroke(MMSize.hairline, MM.colors.accent) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).testTag("companion_none")
            .selectable(selected = selected, role = Role.RadioButton, onClick = onChoose)
    ) {
        Column(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.companion_settings_none_title), style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(stringResource(R.string.companion_settings_none_body), style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).padding(vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = MM.space.m)) {
            Text(title, style = MM.type.bodyStrong, color = if (enabled) c.ink else c.inkMuted)
            Text(body, style = MM.type.secondary, color = c.inkSecondary)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent, checkedThumbColor = c.onAccent,
                uncheckedTrackColor = c.track, uncheckedThumbColor = c.inkMuted, uncheckedBorderColor = c.line,
                disabledCheckedTrackColor = c.track, disabledUncheckedTrackColor = c.track
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
