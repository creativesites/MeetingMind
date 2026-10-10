package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsRules
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.Presence
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Opens Settings → Companion from the quick sheet's footer. Provided by the app's navigation; null hides the footer. */
val LocalCompanionOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The size of the form faces in the sheet's radio row (§7.3). */
private val FormFace = 36.dp

/** What the quick sheet can change. */
interface CompanionQuickActions {
    fun setForm(form: CompanionForm)
    fun setPresence(presence: Presence)
    /** Live: called on every edit, with the raw text. */
    fun rename(raw: String)
    fun hideUntilTomorrow()
    fun show()
}

/**
 * The long-press sheet (§7.3, Z-12): change form, how often they show up, rename (saved as you
 * type), and hide until tomorrow's 07:00. Reads and writes the same settings as Settings → Companion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionQuickSheet(onDismiss: () -> Unit) {
    val source = rememberCompanionSettingsSource()
    val settings by rememberCompanionSettings()
    val scope = rememberCoroutineScope()
    val openSettings = LocalCompanionOpenSettings.current
    val actions = remember(source) {
        object : CompanionQuickActions {
            override fun setForm(form: CompanionForm) { scope.launch { source.setForm(form) } }
            override fun setPresence(presence: Presence) { scope.launch { source.setPresence(presence) } }
            override fun rename(raw: String) { scope.launch { source.setName(raw) } }
            override fun hideUntilTomorrow() {
                scope.launch { source.setHiddenUntil(CompanionSettingsRules.nextMorning(System.currentTimeMillis())); onDismiss() }
            }
            override fun show() { scope.launch { source.setHiddenUntil(null) } }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        CompanionQuickSheetContent(
            settings = settings, actions = actions, onDone = onDismiss,
            onOpenSettings = openSettings?.let { open -> { onDismiss(); open() } }
        )
    }
}

/** The sheet's content, separate from the sheet container so it can be shown in screenshots. */
@Composable
fun CompanionQuickSheetContent(
    settings: CompanionSettings,
    actions: CompanionQuickActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    roster: List<CompanionForm> = CompanionRoster.enabled
) {
    val form = settings.form ?: CompanionForm.ZURI
    val defaultName = stringResource(CompanionRoster.displayName(form))
    var text by remember(form) { mutableStateOf(settings.name ?: "") }
    var nodForm by remember { mutableStateOf<CompanionForm?>(null) }
    LaunchedEffect(nodForm) { if (nodForm != null) { delay(700); nodForm = null } }
    val hidden = settings.hiddenUntilMs?.let { it > System.currentTimeMillis() } == true

    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.l),
        verticalArrangement = Arrangement.spacedBy(MM.space.l)
    ) {
        // 1. Form row: the roster's forms as radio buttons.
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Text(stringResource(R.string.companion_quick_form_label), style = MM.type.heading, color = MM.colors.ink)
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                roster.forEach { f ->
                    val selected = f == settings.form
                    val label = if (selected) settings.name ?: stringResource(CompanionRoster.displayName(f)) else stringResource(CompanionRoster.displayName(f))
                    Surface(
                        shape = MM.radius.card, color = if (selected) MM.colors.accentWash else MM.colors.surfaceSunk,
                        border = if (selected) BorderStroke(MMSize.hairline, MM.colors.accent) else null,
                        modifier = Modifier.weight(1f).heightIn(min = MMSize.minTouch)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) { actions.setForm(f); nodForm = f } })
                    ) {
                        Column(Modifier.padding(vertical = MM.space.s), horizontalAlignment = Alignment.CenterHorizontally) {
                            Companion(
                                form = f, state = CompanionState.IDLE, size = FormFace,
                                variant = if (nodForm == f) CompanionVariant.Nod else CompanionVariant.Normal
                            )
                            Text(label, style = MM.type.caption, color = if (selected) MM.colors.accent else MM.colors.inkSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 2. Presence.
        SegmentedControl(
            listOf(stringResource(R.string.companion_presence_around), stringResource(R.string.companion_presence_moments), stringResource(R.string.companion_presence_off)),
            settings.presence.ordinal, { actions.setPresence(Presence.entries[it]) }
        )

        // 3. Name, saved as you type.
        Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            Text(stringResource(R.string.companion_name_label), style = MM.type.caption, color = MM.colors.inkSecondary)
            NameField(text, defaultName) { raw ->
                text = raw.take(CompanionSettingsRules.MaxNameLength)
                actions.rename(text)
            }
        }

        // 4. Hide for now.
        if (hidden) {
            Text(stringResource(R.string.companion_quick_sheet_hidden, settings.name ?: defaultName), style = MM.type.secondary, color = MM.colors.inkSecondary)
            SecondaryButton(stringResource(R.string.companion_quick_show, settings.name ?: defaultName), actions::show, Modifier.fillMaxWidth())
        } else {
            SecondaryButton(stringResource(R.string.companion_quick_hide), actions::hideUntilTomorrow, Modifier.fillMaxWidth())
        }

        // Footer.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (onOpenSettings != null) TextAction(stringResource(R.string.companion_quick_more), onOpenSettings) else Box(Modifier.size(0.dp))
            TextAction(stringResource(R.string.companion_quick_close), onDone)
        }
    }
}

/** A one-line text field on an inset panel. [placeholder] shows the original name while empty. */
@Composable
internal fun NameField(value: String, placeholder: String, enabled: Boolean = true, onValueChange: (String) -> Unit) {
    InsetPanel(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)) {
        Box(Modifier.heightIn(min = MMSize.minTouch - MM.space.m * 2), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MM.type.body, color = MM.colors.inkMuted)
            BasicTextField(
                value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
                textStyle = MM.type.body.copy(color = MM.colors.ink),
                cursorBrush = SolidColor(MM.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder }
            )
        }
    }
}
