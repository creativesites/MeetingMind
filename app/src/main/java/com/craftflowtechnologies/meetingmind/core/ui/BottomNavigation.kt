package com.craftflowtechnologies.meetingmind.core.ui

import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.remember
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity
import com.craftflowtechnologies.meetingmind.core.navigation.BarConfig
import com.craftflowtechnologies.meetingmind.core.navigation.BarSlot
import com.craftflowtechnologies.meetingmind.ui.theme.LocalAppearance
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.runtime.compositionLocalOf
import com.craftflowtechnologies.meetingmind.core.work.TabSlot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line

/**
 * The bar's destinations: Today and Notes, the New action, then two places that follow the
 * person's spaces ([BarConfig]): Work, Learn, Faith, Tasks or Search.
 *
 * New is a filled action between the tabs, not a destination: it opens the Create sheet, where
 * Record comes first, then a written note, photos and import (docs/PLAN_V1.md §2). Settings is the
 * last place in the bar (founder, 2026-11-18), and is also in the app menu from the avatar.
 */
enum class BottomNavDestination {
    HOME, NOTES, NEW, SEARCH, SETTINGS,
    WORK, LEARN, FAITH, PERSONAL, TASKS,
    /** No tab is current (a screen reached from elsewhere, such as Settings). */
    NONE
}

/**
 * The old Work-or-Search choice for the fourth slot (docs/PLAN_PROFESSIONAL.md §7.4). The bar now
 * follows the person's spaces ([BarConfig]); this is kept for screens that still read it.
 */
val LocalTabSlot = compositionLocalOf { TabSlot.SEARCH }

object TabSlots {
    /** The route the fourth slot opens: the Work space when it holds Work, otherwise Search. */
    fun route(slot: TabSlot, work: String, search: String) = if (slot == TabSlot.WORK) work else search

    fun label(slot: TabSlot) = if (slot == TabSlot.WORK) "Work" else "Search"
}

private data class NavItem(
    val destination: BottomNavDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** A filled, always-labelled action rather than a destination that can be "current". */
    val isAction: Boolean = false
)

private fun slotItem(slot: BarSlot): NavItem = when (slot) {
    BarSlot.WORK -> NavItem(BottomNavDestination.WORK, "Work", Icons.Filled.Work, Icons.Outlined.Work)
    BarSlot.LEARN -> NavItem(BottomNavDestination.LEARN, "Learn", Icons.Filled.School, Icons.Outlined.School)
    BarSlot.FAITH -> NavItem(BottomNavDestination.FAITH, "Faith", Icons.Filled.Church, Icons.Outlined.Church)
    BarSlot.PERSONAL -> NavItem(BottomNavDestination.PERSONAL, "Personal", Icons.Filled.LocalFlorist, Icons.Outlined.LocalFlorist)
    BarSlot.TASKS -> NavItem(BottomNavDestination.TASKS, "Tasks", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt)
    BarSlot.SEARCH -> NavItem(BottomNavDestination.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search)
}

/** The places after New, for the person's spaces and Home. */
@Composable
fun rememberBarSlots(): List<BarSlot> {
    val identity = LocalAppIdentity.current
    val home = LocalAppearance.current.homeStyle
    return remember(identity.spaces, identity.faithFirst, home) { BarConfig.slots(identity, home) }
}

@Composable
private fun navItems(slots: List<BarSlot>) = listOf(
    NavItem(BottomNavDestination.HOME, stringResource(R.string.nav_today), Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(BottomNavDestination.NOTES, stringResource(R.string.nav_notes), Icons.AutoMirrored.Filled.Notes, Icons.AutoMirrored.Outlined.Notes),
    NavItem(BottomNavDestination.NEW, "New", Icons.Filled.Add, Icons.Filled.Add, isAction = true)
) + slots.map(::slotItem) +
    NavItem(BottomNavDestination.SETTINGS, stringResource(R.string.nav_settings), Icons.Filled.Settings, Icons.Outlined.Settings)

/** Below this width the bar's six items do not fit with New's label, so New shows only its icon. */
private val COMPACT_BAR_WIDTH = 380.dp

/**
 * A floating pill navigation bar.
 *
 * Only the current destination carries a text label; the rest are line icons. That keeps the bar
 * narrow enough to float rather than span the screen, and makes the active destination legible at
 * a glance without four labels competing for attention. The chip animates its width as the
 * selection moves, so the change reads as one control moving rather than five independently
 * relabelling.
 *
 * Every primary screen shows this same bar, so bottom navigation never disappears on a primary
 * destination.
 *
 * @param showNewAction False hides the central New action — for screens where starting something
 *   new would interrupt work in progress.
 */
@Composable
fun AppBottomNavigationBar(
    current: BottomNavDestination,
    onNavigate: (BottomNavDestination) -> Unit,
    showNewAction: Boolean = true
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxWidth < COMPACT_BAR_WIDTH
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = SurfaceBase,
            border = BorderStroke(1.dp, Line),
            shadowElevation = 8.dp,
            modifier = Modifier.wrapContentWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                navItems(rememberBarSlots()).forEach { item ->
                    when {
                        item.isAction -> if (showNewAction) NewAction(compact) { onNavigate(BottomNavDestination.NEW) }
                        item.destination == current -> ActiveNavChip(item)
                        else -> InactiveNavIcon(item, onNavigate)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewAction(compact: Boolean, onClick: () -> Unit) {
    // The one action on every screen reflects the person's chosen accent without competing with
    // the selected destination chip.
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = Accent,
        modifier = Modifier
            .height(44.dp)
            .coachTarget("new")
            .testTag("bottom_nav_new")
            .semantics { contentDescription = "Create: record, write a note, add photos or import" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = OnAccent,
                modifier = Modifier.size(19.dp)
            )
            if (!compact) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "New",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    color = OnAccent
                )
            }
        }
    }
}

@Composable
private fun ActiveNavChip(item: NavItem) {
    val background by animateColorAsState(AccentWash, label = "navChipBackground")
    val height by animateDpAsState(44.dp, spring(), label = "navChipHeight")
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = background,
        modifier = Modifier
            .height(height)
            .testTag(item.destination.testTag())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp)
        ) {
            Icon(
                imageVector = item.selectedIcon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color = Ink
            )
        }
    }
}

@Composable
private fun InactiveNavIcon(item: NavItem, onNavigate: (BottomNavDestination) -> Unit) {
    IconButton(
        onClick = { onNavigate(item.destination) },
        modifier = Modifier
            .size(42.dp)
            .testTag(item.destination.testTag())
    ) {
        Icon(
            imageVector = item.unselectedIcon,
            // The label is hidden in this state, so the icon carries it for screen readers.
            contentDescription = item.label,
            tint = InkSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Unchanged from the previous bar, so existing test tags and any saved UI tests still resolve. */
private fun BottomNavDestination.testTag(): String = "bottom_nav_${name.lowercase()}"
