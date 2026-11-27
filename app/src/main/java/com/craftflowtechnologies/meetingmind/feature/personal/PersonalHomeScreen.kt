package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenScene
import com.craftflowtechnologies.meetingmind.feature.personal.garden.Plant
import com.craftflowtechnologies.meetingmind.feature.personal.garden.PlantLook
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPalette
import com.craftflowtechnologies.meetingmind.ui.theme.InterFamily
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** Serif for the garden's headings and the question, as the Sanctuary look and journal notes use. */
internal val GardenSerif = FontFamily.Serif

/** How wide a plant is drawn in the garden. */
private val PLANT_WIDTH = 90.dp

/**
 * Personal: "Your garden". A painted scene that follows the time of day (and the theme), the day's
 * question by the lantern, the person's practices as plants, and the seed jar below.
 *
 * Experience first: the prominent button answers the question out loud; writing is the quiet one.
 */
@Composable
fun PersonalHomeScreen(
    state: PersonalUiState,
    animate: Boolean,
    onAnswerAloud: () -> Unit,
    onWriteAnswer: () -> Unit,
    onOpenPlant: (String) -> Unit,
    onPlantNew: () -> Unit,
    onOpenSeedJar: () -> Unit,
    onCaptureIdea: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSky: () -> Unit = {},
    onOpenPractices: () -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    /** Plant one line of a seed packet: the note, its title, the line. */
    onPlantSeed: (noteId: String, noteTitle: String, line: String) -> Unit = { _, _, _ -> },
    onTalkNotes: () -> Unit = {},
    /** The app menu button; a slot so screenshot tests can leave it out. */
    menuButton: @Composable (tint: Color) -> Unit = {}
) {
    val palette = GardenColors.palette(state.phase, MM.colors.isDark)
    BoxWithConstraints(modifier.fillMaxSize().background(palette.soil).testTag("personal_home")) {
        val sceneHeight = maxOf(600.dp, maxHeight * 0.76f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(sceneHeight)) {
                GardenScene(palette, fireflies = state.lights, animate = animate, modifier = Modifier.fillMaxSize())
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp)) {
                    Header(state, palette, onOpenSky, menuButton)
                    Spacer(Modifier.height(20.dp))
                    LanternCard(state, palette, animate, onAnswerAloud, onWriteAnswer, onOpenSky)
                }
                GardenBed(
                    plants = state.plants, palette = palette, animate = animate,
                    onOpenPlant = onOpenPlant, onPlantNew = onPlantNew,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = sceneHeight * 0.05f)
                )
            }
            SoilPanel(state, onOpenSeedJar, onOpenPractices, onCaptureIdea)
            if (state.packets.any { it.unplanted > 0 }) PacketsSection(state.packets, palette.soil, onOpenNote, onPlantSeed)
            TalkNotesRow(palette.soil, onTalkNotes)
        }
    }
}

@Composable
private fun Header(state: PersonalUiState, p: GardenPalette, onOpenSky: () -> Unit, menuButton: @Composable (Color) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(state.whenLine, color = p.inkSoft, fontFamily = InterFamily, fontSize = 13.sp)
            Text(
                stringResource(R.string.garden_title), color = p.ink, fontFamily = GardenSerif, fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp, lineHeight = 40.sp, modifier = Modifier.semantics { heading() }
            )
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(p.glass).border(1.dp, p.glassLine, CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.garden_sky_open_label), onClick = onOpenSky).testTag("personal_open_sky"),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.garden_sky_description), tint = p.ink, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(48.dp).clip(CircleShape).background(p.glass).border(1.dp, p.glassLine, CircleShape), contentAlignment = Alignment.Center) {
            menuButton(p.ink)
        }
    }
}

