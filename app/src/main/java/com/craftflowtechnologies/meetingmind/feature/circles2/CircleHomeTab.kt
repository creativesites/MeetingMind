package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.WorkStatus
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

/** Moves between the Circle's places from inside Home. */
class HomeNav(
    /** Opens Updates, optionally filtered ("Prayer", "Decisions", "Saved" and so on, as Updates names them). */
    val onOpenUpdates: (String?) -> Unit = {},
    val onOpenPeople: () -> Unit = {},
    val onCatchUp: () -> Unit = {}
)

/** Circle Home: Faith's living room or Work's status board. */
@Composable
fun CircleHomeTab(state: CircleHomeUiState, now: Long, actions: FeedActions, nav: HomeNav, modifier: Modifier = Modifier) {
    val circle = state.circle ?: return
    if (circle.isWork) WorkOverview(circle, state, now, actions, nav, modifier)
    else FaithHome(circle, state, now, actions, nav, modifier)
}

// ---------------------------------------------------------------- Faith Home

/**
 * Faith Home (founder decision D4): today's devotional, then the people to hold in prayer, gatherings, a testimony,
 * the shared reading and the faces of the circle. What you can do for someone comes before anything chronological.
 */
@Composable
private fun FaithHome(circle: Circle, state: CircleHomeUiState, now: Long, actions: FeedActions, nav: HomeNav, modifier: Modifier) {
    val d = remember(state.posts, state.events, state.members, state.unreadFeedCount, now) { CircleHomeDigest.faith(state, now) }
    val allowed = circle.settings.allowedTypes
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xl),
        verticalArrangement = Arrangement.spacedBy(MM.space.l)
    ) {
        state.failure?.let { f -> item(key = "failure") { StatusLine(StatusKind.Warning, f.message) } }
        item(key = "banner") { HomeBanner(circle, d.faces, state.myUid, nav) }
        if (d.newSinceVisit > 0) item(key = "since") { SinceStrip(d.newSinceVisit, state.lastVisitAt, now, nav.onCatchUp) }
        if (state.isAdmin && state.pending.isNotEmpty()) item(key = "pending") {
            StatusLine(StatusKind.Info, "${state.pending.size} waiting for your approval", actionLabel = "Review", onAction = { nav.onOpenUpdates(null) })
        }
        if (d.quiet) {
            item(key = "welcome") { FaithWelcome(circle, actions) }
        } else {
            item(key = "devotional") { DevotionalHero(d.devotional, PostType.Devotional in allowed, actions, now) }
            item(key = "prayer") { HoldInPrayer(d.prayers, PostType.Prayer in allowed, actions, nav, now) }
            item(key = "gatherings") {
                Gatherings(d.gatherings, now, actions.onOpenEvent, if (state.canModerate) actions.onNewEvent else null, word = "Gatherings", emptyLine = "No gatherings planned yet.")
            }
            d.testimony?.let { t -> item(key = "testimony") { TestimonyCard(t, actions, now) } }
            d.reading?.let { r -> item(key = "reading") { ReadingCard(r, actions, now) } }
        }
        item(key = "faces") { FacesRow(circle, d.faces, state.myUid, nav, actions) }
    }
}

@Composable
private fun HomeBanner(circle: Circle, faces: List<Member>, myUid: String?, nav: HomeNav) {
    Box(
        Modifier.fillMaxWidth().height(120.dp).clip(MM.radius.card)
    ) {
        if (circle.coverPath != null) {
            MediaImage(circle.id, circle.coverPath, Modifier.fillMaxSize(), "Cover photo for ${circle.name}")
        } else {
            RippleCover(circle.id, Modifier.fillMaxSize())
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(MM.space.m), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (circle.description.isNotBlank()) {
                Text(circle.description, style = MM.type.secondary, color = com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = nav.onOpenPeople)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    faces.take(5).forEach { m -> MemberAvatar(circle.id, m.uid, m.displayName, m.avatarVersion, size = 28.dp) }
                }
                Spacer(Modifier.width(MM.space.s))
                Text(facesLine(faces, myUid), style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors.White)
            }
        }
    }
}

