package com.craftflowtechnologies.meetingmind.feature.today

import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.ui.coachTarget
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppLook

/**
 * Record, front and centre: the one thing most people open the app to do, in their chosen accent.
 */
@Composable
fun QuickCapture(onRecord: () -> Unit, onNote: () -> Unit, onImport: () -> Unit, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onRecord, shape = RoundedCornerShape(22.dp), color = LocalAppLook.current.accent, shadowElevation = 0.dp,
            modifier = Modifier.weight(1f).height(68.dp).coachTarget("record").testTag("home_record")
        ) {
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(OnAccent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = OnAccent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Record", color = OnAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = OnAccent.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
        SmallStart(Icons.AutoMirrored.Filled.NoteAdd, "Note", "home_note", onNote)
        SmallStart(Icons.Filled.FileUpload, "Import", "home_import", onImport)
    }
}

@Composable
private fun SmallStart(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = SurfaceBase, border = BorderStroke(1.dp, Line), modifier = Modifier.size(width = 64.dp, height = 68.dp).testTag(tag)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = LocalAppLook.current.accent, modifier = Modifier.size(22.dp))
            Text(label, color = Ink, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
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
    val look = LocalAppLook.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
            .border(1.dp, Line, RoundedCornerShape(24.dp)).background(SurfaceBase).padding(18.dp).testTag("getting_started")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (start.isNew) "Welcome — let's make it yours" else "Getting started", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${start.done} of ${start.steps} done", color = InkMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Hide getting started", tint = InkMuted, modifier = Modifier.size(18.dp))
            }
        }
        // Progress along the brand gradient.
        Box(Modifier.padding(top = 12.dp, bottom = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEEF2F6).forTheme())) {
            Box(Modifier.fillMaxWidth(start.done.toFloat() / start.steps).height(6.dp).clip(RoundedCornerShape(3.dp)).background(look.accent))
        }
        StartStep(Icons.Filled.Mic, "Record your first conversation", "A meeting, a class, a sermon — or just say hello to test it.", start.recorded, onRecord)
        StartStep(Icons.AutoMirrored.Filled.NoteAdd, "Write a note", "Type, add photos or scripture. Recordings become notes too.", start.wrote, onNote)
        StartStep(Icons.Filled.Event, "See your day", "Show your calendar here, and get a prep card before meetings.", start.calendar, onCalendar)
        if (start.showsFaith) StartStep(Icons.Filled.WbSunny, "Read today's devotional", "Scripture, a reflection and a prayer — listen or share it.", start.devotional, onDevotional)
    }
}

@Composable
private fun StartStep(icon: ImageVector, title: String, line: String, done: Boolean, onClick: () -> Unit) {
    val look = LocalAppLook.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = !done, onClick = onClick)
            .background(if (done) Color(0xFFF8FAFC).forTheme() else Color(0xFFF4F6FB).forTheme()).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (done) look.accent else Color.White),
            contentAlignment = Alignment.Center
        ) { Icon(if (done) Icons.Filled.Check else icon, contentDescription = null, tint = if (done) Color.White else look.accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (done) InkMuted else Ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            if (!done) Text(line, color = InkMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
}

/** The first-run tour of Home. Steps whose target isn't on screen are skipped. */
fun homeTourSteps(hasStories: Boolean, hasSetup: Boolean) = buildList {
    add(com.craftflowtechnologies.meetingmind.core.ui.CoachStep("record", "Record anything", "Meetings, classes, sermons, voice notes. It keeps going with the screen off, and turns into a transcript and summary."))
    add(com.craftflowtechnologies.meetingmind.core.ui.CoachStep("new", "New, from anywhere", "This button is on every screen: record, write a note, add photos or import audio and video."))
    if (hasStories) add(com.craftflowtechnologies.meetingmind.core.ui.CoachStep("stories", "Your day in stories", "Tap a ring to watch. Hold to pause, swipe up to open, share any card."))
    add(com.craftflowtechnologies.meetingmind.core.ui.CoachStep("tile", "What's next", "Your next event, or a nudge for what to do. Tap to open it — or record it."))
    add(com.craftflowtechnologies.meetingmind.core.ui.CoachStep("home_search_button", "Find anything", "Search every word ever spoken in a recording, your notes and scripture."))
    if (hasSetup) add(com.craftflowtechnologies.meetingmind.core.ui.CoachStep("setup", "One last thing", "Get the offline pack so recordings become private transcripts and summaries on this phone."))
}
