package com.craftflowtechnologies.meetingmind.feature.learn.ui.explain

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.explain.ExplainPhase
import com.craftflowtechnologies.meetingmind.feature.learn.explain.ExplainUiState
import com.craftflowtechnologies.meetingmind.feature.learn.explain.IdeaCoverage
import com.craftflowtechnologies.meetingmind.feature.learn.explain.IdeaRow
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Test tags for the screen's controls. */
object ExplainAloudTags {
    const val START = "explain_start"
    const val STOP = "explain_stop"
    const val TYPE_INSTEAD = "explain_type_instead"
    const val TYPED = "explain_typed"
    const val SUBMIT_TYPED = "explain_submit_typed"
    const val HINT = "explain_hint"
    const val START_OVER = "explain_start_over"
    const val TRY_AGAIN = "explain_try_again"
    const val DONE = "explain_done"
    const val ALLOW_MIC = "explain_allow_mic"
    const val RATE_PREFIX = "explain_rate_"
    const val PLAY_PREFIX = "explain_play_"
    const val LEVELS = "explain_levels"
}

/**
 * "Explain it aloud", drawn from design/study/ExplainAloud.dc.html. Stateless: the view model produces
 * [ExplainUiState] and this draws it. A null [state] is the quiet loading frame, or [missing] when the
 * concept is no longer there.
 */
@Composable
fun ExplainAloudScreen(
    state: ExplainUiState?,
    modifier: Modifier = Modifier,
    missing: Boolean = false,
    onClose: () -> Unit = {},
    onStart: () -> Unit = {},
    onAllowMicrophone: () -> Unit = {},
    onTypeInstead: () -> Unit = {},
    onStop: () -> Unit = {},
    onStartOver: () -> Unit = {},
    onHint: () -> Unit = {},
    onSubmitTyped: (String) -> Unit = {},
    onTryAgain: () -> Unit = {},
    onRate: (RecallButton) -> Unit = {},
    onPlayIdea: (Int) -> Unit = {},
    onDone: () -> Unit = {}
) {
    StudyTheme {
        val c = MM.colors
        Column(
            modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
        ) {
            TopBar(title = "Explain it aloud", onClose = onClose)
            when {
                state == null && missing -> EmptyState(
                    title = "This idea isn't in your lecture any more.",
                    body = "It may have been renamed or hidden. Go back and pick another one.",
                    modifier = Modifier.weight(1f),
                    action = { SecondaryButton("Close", onClose) }
                )
                state == null -> Box(Modifier.weight(1f).fillMaxWidth())
                state.phase == ExplainPhase.NO_EVIDENCE -> EmptyState(
                    title = "Nothing to explain from yet.",
                    body = "This idea has no definition or lecture words to check against. Ask Zuri about it, or come back after the lecture is processed.",
                    modifier = Modifier.weight(1f),
                    action = { SecondaryButton("Close", onClose) }
                )
                else -> Body(
                    state = state,
                    onStart = onStart,
                    onAllowMicrophone = onAllowMicrophone,
                    onTypeInstead = onTypeInstead,
                    onStop = onStop,
                    onStartOver = onStartOver,
                    onHint = onHint,
                    onSubmitTyped = onSubmitTyped,
                    onTryAgain = onTryAgain,
                    onRate = onRate,
                    onPlayIdea = onPlayIdea,
                    onDone = onDone
                )
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().padding(start = MM.space.xs, end = MM.space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(MMSize.minTouch).testTag("explain_close")) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.ink)
        }
        Text(title, style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.size(MMSize.minTouch))
    }
}