@Composable
private fun LanternCard(state: PersonalUiState, p: GardenPalette, animate: Boolean, onAnswer: () -> Unit, onWrite: () -> Unit, onOpenSky: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(p.glass)
            .border(1.dp, p.glassLine, RoundedCornerShape(30.dp)).padding(20.dp).testTag("personal_question"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanternGlyph(Modifier.size(16.dp))
            Text(state.questionLabel, color = p.inkSoft, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.2.sp)
        }
        Text(state.question, color = p.ink, fontFamily = GardenSerif, fontSize = 23.sp, lineHeight = 30.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LanternButton(animate, onAnswer)
            Box(
                Modifier.size(52.dp).clip(CircleShape).border(1.dp, p.glassLine, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.garden_write_answer), onClick = onWrite),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.garden_write_answer), tint = p.ink, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.garden_sky_open_label), onClick = onOpenSky)
                    .padding(4.dp).semantics(mergeDescendants = true) {}.testTag("personal_lights")
            ) {
                Text("${state.lights}", color = p.ink, fontFamily = GardenSerif, fontSize = 22.sp, lineHeight = 24.sp)
                Text(pluralStringResource(R.plurals.garden_lights_this_week, state.lights), color = p.inkSoft, fontFamily = InterFamily, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, textAlign = TextAlign.End)
            }
        }
    }
}

/** The gold "Light the lantern" pill, breathing softly while it waits. */
@Composable
private fun LanternButton(animate: Boolean, onClick: () -> Unit) {
    val breath = if (animate) {
        val t = rememberInfiniteTransition(label = "lantern")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
        v
    } else 0.5f
    Row(
        Modifier
            .drawBehind {
                val grow = 6.dp.toPx() + breath * 10.dp.toPx()
                drawRoundRect(
                    Brush.radialGradient(listOf(GardenColors.Gold.copy(alpha = 0.35f + 0.25f * breath), Color.Transparent), center = center, radius = size.width * 0.75f),
                    topLeft = Offset(-grow, -grow), size = Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height)
                )
            }
            .heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(GardenColors.Gold)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 8.dp, end = 20.dp).testTag("personal_answer"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(GardenColors.OnGold), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = GardenColors.Gold, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.garden_lantern_light), color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun LanternGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.8f * s)
        drawLine(GardenColors.Gold, Offset(9f * s, 3f * s), Offset(15f * s, 3f * s), strokeWidth = 1.8f * s)
        drawRoundRect(GardenColors.Gold, topLeft = Offset(7f * s, 6f * s), size = Size(10f * s, 13f * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s), style = stroke)
        drawLine(GardenColors.Gold, Offset(12f * s, 11f * s), Offset(12f * s, 15f * s), strokeWidth = 1.8f * s)
    }
}

/** The plants, rooted on the front hill, with a seed mound to plant another. */
@Composable
private fun GardenBed(
    plants: List<GardenPlantUi>,
    palette: GardenPalette,
    animate: Boolean,
    onOpenPlant: (String) -> Unit,
    onPlantNew: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("personal_garden_bed"),
        horizontalArrangement = Arrangement.spacedBy(if (plants.size <= 2) 28.dp else 14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        plants.forEachIndexed { i, plant -> PlantSpot(plant, palette, animate, i, onOpenPlant) }
        SeedSpot(first = plants.isEmpty(), palette = palette, onClick = onPlantNew)
    }
}

@Composable
private fun PlantSpot(plant: GardenPlantUi, p: GardenPalette, animate: Boolean, index: Int, onOpen: (String) -> Unit) {
    // The box hugs the drawing, so the label sits just above the plant whatever its stage.
    val height: Dp = (PLANT_WIDTH.value * plant.look.boardHeight / 96f).dp
    val openLabel = stringResource(R.string.garden_plant_open_label, plant.title)
    val description = stringResource(R.string.garden_plant_description, plant.title, plant.tag)
    Column(
        Modifier.width(118.dp).clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = openLabel) { onOpen(plant.id) }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("personal_plant_${plant.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlantLabel(plant, p)
        Spacer(Modifier.height(4.dp))
        Plant(plant.look, animate = animate, swayOffset = index, modifier = Modifier.width(PLANT_WIDTH).height(height))
    }
}

/** Gold that stays readable: the lantern gold on a dark scene, a deep gold on a light one. */
private fun goldText(p: GardenPalette): Color = if (p.darkScene) GardenColors.Gold else GardenColors.GoldInk

