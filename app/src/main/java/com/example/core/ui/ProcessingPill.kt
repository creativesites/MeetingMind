package com.example.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.ai.pipeline.MeetingProcessingWorker
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceTrack

/** What the minimised transcription pill shows. */
data class ActiveProcessing(val meetingId: String, val title: String, val step: String, val percent: Int, val waiting: Int)

/** The running (or next queued) processing job, read straight from WorkManager. */
@Composable
fun rememberActiveProcessing(): ActiveProcessing? {
    val context = LocalContext.current
    val flow = remember { WorkManager.getInstance(context).getWorkInfosByTagFlow(MeetingProcessingWorker.ALL_PROCESSING_TAG) }
    val infos by flow.collectAsState(initial = emptyList())
    val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return null
    val waiting = infos.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
    val p = running.progress
    val meetingId = p.getString(MeetingProcessingWorker.KEY_MEETING_ID)
        ?: running.tags.firstOrNull { it.startsWith("meetmind_processing_") }?.removePrefix("meetmind_processing_")
        ?: return null
    return ActiveProcessing(
        meetingId = meetingId,
        title = p.getString(MeetingProcessingWorker.KEY_RECORDING_TITLE) ?: "Recording",
        step = p.getString(MeetingProcessingWorker.KEY_PROGRESS_STEP) ?: "Starting…",
        percent = p.getInt(MeetingProcessingWorker.KEY_PROGRESS_PERCENT, 0),
        waiting = waiting
    )
}

/**
 * The minimised processing screen: a slim card that follows you around the main tabs while a
 * recording is transcribed. Tap to open the full progress.
 */
@Composable
fun ProcessingPill(active: ActiveProcessing?, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = active != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        val a = active ?: return@AnimatedVisibility
        Surface(
            onClick = { onOpen(a.meetingId) },
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Line),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = Accent, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Transcribing “${a.title}”" + if (a.waiting > 0) " · ${a.waiting} waiting" else "",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(a.step, fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (a.percent in 1..99) Text("${a.percent}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (a.percent.coerceIn(0, 100)) / 100f },
                    color = Accent,
                    trackColor = SurfaceTrack,
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }
        }
    }
}
