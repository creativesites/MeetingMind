package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRhythm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Small enough for the T0 glyph (§4.2): no idle clock runs for a line of text. */
private val RhythmFaceDp = 24.dp

@PluralsRes
internal fun rhythmPlural(kind: CompanionRhythm.Kind): Int = when (kind) {
    CompanionRhythm.Kind.FAITH_DAYS -> R.plurals.companion_rhythm_faith_days
    CompanionRhythm.Kind.STUDY_DAYS -> R.plurals.companion_rhythm_study_days
    CompanionRhythm.Kind.NOTES_KEPT -> R.plurals.companion_rhythm_notes_kept
}

/**
 * A calm rhythm (ZURI_EXPERIENCE §5.7): one quiet line, such as "3 days with the Word this week", led by the
 * companion's small face (or its glyph when the companion is hidden or set to "No companion").
 *
 * Draws nothing for a null [line]: [CompanionRhythm.thisWeek] returns null for a quiet week, and a quiet week
 * gets no line at all. Place it under a home's greeting or above a list; it carries no action.
 */
@Composable
fun RhythmLine(line: CompanionRhythm.Line?, modifier: Modifier = Modifier) {
    if (line == null) return
    val c = MM.colors
    val settings by rememberCompanionSettings()
    val form = settings.form
    val showFace = form != null && rememberCompanionVisible(CompanionPage.HOME)
    Row(
        modifier.heightIn(min = MMSize.minTouch).testTag("rhythm_line_${line.kind.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showFace && form != null) {
            Companion(form = form, state = CompanionState.IDLE, size = RhythmFaceDp)
        } else {
            Icon(
                painterResource(R.drawable.ic_zuri_glyph), contentDescription = null,
                tint = c.accent, modifier = Modifier.size(MMSize.iconSmall)
            )
        }
        Spacer(Modifier.width(MM.space.s))
        Text(
            pluralStringResource(rhythmPlural(line.kind), line.count, line.count),
            style = MM.type.secondary, color = c.inkSecondary
        )
    }
}
