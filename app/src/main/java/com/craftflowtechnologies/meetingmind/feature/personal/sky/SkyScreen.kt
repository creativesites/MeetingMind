package com.craftflowtechnologies.meetingmind.feature.personal.sky

import android.text.format.DateUtils
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.feature.personal.GardenSerif
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPalette
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import com.craftflowtechnologies.meetingmind.ui.theme.InterFamily
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.rememberSystemAnimationScale
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.sin

/** The sky's background stars: fixed positions as fractions of the screen, with a twinkle phase each. */
private val BACKDROP_STARS: List<FloatArray> = (0 until 46).map { StarLayout.unitsFor("sky-backdrop-$it", 3) }

/** Constellation tint by index, cycled. */
private fun tintAt(index: Int): Color = GardenColors.SkyTints[index.coerceAtLeast(0) % GardenColors.SkyTints.size]

/** Your sky: the month's reflections as stars, joined into constellations, with the pattern card and Zuri's read. */
@Composable
fun SkyHost(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onUpgrade: () -> Unit,
    onOpenSetup: () -> Unit,
    vm: SkyViewModel = viewModel(factory = SkyViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    SkyScreen(
        state = state,
        animate = rememberSystemAnimationScale() > 0f,
        onBack = onBack,
        onPreviousMonth = vm::previousMonth,
        onNextMonth = vm::nextMonth,
        onSelect = vm::select,
        onOpenNote = onOpenNote,
        onRequestZuri = { vm.requestZuri() },
        onUpgrade = onUpgrade,
        onOpenSetup = onOpenSetup
    )
}

/**
 * The sky, stateless: everything it shows comes from [state]. Motion is off when [animate] is false,
 * so the screenshots are deterministic.
 */
@Composable
fun SkyScreen(
    state: SkyUiState,
    animate: Boolean,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onRequestZuri: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = GardenColors.palette(GardenPhase.NIGHT, MM.colors.isDark)
    var listing by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().testTag("sky_screen")) {
        SkyBackdrop(p, animate, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
        ) {
            SkyHeader(state, p, onBack, onPreviousMonth, onNextMonth)
            SkySky(state, p, animate, onSelect, Modifier.weight(1f).fillMaxWidth())
            if (state.constellations.isNotEmpty()) ChipRow(state, p, onSelect)
            PatternCard(
                state, p, onRead = { listing = true }, onRequestZuri = onRequestZuri,
                onUpgrade = onUpgrade, onOpenSetup = onOpenSetup
            )
        }
    }
    if (listing) {
        state.selected?.let { c ->
            ReadThemSheet(
                name = c.name,
                entries = state.selectedEntries,
                onDismiss = { listing = false },
                onOpenNote = { id -> listing = false; onOpenNote(id) }
            )
        }
    }
}

@Composable
private fun SkyBackdrop(p: GardenPalette, animate: Boolean, modifier: Modifier) {
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "sky")
        val v by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "sky_t")
        v
    } else 0.3f
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(0f to p.skyTop, 0.48f to p.skyMid, 0.78f to p.skyLow, 1f to p.skyLow))
        BACKDROP_STARS.forEach { u ->
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t + u[2])))
            drawCircle(
                GardenColors.Star.copy(alpha = p.stars * twinkle),
                radius = (0.6f + 1.1f * u[2]).dp.toPx(),
                center = Offset(u[0] * size.width, u[1] * size.height)
            )
        }
    }
}

@Composable
private fun SkyHeader(state: SkyUiState, p: GardenPalette, onBack: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val count = state.entries.size
    val monthLine = stringResource(
        R.string.sky_month_line,
        SkyPatterns.monthName(state.month, YearMonth.now()),
        context.resources.getQuantityString(R.plurals.sky_reflections_count, count, count)
    )
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp).testTag("sky_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sky_back), tint = p.ink)
        }
        Text(
            stringResource(R.string.sky_title), color = p.ink, fontFamily = GardenSerif, fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp, lineHeight = 40.sp, modifier = Modifier.semantics { heading() }
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp).testTag("sky_previous")) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.sky_previous_month), tint = p.inkSoft)
            }
            Text(
                monthLine, color = p.inkSoft, fontFamily = InterFamily, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp).testTag("sky_next")) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.sky_next_month), tint = p.inkSoft)
            }
        }
    }
}

