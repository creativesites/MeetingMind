package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.feature.learn.NextStep
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeState
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.RoundResult
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * One practice round, one item at a time. Stateless: the view model produces [PracticeState] and this draws it.
 * Shows [RoundDoneScreen] when the round is over, a calm panel when there is nothing to practise, and a quiet
 * skeleton while loading.
 */
@Composable
fun PracticeScreen(
    state: PracticeState,
    onAnswerChoice: (Int) -> Unit,
    onNotSure: () -> Unit,
    onReveal: () -> Unit,
    onRate: (RecallButton) -> Unit,
    onContinue: () -> Unit,
    onClose: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit,
    onDone: () -> Unit,
    onOpenNextStep: (NextStep) -> Unit,
    onMakeCard: () -> Unit,
    sky: @Composable (ignite: Set<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    StudyTheme {
        val result = state.result
        val item = state.current
        when {
            result != null -> RoundDoneScreen(
                result = result,
                onDone = onDone,
                onOpenNextStep = onOpenNextStep,
                onMakeCard = onMakeCard,
                sky = sky,
                modifier = modifier
            )
            state.emptyMessage != null -> PracticeEmpty(state.emptyMessage, onClose, modifier)
            state.loading || item == null -> PracticeSkeleton(onClose, modifier)
            else -> Column(
                modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding().navigationBarsPadding()
            ) {
                PracticeHeader(state.index, state.total, onClose)
                when (item) {
                    is PracticeItem.Choice -> ChoiceItem(
                        item = item,
                        index = state.index,
                        feedback = state.feedback,
                        onAnswerChoice = onAnswerChoice,
                        onNotSure = onNotSure,
                        onContinue = onContinue,
                        onPlaySource = onPlaySource
                    )
                    is PracticeItem.Recall -> RecallItem(
                        item = item,
                        revealed = state.revealed,
                        recallHints = state.recallHints,
                        onReveal = onReveal,
                        onRate = onRate,
                        onPlaySource = onPlaySource
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeEmpty(message: String, onClose: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding().navigationBarsPadding()) {
        PracticeHeader(index = 0, total = 0, onClose = onClose)
        EmptyState(
            title = "Nothing to practise yet.",
            body = message,
            modifier = Modifier.weight(1f),
            illustration = { ZuriSlot(CompanionPage.PRACTICE, size = PracticeSize.zuriEmpty, state = CompanionState.IDLE) },
            action = { SecondaryButton("Close", onClose) }
        )
    }
}

/** A quiet placeholder while the round loads: three grey bars, no spinner. */
@Composable
private fun PracticeSkeleton(onClose: () -> Unit, modifier: Modifier) {
    val c = MM.colors
    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        PracticeHeader(index = 0, total = 0, onClose = onClose)
        Column(Modifier.padding(horizontal = MM.space.l, vertical = MM.space.xl)) {
            Box(Modifier.fillMaxWidth().height(28.dp).clip(MM.radius.card).background(c.track))
            Box(Modifier.padding(top = MM.space.m).fillMaxWidth().height(96.dp).clip(MM.radius.card).background(c.track))
            Box(Modifier.padding(top = MM.space.m).fillMaxWidth().height(56.dp).clip(MM.radius.card).background(c.track))
        }
    }
}
