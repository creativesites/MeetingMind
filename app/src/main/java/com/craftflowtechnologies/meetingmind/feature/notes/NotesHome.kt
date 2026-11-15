package com.craftflowtechnologies.meetingmind.feature.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity
import com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel
import com.craftflowtechnologies.meetingmind.core.notes.LibraryChip
import com.craftflowtechnologies.meetingmind.core.notes.LibraryItem
import com.craftflowtechnologies.meetingmind.core.notes.LibraryQuery
import com.craftflowtechnologies.meetingmind.core.notes.NoteLabels
import com.craftflowtechnologies.meetingmind.core.notes.NoteSort
import com.craftflowtechnologies.meetingmind.core.timeline.TimeOfDaySky
import com.craftflowtechnologies.meetingmind.core.ui.MeetingMindMark
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionVolume
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.feature.today.home.rememberSceneTime
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Design token aliases ensuring full semantic clarity
private val MMColors.borderSubtle: Color get() = lineSoft
private val MMColors.border: Color get() = line
private val MMColors.accentSoft: Color get() = accentWash

// The Notes "home" layout (root, list view): top bar, greeting with Zuri, the Latest meeting hero, then the library.
// Everything here uses MM tokens only, so light/dark and all accents work without extra code.

/** Wordmark on the left; search, list/folders toggle, and notebooks on the right. */
@Composable
internal fun NotesHomeTopBar(
    onSearch: () -> Unit,
    onFolders: () -> Unit,
    archived: Int,
    trash: Int,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    noteCount: Int? = null,
    view: LibraryView = LibraryView.LIST,
    onViewChange: ((LibraryView) -> Unit)? = null
) {
    val c = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeetingMindMark(size = 32.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = MM.space.s)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Meeting", style = MM.type.title, color = c.ink)
                Text("Mind", style = MM.type.title, color = c.accent)
            }
            if (noteCount != null && noteCount > 0) {
                Text(
                    if (noteCount == 1) "1 note" else "$noteCount notes",
                    style = MM.type.caption,
                    color = c.inkMuted
                )
            }
        }

        // Tonal circular action buttons & List/Folders segmented control
        Row(
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onViewChange != null) {
                Surface(
                    shape = MM.radius.pill,
                    color = c.track,
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { onViewChange(LibraryView.LIST) },
                            shape = MM.radius.pill,
                            color = if (view == LibraryView.LIST) c.surfaceRaised else Color.Transparent,
                            border = if (view == LibraryView.LIST) BorderStroke(MMSize.hairline, c.borderSubtle) else null,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Notes,
                                    contentDescription = "Show notes",
                                    tint = if (view == LibraryView.LIST) c.accent else c.inkSecondary,
                                    modifier = Modifier.size(MMSize.iconSmall)
                                )
                            }
                        }
                        Surface(
                            onClick = { onViewChange(LibraryView.FOLDERS) },
                            shape = MM.radius.pill,
                            color = if (view == LibraryView.FOLDERS) c.surfaceRaised else Color.Transparent,
                            border = if (view == LibraryView.FOLDERS) BorderStroke(MMSize.hairline, c.borderSubtle) else null,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.FolderOpen,
                                    contentDescription = "Show notebooks",
                                    tint = if (view == LibraryView.FOLDERS) c.accent else c.inkSecondary,
                                    modifier = Modifier.size(MMSize.iconSmall)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    onClick = onFolders,
                    shape = CircleShape,
                    color = c.surfaceRaised,
                    border = BorderStroke(MMSize.hairline, c.borderSubtle),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FolderOpen, "Show notebooks", tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                    }
                }
            }

            Surface(
                onClick = onSearch,
                shape = CircleShape,
                color = c.surfaceRaised,
                border = BorderStroke(MMSize.hairline, c.borderSubtle),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, "Search notes", tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                }
            }

            if (archived > 0 || trash > 0) {
                LibraryMenu(archived, trash, onArchive, onTrash)
            }
        }
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
    Column(modifier = modifier) {
        Text(greeting, style = MM.type.title, color = MM.colors.ink)
        Text(
            "Your workspace at a glance",
            style = MM.type.secondary,
            color = MM.colors.inkSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Zuri rendered with full 3D volumetric form, rim lighting, and interactive capabilities
 * on an unobstructed ambient hero stage. Nothing may draw on top of or slice off the Companion.
 */
private val ZURI_SIZE = 76.dp
private val ZURI_STAGE_SIZE = 84.dp

@Composable
private fun CompanionZuri(lateNight: Boolean, modifier: Modifier = Modifier) {
    val c = MM.colors
    Box(
        modifier = modifier
            .size(ZURI_STAGE_SIZE)
            .background(
                Brush.radialGradient(
                    listOf(c.accentSoft.copy(alpha = 0.25f), Color.Transparent)
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalCompanionVolume provides true) {
            ZuriSlot(
                page = CompanionPage.HOME,
                size = ZURI_SIZE,
                state = if (lateNight) CompanionState.SLEEPY else CompanionState.IDLE,
                offerShow = true
            )
        }
    }
}

/** Greeting on the left, Zuri in full 3D on the right, side by side. Used when the Latest meeting hero is not shown. */
@Composable
internal fun NotesGreeting() {
    val info = rememberGreeting()
    val companionShown = rememberCompanionVisible(CompanionPage.HOME)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = MM.space.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GreetingText(info.greeting, Modifier.weight(1f))
        if (companionShown) {
            CompanionZuri(info.lateNight)
        }
    }
}

/**
 * The greeting, Zuri and the Latest meeting card as one cohesive block.
 * Zuri sits prominently in the foreground beside the greeting, completely unobstructed above the hero card.
 */
@Composable
internal fun HeroWithZuri(
    greetingSlot: @Composable () -> Unit,
    bubbleSlot: @Composable () -> Unit,
    zuriSlot: @Composable () -> Unit,
    heroSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = MM.space.m)
            ) {
                greetingSlot()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                bubbleSlot()
                zuriSlot()
            }
        }
        heroSlot()
    }
}

