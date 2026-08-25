package com.example.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.RecordingType
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryLight

/**
 * Shared "what are you recording? / who's speaking?" building blocks, used by both the recording
 * flow ([com.example.feature.recording.RecordingScreen]) and the import flow
 * ([com.example.feature.importing.ImportScreen]) — recording type and expected speaker count are
 * first-class inputs to the whole processing pipeline (see [com.example.core.model.RecordingContext]),
 * and both entry points into MeetingMind must capture them the same way rather than import
 * silently defaulting to a generic recording.
 */
@Composable
fun RecordingTypeGrid(
    selected: RecordingType,
    onSelect: (RecordingType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val types = RecordingType.entries.filter { it != RecordingType.GENERAL }
        types.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { type ->
                    RecordingTypeChip(
                        type = type,
                        isSelected = selected == type,
                        onClick = { onSelect(type) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Null = unspecified ("Not sure"). 1 = "Just me". 2..5 individually, 6 stands in for "6+". */
@Composable
fun SpeakerCountRow(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        SpeakerCountOptionChip(label = "Not sure", isSelected = selected == null) { onSelect(null) }
        SpeakerCountOptionChip(label = "Just me", isSelected = selected == 1) { onSelect(1) }
        for (count in 2..5) {
            SpeakerCountOptionChip(label = "$count people", isSelected = selected == count) { onSelect(count) }
        }
        SpeakerCountOptionChip(label = "6+", isSelected = selected != null && selected >= 6) { onSelect(6) }
    }
}

@Composable
private fun RecordingTypeChip(
    type: RecordingType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) IndigoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) IndigoPrimaryLight else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = type.shortDescription,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun SpeakerCountOptionChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) IndigoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) IndigoPrimaryLight else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
