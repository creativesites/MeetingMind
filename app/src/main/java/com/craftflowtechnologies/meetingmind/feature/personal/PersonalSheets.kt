package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.feature.personal.garden.Plant
import com.craftflowtechnologies.meetingmind.feature.personal.garden.PlantLook
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.InterFamily
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * "Plant it": a new practice. How often it comes round, an optional nudge, and when to look at how
 * it grew. Missed days rest the plant; nothing resets and nothing withers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantSheet(initialTitle: String, onDismiss: () -> Unit, onPlant: (PlantRequest) -> Unit) {
    val colors = MM.colors
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var rhythm by rememberSaveable { mutableStateOf(PlantRhythm.WEEKDAYS) }
    var nudge by rememberSaveable { mutableStateOf(PlantNudge.NONE) }
    var weeks by rememberSaveable { mutableIntStateOf(2) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp).navigationBarsPadding().testTag("personal_plant_sheet"),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Plant(PlantLook(leaves = 2), animate = false, modifier = Modifier.width(44.dp).height(56.dp))
                Text("Plant it", color = colors.ink, fontFamily = GardenSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(80) },
                label = { Text("What will you practise?") },
                placeholder = { Text("Write tomorrow's three before closing the laptop") },
                singleLine = false, maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.ink, cursorColor = colors.ink),
                modifier = Modifier.fillMaxWidth().testTag("personal_plant_title")
            )
            Section("How often") {
                ChipRow(PlantRhythm.entries, rhythm, { it.label }) { rhythm = it }
            }
            Section("Nudge me") {
                ChipRow(PlantNudge.entries, nudge, { it.label }) { nudge = it }
            }
            Section("Check in with me after") {
                ChipRow(listOf(1, 2, 0), weeks, { if (it == 0) "No end" else if (it == 1) "1 week" else "$it weeks" }) { weeks = it }
            }
            Text(PlantSummary.line(rhythm, weeks), color = colors.inkSecondary, fontFamily = GardenSerif, fontSize = 16.sp, lineHeight = 23.sp)
            GoldButton("Plant it in my garden", enabled = title.isNotBlank(), tag = "personal_plant_confirm") {
                onPlant(PlantRequest(title.trim(), rhythm, nudge, weeks))
            }
        }
    }
}

/**
 * One plant, opened: this week's days, tend it today, and, when its time comes, what next. Letting
 * go is always there but quiet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSheet(
    plant: GardenPlantUi,
    onDismiss: () -> Unit,
    onTend: (Boolean) -> Unit,
    onKeepGoing: () -> Unit,
    onBloom: () -> Unit,
    onLetGo: () -> Unit,
    onOpenSource: (String) -> Unit
) {
    val colors = MM.colors
    var confirmLetGo by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp).navigationBarsPadding().testTag("personal_practice_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Plant(plant.look, animate = false, modifier = Modifier.width(64.dp).height(96.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(plant.title, color = colors.ink, fontFamily = GardenSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp)
                    Text(plant.rhythmLabel + if (plant.bloomed) " · in bloom" else if (plant.resting) " · resting" else "", color = colors.inkSecondary, fontFamily = InterFamily, fontSize = 13.sp)
                }
            }
            if (!plant.bloomed) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        plant.week.forEach { d -> DayChip(d) }
                    }
                    Text(plant.rhythmLine + " · " + plant.windowLine, color = colors.inkSecondary, fontFamily = InterFamily, fontSize = 13.sp)
                }
            } else {
                Text(plant.windowLine, color = colors.inkSecondary, fontFamily = InterFamily, fontSize = 13.sp)
            }
            plant.sourceLine?.let { line ->
                val id = plant.sourceNoteId
                Text(
                    line, color = colors.accent, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    modifier = if (id != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onOpenSource(id) }.padding(vertical = 6.dp) else Modifier
                )
            }
            if (plant.checkInDue && !plant.bloomed) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.goldWash).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("It has taken root. What would you like to do?", color = colors.ink, fontFamily = GardenSerif, fontSize = 18.sp, lineHeight = 24.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietPill("Keep tending", Modifier.weight(1f), "personal_keep", onKeepGoing)
                        QuietPill("It's part of me", Modifier.weight(1f), "personal_bloom", onBloom)
                    }
                }
            } else if (!plant.bloomed) {
                GoldButton(
                    if (plant.tendedToday) "Tended today" else "I did it today",
                    enabled = true, tag = "personal_tend", checked = plant.tendedToday
                ) { onTend(!plant.tendedToday) }
            }
            if (confirmLetGo) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceSunk).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Let it go? It leaves the garden and Tasks. The days you kept stay with your notes.", color = colors.ink, fontFamily = InterFamily, fontSize = 14.sp, lineHeight = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietPill("Let it go", Modifier.weight(1f), "personal_let_go_confirm", onLetGo)
                        QuietPill("Keep it", Modifier.weight(1f), "personal_let_go_cancel") { confirmLetGo = false }
                    }
                }
            } else {
                TextButton(onClick = { confirmLetGo = true }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("personal_let_go")) {
                    Text(if (plant.bloomed) "Take it out of the garden" else "Let it go", color = colors.inkSecondary, fontFamily = InterFamily)
                }
            }
        }
    }
}

@Composable
private fun DayChip(d: DayDotUi) {
    val colors = MM.colors
    val (bg, fg, line) = when (d.state) {
        DayState.KEPT -> Triple(GardenColors.Stem, FixedWhite, GardenColors.Stem)
        DayState.TODAY -> Triple(Color.Transparent, colors.ink, GardenColors.GoldDeep)
        DayState.MISSED -> Triple(colors.surfaceSunk, colors.inkMuted, colors.surfaceSunk)
        DayState.UPCOMING -> Triple(Color.Transparent, colors.inkSecondary, colors.line)
        DayState.OFF -> Triple(Color.Transparent, colors.inkFaint, Color.Transparent)
    }
    Box(Modifier.size(32.dp).clip(CircleShape).background(bg).border(1.5.dp, line, CircleShape), contentAlignment = Alignment.Center) {
        if (d.state == DayState.KEPT) Icon(Icons.Outlined.Check, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        else Text(d.letter, color = fg, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MM.colors.inkSecondary, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        content()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    val colors = MM.colors
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (on) colors.ink else Color.Transparent)
                    .border(1.dp, if (on) colors.ink else colors.line, RoundedCornerShape(22.dp))
                    .semantics { this.selected = on }
                    .clickable(role = Role.RadioButton) { onPick(o) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label(o), color = if (on) colors.onInk else colors.ink, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun GoldButton(text: String, enabled: Boolean, tag: String, checked: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(27.dp))
            .background(if (checked) GardenColors.Stem else if (enabled) GardenColors.Gold else GardenColors.Gold.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checked) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = FixedWhite, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (checked) FixedWhite else GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun QuietPill(text: String, modifier: Modifier, tag: String, onClick: () -> Unit) {
    val colors = MM.colors
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .background(colors.surface).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp).testTag(tag),
        contentAlignment = Alignment.Center
    ) { Text(text, color = colors.ink, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
}
