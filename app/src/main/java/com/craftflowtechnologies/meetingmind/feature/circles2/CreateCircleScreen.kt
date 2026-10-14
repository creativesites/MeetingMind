package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CircleTemplate
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@Composable
fun CreateCircleScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val vm: CreateCircleViewModel = viewModel(factory = CreateCircleViewModel.Factory(repo))
    val state by vm.state.collectAsState()
    val askNotifications = rememberNotificationAsk()
    CreateCircleContent(
        state, onBack, vm::pickTemplate, vm::setName, vm::setVocab, vm::setDisplayName, vm::toggleType, vm::setApproval, vm::setWhoCanInvite,
        onCreate = { vm.create { id -> if (repo.isFirstCircle()) askNotifications { onCreated(id) } else onCreated(id) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateCircleContent(
    state: CreateUiState,
    onBack: () -> Unit,
    onTemplate: (CircleTemplate) -> Unit,
    onName: (String) -> Unit,
    onVocab: (String) -> Unit,
    onDisplayName: (String) -> Unit,
    onToggleType: (PostType) -> Unit,
    onApproval: (Boolean) -> Unit,
    onWhoCanInvite: (WhoCanInvite) -> Unit,
    onCreate: () -> Unit
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding().imePadding()) {
        ScreenHeader(
            "Start a circle", Modifier.padding(horizontal = MM.space.s),
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.inkSecondary) } }
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            SectionHeader("What kind of group?")
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                CircleTemplate.all.forEach { t ->
                    val selected = t.id == state.template.id
                    MMCard(onClick = { onTemplate(t) }, modifier = Modifier.semantics { }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.label, style = MM.type.bodyStrong, color = MM.colors.ink)
                                Text(t.blurb, style = MM.type.secondary, color = MM.colors.inkSecondary)
                            }
                            MMChip(if (selected) "Chosen" else "Choose", selected, { onTemplate(t) })
                        }
                    }
                }
            }
            SectionHeader("Name it")
            CircleField(state.name, onName, "Name", placeholder = "Tuesday night ${state.vocab.lowercase()}")
            FieldLabel("What does your group call itself?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                state.template.vocab.forEach { v -> MMChip(v, selected = state.vocab == v, onClick = { onVocab(v) }) }
            }
            CircleField(state.vocab, onVocab, "Or type your own word")
            CircleField(state.displayName, onDisplayName, "Your name in this circle", placeholder = "How should people see you?")

            TextAction(if (advanced) "Hide options" else "More options", { advanced = !advanced })
            if (advanced) {
                SectionHeader("What can people post?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PostType.entries.forEach { t -> MMChip(t.label, selected = t in state.types, onClick = { onToggleType(t) }) }
                }
                ToggleRow("Admins approve prayer requests first", "Keeps the space gentle. Turn off to share requests right away.", state.prayerApproval, onApproval)
                FieldLabel("Who can invite people?")
                SegmentedControl(WhoCanInvite.entries.map { it.label }, WhoCanInvite.entries.indexOf(state.whoCanInvite), { onWhoCanInvite(WhoCanInvite.entries[it]) })
            }
            Text(
                "Only members of this circle can see what's shared here. Anonymous prayer requests hide the name from everyone, admins included.",
                style = MM.type.caption, color = MM.colors.inkSecondary
            )
            state.error?.let { StatusLine(StatusKind.Error, it) }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(MM.space.l), horizontalArrangement = Arrangement.Center) {
            PrimaryButton(if (state.busy) "Creating..." else "Create circle", onCreate, enabled = state.canCreate, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.body, color = MM.colors.ink)
            if (subtitle != null) Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
        Switch(
            checked, null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MM.colors.onAccent, checkedTrackColor = MM.colors.accent,
                uncheckedThumbColor = MM.colors.surface, uncheckedTrackColor = MM.colors.track, uncheckedBorderColor = MM.colors.line
            )
        )
    }
}
