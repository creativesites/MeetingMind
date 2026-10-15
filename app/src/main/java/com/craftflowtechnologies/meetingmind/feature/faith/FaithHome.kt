package com.craftflowtechnologies.meetingmind.feature.faith

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.biblestudy.TodayReading
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.faith.PrayerPerson
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeScaffold
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Today's devotional as the home shows it: [written] is false until the day's devotional exists. */
data class FaithDevotionalLine(val title: String, val scripture: String?, val written: Boolean)

/** Today's verse of the day, already fetched. */
data class FaithVerseLine(val reference: String, val version: String, val text: String, val attribution: String)

/** The reading plan the person is on, as one line of progress. */
data class FaithPlanLine(
    val name: String, val reading: String, val day: Int, val length: Int, val percent: Int, val behind: Int,
    val passage: ScriptureReference
)

/**
 * What the Faith home shows (ONE_PLACE §4). Everything is optional: an empty section renders nothing,
 * and a person who has never read here sees "Start reading".
 */
data class FaithHomeState(
    val place: ScriptureReference? = null,
    val start: ScriptureReference? = null,
    val today: TodayReading? = null,
    val devotional: FaithDevotionalLine? = null,
    /** What the person is reading ("Morning and Evening"), for the quiet line under Today's devotional; null renders nothing. */
    val reading: String? = null,
    val verse: FaithVerseLine? = null,
    val versePending: Boolean = false,
    val plan: FaithPlanLine? = null,
    val prayerPeople: List<PrayerPerson> = emptyList(),
    val prayedToday: Set<Long> = emptySet(),
    val requests: List<Note> = emptyList(),
    val answered: Int = 0,
    val journeyLine: String? = null,
    val entryCount: Int = 0,
    val scripturePassages: Int = 0,
    val collections: Int = 0,
    val recent: List<Note> = emptyList(),
    /** The last seven days in faith, oldest first, for the week card at the top. */
    val week: List<WeekDay> = emptyList()
)

data class FaithHomeActions(
    val onBack: () -> Unit = {},
    val onAssistant: () -> Unit = {},
    val onOpenBible: () -> Unit = {},
    val onRead: (ScriptureReference?) -> Unit = {},
    val onGuideToday: (ScriptureReference) -> Unit = {},
    val onOpenDevotional: () -> Unit = {},
    val onWriteDevotionalWithAi: () -> Unit = {},
    val onPray: () -> Unit = {},
    val onCircles: () -> Unit = {},
    val onReadVerse: () -> Unit = {},
    val onWriteOnVerse: () -> Unit = {},
    val onShareVerse: () -> Unit = {},
    val onTestimonies: () -> Unit = {},
    val onWrite: (RecordingType) -> Unit = {},
    val onRecordSermon: () -> Unit = {},
    val onOpenJourney: () -> Unit = {},
    val onOpenScripture: () -> Unit = {},
    val onOpenPrayerList: () -> Unit = {},
    val onPrayed: (PrayerPerson) -> Unit = {},
    val onAnswered: (Note) -> Unit = {},
    val onOpenNote: (String) -> Unit = {},
    val onDeleteNote: (Note) -> Unit = {},
    val onOpenPlans: () -> Unit = {},
    val onMarkPlanRead: () -> Unit = {}
)

/** The six writing tiles, in display order, with the note each one starts. Labels come from [EntryPoints]. */
internal val FaithWriteTiles = listOf(
    EntryPoints.faithWritePrayer to RecordingType.PRAYER,
    EntryPoints.faithWritePrayerRequest to RecordingType.PRAYER_REQUEST,
    EntryPoints.faithWriteBlessing to RecordingType.GRATITUDE,
    EntryPoints.faithWriteTestimony to RecordingType.TESTIMONY,
    EntryPoints.faithWriteReflection to RecordingType.REFLECTION,
    EntryPoints.faithWriteDevotional to RecordingType.DEVOTIONAL
)

/**
 * The Faith home. One hero: the Bible (continue where you left off, today's reading, Guide me or
 * Just read). Then today's devotional, Pray with me and Circles. Everything else is quieter, and the
 * ways to write a note are small tiles under one quiet "Write" heading.
 *
 * [more] adds the long tail (on this day, themes, photos…) behind one "More" row.
 */