/** "You, Grace and 12 others". Names, not counts of what anyone did. */
internal fun facesLine(faces: List<Member>, myUid: String?): String {
    val others = faces.filter { it.uid != myUid }
    val includesMe = faces.any { it.uid == myUid }
    val named = others.take(if (includesMe) 1 else 2).map { it.displayName.substringBefore(' ').ifBlank { "Someone" } }
    val rest = others.size - named.size
    val head = (if (includesMe) listOf("You") else emptyList()) + named
    return when {
        head.isEmpty() -> "Nobody here yet"
        rest <= 0 -> if (head.size == 1) head[0] else head.dropLast(1).joinToString(", ") + " and " + head.last()
        else -> head.joinToString(", ") + " and $rest " + if (rest == 1) "other" else "others"
    }
}

@Composable
private fun SinceStrip(count: Int, lastVisit: Long, now: Long, onCatchUp: () -> Unit) {
    Surface(
        onClick = onCatchUp,
        shape = MM.radius.card,
        color = MM.colors.accentWash,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 new since you were here" else "$count new since you were here",
                    style = MM.type.bodyStrong, color = MM.colors.ink
                )
                if (lastVisit > 0L) Text("Last visit ${relativeTime(now, lastVisit)}", style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            Text("Catch up", style = MM.type.bodyStrong, color = MM.colors.accent)
            Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WarmCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MM.radius.card,
        color = CirclesPalette.WarmBg,
        border = BorderStroke(MMSize.hairline, CirclesPalette.WarmBorder),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s), content = content)
    }
}

@Composable
private fun Overline(text: String, color: Color = CirclesPalette.WarmInk) {
    Text(text.uppercase(), style = MM.type.overline, color = color, modifier = Modifier.semantics { heading() })
}

@Composable
private fun DevotionalHero(post: FeedPost?, canShare: Boolean, actions: FeedActions, now: Long) {
    if (post == null) {
        if (!canShare) return
        WarmCard {
            Overline("Today's devotional")
            Text("Nobody has shared a devotional today.", style = MM.type.body, color = MM.colors.ink)
            TextAction("Share one", { actions.onStartPost(PostType.Devotional) })
        }
        return
    }
    val p = post.post
    WarmCard(onClick = { actions.onOpenThread(p) }) {
        Overline(if (now - p.createdAt < 86_400_000L) "Today's devotional" else "Devotional")
        p.verseRef?.takeIf { it.isNotBlank() }?.let { ref ->
            val parsed = remember(ref) { ScriptureReferenceParser.parse(ref) }
            Text(
                ref, style = MM.type.bodyStrong, color = CirclesPalette.WarmInk,
                modifier = if (parsed != null) Modifier.clickable { actions.onOpenVerse(parsed) } else Modifier
            )
        }
        Text(p.body, style = MM.type.scripture, color = MM.colors.ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
        Text("${p.authorName ?: "Someone in the circle"} · ${relativeTime(now, p.createdAt)}", style = MM.type.caption, color = MM.colors.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
            VerbChip(
                if (post.myReaction == ReactionKind.Amen) "Amen ✓" else "Amen",
                selected = post.myReaction == ReactionKind.Amen,
                onClick = { actions.onReact(p, ReactionKind.Amen) }
            )
            VerbChip("Reflect", selected = false, onClick = { actions.onOpenThread(p) })
            Spacer(Modifier.weight(1f))
            if (p.counts.comments > 0) Text("${p.counts.comments} reflections", style = MM.type.caption, color = MM.colors.inkSecondary)
        }
    }
}

/** A one-tap verb: "I'm praying", "Amen", "Seen it". [selected] shows it's done; tapping again undoes where the server allows. */
@Composable
internal fun VerbChip(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MM.radius.pill,
        color = if (selected) MM.colors.accent else MM.colors.surface,
        border = if (selected) null else BorderStroke(MMSize.hairline, MM.colors.line),
        modifier = Modifier.heightIn(min = 36.dp)
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MM.type.bodyStrong, color = if (selected) MM.colors.onAccent else MM.colors.ink)
        }
    }
}

