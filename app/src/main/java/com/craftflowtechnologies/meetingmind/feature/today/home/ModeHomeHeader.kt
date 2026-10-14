package com.craftflowtechnologies.meetingmind.feature.today.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.timeline.Greetings
import com.craftflowtechnologies.meetingmind.feature.today.HeroTile
import com.craftflowtechnologies.meetingmind.feature.today.HomeHeroHeader
import com.craftflowtechnologies.meetingmind.feature.today.HomeModes
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.today.ModeHome
import com.craftflowtechnologies.meetingmind.feature.today.OutfitFamily
import com.craftflowtechnologies.meetingmind.feature.today.TodayFocus
import com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised

/**
 * The header every mode home shares: the Today header and sky hero, then the mode switch. The
 * mode lives in [TodayViewModel.focus], so it carries between the homes. A home only offers the
 * modes [HomeModes] allows for it, and a mode it does not offer falls back to its first.
 */
@Composable
fun HomeModeHeader(
    today: TodayViewModel,
    home: ModeHome,
    tile: HeroTile,
    listState: LazyListState,
    inboxCount: Int,
    onSearch: () -> Unit,
    onInbox: () -> Unit,
    onOpenDevotional: () -> Unit,
    modifier: Modifier = Modifier
) {
    val identity by today.identity.collectAsState()
    val focus by today.focus.collectAsState()
    val contextLine by today.contextLine.collectAsState()
    val stats by today.stats.collectAsState()
    val heroMode by today.homeHeroDisplayMode.collectAsState()
    val devotional by today.devotional.collectAsState()
    val now by today.now.collectAsState()
    val offered = remember(identity.showsFaith, home) { HomeModes.offered(home, identity.showsFaith) }
    val mode = HomeModes.effective(focus, offered)
    // Keep the stored mode in step with what this home shows, so the timeline and the switch agree.
    LaunchedEffect(mode) { if (focus != mode) today.setFocus(mode) }

    val devData = devotional?.devotional
    val scripture = devData?.scripture?.firstOrNull()
    Column(modifier.fillMaxWidth()) {
        HomeHeroHeader(
            identity = identity,
            greeting = remember(identity, now / 3_600_000) { Greetings.pick(identity) },
            contextLine = contextLine,
            streakLabel = stats.streakLabel,
            weekLabel = stats.weekLabel,
            inboxCount = inboxCount,
            showSwitch = offered.size > 1,
            switchLabel = HomeModes.name(HomeModes.next(mode, offered)),
            tile = tile,
            listState = listState,
            // The home has its own next-up card, so the floating tile stays off.
            showTile = false,
            onAvatar = {},
            onSearch = onSearch,
            onInbox = onInbox,
            onSwitch = { today.setFocus(HomeModes.next(mode, offered)) },
            displayMode = heroMode,
            scriptureReference = scripture?.display(),
            scriptureText = devData?.keyText ?: devData?.reflection?.firstOrNull(),
            quoteText = devData?.insight?.text,
            quoteAuthor = devData?.insight?.author,
            onToggleMode = { today.cycleHomeHeroMode() },
            onOpenDevotional = onOpenDevotional
        )
        Spacer(Modifier.height(14.dp))
        HomeModeSwitch(home = home, current = mode, offered = offered, onSelect = { today.setFocus(it) }, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/**
 * The mode switch: a segmented control whose highlight slides to the chosen mode, and a caption
 * that says what is showing and what the next tap shows. Hidden when a home offers one mode.
 */
@Composable
fun HomeModeSwitch(
    home: ModeHome,
    current: TodayFocus,
    offered: List<TodayFocus>,
    onSelect: (TodayFocus) -> Unit,
    modifier: Modifier = Modifier
) {
    if (offered.size < 2) return
    val index = offered.indexOf(current).coerceAtLeast(0)
    val slot by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "mode_slot"
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(50)).background(SurfaceRaised)
                .border(1.dp, LineSoft, RoundedCornerShape(50)).padding(4.dp)
        ) {
            val segment = maxWidth / offered.size
            Box(Modifier.width(segment).fillMaxHeight().offset(x = segment * slot).clip(RoundedCornerShape(50)).background(Accent))
            Row(Modifier.fillMaxSize()) {
                offered.forEach { mode ->
                    val ink by animateColorAsState(
                        targetValue = if (mode == current) FixedWhite else InkSecondary,
                        animationSpec = tween(260),
                        label = "mode_ink"
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                            .clickable(role = Role.Tab) { onSelect(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(HomeModes.name(mode), color = ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(160)))
            },
            label = "mode_caption"
        ) { mode ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    HomeModes.showingLine(home, mode), color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                HomeModes.nextLine(mode, offered)?.let { next ->
                    Text(next, color = InkMuted, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
