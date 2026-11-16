package com.craftflowtechnologies.meetingmind.core.ui.work

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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMElevation
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** One destination in a vertical's bottom bar. [key] names it in [VerticalBar]'s selection and test tags. */
data class VerticalBarItem(val key: String, val label: String, val icon: ImageVector)

/**
 * The bottom bar inside a vertical (Work, Learn): the same floating pill as the app's main bar
 * ([AppBottomNavigationBar]), with the selected item as an accent wash chip showing icon and label,
 * and the others as 44 dp icon-only buttons.
 *
 * An optional leading Home button ([homeItem]) leaves the vertical and returns to the main home.
 * Test tags: `vbar_<key>` for each item and `vbar_home` for the Home button.
 *
 * @param selected the [VerticalBarItem.key] of the current destination.
 * @param onSelect called with the key of the tapped item (including the selected one).
 * @param homeItem called when the Home button is tapped; null hides the button.
 */
@Composable
fun VerticalBar(
    items: List<VerticalBarItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    homeItem: (() -> Unit)? = null
) {
    val colors = MM.colors
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MM.space.l)
            .padding(top = 4.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = if (colors.isDark) colors.surfaceRaised else colors.surface,
            border = BorderStroke(MMSize.hairline, colors.line),
            shadowElevation = MMElevation.floating,
            modifier = Modifier.wrapContentWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (homeItem != null) {
                    HomeButton(onClick = homeItem)
                }
                items.forEach { item ->
                    if (item.key == selected) {
                        SelectedChip(item = item, onClick = { onSelect(item.key) })
                    } else {
                        IdleIcon(item = item, onClick = { onSelect(item.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).testTag("vbar_home")
    ) {
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = "Home",
            tint = MM.colors.inkSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SelectedChip(item: VerticalBarItem, onClick: () -> Unit) {
    val colors = MM.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = colors.accentWash,
        modifier = Modifier.height(44.dp).testTag("vbar_${item.key}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = item.label,
                style = MM.type.secondary.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink
            )
        }
    }
}

@Composable
private fun IdleIcon(item: VerticalBarItem, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).testTag("vbar_${item.key}")
    ) {
        Icon(
            imageVector = item.icon,
            // The label is hidden while the item is idle, so the icon carries it for screen readers.
            contentDescription = item.label,
            tint = MM.colors.inkSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
