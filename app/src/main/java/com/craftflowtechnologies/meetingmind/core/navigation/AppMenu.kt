package com.craftflowtechnologies.meetingmind.core.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.identity.Avatar
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity
import com.craftflowtechnologies.meetingmind.core.identity.SpacesPicker
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.ui.theme.LocalAppearance
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch

/**
 * Opens the app menu from anywhere. [focus] puts that vertical's group first (null on a home).
 * [local] is the vertical's own menu, which takes that group's place (see [AppMenuModel.sections]).
 */
class AppMenuController(private val show: (NotebookSpace?, MenuSection?) -> Unit) {
    fun open(focus: NotebookSpace? = null, local: MenuSection? = null) = show(focus, local)
}

/** The app menu, for any screen: `LocalAppMenu.current.open(focus = NotebookSpace.WORK)`. A no-op outside [AppMenuHost]. */
val LocalAppMenu = staticCompositionLocalOf { AppMenuController { _, _ -> } }

/** Whether the menu is open, and from where. One per app; see [rememberAppMenuState]. */
class AppMenuState {
    var open by mutableStateOf(false)
    var focus by mutableStateOf<NotebookSpace?>(null)
    var local by mutableStateOf<MenuSection?>(null)
    val controller = AppMenuController { f, l -> focus = f; local = l; open = true }
}

@Composable
fun rememberAppMenuState(): AppMenuState = remember { AppMenuState() }

/**
 * Draws the one app menu when it is open. Provide `LocalAppMenu provides state.controller` above
 * the screens. [navigate] opens a route; [onCreate] opens the Create sheet; [onYouTube] opens
 * "Notes from a YouTube video".
 */
@Composable
fun AppMenuHost(state: AppMenuState, navigate: (String) -> Unit, onCreate: () -> Unit, onYouTube: (NotebookSpace?) -> Unit = {}) {
    if (state.open) AppMenuSheet(
        focus = state.focus,
        local = state.local,
        onDismiss = { state.open = false },
        onTarget = { target ->
            state.open = false
            when (target) {
                is MenuTarget.Go -> navigate(target.route)
                MenuTarget.Create -> onCreate()
                is MenuTarget.Run -> target.action()
                is MenuTarget.YouTube -> onYouTube(target.from)
            }
        }
    )
}

/**
 * A menu button for a vertical's top bar: the same app menu, with [focus]'s group first. [local]
 * builds the vertical's own section (its tools), read when the button is tapped.
 */
@Composable
fun AppMenuButton(focus: NotebookSpace?, modifier: Modifier = Modifier, tint: Color = MM.colors.ink, local: (() -> MenuSection)? = null) {
    val menu = LocalAppMenu.current
    IconButton(onClick = { menu.open(focus, local?.invoke()) }, modifier = modifier.testTag("app_menu_button")) {
        Icon(Icons.Rounded.Apps, contentDescription = "Menu: every space and feature", tint = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(focus: NotebookSpace?, onDismiss: () -> Unit, onTarget: (MenuTarget) -> Unit, local: MenuSection? = null) {
    val identity = LocalAppIdentity.current
    val home = LocalAppearance.current.homeStyle
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferencesManager(context) }
    var editingSpaces by remember { mutableStateOf(false) }
    val lead = BarConfig.lead(home, identity)
    val sections = remember(identity.spaces, focus, lead, local) { AppMenuModel.sections(identity.spaces, focus, lead, local) }
    val colors = MM.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceRaised,
        modifier = Modifier.testTag("app_menu")
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = MM.space.xxl)) {
            // Who you are, and the way out.
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.l), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(identity, 44.dp)
                    Column(Modifier.weight(1f).padding(start = MM.space.m)) {
                        Text(identity.displayName?.takeIf { it.isNotBlank() } ?: "MeetingMind", style = MM.type.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(focus?.let { "In ${SpaceStyle.title(it)} · everything is one tap away" } ?: "Everything in MeetingMind", style = MM.type.secondary, color = colors.inkSecondary)
                    }
                    // Settings is one tap from every menu, wherever it was opened.
                    IconButton(onClick = { onTarget(MenuTarget.Go(Routes.SETTINGS)) }, modifier = Modifier.testTag("app_menu_settings")) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = colors.inkSecondary)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close menu", tint = colors.inkSecondary) }
                }
            }
            // Your spaces: one big tap into each, plus the switch that changes them.
            item(key = "spaces") {
                Column(Modifier.padding(top = MM.space.l)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.l), verticalAlignment = Alignment.CenterVertically) {
                        Text("YOUR SPACES", style = MM.type.overline, color = colors.inkMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editingSpaces = !editingSpaces }, modifier = Modifier.testTag("app_menu_edit_spaces")) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accent)
                            Spacer(Modifier.width(MM.space.xs))
                            Text(if (editingSpaces) "Done" else "Choose spaces", color = colors.accent, style = MM.type.bodyStrong)
                        }
                    }
                    if (editingSpaces) {
                        Box(Modifier.padding(horizontal = MM.space.l)) {
                            SpacesPicker(identity.spaces) { next -> scope.launch { SpaceSchedules.setSpaces(context, next) } }
                        }
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = MM.space.l), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            items(BarConfig.verticals(identity.spaces, focus ?: lead), key = { it.name }) { space ->
                                SpaceTile(space, current = space == focus) { onTarget(AppMenuModel.section(space).entries.first().target) }
                            }
                        }
                    }
                }
            }
            sections.forEach { section ->
                item(key = "s-${section.id}") { SectionHeader(section) }
                items(section.entries, key = { "e-${section.id}-${it.id}" }) { entry ->
                    MenuRow(entry, tint = section.space?.let { SpaceStyle.tint(it) } ?: colors.inkSecondary) { onTarget(entry.target) }
                }
            }
        }
    }
}

