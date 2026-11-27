package com.craftflowtechnologies.meetingmind.feature.personal.seeds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.feature.personal.GardenSerif
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPalette
import com.craftflowtechnologies.meetingmind.ui.theme.InterFamily
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the seeds sit in the jar, as fractions of its width and height, newest first. Twelve at most. */
private val SEED_SPOTS = listOf(
    0.36f to 0.82f, 0.50f to 0.84f, 0.64f to 0.80f,
    0.42f to 0.68f, 0.58f to 0.66f, 0.30f to 0.56f, 0.70f to 0.54f,
    0.50f to 0.50f, 0.38f to 0.40f, 0.62f to 0.38f, 0.50f to 0.28f, 0.44f to 0.20f
)

private val JAR_WIDTH = 220.dp
private val JAR_HEIGHT = 280.dp
private val SEED_TARGET = 44.dp

/**
 * The seed jar: the ideas the person dropped in, the notes waiting to be sorted, and what to do
 * with them. Stateless: the host holds the view model and passes the state and callbacks down.
 */
@Composable
fun SeedJarScreen(
    state: SeedJarUiState,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onCaptureIdea: () -> Unit,
    onTab: (SeedTab) -> Unit,
    onSelectIdea: (String) -> Unit,
    onMove: (String, NotebookSpace) -> Unit,
    onKeep: (String) -> Unit,
    onMakeTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = GardenColors.palette(state.phase, MM.colors.isDark)
    Box(modifier.fillMaxSize().background(p.soil).testTag("seeds_screen")) {
        if (state.tab == SeedTab.IDEAS) {
            IdeasLayout(state, p, onBack, onOpenNote, onCaptureIdea, onTab, onSelectIdea)
        } else {
            SortLayout(state, p, onBack, onCaptureIdea, onTab, onMove, onKeep, onMakeTask)
        }
    }
}

@Composable
private fun IdeasLayout(
    state: SeedJarUiState,
    p: GardenPalette,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onCaptureIdea: () -> Unit,
    onTab: (SeedTab) -> Unit,
    onSelectIdea: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SkyHeader(p, onBack, onCaptureIdea) {
            JarBlock(state, p, onSelectIdea, onOpenNote)
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 240.dp).background(p.soil).padding(horizontal = 20.dp, vertical = 16.dp)) {
            SeedTabs(state, onTab)
            // Every idea in the jar, newest first: tap to hold it up in the jar's card, or open it.
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.ideas.forEach { idea -> IdeaRow(idea, onSelectIdea, onOpenNote) }
            }
        }
    }
}

@Composable
private fun IdeaRow(idea: SeedIdeaUi, onSelect: (String) -> Unit, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
            .background(GardenColors.SoilGlass).border(1.dp, GardenColors.SoilLine, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.seeds_open)) { onSelect(idea.id); onOpen(idea.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp).testTag("seeds_idea_${idea.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(GardenColors.Gold))
        Column(Modifier.weight(1f)) {
            Text(idea.title, color = GardenColors.SoilInk, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(dateLabel(idea.createdAt), color = GardenColors.SoilInkSoft, fontFamily = InterFamily, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SortLayout(
    state: SeedJarUiState,
    p: GardenPalette,
    onBack: () -> Unit,
    onCaptureIdea: () -> Unit,
    onTab: (SeedTab) -> Unit,
    onMove: (String, NotebookSpace) -> Unit,
    onKeep: (String) -> Unit,
    onMakeTask: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        SkyHeader(p, onBack, onCaptureIdea) {}
        Column(
            Modifier.fillMaxWidth().weight(1f).background(p.soil).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SeedTabs(state, onTab)
            Text(
                stringResource(R.string.seeds_sort_explainer),
                color = GardenColors.SoilInkSoft, fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp
            )
            if (state.toSort.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.seeds_sort_empty),
                        color = GardenColors.SoilInk, fontFamily = GardenSerif, fontSize = 18.sp, textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.toSort, key = { it.id }) { item ->
                        SortCard(item, onMove, onKeep, onMakeTask)
                    }
                }
            }
        }
    }
}

/** The sky: the gradient, the top bar, and whatever the tab puts under it. */
@Composable
private fun SkyHeader(p: GardenPalette, onBack: () -> Unit, onCaptureIdea: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to p.skyTop, 0.5f to p.skyMid, 1f to p.skyLow))
            .statusBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        TopBar(p, onBack, onCaptureIdea)
        content()
    }
}

