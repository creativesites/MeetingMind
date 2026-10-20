package com.craftflowtechnologies.meetingmind.feature.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import com.craftflowtechnologies.meetingmind.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.MeetingMindMark
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.notes.LibraryChip
import com.craftflowtechnologies.meetingmind.core.notes.LibraryItem
import com.craftflowtechnologies.meetingmind.core.notes.LibraryQuery
import com.craftflowtechnologies.meetingmind.core.notes.NoteLabels
import com.craftflowtechnologies.meetingmind.core.notes.NoteSort
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// The Notes "home" layout (root, list view): top bar, greeting with Zuri, the Latest meeting hero, then the library.
// Everything here uses MM tokens only, so light/dark and all six accents work without extra code.

/** Wordmark on the left; search and notebooks on the right. */
@Composable
internal fun NotesHomeTopBar(
    onSearch: () -> Unit, onFolders: () -> Unit,
    archived: Int, trash: Int, onArchive: () -> Unit, onTrash: () -> Unit
) {
    val c = MM.colors
    Row(Modifier.fillMaxWidth().padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        MeetingMindMark(size = 32.dp)
        Row(Modifier.weight(1f).padding(start = MM.space.s)) {
            Text("Meeting", style = MM.type.title, color = c.ink)
            Text("Mind", style = MM.type.title, color = c.accent)
        }
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search notes", tint = c.inkSecondary) }
        IconButton(onClick = onFolders) { Icon(Icons.Rounded.FolderOpen, "Show notebooks", tint = c.inkSecondary) }
        if (archived > 0 || trash > 0) LibraryMenu(archived, trash, onArchive, onTrash)
    }
}

/** The hour's greeting, and whether Zuri should be asleep (late at night). */
private class GreetingInfo(val greeting: String, val lateNight: Boolean)

@Composable
private fun rememberGreeting(): GreetingInfo = remember {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    GreetingInfo(
        greeting = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" },
        lateNight = hour >= 22 || hour < 5
    )
}

@Composable
private fun GreetingText(greeting: String, modifier: Modifier = Modifier) {
    Text(greeting, style = MM.type.title, color = MM.colors.ink, modifier = modifier)
}

/** Greeting on the left, Zuri and a speech bubble on the right, side by side. Used when the Latest meeting hero is not shown. */
@Composable
internal fun NotesGreeting() {
    val info = rememberGreeting()
    val companionShown = rememberCompanionVisible(CompanionPage.HOME)
    Row(Modifier.fillMaxWidth().padding(top = MM.space.l), verticalAlignment = Alignment.Top) {
        GreetingText(info.greeting, Modifier.weight(1f))
        if (companionShown) {
            Column(Modifier.padding(start = MM.space.s).widthIn(max = ZURI_COLUMN_WIDTH), horizontalAlignment = Alignment.End) {
                CompanionZuri(info.lateNight)
            }
        }
    }
}

/**
 * How much of Zuri's height sits behind the hero card: the lower 38% is covered, so the face and the speech bubble
 * stay visible. Tune on a device; raise it to hide more of Zuri's body, lower it to show more.
 */
private const val ZURI_OVERLAP = 0.38f
private val ZURI_SIZE = 104.dp
private val ZURI_COLUMN_WIDTH = 132.dp

@Composable
private fun CompanionZuri(lateNight: Boolean) =
    ZuriSlot(CompanionPage.HOME, ZURI_SIZE, state = if (lateNight) CompanionState.SLEEPY else CompanionState.IDLE)

/**
 * The greeting, Zuri and the Latest meeting card as one block. Zuri sits top-end; the card is placed last, so it covers
 * Zuri's lower body and takes every tap over that overlap. The card's top is measured from the greeting and from Zuri,
 * never a fixed height, so the greeting text cannot run under the card at any font scale.
 */
