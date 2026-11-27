package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainMode
import com.craftflowtechnologies.meetingmind.feature.learn.LessonKitStatus
import com.craftflowtechnologies.meetingmind.feature.learn.LessonSection
import com.craftflowtechnologies.meetingmind.feature.learn.LessonState
import com.craftflowtechnologies.meetingmind.feature.learn.LessonTerm
import com.craftflowtechnologies.meetingmind.feature.learn.QuickCheck
import com.craftflowtechnologies.meetingmind.feature.learn.StressedMoment
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.TimeRange
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlin.math.ceil

/**
 * The Study lesson page (design/study/Lesson.dc.html). The lecture is the page: a summary to hear, what the lecturer
 * stressed (each one plays from its moment), the sections with tappable terms, a quick check after each section,
 * and a bottom bar with Listen, the Practice pill and Ask.
 *
 * Stateless: [state] and [explain] come from the view model. [openConceptId] opens the concept sheet.
 */
@Composable
fun LessonScreen(
    state: LessonState,
    explain: ExplainState,
    openConceptId: String?,
    onBack: () -> Unit,
    onPlayFrom: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onHearSummary: () -> Unit,
    onOpenConcept: (String) -> Unit,
    onCloseConcept: () -> Unit,
    onExplain: (String, ExplainMode) -> Unit,
    onQuizConcept: (String) -> Unit,
    onReportConcept: (String) -> Unit,
    onListen: () -> Unit,
    onPractice: () -> Unit,
    onAsk: () -> Unit,
    onGenerate: () -> Unit,
    onFixAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudyTheme {
        val c = MM.colors
        val kit = state.kit
        val openConcept = openConceptId?.let { id -> state.concepts.firstOrNull { it.id == id } }
        val zuri = if (state.status is LessonKitStatus.Generating) CompanionState.THINKING else CompanionState.CURIOUS
        Box(modifier.fillMaxSize().background(c.background)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
                LessonTopBar(courseName = state.courseName, onBack = onBack)
                LessonHeader(title = state.title, metaLine = state.metaLine, zuriState = zuri)
                LectureCard(state = state, onPlayFrom = onPlayFrom, onTogglePlay = onTogglePlay)
                if (kit != null && state.status == LessonKitStatus.Ready) {
                    LessonBody(
                        kit = kit,
                        onHearSummary = onHearSummary,
                        onPlayFrom = onPlayFrom,
                        onOpenConcept = onOpenConcept,
                        onFixAudio = onFixAudio
                    )
                } else {
                    LessonStatusPanel(status = state.status, onGenerate = onGenerate)
                }
            }
            LessonBottomBar(
                practiceCount = state.practiceCount,
                onListen = onListen,
                onPractice = onPractice,
                onAsk = onAsk,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            if (openConcept != null) {
                ConceptSheet(
                    concept = openConcept,
                    explain = explain,
                    onDismiss = onCloseConcept,
                    onPlayQuote = onPlayFrom,
                    onExplain = { onExplain(openConcept.id, it) },
                    onQuizMe = { onQuizConcept(openConcept.id) },
                    onNotRight = { onReportConcept(openConcept.id) }
                )
            }
        }
    }
}

@Composable
private fun LessonTopBar(courseName: String?, onBack: () -> Unit) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().padding(start = MM.space.s, end = MM.space.s, top = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink)
        }
        Text(
            courseName.orEmpty(),
            style = MM.type.caption.copy(fontFamily = FontFamily.Monospace),
            color = c.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        // Balances the back button so the course label sits centred.
        Spacer(Modifier.size(MMSize.minTouch))
    }
}

@Composable
private fun LessonHeader(title: String, metaLine: String, zuriState: CompanionState) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().padding(start = MM.space.xl, end = MM.space.l, top = MM.space.s),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            Text(title, style = MM.type.display, color = c.ink, modifier = Modifier.semantics { heading() })
            Text(metaLine, style = MM.type.secondary, color = c.inkMuted)
        }
        ZuriSlot(page = CompanionPage.STUDY_HOME, size = 44.dp, state = zuriState)
    }
}

/** The ribbon card: the whole lecture with its stressed moments, and a play or pause button. */
@Composable
private fun LectureCard(state: LessonState, onPlayFrom: (Long) -> Unit, onTogglePlay: () -> Unit) {
    val c = MM.colors
    val stressed = state.kit?.stressed?.map { it.startMs }.orEmpty()
    MMCard(
        modifier = Modifier.padding(start = MM.space.l, end = MM.space.l, top = MM.space.l),
        contentPadding = MM.space.l
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("The lecture, at a glance", style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            if (stressed.isNotEmpty()) {
                Text(stressedCountLabel(stressed.size), style = MM.type.caption, color = c.goldInk)
                Spacer(Modifier.width(MM.space.xs))
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause the lecture" else "Play the lecture",
                    tint = c.accent
                )
            }
        }
        LectureRibbon(
            waveform = state.waveform,
            positionMs = state.playheadMs,
            durationMs = state.durationMs,
            stressedAtMs = stressed,
            onSeek = onPlayFrom
        )
    }
}

