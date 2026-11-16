package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.work.AttentionRow
import com.craftflowtechnologies.meetingmind.core.ui.work.AttentionTone
import com.craftflowtechnologies.meetingmind.core.ui.work.BriefingButton
import com.craftflowtechnologies.meetingmind.core.ui.work.BriefingCard
import com.craftflowtechnologies.meetingmind.core.ui.work.BriefingChip
import com.craftflowtechnologies.meetingmind.core.ui.work.BriefingLabel
import com.craftflowtechnologies.meetingmind.core.ui.work.DayRibbon
import com.craftflowtechnologies.meetingmind.core.ui.work.RibbonBlock
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.time.LocalTime

/** One row of "Needs you": its kind, the title, the source line, the one verb, and where the row opens. */
internal class PulseNeed(
    val key: String,
    val kind: String,
    val title: String,
    val line: String,
    val verb: String,
    val tone: AttentionTone,
    val icon: ImageVector,
    val onVerb: () -> Unit,
    val onOpen: () -> Unit
)

/** One sentence of "Since yesterday". Tapping it opens the evidence behind the sentence. */
internal class PulseChange(val text: String, val onOpen: () -> Unit)

/** A line on the calendar: an event, its time, and where it opens. */
internal class ScheduleRow(val key: String, val title: String, val subtitle: String?, val time: String, val onClick: () -> Unit)

/** The hero, chosen by [com.craftflowtechnologies.meetingmind.feature.work.hub.pulseMoment]. */
internal sealed interface PulseHero {
    /** Before a meeting: title, who, three lines (You owe, They owe, Still open) and the day's ribbon. */
    class Next(
        val whenLabel: String,
        val title: String,
        val withLine: String?,
        val lines: List<Pair<String, String>>,
        val blocks: List<RibbonBlock>,
        val now: LocalTime?,
        val onPrepare: () -> Unit,
        val onRecord: () -> Unit
    ) : PulseHero

    /** After meetings: a finished recording waits for its findings to be checked. */
    class ToReview(val headline: String, val sub: String, val onReview: () -> Unit) : PulseHero

    /** Friday after the review time. */
    class Weekly(val onStart: () -> Unit) : PulseHero

    /** Day one: nothing reviewed yet. The calendar leads when there is a meeting; otherwise three ways to start. */
    class FirstDay(val calendarLine: String?, val onRecord: () -> Unit, val onWrite: () -> Unit, val onInbox: () -> Unit) : PulseHero
}

/** Everything the Pulse pane shows. An empty part renders nothing. */
internal class PulseModel(
    /** "Good morning, Ana" or, when nothing needs the person, "Nothing needs you today". */
    val headline: String,
    /** One human sentence that names the most important thing; null when there is none. */
    val sentence: String?,
    val hero: PulseHero?,
    val needs: List<PulseNeed>,
    val needsTotal: Int,
    val sinceLabel: String,
    val changes: List<PulseChange>,
    val today: List<ScheduleRow>,
    val earlier: List<ScheduleRow>,
    val onAsk: () -> Unit,
    val onOpenWeeklyReview: () -> Unit = {}
)

private const val CHANGES_SHOWN = 3

/**
 * Work › Pulse (artifact #1b to #1h): follow-through for today. The greeting and one sentence, the hero for the
 * moment, Needs you (at most four rows, each with one verb), Since yesterday as plain sentences, today's calendar,
 * and the Ask field. The header and the bottom bar belong to [WorkPageScreen]; nothing here repeats them.
 */