@Composable
private fun Body(
    state: ExplainUiState,
    onStart: () -> Unit,
    onAllowMicrophone: () -> Unit,
    onTypeInstead: () -> Unit,
    onStop: () -> Unit,
    onStartOver: () -> Unit,
    onHint: () -> Unit,
    onSubmitTyped: (String) -> Unit,
    onTryAgain: () -> Unit,
    onRate: (RecallButton) -> Unit,
    onPlayIdea: (Int) -> Unit,
    onDone: () -> Unit
) {
    val c = MM.colors
    val reduced = LocalCompanionReducedMotion.current == true
    val listening = state.phase == ExplainPhase.RECORDING
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Spacer(Modifier.height(MM.space.s))
        if (state.subjectLine.isNotBlank()) {
            Text(state.subjectLine, style = MM.type.caption, color = c.accent)
        }
        Text(
            state.prompt,
            style = MM.type.heading,
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )

        // Zuri, with pulsing rings while the learner talks.
        Box(
            Modifier.padding(top = MM.space.s).semantics {
                contentDescription = when (state.phase) {
                    ExplainPhase.RECORDING -> "Zuri is listening"
                    ExplainPhase.THINKING -> "Zuri is thinking"
                    else -> "Zuri"
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Rings(active = listening, reduced = reduced, size = ZURI_SIZE)
            ZuriSlot(
                page = CompanionPage.PRACTICE,
                size = ZURI_SIZE,
                state = companionState(state),
                level = { state.levels.lastOrNull() ?: 0f }
            )
        }

        when (state.phase) {
            ExplainPhase.RECORDING -> {
                LevelBars(state.levels)
                Text(formatLectureTime(state.elapsedMs), style = MM.type.bodyStrong, color = c.ink,
                    modifier = Modifier.semantics { contentDescription = "Time spoken ${formatLectureTime(state.elapsedMs)}" })
            }
            ExplainPhase.THINKING -> Text(
                "Checking your explanation against the lecture…",
                style = MM.type.secondary, color = c.inkSecondary
            )
            else -> Unit
        }

        if (state.phase != ExplainPhase.RESULT) IdeaChecklist(state.ideas, title = "Key ideas, as you go")

        state.message?.let { StatusLine(kind = StatusKind.Info, text = it) }
        state.hint?.let { Text(it, style = MM.type.secondary, color = c.inkSecondary) }

        when (state.phase) {
            ExplainPhase.READY -> {
                BigButton(icon = Icons.Rounded.Mic, label = "Start explaining", testTag = ExplainAloudTags.START, onClick = onStart)
                TextAction("Type it instead", onClick = onTypeInstead, modifier = Modifier.testTag(ExplainAloudTags.TYPE_INSTEAD))
                PrivacyLine()
            }
            ExplainPhase.NEEDS_MIC -> {
                MMCard {
                    Text("Zuri needs the microphone to hear you", style = MM.type.bodyStrong, color = c.ink)
                    Text(
                        "Your explanation is turned into text on this phone. The audio is not uploaded and is not kept.",
                        style = MM.type.secondary, color = c.inkSecondary,
                        modifier = Modifier.padding(top = MM.space.xs)
                    )
                    Row(Modifier.padding(top = MM.space.m), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton("Allow microphone", onClick = onAllowMicrophone, modifier = Modifier.testTag(ExplainAloudTags.ALLOW_MIC))
                        Spacer(Modifier.width(MM.space.s))
                        TextAction("Type it instead", onClick = onTypeInstead, modifier = Modifier.testTag(ExplainAloudTags.TYPE_INSTEAD))
                    }
                }
            }
            ExplainPhase.RECORDING -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextAction("Start over", onClick = onStartOver, modifier = Modifier.testTag(ExplainAloudTags.START_OVER))
                    BigButton(icon = Icons.Rounded.Stop, label = "Done talking", testTag = ExplainAloudTags.STOP, onClick = onStop, square = true)
                    TextAction("Give a hint", onClick = onHint, modifier = Modifier.testTag(ExplainAloudTags.HINT))
                }
                PrivacyLine()
            }
            ExplainPhase.TYPING -> TypedAnswer(initial = state.transcript, onSubmit = onSubmitTyped)
            ExplainPhase.THINKING -> Unit
            ExplainPhase.RESULT -> ResultPanel(state, onPlayIdea, onTryAgain, onDone)
            ExplainPhase.SELF_RATE -> SelfRate(onRate)
            ExplainPhase.SAVED -> {
                StatusLine(kind = StatusKind.Ok, text = "Saved. Zuri will bring this idea back when it's due.")
                Row(Modifier.padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton("Try again", onClick = onTryAgain, modifier = Modifier.testTag(ExplainAloudTags.TRY_AGAIN))
                    Spacer(Modifier.width(MM.space.s))
                    PrimaryButton("Done", onClick = onDone, modifier = Modifier.testTag(ExplainAloudTags.DONE))
                }
            }
            ExplainPhase.NO_EVIDENCE -> Unit
        }
        Spacer(Modifier.height(MM.space.xl))
    }
}

private val ZURI_SIZE: Dp = 132.dp

private fun companionState(state: ExplainUiState): CompanionState = when (state.phase) {
    ExplainPhase.RECORDING -> CompanionState.LISTENING
    ExplainPhase.THINKING -> CompanionState.THINKING
    ExplainPhase.RESULT -> if (state.allCovered) CompanionState.CELEBRATING else CompanionState.IDLE
    else -> CompanionState.IDLE
}

/** Three rings that pulse out from Zuri while listening. One still ring under reduced motion. */
@Composable
private fun Rings(active: Boolean, reduced: Boolean, size: Dp) {
    if (!active) return
    val accent = MM.colors.accent
    if (reduced) {
        Box(Modifier.size(size).border(2.dp, accent.copy(alpha = 0.55f), CircleShape))
        return
    }
    val transition = rememberInfiniteTransition(label = "explain-rings")
    for (i in 0 until 3) {
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 2400, delayMillis = i * 800, easing = LinearEasing), RepeatMode.Restart),
            label = "explain-ring-$i"
        )
        Box(
            Modifier.size(size).graphicsLayer {
                scaleX = 0.7f + 0.65f * progress
                scaleY = 0.7f + 0.65f * progress
                alpha = 0.55f * (1f - progress)
            }.border(2.dp, accent, CircleShape)
        )
    }
}

@Composable
private fun LevelBars(levels: List<Float>) {
    val accent = MM.colors.accent
    Row(
        Modifier.height(44.dp).testTag(ExplainAloudTags.LEVELS).clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        levels.forEach { level ->
            Box(Modifier.width(4.dp).height(4.dp + 36.dp * level).clip(MM.radius.small).background(accent))
        }
    }
}