/** The stars, their lines, and one tap target per constellation over its middle. */
@Composable
private fun SkySky(state: SkyUiState, p: GardenPalette, animate: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val pulse = if (animate) {
        val transition = rememberInfiniteTransition(label = "newest")
        val v by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "newest_t")
        v
    } else 0.5f
    BoxWithConstraints(modifier.testTag("sky_stars")) {
        val w = maxWidth.value
        val h = maxHeight.value
        val ids = state.entries.map { it.id }
        val spots = remember(ids, w, h) { StarLayout.place(ids, w, h) }
        val starsDescription = stringResource(R.string.sky_stars_ungrouped, state.entries.size)
        Canvas(
            Modifier.fillMaxSize().semantics { contentDescription = starsDescription }
        ) {
            drawSky(state, spots, pulse)
        }
        state.constellations.forEachIndexed { i, c ->
            val pts = c.entryIds.mapNotNull { spots[it] }
            if (pts.isEmpty()) return@forEachIndexed
            val cx = pts.map { it.x }.average().toFloat()
            val cy = pts.map { it.y }.average().toFloat()
            val label = stringResource(R.string.sky_stars_description, c.name, c.entryIds.size)
            Box(
                Modifier
                    .offset(x = (cx - TAP_HALF).dp, y = (cy - TAP_HALF).dp)
                    .size(TAP_SIZE)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = label) { onSelect(c.name) }
                    .semantics { contentDescription = label }
                    .testTag("sky_cluster_${i}")
            )
        }
        if (state.entries.isEmpty() && state.loaded) {
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.sky_empty), color = p.ink, fontFamily = GardenSerif, fontSize = 20.sp,
                    lineHeight = 27.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("sky_empty_text")
                )
            }
        }
    }
}

private val TAP_SIZE = 56.dp
private const val TAP_HALF = 28f

private fun DrawScope.drawSky(state: SkyUiState, spots: Map<String, StarLayout.Point>, pulse: Float) {
    fun at(point: StarLayout.Point) = Offset(point.x.dp.toPx(), point.y.dp.toPx())

    val selected = state.selected
    if (selected != null) {
        val tint = tintAt(state.constellations.indexOf(selected))
        val pts = StarLayout.chain(selected.entryIds.mapNotNull { spots[it] }).map { at(it) }
        for (i in 1 until pts.size) {
            drawLine(tint.copy(alpha = 0.55f), pts[i - 1], pts[i], strokeWidth = 1.dp.toPx())
        }
    }
    val newest = state.entries.lastOrNull()?.id
    val count = state.entries.size
    state.entries.forEachIndexed { index, entry ->
        val point = spots[entry.id] ?: return@forEachIndexed
        val rank = if (count <= 1) 1f else index.toFloat() / (count - 1)
        val group = state.constellations.indexOfFirst { entry.id in it.entryIds }
        val color = if (group >= 0) tintAt(group) else GardenColors.Star
        val inSelected = selected != null && group >= 0 && state.constellations[group].name == selected.name
        val dim = if (selected == null || inSelected) 1f else 0.35f
        val radius = (1.6f + 1.0f * rank).dp.toPx()
        val center = at(point)
        if (entry.id == newest) {
            drawCircle(color.copy(alpha = 0.18f + 0.18f * pulse), radius = radius * (2.6f + 1.2f * pulse), center = center)
        }
        drawCircle(color.copy(alpha = (0.45f + 0.55f * rank) * dim), radius = radius, center = center)
    }
}

/** The chips: one per constellation, a real button each, 44 dp tall. */
@Composable
private fun ChipRow(state: SkyUiState, p: GardenPalette, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.constellations.forEachIndexed { i, c ->
            val selected = c.name == state.selected?.name
            val shape = RoundedCornerShape(22.dp)
            Box(
                Modifier
                    .height(44.dp)
                    .clip(shape)
                    .background(if (selected) tintAt(i) else p.glass)
                    .border(BorderStroke(1.dp, if (selected) tintAt(i) else p.glassLine), shape)
                    .selectable(selected = selected, role = Role.Button, onClick = { onSelect(c.name) })
                    .testTag("sky_chip_${c.name}")
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.sky_chip_label, c.name, c.entryIds.size),
                    color = if (selected) GardenColors.OnGold else p.ink,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/** The glass card at the bottom: the pattern line, "Read them", and Zuri's read (or the locked card). */
@Composable
private fun PatternCard(
    state: SkyUiState,
    p: GardenPalette,
    onRead: () -> Unit,
    onRequestZuri: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenSetup: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(shape)
            // Nearly solid, so background stars never sit on the words.
            .background(p.skyMid.copy(alpha = 0.94f))
            .border(1.dp, p.glassLine, shape)
            .padding(20.dp)
            .testTag("sky_pattern"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.sky_kicker), color = p.inkSoft, fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.2.sp
        )
        val selected = state.selected
        if (selected != null) {
            Text(
                state.patternLines.joinToString(" "), color = p.ink, fontFamily = GardenSerif,
                fontSize = 21.sp, lineHeight = 28.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuietButton(stringResource(R.string.sky_read_them), p, onClick = onRead, tag = "sky_read")
            }
            if (state.zuriAllowed) {
                ZuriSection(state, p, onRequestZuri, onOpenSetup)
            } else {
                LockedCard(p, onUpgrade)
            }
        } else {
            Text(
                stringResource(R.string.sky_no_patterns), color = p.ink, fontFamily = GardenSerif,
                fontSize = 21.sp, lineHeight = 28.sp
            )
        }
    }
}

