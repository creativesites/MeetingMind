package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.feature.personal.garden.Plant
import com.craftflowtechnologies.meetingmind.feature.personal.garden.PlantLook
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.InterFamily
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * Every practice in one list: what's growing, what became part of the person, and (folded) what
 * they let go, with "Plant again". The garden shows the plants; this is where they're all findable.
 */
@Composable
fun PracticesScreen(
    state: PersonalUiState,
    onBack: () -> Unit,
    onOpenPlant: (String) -> Unit,
    onPlantNew: () -> Unit,
    onPlantAgain: (LetGoUi) -> Unit
) {
    val colors = MM.colors
    val growing = state.plants.filter { !it.bloomed }
    val bloomed = state.plants.filter { it.bloomed }
    var showLetGo by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().testTag("personal_practices")) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.practices_back), tint = colors.ink) }
            Text(
                stringResource(R.string.practices_title), color = colors.ink, fontFamily = GardenSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() }
            )
            Row(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(GardenColors.Gold)
                    .clickable(role = Role.Button, onClick = onPlantNew).padding(horizontal = 16.dp).testTag("practices_plant"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = GardenColors.OnGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.practices_plant), color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        if (state.plants.isEmpty() && state.letGo.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp).testTag("practices_empty"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Plant(PlantLook(leaves = 0), animate = false, modifier = Modifier.width(96.dp).height(52.dp))
                Text(stringResource(R.string.practices_empty_title), color = colors.ink, fontFamily = GardenSerif, fontSize = 22.sp)
                Text(
                    stringResource(R.string.practices_empty_body),
                    color = colors.inkSecondary, fontFamily = InterFamily, fontSize = 14.sp, lineHeight = 20.sp
                )
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (growing.isNotEmpty()) {
                item(key = "h_growing") { Header(stringResource(R.string.practices_growing_header, growing.size)) }
                items(growing, key = { it.id }) { PracticeRow(it, onOpenPlant) }
            }
            if (bloomed.isNotEmpty()) {
                item(key = "h_bloomed") { Header(stringResource(R.string.practices_bloomed_header, bloomed.size)) }
                items(bloomed, key = { it.id }) { PracticeRow(it, onOpenPlant) }
            }
            if (state.letGo.isNotEmpty()) {
                item(key = "h_letgo") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClickLabel = stringResource(if (showLetGo) R.string.practices_hide else R.string.practices_show)) { showLetGo = !showLetGo }
                            .padding(horizontal = 4.dp).testTag("practices_letgo_toggle"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.practices_let_go_header, state.letGo.size), color = colors.inkSecondary, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Icon(if (showLetGo) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.inkSecondary)
                    }
                }
                if (showLetGo) items(state.letGo, key = { "l_" + it.id }) { LetGoRow(it, onPlantAgain) }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, color = MM.colors.inkSecondary, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        modifier = Modifier.padding(top = 14.dp, start = 4.dp).semantics { heading() }
    )
}

@Composable
private fun PracticeRow(plant: GardenPlantUi, onOpen: (String) -> Unit) {
    val colors = MM.colors
    val description = stringResource(R.string.practices_row_description, plant.title, plant.rhythmLabel, plant.tag)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).border(1.dp, colors.lineSoft, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.practices_open)) { onOpen(plant.id) }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 12.dp).testTag("practices_row_${plant.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(colors.surfaceSunk), contentAlignment = Alignment.BottomCenter) {
            Plant(plant.look, animate = false, modifier = Modifier.width(36.dp).height(48.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(plant.title, color = colors.ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (plant.bloomed) plant.windowLine else stringResource(R.string.practices_row_meta, plant.rhythmLabel, plant.tag),
                color = colors.inkSecondary, fontFamily = InterFamily, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (!plant.bloomed && plant.week.isNotEmpty()) WeekDots(plant.week, colors.inkSecondary, dot = 8.dp, gap = 4.dp)
        }
    }
}

@Composable
private fun LetGoRow(item: LetGoUi, onPlantAgain: (LetGoUi) -> Unit) {
    val colors = MM.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceSunk).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, color = colors.ink, fontFamily = InterFamily, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.practices_days_kept, item.keptDays, item.keptDays), color = colors.inkMuted, fontFamily = InterFamily, fontSize = 12.sp)
        }
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, colors.line, RoundedCornerShape(22.dp))
                .clickable(role = Role.Button, onClick = { onPlantAgain(item) }).padding(horizontal = 14.dp).testTag("practices_again_${item.id}"),
            contentAlignment = Alignment.Center
        ) { Text(stringResource(R.string.practices_plant_again), color = colors.ink, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
    }
}
