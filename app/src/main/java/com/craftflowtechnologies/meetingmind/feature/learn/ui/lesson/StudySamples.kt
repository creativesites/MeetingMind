package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import com.craftflowtechnologies.meetingmind.feature.learn.ConceptCard
import com.craftflowtechnologies.meetingmind.feature.learn.DiagramEdge
import com.craftflowtechnologies.meetingmind.feature.learn.DiagramSpec
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainMode
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.ListenState
import com.craftflowtechnologies.meetingmind.feature.learn.ListenVoice
import com.craftflowtechnologies.meetingmind.feature.learn.LessonKitStatus
import com.craftflowtechnologies.meetingmind.feature.learn.LessonSection
import com.craftflowtechnologies.meetingmind.feature.learn.LessonState
import com.craftflowtechnologies.meetingmind.feature.learn.LessonTerm
import com.craftflowtechnologies.meetingmind.feature.learn.QuickCheck
import com.craftflowtechnologies.meetingmind.feature.learn.ReadAlongLine
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import com.craftflowtechnologies.meetingmind.feature.learn.StressReason
import com.craftflowtechnologies.meetingmind.feature.learn.StressedMoment
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learn.TimeRange
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The mockups' sample content (Enzyme kinetics, BIO 201, design/study/Lesson.dc.html and friends). Used by the
 * previews and the screenshot and behaviour tests, so they all show the same lesson.
 */
internal object StudySamples {
    private const val MINUTE = 60_000L

    /** 60 bars, each 0..1: a lecture-shaped waveform. */
    val waveform: List<Float> = List(60) { i ->
        (0.3f + 0.5f * abs(sin(i * 1.3)).toFloat() + 0.2f * abs(cos(i * 0.5)).toFloat()).coerceAtMost(1f)
    }

    const val durationMs: Long = 74 * MINUTE + 2_000L
    const val playheadMs: Long = 31 * MINUTE + 40_000L

    val km = ConceptCard(
        id = "km",
        sessionId = "lecture-7",
        name = "Km",
        subtitle = "Michaelis constant",
        definition = "The substrate concentration at which the reaction runs at half its top speed. Lower Km, tighter grip.",
        lecturerQuote = "Km is not a speed. It's a concentration. Write that down twice.",
        quoteStartMs = 14 * MINUTE + 2_000L,
        quoteEndMs = 14 * MINUTE + 11_000L,
        star = StarState.GROWING,
        courseName = "BIO 201"
    )

    val concepts: List<ConceptCard> = listOf(
        km,
        ConceptCard(
            id = "substrate", sessionId = "lecture-7", name = "Substrate",
            definition = "The molecule an enzyme acts on.", star = StarState.SHINING
        ),
        ConceptCard(
            id = "vmax", sessionId = "lecture-7", name = "Vmax",
            subtitle = "Maximum speed",
            definition = "The top speed of the reaction, reached when every enzyme is busy.", star = StarState.NEW
        ),
        ConceptCard(
            id = "competitive", sessionId = "lecture-7", name = "Competitive inhibitor",
            definition = "An inhibitor that competes with the substrate for the active site.", star = StarState.WORTH_A_LOOK
        ),
        ConceptCard(
            id = "noncompetitive", sessionId = "lecture-7", name = "Non-competitive inhibitor",
            definition = "An inhibitor that binds elsewhere and lowers Vmax.", star = StarState.NEW
        )
    )

    private val sectionOne = LessonSection(
        index = 1,
        title = "Michaelis–Menten",
        body = "Reaction speed rises with substrate concentration, then levels off at Vmax, when every enzyme is busy. " +
            "Km is the concentration that gives half of Vmax. A low Km means the enzyme grabs its substrate easily.",
        terms = listOf(
            LessonTerm("substrate", "substrate"),
            LessonTerm("Vmax", "vmax"),
            LessonTerm("Km", "km")
        ),
        quickCheck = QuickCheck(
            prompt = "What does a low Km tell you about an enzyme?",
            answer = "It grabs its substrate easily. It reaches half speed at a low concentration.",
            conceptId = "km"
        ),
        startMs = 0L
    )

    private val sectionTwo = LessonSection(
        index = 2,
        title = "Inhibition",
        body = "A competitive inhibitor sits in the active site, so more substrate can push it out. " +
            "A non-competitive inhibitor binds elsewhere and lowers Vmax however much substrate you add.",
        terms = listOf(
            LessonTerm("competitive inhibitor", "competitive"),
            LessonTerm("non-competitive inhibitor", "noncompetitive")
        ),
        quickCheck = QuickCheck(
            prompt = "Which inhibitor can be outcompeted by adding more substrate?",
            answer = "The competitive inhibitor. More substrate pushes it out of the active site.",
            conceptId = "competitive"
        ),
        startMs = 31 * MINUTE + 40_000L
    )