@Composable
private fun IdeaChecklist(ideas: List<IdeaRow>, title: String) {
    val c = MM.colors
    MMCard(Modifier.fillMaxWidth()) {
        Text(title, style = MM.type.caption, color = c.inkSecondary)
        ideas.forEach { idea ->
            Row(Modifier.fillMaxWidth().padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                CoverageIcon(idea.coverage)
                Spacer(Modifier.width(MM.space.s))
                Text(idea.label, style = MM.type.body, color = if (idea.coverage == IdeaCoverage.NOT_YET) c.inkSecondary else c.ink, modifier = Modifier.weight(1f))
                Text(idea.coverage.label, style = MM.type.caption, color = if (idea.coverage == IdeaCoverage.COVERED) c.accent else c.inkMuted)
            }
        }
    }
}

@Composable
private fun CoverageIcon(coverage: IdeaCoverage) {
    val c = MM.colors
    when (coverage) {
        IdeaCoverage.COVERED -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.icon))
        IdeaCoverage.PARTLY -> Icon(Icons.Rounded.Adjust, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.icon))
        IdeaCoverage.NOT_YET -> Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(MMSize.icon))
    }
}

@Composable
private fun ResultPanel(state: ExplainUiState, onPlayIdea: (Int) -> Unit, onTryAgain: () -> Unit, onDone: () -> Unit) {
    val c = MM.colors
    state.summary?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MM.type.body, color = c.ink, modifier = Modifier.fillMaxWidth())
    }
    MMCard(Modifier.fillMaxWidth()) {
        Text("Key ideas", style = MM.type.caption, color = c.inkSecondary)
        state.ideas.forEachIndexed { index, idea ->
            Column(Modifier.fillMaxWidth().padding(top = MM.space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverageIcon(idea.coverage)
                    Spacer(Modifier.width(MM.space.s))
                    Text(idea.label, style = MM.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Text(idea.coverage.label, style = MM.type.caption, color = if (idea.coverage == IdeaCoverage.COVERED) c.accent else c.inkMuted)
                }
                if (idea.coverage != IdeaCoverage.COVERED) {
                    idea.quote?.let { quote ->
                        InsetPanel(Modifier.fillMaxWidth().padding(top = MM.space.xs)) {
                            Text("In your lecturer's words", style = MM.type.caption, color = c.inkSecondary)
                            Text("“$quote”", style = MM.type.secondary.copy(fontStyle = FontStyle.Italic), color = c.ink)
                        }
                    }
                    if (idea.startMs != null) {
                        TextAction(
                            "Play this part",
                            onClick = { onPlayIdea(index) },
                            modifier = Modifier.testTag(ExplainAloudTags.PLAY_PREFIX + index).semantics {
                                contentDescription = "Play the lecturer's words for ${idea.label}"
                            }
                        )
                    }
                }
            }
        }
    }
    state.nextPrompt?.let {
        InsetPanel(Modifier.fillMaxWidth()) {
            Text("Next", style = MM.type.caption, color = c.inkSecondary)
            Text(it, style = MM.type.body, color = c.ink)
        }
    }
    Row(Modifier.padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("Try again", onClick = onTryAgain, modifier = Modifier.testTag(ExplainAloudTags.TRY_AGAIN))
        Spacer(Modifier.width(MM.space.s))
        PrimaryButton("Done", onClick = onDone, modifier = Modifier.testTag(ExplainAloudTags.DONE))
    }
}

@Composable
private fun SelfRate(onRate: (RecallButton) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        RecallButton.entries.forEach { button ->
            SecondaryButton(
                button.label,
                onClick = { onRate(button) },
                modifier = Modifier.weight(1f).testTag(ExplainAloudTags.RATE_PREFIX + button.name.lowercase())
            )
        }
    }
}

@Composable
private fun TypedAnswer(initial: String, onSubmit: (String) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text("Your explanation") },
        minLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag(ExplainAloudTags.TYPED)
    )
    PrimaryButton(
        "Check my explanation",
        onClick = { onSubmit(draft) },
        enabled = draft.isNotBlank(),
        modifier = Modifier.testTag(ExplainAloudTags.SUBMIT_TYPED)
    )
}

/** The big round button: start (accent, mic) or stop (ink, square), at least 48 dp, labelled for TalkBack. */
@Composable
private fun BigButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    square: Boolean = false
) {
    val c = MM.colors
    val background = if (square) c.ink else c.accent
    val glyph = if (square) c.onInk else c.onAccent
    Box(
        Modifier
            .size(BIG_BUTTON)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (square) {
            Box(Modifier.size(22.dp).clip(MM.radius.small).background(glyph))
        } else {
            Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(MMSize.icon + 8.dp))
        }
    }
}

@Composable
private fun PrivacyLine() {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().padding(top = MM.space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
        Spacer(Modifier.width(MM.space.xs))
        Text("Your voice stays on this phone", style = MM.type.caption, color = c.inkMuted)
    }
}

private val BIG_BUTTON: Dp = 76.dp
