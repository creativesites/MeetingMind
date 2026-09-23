package com.example.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line

/**
 * The app's four primary destinations, plus New.
 *
 * New is a filled action between the tabs, not a fifth destination: it opens the Create sheet,
 * where Record comes first, then a written note, photos and import (docs/PLAN_V1.md §2). The AI
 * Engine lives under Settings; it is something set up once, not somewhere people go daily.
 */
enum class BottomNavDestination { HOME, NOTES, NEW, SEARCH, SETTINGS }

private data class NavItem(
    val destination: BottomNavDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** A filled, always-labelled action rather than a destination that can be "current". */
    val isAction: Boolean = false
)

private val navItems = listOf(
    NavItem(BottomNavDestination.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(BottomNavDestination.NOTES, "Notes", Icons.AutoMirrored.Filled.Notes, Icons.AutoMirrored.Outlined.Notes),
    NavItem(BottomNavDestination.NEW, "New", Icons.Filled.Add, Icons.Filled.Add, isAction = true),
    NavItem(BottomNavDestination.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    NavItem(BottomNavDestination.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = Color.White,
            border = BorderStroke(1.dp, Line),
            shadowElevation = 8.dp,
            modifier = Modifier.wrapContentWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                navItems.forEach { item ->
                    when {
                        item.isAction -> if (showNewAction) NewAction { onNavigate(BottomNavDestination.NEW) }
                        item.destination == current -> ActiveNavChip(item)
                        else -> InactiveNavIcon(item, onNavigate)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewAction(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = Ink,
        modifier = Modifier
            .height(44.dp)
            .testTag("bottom_nav_new")
            .semantics { contentDescription = "Create: record, write a note, add photos or import" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "New",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color = Color.White
            )
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
            .size(44.dp)
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
