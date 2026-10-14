package com.craftflowtechnologies.meetingmind.feature.today.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.today.OutfitFamily
import com.craftflowtechnologies.meetingmind.ui.theme.BentoColors
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import kotlinx.coroutines.delay

/** One line of "Today's plan": a scheduled item, opened on tap. */
data class PlanLine(val title: String, val time: String, val onOpen: () -> Unit)

/** What the bento shows. Everything here comes from data Home already loads; nothing is invented. */
data class BentoData(
    val weekCount: Int,
    val devotionalTitle: String,
    val devotionalSub: String,
    val devotionalNew: Boolean,
    val todayCount: Int,
    val nextTitle: String,
    val nextWhen: String,
    val plan: List<PlanLine>
)

/** Where each tile goes. */
data class BentoActions(
    val onFaith: () -> Unit,
    val onDevotional: () -> Unit,
    val onPrayer: () -> Unit,
    val onCreate: () -> Unit,
    val onCircles: () -> Unit,
    val onWork: () -> Unit,
    val onNextUp: () -> Unit,
    val onJournal: () -> Unit
)

private val Ink = BentoColors.Ink
private val InkSoft = BentoColors.InkSoft
private val Paper = BentoColors.Paper

/**
 * The Today home's tiles, laid out for the person's spaces: all three (faith, work, personal),
 * work and personal, or personal only. The illustrated tiles are the feature shortcuts.
 */
@Composable
fun HomeBento(sections: HomeSections, data: BentoData, actions: BentoActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (sections.layout) {
            HomeLayout.ALL -> {
                FaithTile(data, actions.onFaith, index = 0)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DevotionalTile(data, actions.onDevotional, index = 1, modifier = Modifier.weight(1f))
                    PrayerTile(actions.onPrayer, index = 2, modifier = Modifier.weight(1f))
                }
                if (sections.personal) CreateAndCircles(actions.onCreate, actions.onCircles, index = 3)
                if (sections.work) WorkTile(data, actions.onWork, index = 4)
            }
            HomeLayout.WORK_PERSONAL -> {
                WorkTile(data, actions.onWork, index = 0)
                CreateAndCircles(actions.onCreate, actions.onCircles, index = 1)
                EveningTile(data, actions.onNextUp, index = 3)
                PlanCard(data.plan, index = 4)
            }
            HomeLayout.PERSONAL -> {
                EveningTile(data, actions.onNextUp, index = 0)
                CreateAndCircles(actions.onCreate, actions.onCircles, index = 1)
                JournalTile(actions.onJournal, index = 3)
                PlanCard(data.plan, index = 4)
            }
            HomeLayout.WORK -> {
                WorkTile(data, actions.onWork, index = 0)
                PlanCard(data.plan, index = 1)
            }
        }
    }
}

