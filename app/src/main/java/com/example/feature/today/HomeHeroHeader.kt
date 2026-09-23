package com.example.feature.today

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.identity.AppIdentity
import com.example.core.identity.Avatar
import com.example.core.identity.LocalAppLook
import com.example.core.timeline.TimeOfDaySky
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

val InterFamily = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold)
)
val OutfitFamily = FontFamily(
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold)
)

private val InkNavy = Color(0xFF0F172A)
private val Slate = Color(0xFF64748B)
private val Hairline = Color(0xFFE2E8F0)

/** What the floating tile under the hero shows. */
data class HeroTile(val label: String, val title: String, val subtitle: String, val icon: ImageVector, val accent: Color, val onClick: () -> Unit)

/**
 * Home's immersive header (PLAN_V2 F1), after the design the user supplied.
 *
 * It scrolls with the page. The hero is a lit, floating stage whose sky and orb follow the real
 * time of day — a sun rising, arcing and setting, a moon and stars at night:
 * - press and hold tilts the card toward your finger, then it springs back;
 * - the orb bobs over its cast shadow, and layers shift at different rates;
 * - as the page scrolls, the card recedes (tilts back, shrinks, fades);
 * - the "Up next" tile floats over the card's edge on its own plane.
 */
@Composable
fun HomeHeroHeader(
    identity: AppIdentity,
    greeting: String,
    contextLine: String?,
    streakLabel: String,
    weekLabel: String,
    inboxCount: Int,
    showSwitch: Boolean,
    switchLabel: String,
    tile: HeroTile,
    listState: LazyListState,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
    onInbox: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixes the sky to a moment (previews and screenshots); null follows the clock. */
    at: Calendar? = null
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TopRow(identity, inboxCount, showSwitch, switchLabel, onAvatar, onSearch, onInbox, onSwitch)
        Spacer(Modifier.height(18.dp))
        HeroStage(greeting, contextLine, streakLabel, weekLabel, identity, tile, listState, at)
    }
}

@Composable
private fun TopRow(
    identity: AppIdentity,
    inboxCount: Int,
    showSwitch: Boolean,
    switchLabel: String,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
    onInbox: () -> Unit,
    onSwitch: () -> Unit
) {
    val look = LocalAppLook.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val plain = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val dateLabel = remember { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(50.dp)
                .border(1.5.dp, Brush.sweepGradient(listOf(look.warm, Color.White, look.accent, look.warm)), CircleShape)
                .padding(3.dp)
        ) {
            Avatar(identity, 44.dp, Modifier.testTag("home_avatar"), onClick = onAvatar)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(plain + (identity.firstName?.let { ", $it" } ?: ""), color = Slate, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1)
            Text(dateLabel, color = InkNavy, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = CircleShape, color = Color.White.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White, Hairline))), shadowElevation = 10.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(4.dp)) {
                DockButton(Color.Transparent, "home_search_button", onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search everything", tint = InkNavy, modifier = Modifier.size(19.dp))
                }
                DockButton(Color.Transparent, "home_inbox_button", onInbox) {
                    BadgedBox(badge = {
                        if (inboxCount > 0) Badge(containerColor = look.accent) { Text("$inboxCount", color = Color.White, fontSize = 10.sp) }
                    }) { Icon(Icons.Outlined.Notifications, contentDescription = "Inbox", tint = InkNavy, modifier = Modifier.size(19.dp)) }
                }
                if (showSwitch) DockButton(look.accentSoft, "home_switch_button", onSwitch) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "Switch to $switchLabel", tint = look.accent, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun DockButton(background: Color, tag: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(38.dp).clip(CircleShape).background(background).clickable(onClick = onClick).testTag(tag),
        content = content
    )
}

