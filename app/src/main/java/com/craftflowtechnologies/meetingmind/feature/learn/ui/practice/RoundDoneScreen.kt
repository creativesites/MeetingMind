package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.OneShotKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.feature.learn.NextStep
import com.craftflowtechnologies.meetingmind.feature.learn.RoundResult
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.StudyFixedColors
import kotlinx.coroutines.delay

/**
 * The end of a practice round (RoundDone.dc.html): a night-sky hero with Zuri proud (celebrating when every due card
 * is cleared), three stat tiles, one good next step, and the two footer actions.
 *
 * [sky] draws the star field behind Zuri. It is given the names in [RoundResult.newStars]: the sky's ignite set is
 * meant to hold concept ids, so the caller should map names to ids if it wants the new stars to light up.
 */
@Composable
fun RoundDoneScreen(
    result: RoundResult,
    onDone: () -> Unit,
    onOpenNextStep: (NextStep) -> Unit,
    onMakeCard: () -> Unit,
    sky: @Composable (ignite: Set<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    StudyTheme {
        val c = MM.colors
        val pose = rememberRoundPose(result.allCleared)
        Column(modifier.fillMaxSize().background(c.background)) {
            RoundHero(result, pose, sky)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = MM.space.l)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MM.space.l),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    StatTile("${result.backTomorrow}", "back tomorrow", Modifier.weight(1f))
                    StatTile("${result.minutes} min", "well spent", Modifier.weight(1f))
                    StatTile("${result.daysThisWeek} ${if (result.daysThisWeek == 1) "day" else "days"}", "this week", Modifier.weight(1f))
                }
                result.nextStep?.let { NextStepRow(it, onOpenNextStep) }
            }
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MM.space.l, vertical = MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                InkButton("Done for today", onDone)
                TextButton(
                    onClick = onMakeCard,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
                    colors = ButtonDefaults.textButtonColors(contentColor = c.ink)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(MMSize.icon))
                    Spacer(Modifier.width(MM.space.s))
                    Text("Make a study-win card", style = MM.type.bodyStrong)
                }
            }
        }
    }
}

/** The night hero: sky behind, Zuri in the middle, "Round done." and the summary line. Bottom corners rounded. */
@Composable
private fun RoundHero(result: RoundResult, pose: CompanionState, sky: @Composable (ignite: Set<String>) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(MM.radius.heroBottom).background(StudyFixedColors.Night)) {
        Box(Modifier.matchParentSize()) { sky(result.newStars.toSet()) }
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = MM.space.l, vertical = MM.space.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ZuriSlot(CompanionPage.PRACTICE, size = PracticeSize.zuriHero, state = pose)
            Text(
                "Round done.",
                style = MM.type.display,
                color = StudyFixedColors.Ink,
                modifier = Modifier.padding(top = MM.space.s).semantics { heading() }
            )
            Text(
                roundSummary(result),
                style = MM.type.body,
                color = StudyFixedColors.InkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MM.space.xs)
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    MMCard(modifier, contentPadding = MM.space.m) {
        Text(value, style = MM.type.title, color = MM.colors.ink)
        Text(label, style = MM.type.caption, color = MM.colors.inkSecondary)
    }
}

/** "One good next step": a gold-wash row that opens the lesson section. */
@Composable
private fun NextStepRow(step: NextStep, onOpen: (NextStep) -> Unit) {
    val c = MM.colors
    Text(
        "One good next step",
        style = MM.type.bodyStrong,
        color = c.inkSecondary,
        modifier = Modifier
            .padding(start = MM.space.l, end = MM.space.l, top = MM.space.xl, bottom = MM.space.s)
            .semantics { heading() }
    )
    Surface(
        onClick = { onOpen(step) },
        shape = MM.radius.card,
        color = c.goldWash,
        modifier = Modifier.padding(horizontal = MM.space.m).fillMaxWidth().heightIn(min = MMSize.minTouch)
    ) {
        Row(
            Modifier.padding(MM.space.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Box(
                Modifier.size(44.dp).clip(MM.radius.pill).background(c.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = c.gold, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                Text(step.title, style = MM.type.bodyStrong, color = c.ink)
                if (step.detail.isNotBlank()) Text(step.detail, style = MM.type.secondary, color = c.goldInk)
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = c.goldInk,
                modifier = Modifier.size(MMSize.icon)
            )
        }
    }
}

/** The hero's Zuri pose: PROUD for the round, CELEBRATING when every due card is cleared; back to idle after the one-shot. */
@Composable
private fun rememberRoundPose(allCleared: Boolean): CompanionState {
    var pose by remember(allCleared) {
        mutableStateOf(if (allCleared) CompanionState.CELEBRATING else CompanionState.PROUD)
    }
    LaunchedEffect(allCleared) {
        delay(if (allCleared) OneShotKind.CELEBRATING.durationMs else OneShotKind.PROUD.durationMs)
        pose = CompanionState.IDLE
    }
    return pose
}

private fun roundSummary(result: RoundResult): String {
    val stars = result.newStars.size
    val starWord = if (stars == 1) "new star" else "new stars"
    return "${result.firstTryCorrect} of ${result.total} on the first try · $stars $starWord"
}