@Composable
internal fun PulsePane(model: PulseModel, listState: LazyListState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize().testTag("work_pulse_list"), state = listState,
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xxl),
        verticalArrangement = Arrangement.spacedBy(MM.space.xl)
    ) {
        item(key = "greeting") { Greeting(model) }
        model.hero?.let { hero -> item(key = "hero") { PulseHeroCard(hero) } }
        if (model.needs.isNotEmpty()) item(key = "needs") {
            Column {
                SectionHeader("Needs you", count = model.needsTotal)
                MMCard(contentPadding = MM.space.s) {
                    model.needs.forEach { n ->
                        AttentionRow(
                            kindLabel = n.kind, title = n.title, subtitle = n.line, verb = n.verb,
                            onVerb = n.onVerb, onClick = n.onOpen, leadingIcon = n.icon,
                            modifier = Modifier.testTag("work_need_${n.key}"), tone = n.tone
                        )
                    }
                }
            }
        }
        if (model.changes.isNotEmpty()) item(key = "since") {
            Column {
                SectionHeader(model.sinceLabel)
                MMCard(contentPadding = MM.space.m) {
                    model.changes.take(CHANGES_SHOWN).forEach { c ->
                        Text(
                            c.text, style = MM.type.body, color = MM.colors.ink,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = c.onOpen).padding(vertical = MM.space.xs)
                        )
                    }
                }
            }
        }
        if (model.today.isNotEmpty() || model.earlier.isNotEmpty()) item(key = "today") {
            Column {
                SectionHeader("Today", count = model.today.size + model.earlier.size)
                MMCard(contentPadding = MM.space.m) {
                    model.earlier.forEach { ListRow(it.title, subtitle = it.subtitle, meta = it.time, onClick = it.onClick) }
                    model.today.forEach { ListRow(it.title, subtitle = it.subtitle, meta = it.time, onClick = it.onClick) }
                }
            }
        }
        item(key = "ask") { AskSection(model.onAsk) }
    }
}

@Composable
private fun Greeting(model: PulseModel) {
    Column(Modifier.padding(top = MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        Text(model.headline, style = MM.type.display, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        model.sentence?.let { Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary) }
    }
}

@Composable
private fun PulseHeroCard(hero: PulseHero) {
    when (hero) {
        is PulseHero.Next -> BriefingCard {
            BriefingLabel("NEXT · ${hero.whenLabel.uppercase()}")
            Text(hero.title, style = MM.type.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
            hero.withLine?.let { Text(it, style = MM.type.secondary, color = FixedWhite.copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp)) }
            if (hero.blocks.isNotEmpty()) DayRibbon(
                modifier = Modifier.padding(top = MM.space.m), blocks = hero.blocks, now = hero.now, onDark = true
            )
            if (hero.lines.isNotEmpty()) Column(Modifier.padding(top = MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                hero.lines.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label.uppercase(), style = MM.type.overline, color = FixedWhite.copy(alpha = 0.6f), modifier = Modifier.width(104.dp))
                        Text(value, style = MM.type.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.padding(top = MM.space.m), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                BriefingButton("Record", hero.onRecord, primary = true)
                BriefingButton("Prepare", hero.onPrepare, primary = false)
            }
        }
        is PulseHero.ToReview -> BriefingCard {
            BriefingLabel("TO REVIEW")
            Text(hero.headline, style = MM.type.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
            Text(hero.sub, style = MM.type.secondary, color = FixedWhite.copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = MM.space.l)) { BriefingButton("Review findings", hero.onReview) }
        }
        is PulseHero.Weekly -> BriefingCard {
            BriefingLabel("WEEKLY REVIEW · FRIDAY")
            Text("Close the week in five minutes", style = MM.type.title, modifier = Modifier.padding(top = MM.space.xs))
            Text("Carry forward what matters and leave the rest.", style = MM.type.secondary, color = FixedWhite.copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = MM.space.l)) { BriefingButton("Start review", hero.onStart) }
        }
        is PulseHero.FirstDay -> BriefingCard {
            BriefingLabel(if (hero.calendarLine != null) "FROM YOUR CALENDAR" else "WORK, IN ONE PLACE")
            Text(
                hero.calendarLine ?: "Record a meeting and tomorrow's Pulse knows what was decided and promised.",
                style = MM.type.title, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs)
            )
            Row(Modifier.padding(top = MM.space.l), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                BriefingButton("Record", hero.onRecord, primary = true)
                BriefingChip("Write a note", onClick = hero.onWrite)
                BriefingChip("Inbox", onClick = hero.onInbox)
            }
        }
    }
}

@Composable
private fun AskSection(onAsk: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
                .background(MM.colors.surface, MM.radius.card)
                .clickable(role = Role.Button, onClick = onAsk)
                .padding(horizontal = MM.space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Icon(Icons.Rounded.Search, null, tint = MM.colors.inkMuted)
            Text("Ask about your work…", style = MM.type.body, color = MM.colors.inkMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            SuggestionChip("What am I waiting for?", onAsk)
            SuggestionChip("What did we decide?", onAsk)
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = MMSize.minTouch)
            .background(MM.colors.accentWash, MM.radius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MM.space.m),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MM.type.caption, color = MM.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