@Composable
private fun HeroStage(
    greeting: String,
    contextLine: String?,
    streakLabel: String,
    weekLabel: String,
    identity: AppIdentity,
    tile: HeroTile,
    listState: LazyListState,
    at: Calendar?
) {
    val context = LocalContext.current
    val look = LocalAppLook.current
    val scope = rememberCoroutineScope()
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val reducedMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }

    // The sky for this minute.
    val now = at ?: Calendar.getInstance()
    val sky = remember(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE) / 5, identity.look) {
        TimeOfDaySky.at(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), warm = identity.look == com.example.core.identity.LookAndFeel.SANCTUARY)
    }

    val idle = rememberInfiniteTransition(label = "hero_idle")
    val float = idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orb_float"
    )
    val twinkle = idle.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "twinkle")
    val still = remember { mutableStateOf(0f) }
    val bob: State<Float> = if (reducedMotion) still else float
    val sparkle: State<Float> = if (reducedMotion) still else twinkle

    // How far the page has scrolled past the hero: 0 at rest, 1 once it's gone.
    val scroll by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 900f).coerceIn(0f, 1f)
        }
    }

    val cardShape = RoundedCornerShape(36.dp)
    val top = Color(sky.skyTop)
    val bottom = Color(sky.skyBottom)
    val stars = remember { List(28) { Triple(Random(it * 31 + 7).nextFloat(), Random(it * 17 + 3).nextFloat() * 0.7f, Random(it).nextFloat()) } }

    Box(Modifier.fillMaxWidth().height(338.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(300.dp)
                .graphicsLayer {
                    rotationX = rotX.value + scroll * 14f
                    rotationY = rotY.value
                    cameraDistance = 14f * density
                    val s = 1f - scroll * 0.06f
                    scaleX = s; scaleY = s
                    alpha = 1f - scroll * 0.35f
                    translationY = scroll * 120f
                }
                .shadow(28.dp, cardShape, ambientColor = bottom.copy(alpha = 0.35f), spotColor = bottom.copy(alpha = 0.6f))
                .clip(cardShape)
                .drawBehind { drawSky(top, bottom, sky, stars, sparkle.value) }
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.04f))), cardShape)
                .pointerInput(Unit) {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    detectTapGestures(onPress = { touch ->
                        val nx = (touch.x / w - 0.5f) * 2f
                        val ny = (touch.y / h - 0.5f) * 2f
                        scope.launch { rotY.animateTo(nx * 9f, spring(stiffness = Spring.StiffnessMedium)) }
                        scope.launch { rotX.animateTo(-ny * 7f, spring(stiffness = Spring.StiffnessMedium)) }
                        tryAwaitRelease()
                        scope.launch { rotY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                        scope.launch { rotX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                    })
                }
                .testTag("home_hero")
        ) {
            // The orb travels its arc across the card through the day.
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = -rotY.value * 2.2f * density
                    translationY = scroll * 60f + rotX.value * 2f * density
                }
            ) { drawOrb(sky, bob.value) }

            Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.62f).padding(start = 24.dp, top = 30.dp)) {
                Text(
                    greeting, color = Color.White, fontFamily = if (identity.faithFirst) look.headingFont else OutfitFamily,
                    fontWeight = FontWeight.Medium, fontSize = 27.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (contextLine != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(contextLine, color = Color.White.copy(alpha = 0.78f), fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 52.dp)) {
                GlassChip(Icons.Outlined.LocalFireDepartment, streakLabel, Color(sky.orbBody))
                GlassChip(if (identity.faithFirst) Icons.Outlined.AutoStories else Icons.Outlined.EditNote, weekLabel, Color(sky.orbBody))
            }
        }

        // Up next, floating over the card's lower edge.
        Surface(
            onClick = tile.onClick, shape = RoundedCornerShape(26.dp), color = Color.White,
            border = BorderStroke(1.dp, Hairline), shadowElevation = 16.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).fillMaxWidth().height(76.dp)
                .graphicsLayer {
                    translationX = rotY.value * -2.6f * density
                    translationY = -rotX.value * 2.2f * density
                }
                .testTag("home_up_next_tile")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(tile.accent, lerp(tile.accent, InkNavy, 0.6f))))
                ) { Icon(tile.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tile.label, color = Slate, fontFamily = InterFamily, fontSize = 11.5.sp, maxLines = 1)
                    Text(tile.title, color = InkNavy, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tile.subtitle, color = Slate, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(CircleShape).background(InkNavy)) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun GlassChip(icon: ImageVector, label: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1)
    }
}

/** Icons and labels for the tile, by what's next. */
object HeroTiles {
    fun eventIcon() = Icons.Outlined.Event
    fun recordIcon() = Icons.Outlined.Mic
    fun noteIcon() = Icons.Outlined.EditNote
    fun faithIcon() = Icons.Outlined.AutoStories
}