@Composable
private fun TopBar(p: GardenPalette, onBack: () -> Unit, onCaptureIdea: () -> Unit) {
    val backLabel = stringResource(R.string.seeds_back)
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backLabel }
                .testTag("seeds_back"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = p.ink)
        }
        Text(
            stringResource(R.string.seeds_title),
            color = p.ink, fontFamily = GardenSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        Row(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(GardenColors.Gold)
                .clickable(role = Role.Button, onClick = onCaptureIdea)
                .padding(horizontal = 16.dp)
                .testTag("seeds_new_idea"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = GardenColors.OnGold, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.seeds_new_idea),
                color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
            )
        }
    }
}

/** The jar on the sky, and under it the selected idea's card, or the empty line. */
@Composable
private fun JarBlock(state: SeedJarUiState, p: GardenPalette, onSelect: (String) -> Unit, onOpen: (String) -> Unit) {
    val ideas = state.ideas
    val count = ideas.size
    val selected = ideas.firstOrNull { it.id == state.selectedIdeaId } ?: ideas.firstOrNull()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Jar(ideas, selected?.id, pluralStringResource(R.plurals.seeds_jar_count, count, count), onSelect)
        Spacer(Modifier.height(14.dp))
        if (selected == null) {
            Text(
                stringResource(R.string.seeds_empty_jar),
                color = p.inkSoft, fontFamily = InterFamily, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
            )
        } else {
            IdeaCard(selected, p, onOpen)
        }
    }
}

@Composable
private fun Jar(ideas: List<SeedIdeaUi>, selectedId: String?, label: String, onSelect: (String) -> Unit) {
    val shown = ideas.take(SEED_SPOTS.size)
    BoxWithConstraints(Modifier.width(JAR_WIDTH).height(JAR_HEIGHT).semantics { contentDescription = label }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Glass body, its hairline edge, a shine down the left, and the lid.
            drawRoundRect(
                GardenColors.JarGlass, topLeft = Offset(w * 0.10f, h * 0.13f), size = Size(w * 0.80f, h * 0.84f),
                cornerRadius = CornerRadius(w * 0.22f)
            )
            drawRoundRect(
                GardenColors.JarLine, topLeft = Offset(w * 0.10f, h * 0.13f), size = Size(w * 0.80f, h * 0.84f),
                cornerRadius = CornerRadius(w * 0.22f), style = Stroke(width = 2.dp.toPx())
            )
            drawRoundRect(
                GardenColors.JarShine, topLeft = Offset(w * 0.20f, h * 0.24f), size = Size(w * 0.06f, h * 0.50f),
                cornerRadius = CornerRadius(w * 0.03f)
            )
            drawRoundRect(
                GardenColors.Seed, topLeft = Offset(w * 0.18f, h * 0.02f), size = Size(w * 0.64f, h * 0.10f),
                cornerRadius = CornerRadius(w * 0.03f)
            )
            // One glowing seed per idea; the selected one has a ring.
            shown.forEachIndexed { i, idea ->
                val (fx, fy) = SEED_SPOTS[i]
                val c = Offset(fx * w, fy * h)
                val selected = idea.id == selectedId
                val glow = if (selected) 0.9f else 0.6f
                val halo = 20.dp.toPx()
                drawCircle(
                    Brush.radialGradient(listOf(GardenColors.EmberGlow.copy(alpha = glow), Color.Transparent), center = c, radius = halo),
                    radius = halo, center = c
                )
                drawCircle(if (i % 2 == 0) GardenColors.Gold else GardenColors.GoldDeep, radius = 6.dp.toPx(), center = c)
                if (selected) drawCircle(GardenColors.Gold, radius = 14.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            }
        }
        // The touch targets: 44 dp around each seed, drawn above the canvas.
        shown.forEachIndexed { i, idea ->
            val (fx, fy) = SEED_SPOTS[i]
            val seedLabel = stringResource(R.string.seeds_seed_description, idea.title)
            Box(
                Modifier.offset(x = maxWidth * fx - SEED_TARGET / 2, y = maxHeight * fy - SEED_TARGET / 2)
                    .size(SEED_TARGET).clip(CircleShape)
                    .selectable(selected = idea.id == selectedId, role = Role.Button, onClick = { onSelect(idea.id) })
                    .semantics { contentDescription = seedLabel }
                    .testTag("seeds_seed_${idea.id}")
            )
        }
    }
}