@Composable
private fun SpaceTile(space: NotebookSpace, current: Boolean, onClick: () -> Unit) {
    val tint = SpaceStyle.tint(space)
    val colors = MM.colors
    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = tint.copy(alpha = if (current) 0.20f else 0.10f),
        border = BorderStroke(if (current) 1.5.dp else 1.dp, if (current) tint else colors.line),
        modifier = Modifier.width(132.dp).height(104.dp).testTag("app_menu_space_${space.name.lowercase()}")
    ) {
        Column(Modifier.padding(MM.space.m)) {
            Icon(SpaceStyle.icon(space), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            Text(SpaceStyle.title(space), style = MM.type.heading, color = colors.ink)
            Text(if (current) "You are here" else "Open", style = MM.type.caption, color = colors.inkSecondary)
        }
    }
}

@Composable
private fun SectionHeader(section: MenuSection) {
    val colors = MM.colors
    Row(Modifier.fillMaxWidth().padding(start = MM.space.l, end = MM.space.l, top = MM.space.xl, bottom = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
        section.space?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(SpaceStyle.tint(it))) ; Spacer(Modifier.width(MM.space.s)) }
        Text(section.title.uppercase(), style = MM.type.overline, color = colors.inkMuted)
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, tint: Color, onClick: () -> Unit) {
    val colors = MM.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = MM.space.l, vertical = MM.space.s).testTag("app_menu_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(iconFor(entry.icon), contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(start = MM.space.m)) {
            Text(entry.label, style = MM.type.body, color = colors.ink)
            entry.hint?.let { Text(it, style = MM.type.caption, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
    }
}

private fun iconFor(icon: MenuIcon): ImageVector = when (icon) {
    MenuIcon.HOME -> Icons.Rounded.Home
    MenuIcon.PULSE -> Icons.Rounded.Insights
    MenuIcon.TASKS -> Icons.Rounded.TaskAlt
    MenuIcon.INBOX -> Icons.Rounded.Inbox
    MenuIcon.REVIEW -> Icons.Rounded.CalendarMonth
    MenuIcon.RECORD -> Icons.Rounded.Mic
    MenuIcon.LEARN -> Icons.Rounded.School
    MenuIcon.PRACTICE -> Icons.Rounded.Style
    MenuIcon.LIBRARY -> Icons.AutoMirrored.Rounded.MenuBook
    MenuIcon.FAITH -> Icons.Rounded.Church
    MenuIcon.BIBLE -> Icons.AutoMirrored.Rounded.MenuBook
    MenuIcon.DEVOTIONAL -> Icons.Rounded.AutoStories
    MenuIcon.PRAY -> Icons.Rounded.VolunteerActivism
    MenuIcon.PRAYER_LIST -> Icons.Rounded.EditNote
    MenuIcon.PLANS -> Icons.Rounded.CalendarMonth
    MenuIcon.TESTIMONIES -> Icons.Rounded.Celebration
    MenuIcon.JOURNAL -> Icons.Rounded.EditNote
    MenuIcon.NOTES -> Icons.AutoMirrored.Rounded.Notes
    MenuIcon.SEARCH -> Icons.Rounded.Search
    MenuIcon.CIRCLES -> Icons.Rounded.Groups
    MenuIcon.IMPORT -> Icons.Rounded.FileUpload
    MenuIcon.CREATE -> Icons.Rounded.Add
    MenuIcon.ARCHIVE -> Icons.Rounded.Archive
    MenuIcon.SETTINGS -> Icons.Rounded.Settings
    MenuIcon.PLAN -> Icons.Rounded.WorkspacePremium
    MenuIcon.AI -> Icons.Rounded.Memory
    MenuIcon.APPEARANCE -> Icons.Rounded.Palette
    MenuIcon.BACKUP -> Icons.Rounded.Backup
    MenuIcon.INTEGRATIONS -> Icons.Rounded.Extension
    MenuIcon.VIDEO -> Icons.Rounded.SmartDisplay
}
