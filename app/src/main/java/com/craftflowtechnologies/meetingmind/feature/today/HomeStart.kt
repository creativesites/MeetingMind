package com.craftflowtechnologies.meetingmind.feature.today

import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
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
import androidx.compose.runtime.getValue
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.displayName
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * For someone new: what the app can do, as small first steps that tick themselves off, introduced by
 * the companion (when there is one). Replaces the empty calendar so Home never looks blank on day one.
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
    val c = MM.colors
    val showsCompanion = com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible(
        com.craftflowtechnologies.meetingmind.core.companion.CompanionPage.HOME
    )
    val settings by com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings()
    val companionName = settings.form?.let { settings.displayName(it) }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(MM.radius.card)
            .border(1.dp, c.line, MM.radius.card).background(c.surfaceRaised).padding(MM.space.l).testTag("getting_started")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showsCompanion) {
                com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot(
                    com.craftflowtechnologies.meetingmind.core.companion.CompanionPage.HOME, 44.dp,
                    state = com.craftflowtechnologies.meetingmind.core.companion.CompanionState.CURIOUS
                )
                Spacer(Modifier.width(MM.space.m))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        start.isNew && showsCompanion && companionName != null -> "Hi, I'm $companionName. Let's make it yours"
                        start.isNew -> "Welcome. Let's make it yours"
                        else -> "Getting started"
                    },
                    style = MM.type.heading, color = c.ink
                )
                Text("${start.done} of ${start.steps} done", style = MM.type.secondary, color = c.inkMuted, modifier = Modifier.padding(top = MM.space.xs))
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDismiss).testTag("getting_started_hide"), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Hide getting started", tint = c.inkMuted, modifier = Modifier.size(18.dp))
            }
        }
        // Progress in the person's accent.
        Box(Modifier.padding(top = MM.space.m, bottom = MM.space.xs).fillMaxWidth().height(6.dp).clip(MM.radius.pill).background(c.surfaceSunk)) {
            Box(Modifier.fillMaxWidth(start.done.toFloat() / start.steps).height(6.dp).clip(MM.radius.pill).background(c.accent))
        }
        StartStep(Icons.Filled.Mic, "Record your first conversation", "A meeting, a class, a sermon, or just say hello to test it.", start.recorded, "start_record", onRecord)
        StartStep(Icons.AutoMirrored.Filled.NoteAdd, "Write a note", "Type, add photos or scripture. Recordings become notes too.", start.wrote, "start_note", onNote)
        StartStep(Icons.Filled.Event, "See your day", "Show your calendar here, and get a prep card before meetings.", start.calendar, "start_calendar", onCalendar)
        if (start.showsFaith) StartStep(Icons.Filled.WbSunny, "Read today's devotional", "Scripture, a reflection and a prayer. Listen or share it.", start.devotional, "start_devotional", onDevotional)
    }
}

@Composable
private fun StartStep(icon: ImageVector, title: String, line: String, done: Boolean, tag: String, onClick: () -> Unit) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().padding(top = MM.space.s).clip(MM.radius.card).clickable(enabled = !done, onClick = onClick)
            .background(if (done) c.surface else c.surfaceSunk).padding(MM.space.m).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (done) c.accent else c.surfaceRaised),
            contentAlignment = Alignment.Center
        ) { Icon(if (done) Icons.Filled.Check else icon, contentDescription = null, tint = if (done) c.onAccent else c.accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(MM.space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = if (done) c.inkMuted else c.ink)
            if (!done) Text(line, style = MM.type.secondary, color = c.inkMuted)
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
