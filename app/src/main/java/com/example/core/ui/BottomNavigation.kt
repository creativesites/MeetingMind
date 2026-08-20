package com.example.core.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The app's four primary destinations — every one of them shows this same bar so bottom
 * navigation never randomly disappears on a primary screen. Record is deliberately not a tab
 * here: it stays a prominent FAB/hero action (see [PrimaryCircleButton] on Home) rather than
 * competing for one of these four slots.
 */
enum class BottomNavDestination { HOME, SEARCH, AI_ENGINE, SETTINGS }

@Composable
fun AppBottomNavigationBar(
    current: BottomNavDestination,
    onNavigate: (BottomNavDestination) -> Unit
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        AppBottomNavItem(BottomNavDestination.HOME, current, Icons.Default.Home, "Dashboard", "Home", onNavigate)
        AppBottomNavItem(BottomNavDestination.SEARCH, current, Icons.Default.Search, "Search", "Search", onNavigate)
        AppBottomNavItem(BottomNavDestination.AI_ENGINE, current, Icons.Default.Memory, "AI Engine", "AI Engine", onNavigate)
        AppBottomNavItem(BottomNavDestination.SETTINGS, current, Icons.Default.Settings, "Settings", "Settings", onNavigate)
    }
}

@Composable
private fun RowScope.AppBottomNavItem(
    destination: BottomNavDestination,
    current: BottomNavDestination,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onNavigate: (BottomNavDestination) -> Unit
) {
    val selected = destination == current
    NavigationBarItem(
        selected = selected,
        onClick = { if (!selected) onNavigate(destination) },
        icon = { Icon(icon, contentDescription = contentDescription) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.testTag("bottom_nav_${destination.name.lowercase()}")
    )
}
