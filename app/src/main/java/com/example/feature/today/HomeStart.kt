package com.example.feature.today

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ui.coachTarget
import com.example.ui.theme.Brand

/**
 * Record, front and centre: the one thing most people open the app to do. A big brand button
 * with live sound bars, and the two other ways to start beside it.
 */
@Composable
fun QuickCapture(onRecord: () -> Unit, onNote: () -> Unit, onImport: () -> Unit, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onRecord, shape = RoundedCornerShape(22.dp), color = Color.Transparent, shadowElevation = 6.dp,
            modifier = Modifier.weight(1f).height(68.dp).coachTarget("record").testTag("home_record")
        ) {
            Box(Modifier.background(Brush.horizontalGradient(listOf(Brand.Blue, Brand.Indigo, Brand.Violet))).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Record", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
                    }
                    SoundBars()
                }
            }
        }
        SmallStart(Icons.AutoMirrored.Filled.NoteAdd, "Note", "home_note", onNote)
        SmallStart(Icons.Filled.FileUpload, "Import", "home_import", onImport)
    }
}

@Composable
private fun SmallStart(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.size(width = 64.dp, height = 68.dp).testTag(tag)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = Brand.Indigo, modifier = Modifier.size(22.dp))
            Text(label, color = Color(0xFF0F172A), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The icon's sound bars, gently alive. */
@Composable
private fun SoundBars() {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "phase")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(0.45f, 0.75f, 1f, 0.75f, 0.45f).forEachIndexed { i, h ->
            val wobble = 0.7f + 0.3f * kotlin.math.sin((phase * 2 * Math.PI + i).toFloat())
            Box(Modifier.width(3.dp).height(22.dp).graphicsLayer { scaleY = h * wobble }.clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.85f)))
        }
    }
}

/**
 * For someone new: what the app can do, as four small first steps that tick themselves off.
 * Replaces the empty calendar so Home never looks blank on day one.
 */
@Composable
fun GettingStartedCard(
    start: GettingStarted,
    onRecord: () -> Unit,
    onNote: () -> Unit,
    onCalendar: () -> Unit,
    onDevotional: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp).testTag("getting_started")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (start.isNew) "Welcome — let's make it yours" else "Getting started", color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${start.done} of ${start.steps} done", color = Color(0xFF64748B), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Hide getting started", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            }
        }
        // Progress along the brand gradient.
        Box(Modifier.padding(top = 12.dp, bottom = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEEF2F6))) {
            Box(Modifier.fillMaxWidth(start.done.toFloat() / start.steps).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(Brand.sweep)))
        }
        StartStep(Icons.Filled.Mic, "Record your first conversation", "A meeting, a class, a sermon — or just say hello to test it.", start.recorded, onRecord)
        StartStep(Icons.AutoMirrored.Filled.NoteAdd, "Write a note", "Type, add photos or scripture. Recordings become notes too.", start.wrote, onNote)
        StartStep(Icons.Filled.Event, "See your day", "Show your calendar here, and get a prep card before meetings.", start.calendar, onCalendar)
        if (start.showsFaith) StartStep(Icons.Filled.WbSunny, "Read today's devotional", "Scripture, a reflection and a prayer — listen or share it.", start.devotional, onDevotional)
    }
}

@Composable
private fun StartStep(icon: ImageVector, title: String, line: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = !done, onClick = onClick)
            .background(if (done) Color(0xFFF8FAFC) else Color(0xFFF4F6FB)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (done) Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet)) else Brush.linearGradient(listOf(Color.White, Color.White))),
            contentAlignment = Alignment.Center
        ) { Icon(if (done) Icons.Filled.Check else icon, contentDescription = null, tint = if (done) Color.White else Brand.Indigo, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (done) Color(0xFF94A3B8) else Color(0xFF0F172A), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            if (!done) Text(line, color = Color(0xFF64748B), fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
}

/** The first-run tour of Home. Steps whose target isn't on screen are skipped. */
fun homeTourSteps(hasStories: Boolean, hasSetup: Boolean) = buildList {
    add(com.example.core.ui.CoachStep("record", "Record anything", "Meetings, classes, sermons, voice notes. It keeps going with the screen off, and turns into a transcript and summary."))
    add(com.example.core.ui.CoachStep("new", "New, from anywhere", "This button is on every screen: record, write a note, add photos or import audio and video."))
    if (hasStories) add(com.example.core.ui.CoachStep("stories", "Your day in stories", "Tap a ring to watch. Hold to pause, swipe up to open, share any card."))
    add(com.example.core.ui.CoachStep("tile", "What's next", "Your next event, or a nudge for what to do. Tap to open it — or record it."))
    add(com.example.core.ui.CoachStep("home_search_button", "Find anything", "Search every word ever spoken in a recording, your notes and scripture."))
    if (hasSetup) add(com.example.core.ui.CoachStep("setup", "One last thing", "Get the offline pack so recordings become private transcripts and summaries on this phone."))
}