@Composable
fun FaithHomeContent(
    state: FaithHomeState,
    actions: FaithHomeActions,
    modifier: Modifier = Modifier,
    createRow: (@Composable () -> Unit)? = null,
    hasMore: Boolean = false,
    more: LazyListScope.() -> Unit = {}
) {
    val c = MM.colors
    var moreOpen by remember { mutableStateOf(false) }
    HomeScaffold(
        modifier = modifier.fillMaxSize().background(c.background).statusBarsPadding().testTag("faith_home"),
        header = {
            ScreenHeader(
                title = "Faith",
                leading = { IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.ink) } },
                actions = {
                    IconButton(onClick = actions.onAssistant, modifier = Modifier.testTag("faith_assistant")) {
                        Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, contentDescription = "Ask the assistant", tint = c.ink)
                    }
                }
            )
        },
        hero = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                WeekInFaith(state.week)
                BibleCard(state, actions)
            }
        },
        sections = {
            item(key = "devotional") {
                Column {
                    DevotionalCard(state.devotional, actions.onOpenDevotional)
                    state.reading?.let { ReadingLine(it, actions.onWriteDevotionalWithAi) }
                }
            }
            item(key = "pray_circles") {
                MMCard(contentPadding = MM.space.s) {
                    ListRow(
                        title = EntryPoints.faithPray.label, subtitle = "Someone to pray with, out loud, your way",
                        modifier = Modifier.padding(horizontal = MM.space.s).testTag("faith_pray_with_me"),
                        leading = { Icon(Icons.Rounded.VolunteerActivism, null, tint = c.gold) },
                        trailing = { Chevron() }, onClick = actions.onPray
                    )
                    HorizontalDivider(color = c.line)
                    ListRow(
                        title = EntryPoints.faithCircles.label, subtitle = "Pray, study and share with your small group",
                        modifier = Modifier.padding(horizontal = MM.space.s).testTag("faith_circles"),
                        leading = { Icon(Icons.Rounded.Groups, null, tint = c.gold) },
                        trailing = { Chevron() }, onClick = actions.onCircles
                    )
                }
            }

            // ---- Quieter, below.
            item(key = "verse") { VerseCard(state, actions) }
            if (state.prayerPeople.isNotEmpty() || state.requests.isNotEmpty() || state.answered > 0) {
                item(key = "praying") { PrayingFor(state, actions) }
            }
            state.plan?.let { plan -> item(key = "plan") { PlanProgress(plan, actions) } }
            item(key = "testimonies") {
                MMCard(contentPadding = MM.space.s) {
                    ListRow(
                        title = EntryPoints.faithTestimonies.label, subtitle = "Stories of answered prayer and God's goodness",
                        modifier = Modifier.padding(horizontal = MM.space.s).testTag("faith_testimonies"),
                        leading = { Icon(Icons.Rounded.Star, null, tint = c.gold) },
                        trailing = { Chevron() }, onClick = actions.onTestimonies
                    )
                    if (state.entryCount > 0) {
                        HorizontalDivider(color = c.line)
                        ListRow(
                            title = "Your journey", subtitle = state.journeyLine, meta = state.entryCount.toString(),
                            modifier = Modifier.padding(horizontal = MM.space.s).testTag("faith_journey"),
                            trailing = { Chevron() }, onClick = actions.onOpenJourney
                        )
                    }
                    if (state.scripturePassages > 0 || state.collections > 0) {
                        HorizontalDivider(color = c.line)
                        ListRow(
                            title = "Scripture in your notes",
                            subtitle = buildString {
                                append(state.scripturePassages).append(if (state.scripturePassages == 1) " passage" else " passages")
                                if (state.collections > 0) append(" · ").append(state.collections).append(if (state.collections == 1) " collection" else " collections")
                            },
                            modifier = Modifier.padding(horizontal = MM.space.s).testTag("faith_scripture"),
                            trailing = { Chevron() }, onClick = actions.onOpenScripture
                        )
                    }
                }
            }
            createRow?.let { row -> item(key = "create") { row() } }
            item(key = "write") { WriteTiles(actions.onWrite) }
            item(key = "sermon") {
                MMCard(contentPadding = MM.space.s) {
                    ListRow(
                        title = EntryPoints.faithRecordSermon.label, subtitle = "Scripture, key points and notes, gathered for you",
                        modifier = Modifier.padding(horizontal = MM.space.s).testTag("faith_record_sermon"),
                        leading = { Icon(Icons.Rounded.Mic, null, tint = c.inkSecondary) },
                        trailing = { Chevron() }, onClick = actions.onRecordSermon
                    )
                }
            }
            if (state.recent.isNotEmpty()) {
                item(key = "recent") {
                    Column {
                        SectionHeader("Recent")
                        state.recent.forEach { n -> RecentRow(n, actions) }
                    }
                }
            } else {
                item(key = "private_line") {
                    Text("Everything you write here stays on your phone.", style = MM.type.secondary, color = c.inkMuted)
                }
            }
            if (hasMore) item(key = "more_toggle") { MoreToggle(moreOpen) { moreOpen = !moreOpen } }
            if (hasMore && moreOpen) more(this)
        }
    )
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MM.colors.inkMuted, modifier = Modifier.width(MMSize.icon))

