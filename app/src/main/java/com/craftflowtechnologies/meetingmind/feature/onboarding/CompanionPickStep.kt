package com.craftflowtechnologies.meetingmind.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay

private val PreviewSize = 112.dp
private val PickSize = 44.dp

/** The one-line description of each choice (spec 1.2). */
private fun lineFor(form: CompanionForm?): Int = when (form) {
    CompanionForm.ZURI -> R.string.companion_line_zuri
    CompanionForm.NAS -> R.string.companion_line_nas
    CompanionForm.WREN -> R.string.companion_line_wren
    CompanionForm.PAGE -> R.string.companion_line_page
    null -> R.string.companion_line_none
}

/**
 * Onboarding step 1 (Z-15, spec 5.1): "Pick who keeps you company". A 112 dp live preview, the
 * roster at 44 dp plus "No companion", Zuri already chosen. One tap, or none: Continue keeps Zuri.
 *
 * Launch-time screen, so the first render is Canvas-only; Rive is allowed once the person has
 * tapped a companion (by then the app has been up for a moment and RiveSafety has had its say).
 */
@Composable
fun CompanionPickStep(
    selected: CompanionForm?,
    onSelect: (CompanionForm?) -> Unit,
    roster: List<CompanionForm> = CompanionRoster.enabled
) {
    var interacted by rememberSaveable { mutableStateOf(false) }
    var nodForm by remember { mutableStateOf<CompanionForm?>(null) }
    LaunchedEffect(nodForm) { if (nodForm != null) { delay(700); nodForm = null } }
    val forceCanvas = LocalCompanionForceCanvas.current || !interacted

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.companion_onboarding_step1_title), style = MM.type.title, color = com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.m).testTag("onboarding_companion_title")
        )
        Text(
            stringResource(R.string.companion_onboarding_step1_body), style = MM.type.body, color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.s)
        )

        // The live preview.
        Box(Modifier.padding(top = MM.space.xl).size(PreviewSize), contentAlignment = Alignment.Center) {
            if (selected != null) {
                CompositionLocalProvider(LocalCompanionForceCanvas provides forceCanvas) {
                    Companion(
                        form = selected, state = CompanionState.IDLE, size = PreviewSize,
                        variant = if (nodForm == selected) CompanionVariant.Nod else CompanionVariant.Normal,
                        contentDescription = stringResource(CompanionRoster.displayName(selected))
                    )
                }
            } else {
                Box(Modifier.size(PickSize + MM.space.l).border(MMSize.hairline, Color.White.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(MMSize.icon))
                }
            }
        }
        Text(
            stringResource(lineFor(selected)), style = MM.type.bodyStrong, color = com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.m).testTag("onboarding_companion_line")
        )

        // The picks.
        Row(
            Modifier.fillMaxWidth().padding(top = MM.space.xl).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            roster.forEach { form ->
                Pick(
                    selected = form == selected, tag = "onboarding_companion_${form.name.lowercase()}",
                    label = stringResource(CompanionRoster.displayName(form)), modifier = Modifier.weight(1f),
                    onClick = { interacted = true; nodForm = form; onSelect(form) }
                ) {
                    Companion(form = form, state = CompanionState.IDLE, size = PickSize)
                }
            }
            Pick(
                selected = selected == null, tag = "onboarding_companion_none",
                label = stringResource(R.string.companion_onboarding_no_companion), modifier = Modifier.weight(1.5f),
                onClick = { interacted = true; nodForm = null; onSelect(null) }
            ) {
                Box(Modifier.size(PickSize), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(MMSize.icon))
                }
            }
        }
        Text(
            stringResource(R.string.companion_onboarding_step1_footnote), style = MM.type.caption, color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.l, bottom = MM.space.l)
        )
    }
}

@Composable
private fun Pick(
    selected: Boolean, tag: String, label: String, modifier: Modifier, onClick: () -> Unit, face: @Composable () -> Unit
) {
    Surface(
        shape = MM.radius.card,
        color = if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
        border = if (selected) BorderStroke(MMSize.hairline, Brand.Cyan) else null,
        modifier = modifier.heightIn(min = MMSize.minTouch).testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(Modifier.padding(vertical = MM.space.s, horizontal = MM.space.xs), horizontalAlignment = Alignment.CenterHorizontally) {
            face()
            Text(
                label, style = MM.type.caption, color = if (selected) com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite else Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = MM.space.xs)
            )
        }
    }
}
