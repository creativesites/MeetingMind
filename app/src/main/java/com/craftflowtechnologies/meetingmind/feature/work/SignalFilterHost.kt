package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.SegmentSignalEntity
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.work.ItemKind
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import kotlinx.coroutines.flow.flowOf

/** The transcript's filter chips, in the order they're offered. */
internal val TranscriptFilters = listOf(ItemKind.DECISION to "Decisions", ItemKind.COMMITMENT to "Commitments", ItemKind.QUESTION to "Questions", ItemKind.RISK to "Risks")

/** Chips with a count for each kind this recording has signals of; none at all when it has none. */
internal fun filterChips(signals: List<SegmentSignalEntity>): List<Triple<ItemKind, String, Int>> =
    TranscriptFilters.mapNotNull { (kind, label) ->
        signals.filter { it.kind == kind.name }.map { it.segmentId }.distinct().size.takeIf { it > 0 }?.let { Triple(kind, label, it) }
    }

/** The paragraphs a kind's signals came from, in transcript order. */
internal fun filterSegments(all: List<TranscriptSegment>, signals: List<SegmentSignalEntity>, kind: ItemKind?): List<TranscriptSegment> {
    if (kind == null) return all
    val ids = signals.filter { it.kind == kind.name }.map { it.segmentId }.toSet()
    return all.filter { it.id in ids }
}

/**
 * Filter chips above the transcript (Decisions · Commitments · Questions · Risks), driven by the
 * signals a recording was found to hold. A recording with none shows the transcript exactly as
 * before: no chips, no change.
 */
@Composable
fun SignalFilterHost(meetingId: String?, all: List<TranscriptSegment>, content: @Composable (List<TranscriptSegment>) -> Unit) {
    val context = LocalContext.current
    val signals by remember(meetingId) { meetingId?.let { MeetMindDatabase.getInstance(context).signalDao().observeForMeeting(it) } ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val chips = filterChips(signals)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    if (chips.isEmpty()) { content(all); return }
    val kind = ItemKind.entries.firstOrNull { it.name == selected }?.takeIf { k -> chips.any { it.first == k } }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("signal_filters")) {
            items(chips) { (k, label, count) -> Chip("$label $count", kind == k, Ink) { selected = if (kind == k) null else k.name } }
        }
        Box(Modifier.weight(1f)) { content(filterSegments(all, signals, kind)) }
    }
}
