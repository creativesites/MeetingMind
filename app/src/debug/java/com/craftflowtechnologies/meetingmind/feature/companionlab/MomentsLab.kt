package com.craftflowtechnologies.meetingmind.feature.companionlab

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedger
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentFacts
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentFlags
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlanner
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.companion.moment.RotationState
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilRotation
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilTicket
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.CardReveal
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.FirstKeepNote
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.FirstKeepOverlay
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.LocalMomentDurationScale
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.LocalMomentTime
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentScope
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentSpecs
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MomentTitle
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.NoteUnveil
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.ResolvedMoment
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.StageTarget
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/*
 * Companion Lab → Moments (Z-30): play any moment on a sample note, scrub it, change the form, the space
 * and the speed, and run the planner with the founder flags. Debug builds only.
 */

/** Sample notes for the Lab and the goldens: the prototype's `CONTENT`. Fixture text, not app copy. */
data class LabNote(
    val meta: String, val title: String, val chips: List<String>, val summary: String,
    val points: List<String>, val actions: List<String>, val headline: String, val sub: String, val companionLine: String?
)

val LabNotes: Map<MomentSpace, LabNote> = mapOf(
    MomentSpace.EVERYDAY to LabNote(
        "Note · 18 min · Today, 12:40", "Lunch with Grace: plans for June", listOf("2 voices"),
        "You and Grace settled the June trip: the cabin, the van and the budget.",
        listOf("Book the cabin before Friday", "Grace brings the van", "Keep the trip under 900"),
        listOf("Book the cabin · Fri", "Send Grace the dates"),
        "Your first note is ready.", "Everything you said, kept.", "I kept every word."
    ),
    MomentSpace.WORK to LabNote(
        "Meeting · 32 min · Today, 14:05", "Acme pricing review", listOf("3 speakers", "3 action items"),
        "Acme wants a revised quote; Sarah will check the discount above 50 seats.",
        listOf("Revised quote to Acme by Friday", "Sarah checks the 50-seat discount", "Onboarding time came up twice"),
        listOf("Revised quote to Acme · Fri", "Ask Sarah about the discount"),
        "Your first note is ready.", "3 action items, kept.", "I kept every word."
    ),
    MomentSpace.FAITH to LabNote(
        "Sermon · 41 min · Sunday, 10:30", "Be still: Psalm 46", listOf("Grace Chapel"),
        "Pastor James on Psalm 46: stillness as an act of trust.",
        listOf("Stillness is trust, not doing nothing", "Psalm 46:10 was read twice", "This week: pray before planning"),
        listOf("Pray before planning", "Read Psalm 46 again"),
        "Your first sermon notes are ready.", "Every word, kept.", null
    ),
    MomentSpace.STUDY to LabNote(
        "Lecture · 52 min · Today, 09:00", "Biochem L7: Glycolysis", listOf("Dr. Okafor", "On your phone"),
        "Glycolysis, its control point and the energy balance.",
        listOf("PFK-1 sets the pace of glycolysis", "Net gain: 2 ATP per glucose", "Quiz on Thursday covers L5 to L7"),
        listOf("Make flashcards for L7", "Revise L5 before Thursday"),
        "Your first note is ready.", "Ready when you are to revise.", "I kept every word."
    )
)

/** A note screen as `MeetingDetail` lays it out, tagged for the stage: top bar, header, body sections. */
@Composable
fun MomentScope.MomentSampleNote(note: LabNote, modifier: Modifier = Modifier) {
    val c = MM.colors
    // No background here: Dawn's glow draws between the screen's background and the header.
    Column(modifier.fillMaxSize().padding(horizontal = MM.space.l)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.inkSecondary)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.Share, null, tint = c.inkSecondary)
            Spacer(Modifier.width(MM.space.l))
            Icon(Icons.Rounded.MoreVert, null, tint = c.inkSecondary)
        }
        Column(Modifier.fillMaxWidth().momentLayer(StageTarget.HEADER).padding(vertical = MM.space.s)) {
            Text(note.meta, style = MM.type.caption, color = c.inkMuted)
            Spacer(Modifier.height(MM.space.xs))
            MomentTitle(note.title, MM.type.title)
            Spacer(Modifier.height(MM.space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                note.chips.forEach {
                    Text(
                        it, style = MM.type.caption, color = c.inkSecondary,
                        modifier = Modifier.clip(MM.radius.pill).background(c.surfaceSunk).padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    )
                }
            }
        }
        Spacer(Modifier.height(MM.space.l))
        Column(Modifier.momentLayer(StageTarget.BODY, 0).padding(bottom = MM.space.l)) {
            Text("SUMMARY", style = MM.type.overline, color = c.inkMuted)
            Spacer(Modifier.height(MM.space.xs))
            Text(note.summary, style = MM.type.secondary, color = c.inkSecondary)
        }
        Column(Modifier.momentLayer(StageTarget.BODY, 1).padding(bottom = MM.space.l)) {
            Text("KEY POINTS", style = MM.type.overline, color = c.inkMuted)
            note.points.forEach { Bullet(it) }
        }
        Column(Modifier.momentLayer(StageTarget.BODY, 2)) {
            Text("ACTION ITEMS", style = MM.type.overline, color = c.inkMuted)
            note.actions.forEach { Bullet(it) }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(top = MM.space.xs), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = MM.space.s).size(5.dp).clip(CircleShape).background(MM.colors.accent))
        Spacer(Modifier.width(MM.space.s))
        Text(text, style = MM.type.secondary, color = MM.colors.inkSecondary)
    }
}