@Composable
private fun LessonBody(
    kit: StudyKit,
    onHearSummary: () -> Unit,
    onPlayFrom: (Long) -> Unit,
    onOpenConcept: (String) -> Unit,
    onFixAudio: () -> Unit
) {
    val c = MM.colors
    val allTerms = kit.sections.flatMap { it.terms }
    Column(Modifier.fillMaxWidth().padding(top = MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.xl)) {
        HeroCard(Modifier.padding(horizontal = MM.space.l), contentPadding = MM.space.l) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("In 30 seconds", style = MM.type.bodyStrong, color = c.accent, modifier = Modifier.weight(1f))
                PrimaryButton("Hear it", onClick = onHearSummary, leadingIcon = Icons.Rounded.PlayArrow)
            }
            Spacer(Modifier.height(MM.space.s))
            Text(linkTerms(kit.summary, allTerms, c.accent, onOpenConcept), style = MM.type.body, color = c.ink)
        }
        if (kit.stressed.isNotEmpty()) StressedMoments(kit.stressed, onPlayFrom)
        kit.sections.forEach { section -> SectionBlock(section, onOpenConcept) }
        hardToHearLine(kit.hardToHear)?.let { line ->
            StatusLine(
                kind = StatusKind.Info,
                text = line,
                modifier = Modifier.padding(horizontal = MM.space.l),
                actionLabel = "Listen and fix",
                onAction = onFixAudio
            )
        }
    }
}

@Composable
private fun StressedMoments(moments: List<StressedMoment>, onPlayFrom: (Long) -> Unit) {
    val c = MM.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MM.space.l),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            FourPointStar(color = c.gold, dimension = 14.dp)
            Text(
                "What your lecturer stressed",
                style = MM.type.heading,
                color = c.ink,
                modifier = Modifier.semantics { heading() }
            )
        }
        moments.forEach { moment -> StressedRow(moment, onPlayFrom) }
    }
}

@Composable
private fun StressedRow(moment: StressedMoment, onPlayFrom: (Long) -> Unit) {
    val c = MM.colors
    val time = formatLectureTime(moment.startMs)
    MMCard(contentPadding = MM.space.m) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            SecondaryButton(
                text = time,
                onClick = { onPlayFrom(moment.startMs) },
                modifier = Modifier.semantics { contentDescription = "Play from $time" },
                leadingIcon = Icons.Rounded.PlayArrow
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                Text(
                    "“${moment.quote}”",
                    style = MM.type.body.copy(fontStyle = FontStyle.Italic),
                    color = c.ink
                )
                Text(moment.reason.label, style = MM.type.caption, color = c.goldInk)
            }
        }
    }
}

@Composable
private fun SectionBlock(section: LessonSection, onOpenConcept: (String) -> Unit) {
    val c = MM.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MM.space.l),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Text(
                "%02d".format(section.index),
                style = MM.type.caption.copy(fontFamily = FontFamily.Monospace),
                color = c.accent
            )
            Text(
                section.title,
                style = MM.type.title,
                color = c.ink,
                modifier = Modifier.semantics { heading() }
            )
        }
        Text(linkTerms(section.body, section.terms, c.accent, onOpenConcept), style = MM.type.body, color = c.ink)
        section.quickCheck?.let { QuickCheckCard(it) }
    }
}

/** One folded recall question. Reveal shows the answer inline; the local state is enough here. */
@Composable
private fun QuickCheckCard(check: QuickCheck) {
    val c = MM.colors
    var revealed by rememberSaveable(check.prompt) { mutableStateOf(false) }
    MMCard(contentPadding = MM.space.l) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
            Text("Quick check", style = MM.type.bodyStrong, color = c.accent)
        }
        Text(check.prompt, style = MM.type.bodyStrong, color = c.ink)
        if (revealed) {
            Text("Answer", style = MM.type.caption, color = c.inkMuted)
            Text(check.answer, style = MM.type.body, color = c.ink)
        } else {
            SecondaryButton("Reveal", onClick = { revealed = true })
        }
    }
}