/**
 * Zuri's read, on this phone only. With no on-device model installed the card says why and offers
 * Setup; otherwise it offers the read and shows the text with the source line "Read on this phone".
 */
@Composable
private fun ZuriSection(state: SkyUiState, p: GardenPalette, onRequest: () -> Unit, onOpenSetup: () -> Unit) {
    val zuri = state.zuri
    val reading = zuri.phase == ZuriPhase.READING
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.route == SkyRoute.NONE) {
            SetupNeeded(p, onOpenSetup)
        } else {
            GoldButton(stringResource(R.string.sky_zuri), enabled = !reading, onClick = onRequest, tag = "sky_zuri")
        }
        when (zuri.phase) {
            ZuriPhase.READING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = p.ink, strokeWidth = 2.dp)
                Text(stringResource(R.string.sky_zuri_reading), color = p.inkSoft, fontFamily = InterFamily, fontSize = 13.sp)
            }
            ZuriPhase.WRITTEN -> Column(Modifier.testTag("sky_zuri_text"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(zuri.text, color = p.ink, fontFamily = GardenSerif, fontSize = 17.sp, lineHeight = 24.sp)
                Text(stringResource(R.string.sky_source_device), color = p.inkSoft, fontFamily = InterFamily, fontSize = 12.sp)
            }
            ZuriPhase.NEEDS_SETUP -> SetupNeeded(p, onOpenSetup)
            ZuriPhase.FAILED -> Text(zuri.text, color = p.inkSoft, fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp)
            ZuriPhase.IDLE -> Unit
        }
    }
}

/** No on-device model yet: say why the read is kept on the phone, and offer Setup. */
@Composable
private fun SetupNeeded(p: GardenPalette, onOpenSetup: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("sky_setup_needed"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.sky_setup_needed), color = p.inkSoft, fontFamily = InterFamily,
            fontSize = 13.sp, lineHeight = 18.sp
        )
        QuietButton(stringResource(R.string.sky_setup), p, onClick = onOpenSetup, tag = "sky_setup")
    }
}

/** The calm locked card: Zuri's read is paid. The free parts above stay as they are. */
@Composable
private fun LockedCard(p: GardenPalette, onUpgrade: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("sky_locked"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.sky_locked), color = p.inkSoft, fontFamily = InterFamily,
            fontSize = 13.sp, lineHeight = 18.sp
        )
        QuietButton(stringResource(R.string.sky_see_plans), p, onClick = onUpgrade, tag = "sky_see_plans")
    }
}

/** A quiet outlined pill: a real button, 48 dp tall. */
@Composable
private fun QuietButton(label: String, p: GardenPalette, onClick: () -> Unit, tag: String) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, p.glassLine, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = p.ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** The gold pill for Zuri's read, the same gold as the lantern. */
@Composable
private fun GoldButton(label: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (enabled) GardenColors.Gold else GardenColors.GoldDeep)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** The list of the constellation's reflections. A tap opens the note and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadThemSheet(
    name: String,
    entries: List<ReflectionEntry>,
    onDismiss: () -> Unit,
    onOpenNote: (String) -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("sky_sheet")) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.sky_sheet_title, name), fontFamily = GardenSerif,
                fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(stringResource(R.string.sky_sheet_empty), fontFamily = InterFamily)
            }
            entries.asReversed().forEach { entry ->
                val date = DateUtils.formatDateTime(
                    context, entry.createdAt,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_WEEKDAY
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onOpenNote(entry.id) }
                        .padding(vertical = 12.dp)
                        .testTag("sky_sheet_entry_${entry.id}")
                ) {
                    Text(date, fontFamily = InterFamily, fontSize = 12.sp)
                    Text(
                        entry.text, fontFamily = GardenSerif, fontSize = 16.sp, lineHeight = 22.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