@Composable
private fun PlantLabel(plant: GardenPlantUi, p: GardenPalette) {
    val highlight = plant.tendedToday || plant.checkInDue
    Column(
        Modifier.widthIn(max = 118.dp).clip(RoundedCornerShape(14.dp)).background(p.glass)
            .border(1.dp, if (highlight) GardenColors.Gold else p.glassLine, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(plant.title, color = p.ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (!plant.bloomed && !plant.tendedToday && !plant.checkInDue && plant.week.isNotEmpty()) {
            WeekDots(plant.week, p.inkSoft, dot = 6.dp, gap = 3.dp)
        } else {
            Text(plant.tag, color = if (highlight) goldText(p) else p.inkSoft, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, maxLines = 1)
        }
    }
}

/** The days of this week, as small dots: kept is filled, today is a ring, the rest are quiet. */
@Composable
internal fun WeekDots(week: List<DayDotUi>, ring: Color, dot: Dp, gap: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        week.filter { it.state != DayState.OFF }.forEach { d ->
            val m = Modifier.size(dot).clip(CircleShape)
            Box(
                when (d.state) {
                    DayState.KEPT -> m.background(GardenColors.Leaf3)
                    DayState.TODAY -> m.border(1.5.dp, GardenColors.Gold, CircleShape)
                    DayState.MISSED -> m.background(ring.copy(alpha = 0.25f))
                    else -> m.border(1.dp, ring.copy(alpha = 0.6f), CircleShape)
                }
            )
        }
    }
}

@Composable
private fun SeedSpot(first: Boolean, palette: GardenPalette, onClick: () -> Unit) {
    Column(
        Modifier.width(if (first) 160.dp else 88.dp).clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.garden_plant_new_label), onClick = onClick)
            .testTag("personal_plant_new"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(palette.glass).border(1.dp, palette.glassLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.ink, modifier = Modifier.size(14.dp))
            Text(stringResource(if (first) R.string.garden_plant_first else R.string.garden_plant), color = palette.ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        Plant(PlantLook(leaves = 0), animate = false, modifier = Modifier.width(80.dp).height(44.dp))
    }
}

/** Below the garden: the seed jar, every practice, and a quick idea. The soil is dark at every hour. */
@Composable
private fun SoilPanel(state: PersonalUiState, onSeedJar: () -> Unit, onPractices: () -> Unit, onCaptureIdea: () -> Unit) {
    val growing = state.plants.count { !it.bloomed }
    Column(Modifier.fillMaxWidth().background(GardenColors.palette(state.phase, MM.colors.isDark).soil).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoilTile(
                title = stringResource(R.string.garden_seed_jar),
                line = if (state.ideas == 0) stringResource(R.string.garden_seed_jar_empty) else pluralStringResource(R.plurals.garden_ideas, state.ideas, state.ideas),
                onClick = onSeedJar, tag = "personal_seed_jar", modifier = Modifier.weight(1f)
            ) { JarGlyph(Modifier.width(40.dp).height(60.dp), filled = state.ideas.coerceAtMost(5)) }
            SoilTile(
                title = stringResource(R.string.garden_practices),
                line = if (growing == 0) stringResource(R.string.garden_practices_empty) else pluralStringResource(R.plurals.garden_growing, growing, growing),
                onClick = onPractices, tag = "personal_practices_tile", modifier = Modifier.weight(1f)
            ) { Plant(PlantLook(leaves = 2), animate = false, modifier = Modifier.width(40.dp).height(60.dp)) }
        }
        SoilRow(Icons.Outlined.Mic, stringResource(R.string.garden_drop_idea), "personal_capture_idea", onCaptureIdea)
    }
}

@Composable
private fun SoilRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, GardenColors.SoilLine, RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = GardenColors.Gold, modifier = Modifier.size(18.dp))
        Text(text, color = GardenColors.SoilInk, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/** Seed packets: lines worth practising from recent talk and how-to notes, one tap to plant. */
@Composable
private fun PacketsSection(packets: List<SeedPacketUi>, soil: Color, onOpenNote: (String) -> Unit, onPlant: (String, String, String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(soil).padding(top = 16.dp, bottom = 8.dp).testTag("personal_packets")) {
        Text(
            stringResource(R.string.garden_packets_title), color = GardenColors.SoilInk, fontFamily = GardenSerif, fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() }
        )
        Text(
            stringResource(R.string.garden_packets_intro), color = GardenColors.SoilInkSoft,
            fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp)
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            packets.filter { it.unplanted > 0 }.forEach { packet -> Packet(packet, onOpenNote, onPlant) }
        }
    }
}