@Composable
internal fun HeroWithZuri(
    greetingSlot: @Composable () -> Unit,
    bubbleSlot: @Composable () -> Unit,
    zuriSlot: @Composable () -> Unit,
    heroSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // Read the MM token here, in composition: the measure lambda below is not composable.
    val gapDp = MM.space.m
    Layout(
        // Each slot is wrapped in its own box so it always yields exactly one child. A slot with nothing to
        // show (no latest meeting, say) would otherwise drop out of the list and break the destructuring below.
        content = { Box { greetingSlot() }; Box { bubbleSlot() }; Box { zuriSlot() }; Box { heroSlot() } },
        modifier = modifier
    ) { measurables, constraints ->
        val (greetingM, bubbleM, zuriM, heroM) = measurables
        val width = constraints.maxWidth
        val gap = gapDp.roundToPx()
        val columnWidth = ZURI_COLUMN_WIDTH.roundToPx()
        val greeting = greetingM.measure(Constraints(maxWidth = (width * 0.6f).roundToInt()))
        val bubble = bubbleM.measure(Constraints(maxWidth = columnWidth))
        val zuri = zuriM.measure(Constraints(maxWidth = columnWidth))
        val zuriTop = bubble.height + gap
        val zuriVisibleBottom = zuriTop + (zuri.height * (1f - ZURI_OVERLAP)).roundToInt()
        val cardTop = maxOf(greeting.height + gap, zuriVisibleBottom)
        val hero = heroM.measure(Constraints.fixedWidth(width))
        layout(width, cardTop + hero.height) {
            greeting.placeRelative(0, 0)
            bubble.placeRelative(width - bubble.width, 0)
            zuri.placeRelative(width - zuri.width, zuriTop)
            hero.placeRelative(0, cardTop)
        }
    }
}

/** The overlap block for the home list: greeting, bubble, Zuri and the hero card. Needs the companion to be shown. */
@Composable
internal fun HomeHeroBlock(item: LibraryItem, tiles: List<MeetingTile>, onOpen: () -> Unit, onTile: (TileAction) -> Unit) {
    val info = rememberGreeting()
    HeroWithZuri(
        greetingSlot = { GreetingText(info.greeting) },
        bubbleSlot = {},
        zuriSlot = { CompanionZuri(info.lateNight) },
        heroSlot = { LatestMeetingCard(item, tiles, onOpen, onTile) },
        modifier = Modifier.fillMaxWidth().padding(top = MM.space.l)
    )
}


/**
 * The one hero of the screen: the most recent recorded Work note. An illustrated sky sits behind it and its two most
 * useful insights sit on it as solid cards. [HeroWithZuri] places the companion above its top edge.
 */
