package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.work.WorkTypeNames
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * Recent Work recordings, in every state: the fix for "I recorded a work meeting and there is nothing there".
 * Before this, Work listed a recording only once it needed the person (to review, failed, a follow-up), so one being
 * recorded, being written up, saved for later, or finished with nothing to check showed nowhere. Each row says where
 * the recording is and opens the right place. Shared by the Work page's home and the Work Home (ProfessionalHome).
 */
object WorkRecent {
    /** Rows a home shows. */
    const val SHOWN = 4
    /** How far back a home looks. */
    const val DAYS = 14
    /** Rows read from the database, before the window and the cap. */
    const val READ = 12

    /** Work types, plus the general and custom recordings Work treats as its own (see WorkRepository.observeToReview). */
    val types: List<String> = WorkTypeNames + listOf(RecordingType.GENERAL.name, RecordingType.CUSTOM.name)

    enum class Kind { RECORDING, WRITING, SAVED, TO_REVIEW, READY, NEEDS_YOU }

    data class Row(val meetingId: String, val noteId: String?, val title: String, val kind: Kind, val line: String)

    fun kindOf(status: String, reviewed: Boolean, toReview: Boolean): Kind = when (status) {
        MeetingStatus.RECORDING.name -> Kind.RECORDING
        MeetingStatus.SAVED.name -> Kind.SAVED
        MeetingStatus.READY.name -> if (toReview && !reviewed) Kind.TO_REVIEW else Kind.READY
        MeetingStatus.ERROR.name, MeetingStatus.MODEL_REQUIRED.name -> Kind.NEEDS_YOU
        else -> Kind.WRITING // QUEUED, PROCESSING
    }

    /** What one recording's row says. [progress] is its processing job's percentage, when one is running. */
    fun rowOf(m: MeetingEntity, progress: Int?, toReview: Boolean, now: Long): Row {
        val kind = kindOf(m.status, m.reviewedAt != null, toReview)
        val line = when (kind) {
            Kind.RECORDING -> "Recording now"
            Kind.WRITING -> if (progress != null && progress in 1..99) "Writing your notes · $progress%" else "Writing your notes"
            Kind.SAVED -> "Notes not made yet · tap to make them"
            Kind.TO_REVIEW -> "Ready · check its tasks and decisions"
            Kind.READY -> listOfNotNull(dayLabel(m.createdAt, now), minutes(m.durationMs)).joinToString(" · ")
            Kind.NEEDS_YOU -> if (m.status == MeetingStatus.MODEL_REQUIRED.name) "Needs a model to make notes · the audio is safe" else "Didn't finish · tap to try again"
        }
        return Row(m.id, m.noteId, m.title.ifBlank { "Untitled recording" }, kind, line)
    }

    /** The rows a home shows: the newest [SHOWN] from the last [DAYS] days, whatever their state. */
    fun rows(meetings: List<MeetingEntity>, progress: Map<String, Int>, toReview: Set<String>, now: Long): List<Row> =
        meetings.asSequence()
            .filter { now - it.createdAt <= DAYS * DAY_MS }
            .sortedByDescending { it.createdAt }
            .distinctBy { it.id }
            .take(SHOWN)
            .map { rowOf(it, progress[it.id], it.id in toReview, now) }
            .toList()

    /**
     * Where a row opens. One to review opens the Wrap-up; every other row opens the recording route (onOpenProcessing,
     * the same one DeepTarget.Recording uses), which shows progress while it works, Make notes or Try again when it
     * waits, and the notes once it is ready. Both Work homes route the same way.
     */
    enum class Target { WRAP_UP, PROCESSING }

    fun targetOf(row: Row): Target = if (row.kind == Kind.TO_REVIEW) Target.WRAP_UP else Target.PROCESSING

    private fun minutes(ms: Long): String? = (ms / 60_000).takeIf { it > 0 }?.let { "$it min" }

    private const val DAY_MS = 86_400_000L
}

/** The Recent section: a header with All, then the rows. Draws nothing when there are no rows. */
@Composable
fun WorkRecentSection(rows: List<WorkRecent.Row>, onOpen: (WorkRecent.Row) -> Unit, onAll: () -> Unit, modifier: Modifier = Modifier) {
    // Nothing recent draws nothing: a new person's home shows no empty card.
    if (rows.isEmpty()) return
    Column(modifier.testTag("work_recent")) {
        SectionHeader("Recent", actionLabel = "All", onAction = onAll)
        WorkRecentCard(rows, onOpen)
    }
}

/** The rows themselves, stateless, for any home and for screenshots. */
@Composable
fun WorkRecentCard(rows: List<WorkRecent.Row>, onOpen: (WorkRecent.Row) -> Unit, modifier: Modifier = Modifier) {
    MMCard(modifier.fillMaxWidth(), contentPadding = MM.space.s) {
        rows.forEach { r ->
            ListRow(
                title = r.title, subtitle = r.line,
                modifier = Modifier.padding(horizontal = MM.space.s).testTag("work_recent_${r.meetingId}"),
                leading = { StatusMark(r.kind) },
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MM.colors.inkFaint) },
                onClick = { onOpen(r) }
            )
        }
    }
}

@Composable
private fun StatusMark(kind: WorkRecent.Kind) {
    val c = MM.colors
    val (icon: ImageVector, tint) = when (kind) {
        WorkRecent.Kind.RECORDING -> Icons.Rounded.FiberManualRecord to c.recording
        WorkRecent.Kind.WRITING -> Icons.Rounded.Sync to c.accent
        WorkRecent.Kind.SAVED -> Icons.Rounded.Inventory2 to c.inkSecondary
        WorkRecent.Kind.TO_REVIEW -> Icons.Rounded.RateReview to c.accent
        WorkRecent.Kind.READY -> Icons.Rounded.CheckCircle to c.success
        WorkRecent.Kind.NEEDS_YOU -> Icons.Rounded.ErrorOutline to c.warning
    }
    Box(Modifier.size(MMSize.minTouch).clip(CircleShape).background(c.surfaceSunk), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}