@Composable
private fun Packet(packet: SeedPacketUi, onOpenNote: (String) -> Unit, onPlant: (String, String, String) -> Unit) {
    Column(
        Modifier.width(236.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(GardenColors.Packet).testTag("personal_packet_${packet.noteId}")
    ) {
        Row(
            Modifier.fillMaxWidth().background(GardenColors.PacketBand).clickable(role = Role.Button, onClickLabel = stringResource(R.string.garden_packet_open_note)) { onOpenNote(packet.noteId) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(packet.kind.uppercase(), color = GardenColors.PacketInkSoft, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(packet.title, color = GardenColors.PacketInk, fontFamily = GardenSerif, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            packet.seeds.filter { !it.planted }.take(3).forEachIndexed { i, seed ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(seed.text, color = GardenColors.PacketInk, fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(GardenColors.PacketButton)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.garden_seed_plant_label, seed.text)) { onPlant(packet.noteId, packet.title, seed.text) }
                            .padding(horizontal = 12.dp).testTag("personal_seed_${packet.noteId}_$i"),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.garden_plant), color = GardenColors.PacketButtonInk, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
            }
            val more = packet.unplanted - 3
            if (more > 0) Text(stringResource(R.string.garden_packet_more, more), color = GardenColors.PacketInkSoft, fontFamily = InterFamily, fontSize = 12.sp)
        }
    }
}

/** Where talk notes come from: a YouTube talk or podcast, or recording one. */
@Composable
private fun TalkNotesRow(soil: Color, onTalkNotes: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(soil).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp)) {
        SoilRow(Icons.Outlined.PlayCircle, stringResource(R.string.garden_talk_notes), "personal_talk_notes", onTalkNotes)
    }
}

@Composable
private fun SoilTile(title: String, line: String, onClick: () -> Unit, tag: String, modifier: Modifier, art: @Composable () -> Unit) {
    Row(
        modifier.heightIn(min = 104.dp).clip(RoundedCornerShape(24.dp)).background(GardenColors.SoilGlass).border(1.dp, GardenColors.SoilLine, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp).testTag(tag),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        art()
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(title, color = GardenColors.SoilInk, fontFamily = GardenSerif, fontSize = 19.sp)
            Text(line, color = GardenColors.SoilInkSoft, fontFamily = InterFamily, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
        }
    }
}

/** A small glass jar with up to five glowing seeds. */
@Composable
private fun JarGlyph(modifier: Modifier, filled: Int) {
    Canvas(modifier) {
        val s = size.width / 40f
        drawRoundRect(GardenColors.Seed, topLeft = Offset(10f * s, 2f * s), size = Size(20f * s, 7f * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * s))
        val jar = Path().apply {
            moveTo(8f * s, 12f * s); lineTo(32f * s, 12f * s)
            quadraticTo(36f * s, 12f * s, 36f * s, 16f * s); lineTo(36f * s, 54f * s)
            quadraticTo(36f * s, 60f * s, 30f * s, 60f * s); lineTo(10f * s, 60f * s)
            quadraticTo(4f * s, 60f * s, 4f * s, 54f * s); lineTo(4f * s, 16f * s)
            quadraticTo(4f * s, 12f * s, 8f * s, 12f * s); close()
        }
        drawPath(jar, Color.White.copy(alpha = 0.16f))
        drawPath(jar, Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5f * s))
        val seeds = listOf(Offset(14f, 52f), Offset(22f, 54f), Offset(28f, 50f), Offset(19f, 46f), Offset(26f, 43f))
        seeds.take(filled).forEachIndexed { i, o ->
            val c = Offset(o.x * s, o.y * s)
            drawCircle(Brush.radialGradient(listOf(GardenColors.EmberGlow.copy(alpha = 0.6f), Color.Transparent), center = c, radius = 6f * s), radius = 6f * s, center = c)
            drawOval(if (i % 2 == 0) GardenColors.Gold else GardenColors.GoldDeep, topLeft = Offset(c.x - 3f * s, c.y - 2f * s), size = Size(6f * s, 4f * s))
        }
    }
}
