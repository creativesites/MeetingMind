package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.MilestoneKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** The companion's size on the milestone card: T1, so it gets its volume shading but never a live 3D clock. */
private val MilestoneCompanionDp = 72.dp

/** The line and the quieter line under it, for each milestone (copy in `strings_companion_milestones.xml`). */
internal data class MilestoneCopy(@StringRes val line: Int, @StringRes val sub: Int)

internal fun milestoneCopy(kind: MilestoneKind): MilestoneCopy = when (kind) {
    MilestoneKind.FIRST_NOTE -> MilestoneCopy(R.string.companion_milestone_first_note, R.string.companion_milestone_first_note_sub)
    MilestoneKind.TENTH_NOTE -> MilestoneCopy(R.string.companion_milestone_tenth_note, R.string.companion_milestone_tenth_note_sub)
    MilestoneKind.READING_PLAN_DONE -> MilestoneCopy(R.string.companion_milestone_reading_plan, R.string.companion_milestone_reading_plan_sub)
    MilestoneKind.SEVEN_DEVOTIONALS_IN_MONTH -> MilestoneCopy(R.string.companion_milestone_devotionals, R.string.companion_milestone_devotionals_sub)
    MilestoneKind.ALL_CARDS_CLEARED -> MilestoneCopy(R.string.companion_milestone_cards_cleared, R.string.companion_milestone_cards_cleared_sub)
    MilestoneKind.PERSONAL_JOURNEY_DONE -> MilestoneCopy(R.string.companion_milestone_personal_journey, R.string.companion_milestone_personal_journey_sub)
    MilestoneKind.PERSONAL_CHALLENGE_DONE -> MilestoneCopy(R.string.companion_milestone_personal_challenge, R.string.companion_milestone_personal_challenge_sub)
    MilestoneKind.FIRST_ROUTINE_WEEK -> MilestoneCopy(R.string.companion_milestone_first_routine_week, R.string.companion_milestone_first_routine_week_sub)
}

/**
 * A Proud milestone, celebrated privately (ZURI_EXPERIENCE §3.1 Proud, §5.9, §9): a lifted card with the companion
 * standing proud, one line, a quieter line under it, **Nice** to close, and, only when [onShare] is given, a quiet
 * "Share with your circle" suggestion that opens the share flow (it never posts by itself).
 *
 * Stateless: [MilestoneHost][com.craftflowtechnologies.meetingmind.feature.moments.MilestoneHost] decides whether it
 * shows (once ever per milestone) and animates it in. With "No companion" ([form] null) the card shows the
 * companion glyph instead, so the milestone is still marked without a character.
 */
@Composable
fun MilestoneCard(
    kind: MilestoneKind,
    form: CompanionForm?,
    companionName: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null
) {
    val c = MM.colors
    val copy = milestoneCopy(kind)
    val line = stringResource(copy.line)
    val announce = stringResource(R.string.companion_milestone_announce, line)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(MM.elevation.floating, MM.radius.card, clip = false)
            .clip(MM.radius.card)
            .background(c.surface)
            .border(MMSize.hairline, c.lineSoft, MM.radius.card)
            .padding(MM.space.l)
            .semantics { paneTitle = announce; liveRegion = LiveRegionMode.Polite }
            .testTag("milestone_card_${kind.name}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(MilestoneCompanionDp).clip(MM.radius.pill).background(c.accentWash),
                contentAlignment = Alignment.Center
            ) {
                if (form != null) {
                    Companion(
                        form = form, state = CompanionState.PROUD, size = MilestoneCompanionDp,
                        contentDescription = stringResource(R.string.companion_milestone_companion_cd, companionName)
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_zuri_glyph), contentDescription = null,
                        tint = c.accent, modifier = Modifier.size(MMSize.icon)
                    )
                }
            }
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.companion_milestone_label), style = MM.type.overline, color = c.accent)
                Text(line, style = MM.type.heading, color = c.ink, modifier = Modifier.padding(top = MM.space.xs))
                Text(
                    stringResource(copy.sub), style = MM.type.secondary, color = c.inkSecondary,
                    modifier = Modifier.padding(top = MM.space.xs)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = MM.space.m),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onShare != null) {
                TextAction(
                    stringResource(R.string.companion_milestone_share), onClick = onShare,
                    modifier = Modifier.testTag("milestone_share")
                )
            }
            PrimaryButton(
                stringResource(R.string.companion_milestone_done), onClick = onDone,
                modifier = Modifier.testTag("milestone_done")
            )
        }
    }
}