/** Create and Circles side by side. */
@Composable
private fun CreateAndCircles(onCreate: () -> Unit, onCircles: () -> Unit, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CreateTile(onCreate, index = index, modifier = Modifier.weight(1f))
        CircleTile(onCircles, index = index + 1, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun FaithTile(data: BentoData, onClick: () -> Unit, index: Int) {
    val days = data.weekCount.coerceIn(0, 7)
    SceneTile(index, SunriseScene, height = 196.dp, scrim = Paper.copy(alpha = 0.6f), tag = "bento_faith", onClick = onClick) {
        TileHeader(Icons.Filled.Church, "Faith", Ink)
        Text("$days of 7 days kept this week", color = InkSoft, fontFamily = InterFamily, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        SegmentBar(days / 7f, BentoColors.Violet, BentoColors.Violet.copy(alpha = 0.18f), modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun DevotionalTile(data: BentoData, onClick: () -> Unit, index: Int, modifier: Modifier = Modifier) {
    SceneTile(index, PaperLightScene, height = 208.dp, scrim = Paper.copy(alpha = 0.45f), tag = "bento_devotional", onClick = onClick, modifier = modifier) {
        TileHeader(Icons.Filled.MenuBook, "Devotional", BentoColors.GoldInk)
        Spacer(Modifier.weight(1f))
        Text(data.devotionalTitle, color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(data.devotionalSub, color = BentoColors.GoldText, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        Pill(if (data.devotionalNew) "New" else "Read", FixedWhite, BentoColors.GoldPill, Modifier.padding(top = 10.dp))
    }
}

@Composable
internal fun PrayerTile(onClick: () -> Unit, index: Int, modifier: Modifier = Modifier) {
    val light = FixedWhite
    SceneTile(index, NightPrayerScene, height = 208.dp, scrim = BentoColors.PrayerScrim.copy(alpha = 0.5f), tag = "bento_prayer", onClick = onClick, modifier = modifier) {
        TileHeader(Icons.Filled.Favorite, "Prayer", light)
        Spacer(Modifier.weight(1f))
        Text("Pray with me", color = light, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text("Requests and answers", color = light.copy(alpha = 0.8f), fontFamily = InterFamily, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        Pill("Pray now", FixedWhite.copy(alpha = 0.2f), light, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun CreateTile(onClick: () -> Unit, index: Int, modifier: Modifier = Modifier) {
    SceneTile(index, WatercolorScene, height = 182.dp, scrim = Paper.copy(alpha = 0.5f), tag = "bento_create", onClick = onClick, modifier = modifier) {
        TileHeader(Icons.Filled.Palette, "Create", BentoColors.ClayInk)
        Spacer(Modifier.weight(1f))
        Text("Start a card", color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text("Share what you're learning", color = BentoColors.ClayText, fontFamily = InterFamily, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun CircleTile(onClick: () -> Unit, index: Int, modifier: Modifier = Modifier) {
    SceneTile(index, CircleRingsScene, height = 182.dp, scrim = Paper.copy(alpha = 0.5f), tag = "bento_circles", onClick = onClick, modifier = modifier) {
        TileHeader(Icons.Filled.Groups, "Circles", BentoColors.TealInk)
        Spacer(Modifier.weight(1f))
        Text("Your circles", color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text("Share and pray together", color = BentoColors.TealText, fontFamily = InterFamily, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        Row {
            listOf(BentoColors.CircleGold, BentoColors.CircleLilac, BentoColors.CircleMint).forEach { c ->
                Box(Modifier.size(26.dp).clip(CircleShape).background(c).border(2.dp, Paper, CircleShape))
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun WorkTile(data: BentoData, onClick: () -> Unit, index: Int) {
    SceneTile(index, WorkGridScene, height = 150.dp, scrim = Color.Transparent, tag = "bento_work", onClick = onClick) {
        TileHeader(Icons.Filled.Work, "Work & projects", BentoColors.IndigoInk)
        Spacer(Modifier.weight(1f))
        Text("Executive pulse", color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Text(
            if (data.todayCount == 0) "Nothing on your calendar today" else "${data.todayCount} on your calendar today",
            color = InkSoft, fontFamily = InterFamily, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp).fillMaxWidth(0.6f)
        )
    }
}

@Composable
private fun EveningTile(data: BentoData, onClick: () -> Unit, index: Int) {
    SceneTile(index, WindowHomeScene, height = 176.dp, scrim = Color.Transparent, tag = "bento_evening", onClick = onClick) {
        TileHeader(Icons.Filled.Home, "Personal", BentoColors.BrownInk)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(0.6f)) {
            Text(data.nextTitle, color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(data.nextWhen, color = BentoColors.BrownText, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Plan my day", color = BentoColors.Violet, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = BentoColors.Violet, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun JournalTile(onClick: () -> Unit, index: Int) {
    SceneTile(index, RuledPaperScene, height = 150.dp, scrim = Color.Transparent, tag = "bento_journal", onClick = onClick) {
        TileHeader(Icons.Filled.EditNote, "Journal", BentoColors.BrownInk)
        Spacer(Modifier.weight(1f))
        Text("Write today's entry", color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("Notes from your day", color = BentoColors.BrownText, fontFamily = InterFamily, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PlanCard(plan: List<PlanLine>, index: Int) {
    Reveal(index) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Paper)
                .border(1.dp, BentoColors.PlanBorder, RoundedCornerShape(28.dp)).padding(16.dp).testTag("bento_plan"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's plan", color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(if (plan.isEmpty()) "" else "${plan.size} today", color = InkSoft, fontFamily = InterFamily, fontSize = 12.sp)
            }
            if (plan.isEmpty()) {
                Text("Nothing scheduled. Record something, or write a note for today.", color = InkSoft, fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp)
            }
            plan.take(4).forEach { line ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = line.onOpen), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(7.dp)).border(1.5.dp, BentoColors.PlanRing, RoundedCornerShape(7.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(line.title, color = Ink, fontFamily = InterFamily, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(line.time, color = InkSoft, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * One illustrated tile: the scene drawn behind, a soft scrim for legibility, and the content on top.
 * Presses ease down and back, and the scene keeps drifting while it is on screen.
 */
@Composable
internal fun SceneTile(
    index: Int,
    scene: Scene,
    height: Dp,
    scrim: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val time = rememberSceneTime()
    Reveal(index, modifier) {
        Box(
            Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(28.dp))
                .bentoPress(onClick).testTag(tag)
        ) {
            Canvas(Modifier.matchParentSize()) { scene(time.value) }
            if (scrim != Color.Transparent) {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(scrim, Color.Transparent))))
            }
            Column(Modifier.fillMaxSize().padding(18.dp), content = content)
        }
    }
}

@Composable
internal fun TileHeader(icon: ImageVector, label: String, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(FixedWhite.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(label, color = ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
internal fun Pill(label: String, background: Color, ink: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, color = ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

/** A thin progress track that fills from empty when it first appears. */
@Composable
private fun SegmentBar(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val fill by animateFloatAsState(
        targetValue = if (shown) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "bento_bar"
    )
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(color))
    }
}

/**
 * Enters with a short stagger: fades up 26 dp. Reduced motion shows it at once.
 */
@Composable
internal fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = rememberHomeReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val density = LocalDensity.current.density
    LaunchedEffect(Unit) {
        if (!reduced && progress.value < 1f) {
            delay((index * 90L).coerceAtMost(720L))
            progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    Box(modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 26f * density
    }) { content() }
}

/** Eases a little smaller while pressed, then springs back; keeps the ripple. */
@Composable
internal fun Modifier.bentoPress(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bento_press"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
}

/** One shareable story card, drawn from today's devotional. */
data class StoryCardData(
    val reference: String,
    val headline: String,
    val body: String,
    val caption: String,
    val onOpen: () -> Unit
)

/**
 * "Stories from today": three 9:16 cards with their own scenes. Tap one to open it; "Share" sends it out.
 */
@Composable
fun StoryCards(cards: List<StoryCardData>, onShareAll: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Stories from today", color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text(
                "Share as image ›", color = BentoColors.Violet, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onShareAll).padding(4.dp)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 14.dp)
        ) {
            itemsIndexed(cards) { i, card -> StoryCardTile(i, card) }
        }
    }
}

@Composable
private fun StoryCardTile(index: Int, card: StoryCardData) {
    val time = rememberSceneTime()
    // Each card has its own scene: sunrise, night sky, then the sage rings.
    val scene: Scene = when (index % 3) {
        0 -> SunriseScene
        1 -> NightPrayerScene
        else -> CircleRingsScene
    }
    val onNight = index % 3 == 1
    val ink = if (onNight) FixedWhite else Ink
    Reveal(index) {
        Box(
            Modifier.width(220.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(30.dp)).bentoPress(card.onOpen)
        ) {
            Canvas(Modifier.matchParentSize()) { scene(time.value) }
            Column(Modifier.fillMaxSize().padding(22.dp)) {
                Text(card.reference.uppercase(), color = ink.copy(alpha = 0.8f), fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text(card.headline, color = ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                if (card.body.isNotBlank()) {
                    Text(card.body, color = ink.copy(alpha = 0.85f), fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.caption, color = ink.copy(alpha = 0.9f), fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Pill("Share", if (onNight) FixedWhite.copy(alpha = 0.22f) else FixedWhite, ink)
                }
            }
        }
    }
}
