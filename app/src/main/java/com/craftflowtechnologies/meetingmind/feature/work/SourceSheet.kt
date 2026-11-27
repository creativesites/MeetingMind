package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustKind
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustMark
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase

/**
 * Where a statement came from. Give an [itemId] when there is one (its evidence is looked up), or a
 * [meetingId] and [startMs] for a moment that is already known (for example a cited Ask source).
 */
data class SourceRef(
    val itemId: String? = null,
    val meetingId: String? = null,
    val startMs: Long? = null,
    val title: String = "",
    val quote: String? = null
)

/**
 * The source of one line: who said it, when, the quote kept as evidence, and a way to play the
 * moment. "Open in transcript" hands the meeting and the moment back to the caller, which opens the
 * recording there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(
    source: SourceRef,
    onOpenInTranscript: (meetingId: String, startMs: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val pageVm: ContextPageViewModel = composeViewModel()
    val detail by produceState<SourceDetail?>(null, source.itemId) {
        value = source.itemId?.let { pageVm.source(it) }
    }
    val d = detail
    val meetingId = d?.meetingId ?: source.meetingId
    val startMs = d?.startMs ?: source.startMs
    val quote = d?.quote ?: source.quote
    val title = d?.title ?: source.title.ifBlank { "Source" }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.m).testTag("source_sheet")) {
            Text("SOURCE", style = MM.type.overline, color = MM.colors.inkMuted)
            Text(title, style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.padding(top = MM.space.xs))

            val meta = listOfNotNull(
                d?.speaker,
                startMs?.let { formatTime(it) },
                d?.at?.let { dayLabel(it) }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
            }

            Column(Modifier.fillMaxWidth().padding(top = MM.space.m)) {
                if (quote != null) {
                    Text("“$quote”", style = MM.type.body.copy(fontStyle = FontStyle.Italic), color = MM.colors.ink)
                } else {
                    Text(
                        "No quote was kept for this one. Open the transcript to hear it in context.",
                        style = MM.type.secondary,
                        color = MM.colors.inkMuted
                    )
                }
            }

            if (d != null) {
                Row(Modifier.padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                    TrustMark(if (d.reviewed) TrustKind.CONFIRMED else TrustKind.SUGGESTED)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = MM.space.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                if (startMs != null) {
                    PlayChip(startMs) { meetingId?.let { onOpenInTranscript(it, startMs) } }
                    Spacer(Modifier.weight(1f))
                }
                if (meetingId != null) {
                    TextButton(onClick = { onOpenInTranscript(meetingId, startMs) }) {
                        Text("Open in transcript", style = MM.type.bodyStrong, color = MM.colors.accent)
                    }
                } else {
                    Text("No recording for this one.", style = MM.type.secondary, color = MM.colors.inkMuted)
                }
            }
        }
    }
}