/** What the Lab (and the goldens) can play. */
enum class LabMoment { FIRST_KEEP, LIFT, INKLINE, CURTAIN, DAWN, PLANT, NOD, DAWN_QUIET, CARD_REVEAL }

/** The plan for a Lab pick. Faith makes the First Keep quiet. */
fun labPlan(m: LabMoment, space: MomentSpace, plays: Int = 1): MomentPlan? {
    fun unveil(v: UnveilVariant) = MomentPlan.Unveil(v, UnveilRotation.durationMs(v, space, plays), quiet = false, space = space)
    return when (m) {
        LabMoment.FIRST_KEEP -> MomentPlan.FirstKeep(
            quiet = space == MomentSpace.FAITH, hello = false, onceEver = true, chime = space != MomentSpace.FAITH, space = space
        )
        LabMoment.LIFT -> unveil(UnveilVariant.LIFT)
        LabMoment.INKLINE -> unveil(UnveilVariant.INKLINE)
        LabMoment.CURTAIN -> unveil(UnveilVariant.CURTAIN)
        LabMoment.DAWN -> unveil(UnveilVariant.DAWN)
        LabMoment.PLANT -> unveil(UnveilVariant.PLANT)
        LabMoment.NOD -> MomentPlan.Nod(space = space)
        LabMoment.DAWN_QUIET -> MomentPlan.Unveil(UnveilVariant.DAWN, MomentPlanner.SermonDawnMs, quiet = true, space = MomentSpace.FAITH)
        LabMoment.CARD_REVEAL -> null
    }
}

/**
 * One moment over the sample note, sized by the caller. [key] restarts it. Freeze it with
 * `LocalMomentTime` (the scrubber, the goldens).
 */
@Composable
fun MomentPreview(plan: MomentPlan?, form: CompanionForm?, note: LabNote, key: String, modifier: Modifier = Modifier, cardMode: CreateMode = CreateMode.JOYFUL) {
    Box(modifier.background(MM.colors.background)) {
        when (plan) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // The Create card reveal on a plain card.
                Box(
                    Modifier.size(220.dp, 300.dp).clip(MM.radius.card).background(MM.colors.accent),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        note.points.first(), style = MM.type.heading, color = MM.colors.onAccent,
                        modifier = Modifier.align(Alignment.Center).padding(MM.space.l)
                    )
                    CardReveal(form ?: CompanionForm.ZURI, cardMode, 72.dp, playKey = key, modifier = Modifier.padding(bottom = MM.space.xl))
                }
            }
            is MomentPlan.FirstKeep -> {
                NoteUnveil(null, form, "") { MomentSampleNote(note) }
                FirstKeepOverlay(
                    plan, form, FirstKeepNote(note.title, note.meta, note.points.take(3)),
                    headline = note.headline, subline = note.sub, openLabel = "Open note", shareLabel = "Share a moment",
                    announcement = "${note.headline} ${note.title}.", companionLine = note.companionLine,
                    onOpen = {}, onShare = {}, sounds = false, key = key
                )
            }
            else -> NoteUnveil(plan, form, "${note.title} is ready.", key = key, titleWords = note.title.split(' ').size) {
                MomentSampleNote(note)
            }
        }
    }
}