@Composable
internal fun LatestMeetingCard(
    item: LibraryItem, tiles: List<MeetingTile>, onOpen: () -> Unit, onTile: (TileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val note = item.note
    val whenMs = note.eventDate ?: note.createdAt
    val date = remember(whenMs) { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(whenMs)) }
    val meta = listOfNotNull(date, item.durationMs?.let(NoteLabels::duration)).joinToString("  ·  ")
    val cards = heroCards(tiles)
    // The sunset image sits behind the card under a scrim, so its type is fixed white in both themes.
    Box(
        modifier.fillMaxWidth().heightIn(min = 300.dp).clip(MM.radius.card).background(c.accentWash)
            .clickable(role = Role.Button, onClick = onOpen)
    ) {
        Image(painterResource(R.drawable.work_header_sunset), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.75f)))))
        Column(Modifier.fillMaxWidth().padding(MM.space.l)) {
            Row(
                Modifier.background(c.surface.copy(alpha = 0.8f), MM.radius.pill).padding(horizontal = MM.space.m, vertical = MM.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Groups, null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                Spacer(Modifier.width(MM.space.xs))
                Text("Latest meeting", style = MM.type.caption, color = c.ink)
            }
            // Room for the companion, which sits in the top-right corner above this card.
            Spacer(Modifier.height(96.dp))
            Text(note.title.ifBlank { "Untitled note" }, style = MM.type.title, color = FixedWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MM.type.secondary, color = FixedWhite.copy(alpha = 0.85f), modifier = Modifier.padding(top = MM.space.xs))
            if (cards.isNotEmpty()) {
                Spacer(Modifier.height(MM.space.l))
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    cards.forEach { tile ->
                        InsightCard(tile.action.icon(), tile.label, tile.subtitle, { onTile(tile.action) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun TileAction.icon(): ImageVector = when (this) {
    TileAction.KEY_POINTS -> Icons.Rounded.Lightbulb
    TileAction.ACTION_ITEMS -> Icons.Rounded.CheckCircle
    TileAction.SUMMARY -> Icons.Rounded.Description
    TileAction.ASSISTANT -> AiMark
}

@Composable
private fun InsightCard(icon: ImageVector, label: String, sub: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MM.colors
    Column(
        modifier.clip(MM.radius.card).background(c.surfaceRaised, MM.radius.card)
            .clickable(role = Role.Button, onClick = onClick).padding(MM.space.m)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(c.accentWash, MM.radius.small), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
            }
            Spacer(Modifier.width(MM.space.xs))
            Text(label, style = MM.type.caption, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(sub, style = MM.type.secondary, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
    }
}

/** "Your Notes" with filters, sort and New. */
@Composable
internal fun NotesSectionHeader(extraFilters: Int, sort: NoteSort, onSort: (NoteSort) -> Unit, onFilters: () -> Unit, onNew: () -> Unit) {
    val c = MM.colors
    Row(Modifier.fillMaxWidth().padding(top = MM.space.xl, bottom = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        Text("Your Notes", style = MM.type.title, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
        IconButton(onClick = onFilters) {
            Icon(
                Icons.Rounded.Tune, if (extraFilters > 0) "Filters, $extraFilters on" else "Filters",
                tint = if (extraFilters > 0) c.accent else c.inkSecondary
            )
        }
        SortMenu(sort, onSort)
        PrimaryButton("New", onNew, leadingIcon = Icons.Rounded.Add)
    }
}

/** The chips row with icons. Which chips appear still comes from [LibraryChip]; types and tags live behind the filter button. */
@Composable
internal fun HomeChips(
    query: LibraryQuery, showWork: Boolean, showFaith: Boolean,
    onAll: () -> Unit, onChip: (LibraryChip) -> Unit, modifier: Modifier = Modifier
) {
    val extra = query.types.size + if (query.tagId != null) 1 else 0
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        IconChip("All", Icons.Rounded.GridView, selected = query.chips.isEmpty() && extra == 0, onClick = onAll)
        LibraryChip.entries
            .filter { (it != LibraryChip.WORK || showWork) && ((it != LibraryChip.FAITH && it != LibraryChip.BIBLE) || showFaith) }
            .forEach { chip ->
                val icon = when (chip) {
                    LibraryChip.WORK -> Icons.Rounded.BusinessCenter
                    LibraryChip.FAITH -> Icons.Rounded.AutoStories
                    LibraryChip.BIBLE -> Icons.Rounded.Book
                    else -> Icons.Rounded.Notes
                }
                IconChip(chip.label, icon, selected = chip in query.chips, onClick = { onChip(chip) })
            }
    }
}

@Composable
private fun IconChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    Row(
        Modifier.heightIn(min = MMSize.minTouch).clip(MM.radius.pill)
            .background(if (selected) c.accentWash else Color.Transparent, MM.radius.pill)
            .border(1.dp, if (selected) c.accent else c.line, MM.radius.pill)
            .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = MM.space.l),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) c.accent else c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
        Spacer(Modifier.width(MM.space.s))
        Text(label, style = MM.type.caption, color = if (selected) c.accent else c.ink)
    }
}

/**
 * A note as a card: type tile, title (with a Latest pill), date and length, a two-line preview, a pin star and a chevron.
 * Light gets the 1 dp line border; dark is separated by the surface step alone (design system §4).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteCard(
    model: NoteRowModel, tile: Color, selected: Boolean, showPin: Boolean,
    onTogglePin: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier
) {
    val c = MM.colors
    val dark = c.background.luminance() < 0.5f
    val shape = MM.radius.card
    val meta = listOf(model.typeLabel, model.durationLabel).filter { it.isNotBlank() }.joinToString("  ·  ")
    val tint = if (tile == c.inkFaint) c.accent else tile
    Row(
        modifier.padding(vertical = MM.space.xs).fillMaxWidth().clip(shape)
            .background(if (selected) c.accentWash else c.surface, shape)
            .then(if (dark) Modifier else Modifier.border(1.dp, c.line, shape))
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(start = MM.space.m, top = MM.space.m, bottom = MM.space.m, end = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(tint.copy(alpha = 0.16f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(model.typeIcon ?: Icons.Rounded.Description, null, tint = tint, modifier = Modifier.size(MMSize.icon))
        }
        Column(Modifier.weight(1f).padding(start = MM.space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.title, style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (model.timeLabel.isNotBlank()) Text(model.timeLabel, style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(start = MM.space.s))
            }
            if (meta.isNotBlank()) Text(meta, style = MM.type.caption, color = c.inkSecondary)
            if (model.preview.isNotBlank()) Text(
                model.preview, style = MM.type.secondary, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MM.space.xs)
            )
        }
        if (showPin) IconButton(onClick = onTogglePin) {
            Icon(
                if (model.pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                if (model.pinned) "Unpin" else "Pin",
                tint = if (model.pinned) c.gold else c.inkFaint
            )
        }
    }
}