@Composable
private fun HoldInPrayer(prayers: List<FeedPost>, canAsk: Boolean, actions: FeedActions, nav: HomeNav, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Hold in prayer", actionLabel = if (prayers.isNotEmpty()) "All requests" else null, onAction = { nav.onOpenUpdates("Prayer") })
        if (prayers.isEmpty()) {
            Text("No open prayer requests right now.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            if (canAsk) TextAction("Ask for prayer", { actions.onStartPost(PostType.Prayer) })
        }
        prayers.forEach { fp ->
            val p = fp.post
            MMCard(onClick = { actions.onOpenThread(p) }) {
                Text(
                    if (p.anonymous) "Someone in the circle" else (p.authorName ?: "Member"),
                    style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.inkSecondary
                )
                Text(p.body, style = MM.type.body, color = MM.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(MM.space.s))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    VerbChip(
                        if (fp.prayedByMe) "Praying ✓" else "I'm praying",
                        selected = fp.prayedByMe,
                        enabled = !fp.prayedByMe,
                        onClick = { actions.onPray(p) }
                    )
                    Spacer(Modifier.weight(1f))
                    val n = p.counts.prayed
                    if (n > 0) Text(if (n == 1) "1 praying" else "$n praying", style = MM.type.caption, color = MM.colors.inkSecondary)
                    Text(relativeTime(now, p.createdAt), style = MM.type.caption, color = MM.colors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun Gatherings(
    events: List<CircleEvent>,
    now: Long,
    onOpen: (CircleEvent) -> Unit,
    onNew: (() -> Unit)?,
    word: String,
    emptyLine: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader(word, actionLabel = if (onNew != null) "Plan one" else null, onAction = onNew)
        if (events.isEmpty()) Text(emptyLine, style = MM.type.secondary, color = MM.colors.inkSecondary)
        events.forEach { e -> EventRow(e, now, onOpen) }
    }
}

@Composable
private fun EventRow(e: CircleEvent, now: Long, onOpen: (CircleEvent) -> Unit) {
    val zone = remember(e.timeZone) { runCatching { java.time.ZoneId.of(e.timeZone) }.getOrDefault(java.time.ZoneId.systemDefault()) }
    val start = remember(e.startsAt, zone) { java.time.Instant.ofEpochMilli(e.startsAt).atZone(zone) }
    MMCard(onClick = { onOpen(e) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Surface(shape = MM.radius.card, color = MM.colors.accentWash, modifier = Modifier.size(52.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        start.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).uppercase(),
                        style = MM.type.overline, color = MM.colors.accent
                    )
                    Text(start.dayOfMonth.toString(), style = MM.type.heading, color = MM.colors.ink)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val time = java.time.format.DateTimeFormatter.ofPattern("EEE h:mm a").format(start)
                Text("$time · ${CircleHomeDigest.daysUntil(now, e.startsAt)}", style = MM.type.caption, color = MM.colors.inkSecondary)
                val where = e.location.ifBlank { if (e.onlineUrl != null) "Online" else "" }
                if (where.isNotBlank()) Text(where, style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkMuted)
        }
    }
}

@Composable
private fun TestimonyCard(post: FeedPost, actions: FeedActions, now: Long) {
    val p = post.post
    WarmCard(onClick = { actions.onOpenThread(p) }) {
        Overline(if (p.type == PostType.Testimony) "Testimony" else "Prayer answered")
        Text(p.body, style = MM.type.scripture, color = MM.colors.ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text("${if (p.anonymous) "Someone in the circle" else (p.authorName ?: "Member")} · ${relativeTime(now, p.createdAt)}", style = MM.type.caption, color = MM.colors.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            VerbChip(
                if (post.myReaction == ReactionKind.Amen) "Amen ✓" else "Amen",
                selected = post.myReaction == ReactionKind.Amen,
                onClick = { actions.onReact(p, ReactionKind.Amen) }
            )
            VerbChip(
                if (post.myReaction == ReactionKind.Celebrate) "Celebrating ✓" else "Celebrate",
                selected = post.myReaction == ReactionKind.Celebrate,
                onClick = { actions.onReact(p, ReactionKind.Celebrate) }
            )
        }
    }
}

@Composable
private fun ReadingCard(post: FeedPost, actions: FeedActions, now: Long) {
    val p = post.post
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Reading together")
        MMCard(onClick = { actions.onOpenThread(p) }) {
            p.verseRef?.takeIf { it.isNotBlank() }?.let { Text(it, style = MM.type.bodyStrong, color = MM.colors.accent) }
            Text(p.body, style = MM.type.body, color = MM.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${p.authorName ?: "Member"} · ${relativeTime(now, p.createdAt)}", style = MM.type.caption, color = MM.colors.inkSecondary)
        }
    }
}

@Composable
private fun FacesRow(circle: Circle, faces: List<Member>, myUid: String?, nav: HomeNav, actions: FeedActions) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader(if (circle.isWork) "The team" else "Faces", actionLabel = "Everyone", onAction = nav.onOpenPeople)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            faces.take(12).forEach { m ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).clickable { actions.onMemberClick(m.uid) }
                ) {
                    MemberAvatar(circle.id, m.uid, m.displayName, m.avatarVersion, size = 48.dp)
                    Text(
                        if (m.uid == myUid) "You" else m.displayName.substringBefore(' '),
                        style = MM.type.caption, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val sub = m.title?.takeIf { it.isNotBlank() } ?: if (m.role.isAdmin) "Leader" else null
                    if (sub != null) Text(sub, style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FaithWelcome(circle: Circle, actions: FeedActions) {
    val allowed = circle.settings.allowedTypes
    WarmCard {
        Overline("Welcome")
        Text("This is ${circle.name}'s living room.", style = MM.type.heading, color = MM.colors.ink)
        Text(
            "Prayer requests, gatherings and testimonies will gather here. Start with something small.",
            style = MM.type.body, color = MM.colors.inkSecondary
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            listOf(PostType.Prayer, PostType.Encouragement, PostType.Devotional, PostType.Testimony)
                .filter { it in allowed }
                .forEach { t -> VerbChip(t.verb, selected = false, onClick = { actions.onStartPost(t) }) }
        }
    }
}

// ---------------------------------------------------------------- Work Overview

/**
 * Work Overview: a status board. The runway to the next milestone, what needs the team (one button each),
 * who owes what this week, the latest brief, what's next, and the meetings it all came from.
 */
@Composable
private fun WorkOverview(circle: Circle, state: CircleHomeUiState, now: Long, actions: FeedActions, nav: HomeNav, modifier: Modifier) {
    val w = remember(state.posts, state.events, state.myUid, state.unreadFeedCount, now) { CircleHomeDigest.work(state, now) }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xl),
        verticalArrangement = Arrangement.spacedBy(MM.space.l)
    ) {
        state.failure?.let { f -> item(key = "failure") { StatusLine(StatusKind.Warning, f.message) } }
        item(key = "runway") { RunwayHero(w, now, actions, state.canModerate) }
        if (w.newSinceVisit > 0) item(key = "since") { SinceStrip(w.newSinceVisit, state.lastVisitAt, now, nav.onCatchUp) }
        if (state.isAdmin && state.pending.isNotEmpty()) item(key = "pending") {
            StatusLine(StatusKind.Info, "${state.pending.size} waiting for your approval", actionLabel = "Review", onAction = { nav.onOpenUpdates(null) })
        }
        if (w.quiet) {
            item(key = "welcome") { WorkWelcome(circle, actions) }
        } else {
            item(key = "needs") { NeedsTheTeam(w.needsTeam, state, actions, now) }
            item(key = "due") { DueThisWeek(w.dueThisWeek, state, actions, now) }
            w.latestBrief?.let { b -> item(key = "brief") { LatestBrief(b, actions, now) } }
            if (w.laterEvents.isNotEmpty()) item(key = "next") {
                Gatherings(w.laterEvents, now, actions.onOpenEvent, null, word = "Next up", emptyLine = "")
            }
            item(key = "decisions") {
                MMCard(onClick = { nav.onOpenUpdates("Decisions") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Decision ledger", style = MM.type.bodyStrong, color = MM.colors.ink)
                            Text(
                                if (w.decisions == 1) "1 decision on record" else "${w.decisions} decisions on record",
                                style = MM.type.caption, color = MM.colors.inkSecondary
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkMuted)
                    }
                }
            }
            if (w.sourceMeetings.isNotEmpty()) item(key = "meetings") {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    SectionHeader("From meetings")
                    w.sourceMeetings.forEach { (label, n) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Meeting $label", style = MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f))
                            Text(if (n == 1) "1 post" else "$n posts", style = MM.type.caption, color = MM.colors.inkSecondary)
                        }
                    }
                }
            }
        }
        item(key = "faces") { FacesRow(circle, CircleHomeDigestFaces(state.members), state.myUid, nav, actions) }
    }
}

private fun CircleHomeDigestFaces(members: List<Member>): List<Member> =
    members.sortedWith(compareBy<Member> { if (it.role.isAdmin) 0 else 1 }.thenBy { it.displayName.lowercase() })

@Composable
private fun RunwayHero(w: CircleHomeDigest.Work, now: Long, actions: FeedActions, canModerate: Boolean) {
    Surface(shape = MM.radius.card, color = MM.colors.accent, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            val next = w.nextUp
            Text("RUNWAY", style = MM.type.overline, color = MM.colors.onAccent.copy(alpha = 0.8f))
            if (next != null) {
                Text(next.title, style = MM.type.title, color = MM.colors.onAccent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    CircleHomeDigest.daysUntil(now, next.startsAt).replaceFirstChar { it.uppercase() },
                    style = MM.type.bodyStrong, color = MM.colors.onAccent.copy(alpha = 0.9f)
                )
            } else {
                Text("No review or milestone scheduled", style = MM.type.heading, color = MM.colors.onAccent)
            }
            val total = w.tasksDone + w.tasksOpen
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { w.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MM.radius.pill),
                    color = MM.colors.onAccent,
                    trackColor = MM.colors.onAccent.copy(alpha = 0.25f)
                )
                Text("${w.tasksDone} of $total actions done", style = MM.type.caption, color = MM.colors.onAccent.copy(alpha = 0.9f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                if (next != null) {
                    Surface(onClick = { actions.onOpenEvent(next) }, shape = MM.radius.pill, color = MM.colors.onAccent) {
                        Text("Open", style = MM.type.bodyStrong, color = MM.colors.accent, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                if (canModerate) {
                    Surface(onClick = actions.onNewEvent, shape = MM.radius.pill, color = MM.colors.onAccent.copy(alpha = 0.18f)) {
                        Text("Schedule", style = MM.type.bodyStrong, color = MM.colors.onAccent, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NeedsTheTeam(items: List<FeedPost>, state: CircleHomeUiState, actions: FeedActions, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Needs the team", count = items.size.takeIf { it > 0 })
        if (items.isEmpty()) {
            Text("Nothing is waiting on the team. Blockers, new decisions and unowned actions show up here.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
        items.forEach { fp ->
            val p = fp.post
            MMCard(onClick = { actions.onOpenThread(p) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PostTypeBadge(p.type)
                    Text(
                        "${p.authorName ?: "Member"} · ${relativeTime(now, p.createdAt)}",
                        style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.weight(1f)
                    )
                }
                Text(p.body, style = MM.type.body, color = MM.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                p.meta?.blockedOn?.takeIf { it.isNotBlank() }?.let { Text("Waiting on $it", style = MM.type.caption, color = MM.colors.danger) }
                Spacer(Modifier.height(MM.space.xs))
                when (p.type) {
                    PostType.Decision -> VerbChip(
                        if (fp.myReaction == ReactionKind.Ack) "Seen ✓" else "Seen it",
                        selected = fp.myReaction == ReactionKind.Ack,
                        onClick = { actions.onReact(p, ReactionKind.Ack) }
                    )
                    PostType.Blocker -> if (fp.mine || state.canModerate) {
                        VerbChip("Mark resolved", selected = false, onClick = { actions.onSetWorkStatus(p, WorkStatus.Done) })
                    } else {
                        VerbChip("I can help", selected = false, onClick = { actions.onOpenThread(p) })
                    }
                    else -> VerbChip("Take it", selected = false, onClick = { actions.onOpenThread(p) })
                }
            }
        }
    }
}

@Composable
private fun DueThisWeek(groups: List<Pair<String, List<FeedPost>>>, state: CircleHomeUiState, actions: FeedActions, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Due this week")
        if (groups.isEmpty()) {
            Text("No actions due in the next seven days.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        }
        groups.forEach { (owner, tasks) ->
            MMCard {
                Text(owner, style = MM.type.bodyStrong, color = MM.colors.ink)
                tasks.forEach { fp ->
                    val p = fp.post
                    val canComplete = fp.mine || state.canModerate || (state.myUid != null && p.meta?.assigneeUid == state.myUid)
                    val due = p.meta?.dueAt
                    val overdue = due != null && due < now
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { actions.onOpenThread(p) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        val done = p.workStatus == WorkStatus.Done
                        Surface(
                            onClick = { if (canComplete && !done) actions.onSetWorkStatus(p, WorkStatus.Done) },
                            enabled = canComplete && !done,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(36.dp).semantics { contentDescription = if (done) "Done" else "Mark done" }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                    tint = if (done) MM.colors.success else if (canComplete) MM.colors.accent else MM.colors.inkMuted
                                )
                            }
                        }
                        Text(p.body.lineSequence().first(), style = MM.type.body, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val dueText = p.meta?.dueText?.takeIf { it.isNotBlank() } ?: due?.let { CircleHomeDigest.daysUntil(now, it) }.orEmpty()
                        if (dueText.isNotBlank()) Text(
                            if (overdue) "Overdue" else dueText,
                            style = MM.type.caption.copy(fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (overdue) MM.colors.danger else MM.colors.inkSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestBrief(fp: FeedPost, actions: FeedActions, now: Long) {
    val p = fp.post
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Latest brief")
        MMCard(onClick = { actions.onOpenThread(p) }) {
            Text(
                listOfNotNull(p.meta?.meetingDate?.takeIf { it.isNotBlank() }?.let { "Meeting $it" }, p.authorName, relativeTime(now, p.createdAt)).joinToString(" · "),
                style = MM.type.caption, color = MM.colors.inkSecondary
            )
            Text(p.body, style = MM.type.body, color = MM.colors.ink, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                VerbChip(
                    if (fp.myReaction == ReactionKind.Ack) "Seen ✓" else "Seen it",
                    selected = fp.myReaction == ReactionKind.Ack,
                    onClick = { actions.onReact(p, ReactionKind.Ack) }
                )
                TextAction("Read brief", { actions.onOpenThread(p) })
                Spacer(Modifier.weight(1f))
                // Briefs shared from a meeting wrap-up are written by AI, so they can be reported in the app (Play policy).
                if (p.meta?.sourceKind == "meeting") {
                    com.craftflowtechnologies.meetingmind.core.aireport.ReportAiAction(
                        source = { com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource(com.craftflowtechnologies.meetingmind.core.aireport.AiFeature.CIRCLE_BRIEF, p.body) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkWelcome(circle: Circle, actions: FeedActions) {
    val allowed = circle.settings.allowedTypes
    MMCard {
        Text("Set up the board", style = MM.type.heading, color = MM.colors.ink)
        Text(
            "Overview fills in as the team records decisions, assigns actions and shares briefs. Share a meeting wrap-up from Work to start it in one step.",
            style = MM.type.body, color = MM.colors.inkSecondary
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            listOf(PostType.Decision, PostType.Task, PostType.Brief, PostType.Standup)
                .filter { it in allowed }
                .forEach { t -> VerbChip(t.verb, selected = false, onClick = { actions.onStartPost(t) }) }
        }
    }
}

// ---------------------------------------------------------------- Catch-up deck

/**
 * The catch-up deck: at most five cards ranked by [CircleHomeDigest.catchUp], each with one verb, ending in
 * "You're caught up". Swipe or use Next and Back. Closing it marks the posts as seen on this phone.
 */
@Composable
fun CatchUpDeckSheet(
    cards: List<CircleHomeDigest.DeckCard>,
    circle: Circle,
    now: Long,
    actions: FeedActions,
    onDone: () -> Unit
) {
    val pages = cards.size + 1
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    CircleSheet(onDone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Catch up", style = MM.type.title, color = MM.colors.ink, modifier = Modifier.weight(1f))
            Text(
                if (pager.currentPage < cards.size) "${pager.currentPage + 1} of ${cards.size}" else "Done",
                style = MM.type.caption, color = MM.colors.inkSecondary
            )
        }
        HorizontalPager(state = pager, pageSpacing = MM.space.m, modifier = Modifier.fillMaxWidth().height(300.dp)) { page ->
            if (page >= cards.size) {
                Column(
                    Modifier.fillMaxSize().background(MM.colors.accentWash, MM.radius.card).padding(MM.space.l),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.Check, null, tint = MM.colors.accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(MM.space.s))
                    Text("You're caught up", style = MM.type.heading, color = MM.colors.ink)
                    Text(
                        if (circle.isWork) "Everything new on the board has been seen." else "Thank you for being here.",
                        style = MM.type.body, color = MM.colors.inkSecondary
                    )
                }
            } else {
                DeckCardView(cards[page], circle, now, actions)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) {
                SecondaryButton("Back", { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } })
            }
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < pages - 1) {
                PrimaryButton("Next", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
            } else {
                PrimaryButton("Done", onDone)
            }
        }
    }
}

@Composable
private fun DeckCardView(card: CircleHomeDigest.DeckCard, circle: Circle, now: Long, actions: FeedActions) {
    val warm = !circle.isWork && card.post?.post?.type in setOf(PostType.Prayer, PostType.Testimony, PostType.Devotional)
    Column(
        Modifier.fillMaxSize()
            .background(if (warm) CirclesPalette.WarmBg else MM.colors.surface, MM.radius.card)
            .padding(MM.space.l),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        Text(card.reason.uppercase(), style = MM.type.overline, color = if (warm) CirclesPalette.WarmInk else MM.colors.accent)
        val fp = card.post
        val e = card.event
        if (fp != null) {
            val p = fp.post
            Text(
                "${if (p.anonymous) "Someone in the circle" else (p.authorName ?: "Member")} · ${relativeTime(now, p.createdAt)}",
                style = MM.type.caption, color = MM.colors.inkSecondary
            )
            Text(
                p.body, style = if (warm) MM.type.scripture else MM.type.body, color = MM.colors.ink,
                maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            when {
                p.type == PostType.Prayer && !p.isResolved -> VerbChip(
                    if (fp.prayedByMe) "Praying ✓" else "I'm praying", fp.prayedByMe, { actions.onPray(p) }, enabled = !fp.prayedByMe
                )
                p.type == PostType.Decision || p.type == PostType.Brief -> VerbChip(
                    if (fp.myReaction == ReactionKind.Ack) "Seen ✓" else "Seen it", fp.myReaction == ReactionKind.Ack, { actions.onReact(p, ReactionKind.Ack) }
                )
                p.type == PostType.Testimony || (p.type == PostType.Prayer && p.isResolved) -> VerbChip(
                    if (fp.myReaction == ReactionKind.Amen) "Amen ✓" else "Amen", fp.myReaction == ReactionKind.Amen, { actions.onReact(p, ReactionKind.Amen) }
                )
                else -> VerbChip("Open", false, { actions.onOpenThread(p) })
            }
        } else if (e != null) {
            Text(e.title, style = MM.type.heading, color = MM.colors.ink)
            Text(CircleHomeDigest.daysUntil(now, e.startsAt).replaceFirstChar { it.uppercase() }, style = MM.type.body, color = MM.colors.inkSecondary)
            if (e.location.isNotBlank()) Text(e.location, style = MM.type.caption, color = MM.colors.inkMuted)
            Spacer(Modifier.weight(1f))
            VerbChip("Answer", false, { actions.onOpenEvent(e) })
        }
    }
}