/** The Moments tab. */
@Composable
fun MomentsLab() {
    var momentIndex by rememberSaveable { mutableIntStateOf(0) }
    var formIndex by rememberSaveable { mutableIntStateOf(0) }
    var spaceIndex by rememberSaveable { mutableIntStateOf(0) }
    var playsIndex by rememberSaveable { mutableIntStateOf(0) }
    var speedIndex by rememberSaveable { mutableIntStateOf(2) }
    var replay by remember { mutableIntStateOf(0) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var everyNote by rememberSaveable { mutableStateOf(MomentFlags.Current.firstKeepEveryNote) }
    var everyOpen by rememberSaveable { mutableStateOf(MomentFlags.Current.unveilOnEveryOpen) }
    var arrival by rememberSaveable { mutableStateOf(true) }
    var rotation by remember { mutableStateOf(RotationState()) }
    var ledger by remember { mutableStateOf(MomentLedger()) }
    var planned by remember { mutableStateOf<MomentPlan?>(null) }
    var plannedLine by remember { mutableStateOf("") }

    val roster = CompanionRoster.enabled
    val form = roster[formIndex.coerceIn(0, roster.lastIndex)]
    val space = MomentSpace.entries[spaceIndex]
    val plays = Plays[playsIndex]
    val pick = LabMoment.entries[momentIndex]
    val plan = planned ?: labPlan(pick, space, plays)
    val note = LabNotes.getValue(if (pick == LabMoment.DAWN_QUIET) MomentSpace.FAITH else space)
    val resolved = plan?.let { ResolvedMoment.forPlan(it, form) }
    val playedMs = resolved?.playedMs ?: MomentSpecs.CardReveal.baseMs
    val speed = Speeds[speedIndex]

    Column(verticalArrangement = Arrangement.spacedBy(MM.space.l)) {
        MMCard(contentPadding = 0.dp) {
            CompositionLocalProvider(
                LocalMomentTime provides scrub?.toInt(),
                LocalMomentDurationScale provides 1f / speed
            ) {
                MomentPreview(plan, form, note, key = "lab/$replay/$pick/$form/$space/$plays", modifier = Modifier.fillMaxWidth().height(560.dp))
            }
        }
        Text(
            stringResource(R.string.companion_lab_moment_time, (scrub ?: playedMs.toFloat()) / 1000f, playedMs / 1000f),
            style = MM.type.caption, color = MM.colors.inkMuted
        )
        Slider(value = scrub ?: playedMs.toFloat(), onValueChange = { scrub = it }, valueRange = 0f..playedMs.toFloat())
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            PrimaryButton(stringResource(R.string.companion_lab_play), onClick = { scrub = null; replay++ })
            SecondaryButton(stringResource(R.string.companion_lab_moment_reset), onClick = { planned = null; plannedLine = ""; scrub = null; replay++ })
        }
        SegmentedControl(Speeds.map { "$it×" }, speedIndex, onSelect = { speedIndex = it })
        resolved?.let { m ->
            Text(
                m.spec.beats.joinToString("\n") { b -> "%.2f–%.2f s  %s".format(b.startMs * m.timeScale / 1000f, b.endMs * m.timeScale / 1000f, b.name) },
                style = MM.type.caption, color = MM.colors.inkSecondary
            )
        }

        SectionHeader(stringResource(R.string.companion_lab_moment))
        LabChips(LabMoment.entries.map { it.name.lowercase().replace('_', ' ') }, if (planned == null) momentIndex else -1) {
            momentIndex = it; planned = null; plannedLine = ""; scrub = null; replay++
        }
        SectionHeader(stringResource(R.string.companion_lab_form))
        LabChips(roster.map { stringResource(CompanionRoster.displayName(it)) }, formIndex) { formIndex = it; replay++ }
        SectionHeader(stringResource(R.string.companion_lab_space))
        LabChips(MomentSpace.entries.map { it.name.lowercase() }, spaceIndex) { spaceIndex = it; replay++ }
        SectionHeader(stringResource(R.string.companion_lab_plays))
        LabChips(Plays.map { "$it" }, playsIndex) { playsIndex = it; replay++ }

        SectionHeader(stringResource(R.string.companion_lab_planner))
        LabToggle(stringResource(R.string.companion_lab_flag_first_keep), everyNote) { everyNote = it }
        LabToggle(stringResource(R.string.companion_lab_flag_every_open), everyOpen) { everyOpen = it }
        LabToggle(stringResource(R.string.companion_lab_arrival), arrival) { arrival = it }
        PrimaryButton(stringResource(R.string.companion_lab_open_note), onClick = {
            val now = LocalClock(System.currentTimeMillis())
            val ticket = if (arrival) UnveilTicket("lab", now.nowMs - 1_000, space = labSpace(space)) else null
            val result = MomentPlanner.plan(
                MomentFacts(
                    noteId = "lab", ticket = ticket, space = labSpace(space), clock = now, ledger = ledger, rotation = rotation,
                    flags = MomentFlags(everyNote, everyOpen)
                )
            )
            result.effects.rotation?.let { rotation = it }
            ledger = result.effects.record.fold(ledger) { l, m -> l.record(m, now) }
            planned = result.plan
            plannedLine = result.plan.toString()
            scrub = null
            replay++
        })
        if (plannedLine.isNotEmpty()) Text(plannedLine, style = MM.type.caption, color = MM.colors.inkSecondary)
        Text(
            stringResource(R.string.companion_lab_next_picks, UnveilRotation.preview(rotation, space, 8).joinToString(" · ") { it.name.lowercase() }),
            style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(bottom = MM.space.xl)
        )
    }
}

private val Plays = listOf(1, 8, 25)
private val Speeds = listOf(0.25f, 0.5f, 1f)

private fun labSpace(s: MomentSpace): NotebookSpace = when (s) {
    MomentSpace.EVERYDAY -> NotebookSpace.PERSONAL
    MomentSpace.FAITH -> NotebookSpace.FAITH
    MomentSpace.WORK -> NotebookSpace.WORK
    MomentSpace.STUDY -> NotebookSpace.LEARNING
}

@Composable
private fun LabChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        labels.forEachIndexed { i, label -> MMChip(label, selected = i == selected, onClick = { onSelect(i) }) }
    }
}

@Composable
private fun LabToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
