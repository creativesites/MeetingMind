package com.craftflowtechnologies.meetingmind.feature.learning.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.ui.theme.LearningTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.util.Locale

/** 24:12, or 1:02:05 past the hour. Timestamps are the only place Learning uses a mono face. */
fun formatLectureTimestamp(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** The lecture moment a piece of evidence points to, if it has one. */
val LearningEvidence.timestamp: String? get() = startMs?.let(::formatLectureTimestamp)

/** True when the evidence can be shown: it has a quote or a moment to open. */
val LearningEvidence.isShowable: Boolean get() = quote.isNotBlank() || startMs != null || blockId != null

/**
 * A tappable source marker: "▶ 24:12" for a lecture moment, "Notes" for a typed source. The visual
 * chip is small; the touch target is at least 48dp.
 */
@Composable
fun CitationChip(
    evidence: LearningEvidence,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sourceName: String = "the lecture"
) {
    val stamp = evidence.timestamp
    val spoken = if (stamp != null) "Source: $sourceName at $stamp. Opens the lecture moment"
    else "Source: $sourceName. Opens the passage"
    Box(
        modifier
            .heightIn(min = MMSize.minTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .clip(MM.radius.pill)
                .background(MM.colors.accentWash)
                .padding(horizontal = MM.space.s, vertical = MM.space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            Icon(
                if (stamp != null) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.Notes,
                contentDescription = null,
                tint = MM.colors.accent,
                modifier = Modifier.size(14.dp)
            )
            Text(stamp ?: "Notes", style = LearningTheme.type.mono, color = MM.colors.accent)
        }
    }
}

/**
 * A quote from the source with an accent rule, its citation chip and, where the transcript is
 * available, "Show in transcript".
 */
@Composable
fun EvidenceQuote(
    evidence: LearningEvidence,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    attribution: String? = null,
    onShowInTranscript: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(MM.radius.pill)
                .background(MM.colors.accent)
        )
        Spacer(Modifier.width(MM.space.m))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            if (evidence.quote.isNotBlank()) {
                Text(
                    "“${evidence.quote.trim()}”",
                    style = LearningTheme.type.reading.copy(
                        fontStyle = FontStyle.Italic,
                        fontSize = MM.type.body.fontSize,
                        lineHeight = MM.type.body.lineHeight
                    ),
                    color = MM.colors.ink
                )
            }
            if (!attribution.isNullOrBlank()) {
                Text(attribution, style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                CitationChip(evidence, onClick = onOpen)
                if (onShowInTranscript != null && evidence.segmentIds.isNotEmpty()) {
                    Box(
                        Modifier
                            .heightIn(min = MMSize.minTouch)
                            .clickable(role = Role.Button, onClick = onShowInTranscript),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Show in transcript", style = MM.type.caption, color = MM.colors.accent)
                    }
                }
            }
        }
    }
}

/**
 * Opens from any citation. Shows the quote and lets the student play the moment or jump to it in
 * the transcript, so a claim can always be checked against what was said.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationSheet(
    evidence: LearningEvidence,
    sourceTitle: String,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)?,
    onShowInTranscript: (() -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MM.colors.surfaceRaised,
        shape = MM.radius.sheet
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LearningTheme.readingGutter)
                .padding(bottom = MM.space.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text("FROM THE LECTURE", style = MM.type.overline, color = MM.colors.inkMuted)
            Text(
                listOfNotNull(sourceTitle, evidence.timestamp).joinToString(" · "),
                style = MM.type.heading,
                color = MM.colors.ink
            )
            if (evidence.quote.isNotBlank()) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(MM.radius.pill).background(MM.colors.accent))
                    Spacer(Modifier.width(MM.space.m))
                    Text(
                        "“${evidence.quote.trim()}”",
                        style = LearningTheme.type.reading.copy(fontStyle = FontStyle.Italic),
                        color = MM.colors.ink
                    )
                }
            } else {
                Text(
                    "This point comes from your notes for this session.",
                    style = MM.type.body,
                    color = MM.colors.inkSecondary
                )
            }
            val stamp = evidence.timestamp
            if (onPlay != null && stamp != null) {
                PrimaryButton(
                    text = "Play from $stamp",
                    onClick = onPlay,
                    leadingIcon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (onShowInTranscript != null) {
                SecondaryButton(
                    text = if (evidence.segmentIds.isNotEmpty()) "Show in transcript" else "Open notes",
                    onClick = onShowInTranscript,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * The session's single "what to do next" action. The whole strip is one button. It changes with
 * evidence: diagnostic first, then the weakest concept, then a new concept.
 */
@Composable
fun NextStepStrip(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeroCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = MM.space.m
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NEXT STEP", style = MM.type.overline, color = MM.colors.accent)
                Text(title, style = MM.type.bodyStrong, color = MM.colors.ink)
                Text(detail, style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            Spacer(Modifier.width(MM.space.s))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MM.colors.accent,
                modifier = Modifier.size(MMSize.iconSmall)
            )
        }
    }
}