@Composable
private fun IdeaCard(idea: SeedIdeaUi, p: GardenPalette, onOpen: (String) -> Unit) {
    val date = remember(idea.createdAt) { dateLabel(idea.createdAt) }
    val dropped = stringResource(R.string.seeds_dropped_in, date).uppercase()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.glass)
            .border(1.dp, p.glassLine, RoundedCornerShape(24.dp)).padding(18.dp)
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                dropped, color = p.inkSoft, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp, letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                idea.title, color = p.ink, fontFamily = GardenSerif, fontSize = 21.sp, lineHeight = 27.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(GardenColors.Gold)
                .clickable(role = Role.Button) { onOpen(idea.id) }
                .padding(horizontal = 22.dp)
                .testTag("seeds_open"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.seeds_open),
                color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
            )
        }
    }
}

/** Ideas and To sort, as a segmented control. Each segment is a 44 dp tab. */
@Composable
private fun SeedTabs(state: SeedJarUiState, onTab: (SeedTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(GardenColors.SoilGlass)
            .border(1.dp, GardenColors.SoilLine, RoundedCornerShape(28.dp)).padding(4.dp)
    ) {
        TabSegment(
            label = stringResource(R.string.seeds_tab_ideas, state.ideas.size),
            selected = state.tab == SeedTab.IDEAS, tag = "seeds_tab_ideas",
            modifier = Modifier.weight(1f)
        ) { onTab(SeedTab.IDEAS) }
        TabSegment(
            label = stringResource(R.string.seeds_tab_sort, state.toSort.size),
            selected = state.tab == SeedTab.TO_SORT, tag = "seeds_tab_sort",
            modifier = Modifier.weight(1f)
        ) { onTab(SeedTab.TO_SORT) }
    }
}

@Composable
private fun TabSegment(label: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(24.dp))
            .background(if (selected) GardenColors.Gold else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) GardenColors.OnGold else GardenColors.SoilInkSoft,
            fontFamily = InterFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

/** One note to sort: its meta line and title, the suggestion as the main button, then the chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortCard(
    item: SeedSortUi,
    onMove: (String, NotebookSpace) -> Unit,
    onKeep: (String) -> Unit,
    onMakeTask: (String) -> Unit
) {
    val meta = stringResource(R.string.seeds_meta_line, item.kind, dayAndTime(item.createdAt))
    Column(
        Modifier.fillMaxWidth().testTag("seeds_card_${item.id}")
            .clip(RoundedCornerShape(22.dp)).background(GardenColors.SoilGlass)
            .border(1.dp, GardenColors.SoilLine, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(meta, color = GardenColors.SoilInkSoft, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                item.title, color = GardenColors.SoilInk, fontFamily = GardenSerif, fontSize = 18.sp, lineHeight = 24.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        when (val suggestion = item.suggestion) {
            is Suggestion.MoveTo -> PrimaryButton(
                stringResource(R.string.seeds_move_to, suggestion.space.displayName),
                "seeds_move_${suggestion.space.name}_${item.id}"
            ) { onMove(item.id, suggestion.space) }
            Suggestion.MakeTask -> PrimaryButton(stringResource(R.string.seeds_add_to_tasks), "seeds_task_${item.id}") {
                onMakeTask(item.id)
            }
            null -> Unit
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.chipSpaces.forEach { space ->
                SpaceChip(stringResource(R.string.seeds_move_to, space.displayName), "seeds_move_${space.name}_${item.id}") {
                    onMove(item.id, space)
                }
            }
            SpaceChip(stringResource(R.string.seeds_keep_here), "seeds_keep_${item.id}") { onKeep(item.id) }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(GardenColors.Gold)
            .clickable(role = Role.Button, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun SpaceChip(label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(GardenColors.SoilGlass)
            .border(1.dp, GardenColors.SoilLine, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GardenColors.SoilInk, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

/** "3 Nov": the day an idea was dropped in, in the person's locale. */
private fun dateLabel(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "Tue 14:02". */
private fun dayAndTime(millis: Long): String =
    DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