    val kit = StudyKit(
        sessionId = "lecture-7",
        summary = "Enzymes speed reactions until every active site is busy. Km tells you how much substrate it takes " +
            "to reach half speed. Inhibitors slow things down in two different ways, and they leave different " +
            "fingerprints on the graph.",
        stressed = listOf(
            StressedMoment(
                startMs = 14 * MINUTE + 2_000L,
                endMs = 14 * MINUTE + 11_000L,
                quote = "If you only remember one thing today, it's what Km actually means.",
                reason = StressReason.REPEATED,
                conceptId = "km"
            ),
            StressedMoment(
                startMs = 41 * MINUTE + 30_000L,
                endMs = 41 * MINUTE + 38_000L,
                quote = "Competitive versus non-competitive. This will be on the exam.",
                reason = StressReason.NAMED_EXAM,
                conceptId = "competitive"
            ),
            StressedMoment(
                startMs = 58 * MINUTE + 10_000L,
                endMs = 58 * MINUTE + 20_000L,
                quote = "Drew the Lineweaver–Burk plot slowly, step by step.",
                reason = StressReason.SLOWED_DOWN
            )
        ),
        sections = listOf(sectionOne, sectionTwo),
        hardToHear = listOf(TimeRange(52 * MINUTE, 55 * MINUTE)),
        modelId = "sample",
        createdAt = 0L
    )

    /** The lesson, ready. Change [status] or [kitOverride] to get the other states. */
    fun lesson(
        status: LessonKitStatus = LessonKitStatus.Ready,
        kitOverride: StudyKit? = kit,
        practiceCount: Int = 8
    ) = LessonState(
        sessionId = "lecture-7",
        noteId = "note-7",
        meetingId = "meeting-7",
        title = "Enzyme kinetics",
        courseName = "BIO 201",
        metaLine = "Tue 12 Nov · 1 h 14 min · 12 ideas",
        durationMs = durationMs,
        kit = kitOverride,
        status = status,
        concepts = concepts,
        waveform = waveform,
        playheadMs = playheadMs,
        practiceCount = practiceCount,
        isPlaying = false
    )

    /** Km with the "Draw it" result: a small flow, drawn from the diagram spec. */
    val drawnExplanation = ExplainState(
        mode = ExplainMode.DRAW,
        diagram = DiagramSpec(
            nodes = listOf("Substrate", "Enzyme–substrate complex", "Product"),
            edges = listOf(
                DiagramEdge(0, 1, "binds"),
                DiagramEdge(1, 2, "turns over")
            ),
            caption = "Km is the substrate level where half of these complexes form."
        ),
        sourceLabel = "14:02 and 16:40"
    )

    val textExplanation = ExplainState(
        mode = ExplainMode.SIMPLER,
        text = "Km is how hungry the enzyme is for its food. A small Km means it is hungry even at low amounts.",
        sourceLabel = "14:02"
    )

    val listenLines: List<ReadAlongLine> = listOf(
        ReadAlongLine("So if you flood the system with substrate, what happens to the inhibitor?", 0L, 6_000L),
        ReadAlongLine("It gets pushed out. A competitive inhibitor sits in the active site, so enough substrate wins the seat back.", 6_000L, 14_000L),
        ReadAlongLine("That's why Vmax doesn't change. Only the apparent Km goes up.", 14_000L, 19_000L),
        ReadAlongLine("Non-competitive inhibitors do something different.", 19_000L, 23_000L)
    )

    fun listen(
        isPlaying: Boolean = false,
        hasLecturerAudio: Boolean = true,
        voice: ListenVoice = ListenVoice.LECTURER
    ) = ListenState(
        title = "Enzyme kinetics",
        courseName = "BIO 201",
        sectionLabel = "02 Inhibition",
        lines = listenLines,
        currentLine = 1,
        positionMs = 41 * MINUTE + 21_000L,
        durationMs = 74 * MINUTE,
        isPlaying = isPlaying,
        speed = 1.25f,
        voice = voice,
        hasLecturerAudio = hasLecturerAudio,
        stressedAtMs = listOf(41 * MINUTE + 30_000L, 58 * MINUTE + 10_000L),
        waveform = List(64) { i -> (0.2f + 0.5f * abs(sin(i * 1.7)).toFloat()).coerceAtMost(1f) }
    )
}