/** Your week in faith: one bar per day, filled when a faith note was written that day. */
@Composable
private fun WeekInFaith(week: List<WeekDay>) {
    val c = MM.colors
    val kept = FaithWeek.keptCount(week)
    MMCard(contentPadding = MM.space.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your week in faith", style = MM.type.heading, color = c.ink, modifier = Modifier.weight(1f))
            Text("$kept of ${week.size} days", style = MM.type.caption, color = c.inkSecondary)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = MM.space.m),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            week.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (day.kept) c.accent else c.surfaceSunk)
                    )
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = MM.space.xs)
                    )
                }
            }
        }
    }
}

/** The hero: the card header opens the Bible home; Continue and today's reading are the two things most people want. */
@Composable
private fun BibleCard(state: FaithHomeState, actions: FaithHomeActions) {
    val c = MM.colors
    val place = state.place
    HeroCard(modifier = Modifier.testTag("faith_bible_card")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clip(MM.radius.small)
                .clickable(onClick = actions.onOpenBible).testTag("faith_bible_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoStories, null, tint = c.accent, modifier = Modifier.width(MMSize.icon))
            Spacer(Modifier.width(MM.space.m))
            Text(EntryPoints.faithBible.label, style = MM.type.heading, color = c.ink, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.accent)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clip(MM.radius.small)
                .clickable { actions.onRead(state.today?.reference ?: place ?: state.start) }.testTag("faith_continue"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        state.today != null -> "Today's reading · ${state.today.reference.display()}"
                        place == null -> EntryPoints.faithStartReading.label
                        else -> "${EntryPoints.faithContinue.label} · ${place.display()}"
                    },
                    style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (place == null) Text("Begin with the Gospel of John", style = MM.type.secondary, color = c.inkSecondary)
            }
        }
        state.today?.let { today ->
            HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = MM.space.s))
            today.lines.firstOrNull()?.let {
                Text(it, style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
            }
            Row(
                Modifier.padding(top = MM.space.m),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryButton(EntryPoints.faithGuideMe.label, onClick = { actions.onGuideToday(today.reference) }, modifier = Modifier.testTag("faith_guide_me"))
                SecondaryButton(EntryPoints.faithJustRead.label, onClick = { actions.onRead(today.reference) }, modifier = Modifier.testTag("faith_just_read"))
            }
        }
    }
}

@Composable
private fun DevotionalCard(devotional: FaithDevotionalLine?, onClick: () -> Unit) {
    val c = MM.colors
    MMCard(onClick = onClick, modifier = Modifier.testTag("faith_today_devotional")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WbSunny, null, tint = c.gold, modifier = Modifier.width(MMSize.icon))
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text(EntryPoints.faithDevotional.label, style = MM.type.caption, color = c.inkSecondary)
                Text(
                    devotional?.title ?: "A word for your day", style = MM.type.heading, color = c.ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    devotional?.scripture ?: "Scripture, a reflection and a prayer",
                    style = MM.type.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Chevron()
        }
    }
}

/** "Reading: Morning and Evening · Write one with AI": quiet, one line, under Today's devotional. */
@Composable
internal fun ReadingLine(name: String, onWriteWithAi: () -> Unit) {
    val c = MM.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.xs).testTag("faith_reading_line"), verticalAlignment = Alignment.CenterVertically) {
        Text("Reading: $name", style = MM.type.caption, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(" · ", style = MM.type.caption, color = c.inkMuted)
        TextAction("Write one with AI", onClick = onWriteWithAi, modifier = Modifier.testTag("faith_write_ai"))
    }
}

@Composable
private fun VerseCard(state: FaithHomeState, actions: FaithHomeActions) {
    val c = MM.colors
    val verse = state.verse
    MMCard(modifier = Modifier.testTag("faith_verse")) {
        Text("Verse of the day", style = MM.type.caption, color = c.inkSecondary)
        if (verse == null) {
            Text(
                if (state.versePending) "Fetching today's verse…" else "Today's verse appears here when you're online.",
                style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.padding(top = MM.space.xs)
            )
            return@MMCard
        }
        Text(
            "“${verse.text.trim()}”", style = MM.type.scripture, color = c.ink, maxLines = 6, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MM.space.s).clickable(onClick = actions.onReadVerse)
        )
        Text("${verse.reference} · ${verse.version}", style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.padding(top = MM.space.s))
        if (verse.attribution.isNotBlank()) {
            Text(verse.attribution, style = MM.type.caption, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = MM.space.xs)) {
            TextAction(EntryPoints.faithVerseWrite.label, actions.onWriteOnVerse, modifier = Modifier.testTag("faith_verse_write"))
            TextAction(EntryPoints.faithVerseShare.label, actions.onShareVerse, modifier = Modifier.testTag("faith_verse_share"))
        }
    }
}

