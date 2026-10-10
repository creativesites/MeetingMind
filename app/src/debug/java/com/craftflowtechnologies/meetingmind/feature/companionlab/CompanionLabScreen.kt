package com.craftflowtechnologies.meetingmind.feature.companionlab

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionAssets
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionRiveContract
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.RendererChoice
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import kotlin.math.sin

private val Ladder = listOf(24, 40, 64, 96, 160)

@Composable
fun CompanionLabScreen() {
    var formIndex by rememberSaveable { mutableIntStateOf(0) }
    var stateIndex by rememberSaveable { mutableIntStateOf(0) }
    var modeIndex by rememberSaveable { mutableIntStateOf(-1) }
    var variantIndex by rememberSaveable { mutableIntStateOf(0) }
    var accentIndex by rememberSaveable { mutableIntStateOf(0) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    var wobble by rememberSaveable { mutableStateOf(true) }
    var level by remember { mutableFloatStateOf(0.55f) }
    var replay by remember { mutableIntStateOf(0) }

    val roster = CompanionRoster.enabled
    val form = roster[formIndex.coerceIn(0, roster.lastIndex)]
    val state = CompanionState.entries[stateIndex]
    val mode = CreateMode.entries.getOrNull(modeIndex)
    val variant = CompanionVariant.entries[variantIndex]

    // "Simulate speech" modulates the slider value like a voice; the companion reads it per frame.
    var live by remember { mutableFloatStateOf(level) }
    LaunchedEffect(wobble, level) {
        if (!wobble) { live = level; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - start) / 1e9f
            live = (level * (0.6f + 0.4f * sin(t * 7f) * sin(t * 2.3f))).coerceIn(0f, 1f)
        }
    }

    MeetMindTheme(darkTheme = dark, accent = MMAccent.entries[accentIndex]) {
        CompositionLocalProvider(LocalCompanionReducedMotion provides if (reduced) true else null) {
            Column(
                Modifier.fillMaxSize().background(MM.colors.background).safeDrawingPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.l)
            ) {
                ScreenHeader(stringResource(R.string.companion_lab_title))

                // The hero: the chosen form at 160 dp, keyed on replay so one-shots play again.
                val heroChoice = remember { mutableStateOf<RendererChoice?>(null) }
                MMCard {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        androidx.compose.runtime.key(replay) {
                            Companion(form, state, 160.dp, mode = mode, variant = variant, level = { live }, onRenderer = { heroChoice.value = it })
                        }
                    }
                    heroChoice.value?.let {
                        Text(stringResource(R.string.companion_lab_renderer, it.renderer.name, it.reason.name), style = MM.type.caption, color = MM.colors.inkSecondary)
                    }
                    Text(
                        stringResource(R.string.companion_lab_contract, CompanionRiveContract.artboard(form), CompanionRiveContract.StateMachine, CompanionRiveContract.ViewModel),
                        style = MM.type.caption, color = MM.colors.inkMuted
                    )
                    SecondaryButton(stringResource(R.string.companion_lab_replay), onClick = { replay++ })
                }

                SectionHeader(stringResource(R.string.companion_lab_form))
                ChipRow(roster.map { stringResource(CompanionRoster.displayName(it)) }, formIndex) { formIndex = it }

                SectionHeader(stringResource(R.string.companion_lab_state))
                ChipRow(CompanionState.entries.map { it.name.lowercase() }, if (mode == null) stateIndex else -1) { stateIndex = it; modeIndex = -1 }

                SectionHeader(stringResource(R.string.companion_lab_mode))
                ChipRow(listOf(stringResource(R.string.companion_lab_mode_none)) + CreateMode.entries.map { it.name.lowercase() }, modeIndex + 1) { modeIndex = it - 1 }

                SectionHeader(stringResource(R.string.companion_lab_variant))
                SegmentedControl(CompanionVariant.entries.map { it.name }, variantIndex, onSelect = { variantIndex = it; replay++ })

                SectionHeader(stringResource(R.string.companion_lab_level, (level * 100).toInt()))
                Slider(value = level, onValueChange = { level = it })
                ToggleRow(stringResource(R.string.companion_lab_level_wobble), wobble) { wobble = it }

                SectionHeader(stringResource(R.string.companion_lab_accent))
                ChipRow(MMAccent.entries.map { it.label }, accentIndex) { accentIndex = it }
                SegmentedControl(
                    listOf(stringResource(R.string.companion_lab_theme_light), stringResource(R.string.companion_lab_theme_dark)),
                    if (dark) 1 else 0, onSelect = { dark = it == 1 }
                )
                ToggleRow(stringResource(R.string.companion_lab_reduced), reduced) { reduced = it }

                SectionHeader(stringResource(R.string.companion_lab_ladder))
                val choices = remember { mutableStateMapOf<Int, RendererChoice>() }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.l), verticalAlignment = Alignment.Bottom
                ) {
                    Ladder.forEach { size ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.runtime.key(replay) {
                                Companion(form, state, size.dp, mode = mode, variant = variant, level = { live }, onRenderer = { choices[size] = it })
                            }
                            Text(
                                stringResource(R.string.companion_lab_size_renderer, size, choices[size]?.renderer?.name.orEmpty()),
                                style = MM.type.caption, color = MM.colors.inkMuted
                            )
                        }
                    }
                }

                SectionHeader(stringResource(R.string.companion_lab_assets))
                val files = CompanionAssets.files(LocalContext.current)
                Text(
                    if (files.isEmpty()) stringResource(R.string.companion_lab_assets_none) else files.sorted().joinToString(" · "),
                    style = MM.type.secondary, color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(bottom = MM.space.xl)
                )
            }
        }
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        labels.forEachIndexed { i, label -> MMChip(label, selected = i == selected, onClick = { onSelect(i) }) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