/** The block for the home list: greeting, Zuri and the compact hero card. */
@Composable
internal fun HomeHeroBlock(
    item: LibraryItem,
    tiles: List<MeetingTile>,
    onOpen: () -> Unit,
    onTile: (TileAction) -> Unit
) {
    val info = rememberGreeting()
    HeroWithZuri(
        greetingSlot = { GreetingText(info.greeting) },
        bubbleSlot = {},
        zuriSlot = { CompanionZuri(info.lateNight) },
        heroSlot = { LatestMeetingCard(item, tiles, onOpen, onTile) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MM.space.m)
    )
}

/**
 * The hero of the screen: the most recent recorded Work note.
 * Renders an atmospheric vector sky/hill canvas driven by [TimeOfDaySky] colors, with a translucent
 * readability wash ensuring contrast in both themes, a crisp badge, editorial title, and insight tiles.
 */
@Composable
internal fun LatestMeetingCard(
    item: LibraryItem,
    tiles: List<MeetingTile>,
    onOpen: () -> Unit,
    onTile: (TileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val appIdentity = LocalAppIdentity.current
    val note = item.note
    val whenMs = note.eventDate ?: note.createdAt
    val date = remember(whenMs) { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(whenMs)) }
    val durationText = item.durationMs?.let(NoteLabels::duration)
    val cards = heroCards(tiles)

    val cal = remember { Calendar.getInstance() }
    val sky = remember(cal, appIdentity.look) {
        TimeOfDaySky.at(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            warm = appIdentity.look == LookAndFeel.SANCTUARY
        )
    }
    val skyTop = Color(sky.skyTop)
    val skyBottom = Color(sky.skyBottom)
    val sun = Color(sky.orbBody)
    val hill = c.surfaceRaised.copy(alpha = if (c.isDark) 0.35f else 0.45f)
    val scene = remember(skyTop, skyBottom, hill, sun) {
        notesHeroScene(
            sky = skyTop.copy(alpha = if (c.isDark) 0.30f else 0.22f),
            horizon = skyBottom.copy(alpha = if (c.isDark) 0.22f else 0.16f),
            hill = hill,
            sun = sun.copy(alpha = if (c.isDark) 0.45f else 0.35f)
        )
    }
    val sceneTime = rememberSceneTime()

    Box(
        modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .background(c.surfaceRaised)
            .border(MMSize.hairline, c.borderSubtle, MM.radius.card)
            .clickable(role = Role.Button, onClick = onOpen)
    ) {
        // Atmospheric vector scene canvas
        Canvas(Modifier.matchParentSize()) {
            scene(sceneTime.value)
        }
        // Translucent surface readability scrim guaranteeing contrast in both themes
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            c.surfaceRaised.copy(alpha = 0.82f),
                            c.surfaceRaised.copy(alpha = 0.94f)
                        )
                    )
                )
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MM.space.l, vertical = MM.space.m)
        ) {
            Row(
                Modifier
                    .background(c.accentWash, MM.radius.pill)
                    .border(MMSize.hairline, c.accent.copy(alpha = 0.3f), MM.radius.pill)
                    .padding(horizontal = MM.space.s, vertical = MM.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Groups, null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                Spacer(Modifier.width(MM.space.xs))
                Text("Latest meeting", style = MM.type.caption, color = c.accent)
                if (date.isNotBlank()) {
                    Text("  ·  $date", style = MM.type.caption, color = c.inkSecondary)
                }
            }

            Text(
                note.title.ifBlank { "Untitled note" },
                style = MM.type.heading,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MM.space.s)
            )

            if (durationText != null) {
                Text(
                    durationText,
                    style = MM.type.caption,
                    color = c.inkSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (cards.isNotEmpty()) {
                Spacer(Modifier.height(MM.space.s))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    cards.forEach { tile ->
                        InsightCard(
                            icon = tile.action.icon(),
                            label = tile.label,
                            sub = tile.subtitle,
                            onClick = { onTile(tile.action) },
                            modifier = Modifier.weight(1f)
                        )
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
private fun InsightCard(
    icon: ImageVector,
    label: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    Column(
        modifier
            .clip(MM.radius.small)
            .background(c.surface, MM.radius.small)
            .border(MMSize.hairline, c.borderSubtle, MM.radius.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(c.accentWash, MM.radius.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(MM.space.xs))
            Text(label, style = MM.type.caption, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            sub,
            style = MM.type.secondary,
            color = c.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** "Your Notes" with filters, sort and New. */
@Composable
internal fun NotesSectionHeader(
    extraFilters: Int,
    sort: NoteSort,
    onSort: (NoteSort) -> Unit,
    onFilters: () -> Unit,
    onNew: () -> Unit
) {
    val c = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = MM.space.m, bottom = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Your Notes",
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onFilters,
                shape = MM.radius.pill,
                color = if (extraFilters > 0) c.accentWash else c.surfaceRaised,
                border = BorderStroke(MMSize.hairline, if (extraFilters > 0) c.accent else c.borderSubtle),
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = MM.space.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = if (extraFilters > 0) "Filters, $extraFilters on" else "Filters",
                        tint = if (extraFilters > 0) c.accent else c.inkSecondary,
                        modifier = Modifier.size(MMSize.iconSmall)
                    )
                    if (extraFilters > 0) {
                        Spacer(Modifier.width(MM.space.xs))
                        Text(
                            "$extraFilters",
                            style = MM.type.caption,
                            color = c.accent
                        )
                    }
                }
            }

            SortMenu(sort, onSort)

            PrimaryButton(
                text = "New",
                onClick = onNew,
                leadingIcon = Icons.Rounded.Add,
                modifier = Modifier.height(34.dp)
            )
        }
    }
}

/** The chips row with icons. Which chips appear still comes from [LibraryChip]; types and tags live behind the filter button. */
@Composable
internal fun HomeChips(
    query: LibraryQuery,
    showWork: Boolean,
    showFaith: Boolean,
    onAll: () -> Unit,
    onChip: (LibraryChip) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = query.types.size + if (query.tagId != null) 1 else 0
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = MM.space.xs),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
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
        Modifier
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.pill)
            .background(if (selected) c.accentWash else c.surfaceRaised, MM.radius.pill)
            .border(MMSize.hairline, if (selected) c.accent else c.borderSubtle, MM.radius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = MM.space.m, vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) c.accent else c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
        Spacer(Modifier.width(MM.space.xs))
        Text(label, style = MM.type.caption, color = if (selected) c.accent else c.ink)
    }
}

/**
 * A note as a card: leading space indicator bar, type badge, title, 2-line preview,
 * balanced bottom metadata pills, and pin/lock badges.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteCard(
    model: NoteRowModel,
    tile: Color,
    selected: Boolean,
    showPin: Boolean,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val dark = c.isDark
    val shape = MM.radius.card
    val tint = if (tile == c.inkFaint) c.accent else tile

    Row(
        modifier = modifier
            .padding(vertical = MM.space.xs)
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.accentWash else c.surface, shape)
            .then(if (dark) Modifier else Modifier.border(MMSize.hairline, c.borderSubtle, shape))
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(start = MM.space.s, top = MM.space.m, bottom = MM.space.m, end = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Subtle leading vertical accent bar
        Box(
            Modifier
                .width(MMSize.marker)
                .height(44.dp)
                .background(tint, MM.radius.pill)
        )
        Spacer(Modifier.width(MM.space.s))

        // Space / type icon box
        Box(
            Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.12f), MM.radius.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                model.typeIcon ?: Icons.Rounded.Description,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(MMSize.iconSmall)
            )
        }

        // Content
        Column(
            Modifier
                .weight(1f)
                .padding(start = MM.space.m)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.title,
                    style = MM.type.bodyStrong,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (showPin && model.pinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        tint = c.gold,
                        modifier = Modifier
                            .padding(start = MM.space.xs)
                            .size(14.dp)
                    )
                }
                if (model.isPrivate) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Private",
                        tint = c.inkMuted,
                        modifier = Modifier
                            .padding(start = MM.space.xs)
                            .size(14.dp)
                    )
                }
            }

            if (model.preview.isNotBlank()) {
                Text(
                    model.preview,
                    style = MM.type.secondary,
                    color = c.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MM.space.xs)
                )
            }

            // Balanced bottom metadata row
            Row(
                Modifier.padding(top = MM.space.xs),
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.timeLabel.isNotBlank()) {
                    Text(model.timeLabel, style = MM.type.caption, color = c.inkMuted)
                }
                if (model.hasRecording && model.durationLabel.isNotBlank()) {
                    Surface(
                        shape = MM.radius.pill,
                        color = c.surfaceRaised,
                        border = BorderStroke(MMSize.hairline, c.borderSubtle)
                    ) {
                        Row(
                            Modifier.padding(horizontal = MM.space.xs, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Mic, null, tint = c.inkSecondary, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(model.durationLabel, style = MM.type.caption, color = c.inkSecondary)
                        }
                    }
                }
                if (model.typeLabel.isNotBlank()) {
                    Surface(
                        shape = MM.radius.pill,
                        color = c.surfaceRaised,
                        border = BorderStroke(MMSize.hairline, c.borderSubtle)
                    ) {
                        Text(
                            model.typeLabel,
                            style = MM.type.caption,
                            color = c.inkSecondary,
                            modifier = Modifier.padding(horizontal = MM.space.xs, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        if (showPin) {
            IconButton(onClick = onTogglePin) {
                Icon(
                    if (model.pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (model.pinned) "Unpin" else "Pin",
                    tint = if (model.pinned) c.gold else c.inkFaint
                )
            }
        }
    }
}
