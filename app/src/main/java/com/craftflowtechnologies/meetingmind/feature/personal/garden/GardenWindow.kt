package com.craftflowtechnologies.meetingmind.feature.personal.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase
import com.craftflowtechnologies.meetingmind.ui.theme.InterFamily
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.time.LocalDateTime

/**
 * A window onto the garden for other homes (Today): the scene at this hour, three small plants,
 * and the day's question. One tap steps outside into Personal.
 */
@Composable
fun GardenWindowTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 232.dp,
    /** Fixed in tests; the phone's clock otherwise. */
    now: LocalDateTime? = null
) {
    val at = now ?: remember { LocalDateTime.now() }
    val phase = GardenTime.phase(at.toLocalTime())
    val palette = GardenColors.palette(phase, MM.colors.isDark)
    val question = GardenQuestions.forDay(at.toLocalDate(), at.toLocalTime())
    val label = if (phase == GardenPhase.DUSK || phase == GardenPhase.NIGHT) {
        stringResource(R.string.window_waiting_evening)
    } else {
        stringResource(R.string.window_waiting_day)
    }
    val description = stringResource(R.string.window_description, label, question)
    val shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 12, bottomEndPercent = 12)
    Box(
        modifier.fillMaxWidth().height(height).clip(shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.window_open_label), onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("garden_window")
    ) {
        GardenScene(palette, fireflies = 3, animate = false, modifier = Modifier.fillMaxSize())
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // The middle stays clear for the question and the button.
            Plant(PlantLook(leaves = 2), animate = false, modifier = Modifier.width(44.dp).height(56.dp))
            Spacer(Modifier.weight(1f))
            Plant(PlantLook(leaves = 4, bud = true), animate = false, modifier = Modifier.width(44.dp).height(72.dp))
            Spacer(Modifier.width(10.dp))
            Plant(PlantLook(leaves = 2, bloom = true), animate = false, modifier = Modifier.width(48.dp).height(72.dp))
        }
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = height * 0.2f, start = 40.dp, end = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.window_title), color = palette.inkSoft, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
            Text(question, color = palette.ink, fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, maxLines = 3)
            Box(
                Modifier.padding(top = 4.dp).heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).background(GardenColors.Gold).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.window_step_outside), color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
    }
}
