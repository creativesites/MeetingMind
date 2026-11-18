package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppLook
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.delay

/** What the end of Today's Timeline shows. */
private enum class FooterState { HIDDEN, MORE, LOADING, END, EMPTY }

/**
 * The end of Today's Timeline: a "Load more" pill, a spinner while the next page loads, then
 * "You're all caught up" with how far back it goes once nothing older is left.
 */
@Composable
fun TimelineFooter(page: TimelinePage, hasMore: Boolean, empty: Boolean, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val state = when {
        page.loading -> FooterState.LOADING
        !page.checked -> FooterState.HIDDEN
        hasMore -> FooterState.MORE
        empty -> FooterState.EMPTY
        else -> FooterState.END
    }
    val motion = MM.motion
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn(motion.standard()) togetherWith fadeOut(motion.quick()) },
        contentAlignment = Alignment.Center,
        label = "timeline_footer",
        modifier = modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.m)
    ) { s ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (s) {
                FooterState.HIDDEN -> Spacer(Modifier.height(MM.space.xl))
                FooterState.MORE, FooterState.LOADING -> MorePill(loading = s == FooterState.LOADING, onClick = onMore)
                FooterState.END -> EndNote(
                    title = "You're all caught up",
                    detail = page.earliest?.let { "That's everything since " + java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) + "." }
                )
                FooterState.EMPTY -> EndNote(title = "Nothing on your timeline yet", detail = "Record or write something and it appears here.")
            }
        }
    }
}

@Composable
private fun MorePill(loading: Boolean, onClick: () -> Unit) {
    val accent = LocalAppLook.current.accent
    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = MM.radius.pill,
        color = MM.colors.surfaceRaised,
        border = BorderStroke(1.dp, MM.colors.line),
        modifier = Modifier.testTag(if (loading) "river_loading" else "river_more")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s)
        ) {
            if (loading) CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            else Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MM.space.s))
            Text(if (loading) "Loading earlier notes" else "Load more", style = MM.type.bodyStrong, color = MM.colors.ink)
        }
    }
}

@Composable
private fun EndNote(title: String, detail: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("river_end")) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MM.colors.inkFaint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(MM.space.xs))
        Text(title, style = MM.type.bodyStrong, color = MM.colors.inkSecondary, textAlign = TextAlign.Center)
        if (detail != null) Text(detail, style = MM.type.caption, color = MM.colors.inkMuted, textAlign = TextAlign.Center)
    }
}

/**
 * A card-shaped placeholder while the next page loads. It fades in once and then holds still: an
 * endless pulse would keep the screen drawing (and Compose busy) for as long as it shows.
 */
@Composable
fun TimelineSkeleton(modifier: Modifier = Modifier) {
    val motion = MM.motion
    val alpha = remember { Animatable(if (motion.reduced) 0.7f else 0f) }
    LaunchedEffect(Unit) { if (!motion.reduced) alpha.animateTo(0.7f, motion.standard()) }
    Box(
        modifier.fillMaxWidth().height(132.dp).graphicsLayer { this.alpha = alpha.value }
            .clip(MM.radius.card).background(MM.colors.surfaceSunk)
    )
}

/**
 * A newly loaded Timeline card fades and rises into place, a beat after the one before it.
 * [order] is its place in the new page; cards seen before don't animate again.
 */
@Composable
fun Modifier.revealIn(animate: Boolean, order: Int): Modifier {
    if (!animate) return this
    val motion = MM.motion
    val progress = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (motion.reduced) return@LaunchedEffect
        delay((order.coerceIn(0, 6) * 45L * motion.durationScale).toLong())
        progress.animateTo(1f, motion.enter())
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}