/** What the page shows instead of the lesson while it is not ready. Each state says what happened, plainly. */
@Composable
private fun LessonStatusPanel(status: LessonKitStatus, onGenerate: () -> Unit) {
    when (status) {
        is LessonKitStatus.Generating -> GeneratingPanel(status.step)
        LessonKitStatus.Ready -> GeneratingPanel("Getting the lesson ready")
        is LessonKitStatus.NeedsAi -> EmptyState(
            title = "Set up AI to make this lesson",
            body = status.message,
            modifier = Modifier.padding(top = MM.space.xl)
        )
        is LessonKitStatus.Failed -> EmptyState(
            title = "This lesson didn't finish",
            body = status.message,
            modifier = Modifier.padding(top = MM.space.xl),
            action = { SecondaryButton("Try again", onClick = onGenerate) }
        )
        LessonKitStatus.NoTranscript -> EmptyState(
            title = "No words to read yet",
            body = "This lecture is still being processed, or nothing was heard. The lesson appears once there are words to work from.",
            modifier = Modifier.padding(top = MM.space.xl)
        )
    }
}

/** A calm, static skeleton while the lesson is made. No animation: the caption says what is happening. */
@Composable
private fun GeneratingPanel(step: String) {
    val c = MM.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.xl),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Text("Making your lesson", style = MM.type.heading, color = c.ink, modifier = Modifier.semantics { heading() })
        Text(step, style = MM.type.secondary, color = c.inkSecondary)
        listOf(1f, 0.85f, 0.6f).forEach { fraction ->
            Box(
                Modifier.fillMaxWidth(fraction).height(14.dp).clip(MM.radius.small).background(c.track)
            )
        }
    }
}

/** The bottom bar: Listen, the one strong Practice pill with its count, and Ask (Zuri's orb). */
@Composable
private fun LessonBottomBar(
    practiceCount: Int,
    onListen: () -> Unit,
    onPractice: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val floating = with(MM.elevation) { Modifier.floatingShadow(MM.radius.pill) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = MM.space.m, end = MM.space.m, bottom = MM.space.l)
            .then(floating)
            .background(c.surfaceRaised, MM.radius.pill)
            .border(MMSize.hairline, c.line, MM.radius.pill)
            .height(68.dp)
            .padding(horizontal = MM.space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        BarItem(label = "Listen", onClick = onListen, modifier = Modifier.width(76.dp)) {
            Icon(Icons.Rounded.Headphones, contentDescription = null, tint = c.ink, modifier = Modifier.size(MMSize.icon))
        }
        PrimaryButton(
            text = "Practice · $practiceCount",
            onClick = onPractice,
            modifier = Modifier.weight(1f)
        )
        BarItem(label = "Ask", onClick = onAsk, modifier = Modifier.width(76.dp)) {
            ZuriOrbGlyph(dimension = 22.dp)
        }
    }
}

@Composable
private fun BarItem(label: String, onClick: () -> Unit, modifier: Modifier, icon: @Composable () -> Unit) {
    Column(
        modifier
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.pill)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Text(label, style = MM.type.caption, color = MM.colors.ink)
    }
}

/** Splits the honest line out so the copy is testable. Null when nothing was hard to hear. */
internal fun hardToHearLine(ranges: List<TimeRange>): String? {
    if (ranges.isEmpty()) return null
    if (ranges.size > 1) return "Parts of this recording were hard to hear. Questions skip them."
    val range = ranges.single()
    val from = range.startMs / 60_000L
    val to = ceil(range.endMs / 60_000.0).toLong()
    return "Minutes $from–$to were hard to hear. Questions skip that part."
}

internal fun stressedCountLabel(count: Int): String =
    if (count == 1) "1 stressed moment" else "$count stressed moments"

/**
 * Renders [text] with every [terms] occurrence (whole words, ignoring case) as a link that opens its concept.
 * Earlier terms win where two overlap, so a longer term is never cut in half.
 */
internal fun linkTerms(text: String, terms: List<LessonTerm>, linkColor: Color, onOpen: (String) -> Unit): AnnotatedString {
    if (text.isEmpty() || terms.isEmpty()) return AnnotatedString(text)
    class Span(val start: Int, val end: Int, val conceptId: String)
    val spans = mutableListOf<Span>()
    terms.filter { it.text.isNotBlank() }.sortedByDescending { it.text.length }.forEach { term ->
        val pattern = "(?<![\\p{L}\\p{N}])" + Regex.escape(term.text) + "(?![\\p{L}\\p{N}])"
        Regex(pattern, RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (spans.none { start < it.end && it.start < end }) spans += Span(start, end, term.conceptId)
        }
    }
    spans.sortBy { it.start }
    return buildAnnotatedString {
        var cursor = 0
        spans.forEach { span ->
            append(text.substring(cursor, span.start))
            withLink(
                LinkAnnotation.Clickable(
                    tag = span.conceptId,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    linkInteractionListener = { onOpen(span.conceptId) }
                )
            ) {
                append(text.substring(span.start, span.end))
            }
            cursor = span.end
        }
        append(text.substring(cursor))
    }
}