@Composable
private fun PrayingFor(state: FaithHomeState, actions: FaithHomeActions) {
    val c = MM.colors
    Column(Modifier.testTag("faith_praying_for")) {
        SectionHeader(
            "Praying for", actionLabel = if (state.answered > 0) "${state.answered} answered" else null,
            onAction = if (state.answered > 0) actions.onOpenPrayerList else null
        )
        MMCard(contentPadding = MM.space.s) {
            val rows = state.prayerPeople.take(3).map<PrayerPerson, @Composable () -> Unit> { p ->
                {
                    val done = p.id in state.prayedToday
                    ListRow(
                        title = p.name, subtitle = p.note.takeIf { it.isNotBlank() },
                        modifier = Modifier.padding(horizontal = MM.space.s),
                        trailing = {
                            if (done) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(MMSize.iconSmall))
                                Text(" Prayed", style = MM.type.caption, color = c.success)
                            } else TextAction("Prayed", { actions.onPrayed(p) })
                        },
                        onClick = actions.onOpenPrayerList
                    )
                }
            } + state.requests.take(3).map<Note, @Composable () -> Unit> { r ->
                {
                    val days = ((System.currentTimeMillis() - r.createdAt) / 86_400_000L).toInt()
                    ListRow(
                        title = r.title.ifBlank { "Prayer request" },
                        subtitle = if (days <= 0) "Started today" else "Praying for $days day${if (days == 1) "" else "s"}",
                        modifier = Modifier.padding(horizontal = MM.space.s),
                        trailing = { TextAction("Answered", { actions.onAnswered(r) }) },
                        onClick = { actions.onOpenNote(r.id) }
                    )
                }
            }
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = c.line)
                row()
            }
            if (rows.isEmpty()) ListRow(
                title = "Your prayer list", modifier = Modifier.padding(horizontal = MM.space.s),
                trailing = { Chevron() }, onClick = actions.onOpenPrayerList
            )
        }
    }
}

@Composable
private fun PlanProgress(plan: FaithPlanLine, actions: FaithHomeActions) {
    val c = MM.colors
    MMCard(onClick = actions.onOpenPlans, modifier = Modifier.testTag("faith_reading_plan")) {
        Text("Reading plan · ${plan.name}", style = MM.type.caption, color = c.inkSecondary)
        Text(plan.reading, style = MM.type.heading, color = c.ink, modifier = Modifier.padding(top = MM.space.xs))
        LinearProgressIndicator(
            progress = { plan.percent / 100f }, color = c.accent, trackColor = c.track,
            modifier = Modifier.fillMaxWidth().padding(top = MM.space.m).heightIn(max = MM.space.s / 2).clip(MM.radius.pill)
        )
        Row(Modifier.padding(top = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Day ${plan.day} of ${plan.length} · ${plan.percent}%" + if (plan.behind > 0) " · ${plan.behind} behind" else "",
                style = MM.type.caption, color = c.inkMuted, modifier = Modifier.weight(1f)
            )
            TextAction("Mark read", actions.onMarkPlanRead, modifier = Modifier.testTag("plan_mark_read"))
        }
    }
}

/** Six small tiles that only create a note. Quiet on purpose: a sunk panel, a small icon, a plain label. */
@Composable
private fun WriteTiles(onWrite: (RecordingType) -> Unit) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s), modifier = Modifier.testTag("faith_write")) {
        Text("Write", style = MM.type.caption, color = c.inkMuted, modifier = Modifier.semantics { heading() })
        FaithWriteTiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                pair.forEach { (entry, type) ->
                    InsetPanel(
                        onClick = { onWrite(type) },
                        modifier = Modifier.weight(1f).testTag("faith_write_" + type.name.lowercase()),
                        contentPadding = MM.space.m
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(faithIcon(type), null, tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                            Spacer(Modifier.width(MM.space.s))
                            Text(entry.label, style = MM.type.secondary, color = c.ink, maxLines = 2)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecentRow(note: Note, actions: FaithHomeActions) {
    val c = MM.colors
    val date = note.eventDate ?: note.createdAt
    ListRow(
        title = note.title.ifBlank { note.workflow.displayName },
        subtitle = note.workflow.displayName + " · " + Formatters.formatDateRelative(date),
        leading = { Icon(faithIcon(note.workflow), null, tint = c.gold, modifier = Modifier.size(MMSize.iconSmall)) },
        trailing = {
            IconButton(onClick = { actions.onDeleteNote(note) }, modifier = Modifier.size(MMSize.minTouch)) {
                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = c.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
            }
        },
        onClick = { actions.onOpenNote(note.id) }
    )
}

/** The "More" row that opens the long tail. Used by the screen wrapper. */
@Composable
internal fun MoreToggle(open: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextAction(if (open) "Fewer" else "More", onToggle, modifier = Modifier.testTag("faith_more"))
        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MM.colors.accent)
    }
}