// ─────────────────────────────────────────────────────────────────────────────
// Drawing: the sky and the orb
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSky(top: Color, bottom: Color, sky: TimeOfDaySky, stars: List<Triple<Float, Float, Float>>, twinkle: Float) {
    drawRect(Brush.linearGradient(listOf(top, lerp(top, bottom, 0.6f), bottom), start = Offset.Zero, end = Offset(size.width * 0.6f, size.height)))

    // Stars at night, each twinkling on its own phase.
    if (sky.stars > 0f) {
        stars.forEach { (x, y, phase) ->
            val t = ((twinkle + phase) % 1f)
            val a = sky.stars * (0.35f + 0.65f * kotlin.math.abs(t * 2f - 1f))
            drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), radius = (0.8f + phase * 1.4f).dp.toPx(), center = Offset(x * size.width, y * size.height))
        }
    }

    // Light pooling around where the orb is.
    val orb = orbCenter(sky, 0f)
    drawCircle(
        Brush.radialGradient(listOf(Color(sky.glow).copy(alpha = if (sky.isMoon) 0.22f else 0.38f), Color.Transparent), center = orb, radius = size.width * 0.55f),
        radius = size.width * 0.55f, center = orb
    )

    // Faint arcs from the lower left give the plane depth.
    val origin = Offset(size.width * 0.05f, size.height * 1.02f)
    listOf(0.35f, 0.55f, 0.78f).forEachIndexed { i, f ->
        drawCircle(Color.White.copy(alpha = 0.06f - i * 0.015f), radius = size.width * f, center = origin, style = Stroke(1.dp.toPx()))
    }
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = size.height * 0.45f))
}

/**
 * Where the orb sits: it travels an arc across the right of the card (rising low on the left of
 * that region, peaking high, setting low on the right), clear of the greeting on the left.
 */
private fun DrawScope.orbCenter(sky: TimeOfDaySky, bob: Float): Offset {
    val x = size.width * (0.64f + 0.24f * sky.arc)
    val horizon = size.height * 0.64f
    val peak = size.height * 0.24f
    val y = horizon - (horizon - peak) * sky.elevation
    return Offset(x, y + bob * 6.dp.toPx())
}

private fun DrawScope.drawOrb(sky: TimeOfDaySky, bob: Float) {
    val r = size.minDimension * 0.15f
    val c = orbCenter(sky, bob)
    val light = Color(sky.orbLight)
    val body = Color(sky.orbBody)
    val deep = Color(sky.orbDeep)

    // Cast shadow on the "floor", shrinking as the orb rises.
    val shadowCenter = Offset(c.x, size.height * 0.86f)
    val shadowRadius = r * 1.5f * (1f + 0.08f * bob) * (1f - 0.4f * sky.elevation)
    scale(1f, 0.22f, pivot = shadowCenter) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent), center = shadowCenter, radius = shadowRadius), shadowRadius, shadowCenter)
    }

    val ringW = r * 3.1f
    val ringH = ringW * 0.26f
    val ringTopLeft = Offset(c.x - ringW / 2f, c.y - ringH / 2f)
    val ringSize = Size(ringW, ringH)
    val ringStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    if (!sky.isMoon) rotate(-16f, pivot = c) {
        drawArc(Color.White.copy(alpha = 0.28f), 180f, 180f, false, ringTopLeft, ringSize, style = ringStroke)
    }

    // Ambient glow (a halo for the moon).
    drawCircle(Brush.radialGradient(listOf(Color(sky.glow).copy(alpha = if (sky.isMoon) 0.35f else 0.5f), Color.Transparent), center = c, radius = r * 2.1f), r * 2.1f, c)

    // Body, lit from the upper left.
    drawCircle(Brush.radialGradient(0f to light, 0.45f to body, 1f to deep, center = Offset(c.x - r * 0.35f, c.y - r * 0.40f), radius = r * 1.55f), r, c)

    if (sky.isMoon) {
        // Soft craters.
        listOf(Triple(0.30f, -0.20f, 0.22f), Triple(-0.25f, 0.28f, 0.16f), Triple(0.05f, 0.45f, 0.10f), Triple(-0.38f, -0.30f, 0.09f)).forEach { (dx, dy, rr) ->
            drawCircle(deep.copy(alpha = 0.28f), radius = r * rr, center = Offset(c.x + r * dx, c.y + r * dy))
        }
    }

    // Terminator: darkens the lower right so it reads as a sphere.
    drawCircle(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.30f), center = Offset(c.x - r * 0.2f, c.y - r * 0.25f), radius = r * 1.35f), r, c)

    // Specular highlight.
    val hl = Offset(c.x - r * 0.42f, c.y - r * 0.48f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (sky.isMoon) 0.6f else 0.9f), Color.Transparent), center = hl, radius = r * 0.38f), r * 0.38f, hl)

    if (!sky.isMoon) rotate(-16f, pivot = c) {
        drawArc(Color.White.copy(alpha = 0.9f), 0f, 180f, false, ringTopLeft, ringSize, style = ringStroke)
    }
}
