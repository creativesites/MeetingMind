package com.example.core.model

/** How the "✨ AI Tools" menu on the transcript workspace groups its actions. */
enum class TranscriptAiToolCategory { TRANSCRIPT, ANALYSIS, UTILITIES }

/**
 * How ready [TranscriptAiToolType] actually is today — deliberately three states, not a boolean,
 * because "needs a new AI engine" and "the data already exists, just needs a menu item" are very
 * different amounts of future work and a UI (or a future session) reading this registry should be
 * able to tell them apart without re-auditing the codebase.
 */
enum class TranscriptAiToolReadiness {
    /** A real, callable use case already exists and can be wired to a menu item as-is. */
    READY,
    /** The underlying data is already computed and persisted elsewhere (during normal processing)
     * — this tool needs a menu item and a place to show what already exists, not a new AI engine. */
    DATA_EXISTS_NEEDS_UI,
    /** No underlying engine exists yet — this is genuinely new work: a prompt contract, a
     * validator (or reuse of [com.example.ai.pipeline.TranscriptQualityValidator]'s pattern), and
     * a use case, following the same architecture [com.example.ai.pipeline.TranscriptAiCleanupEngine]
     * and [com.example.ai.diarization.DiarizationReconciliationEngine] already establish. */
    NOT_STARTED
}

/**
 * The "✨ AI Tools" menu the transcript workspace (currently the Transcript tab) will eventually
 * expose — this enum is architecture prep only: a single source of truth for what the menu will
 * contain and how ready each entry is, so a future session can wire up real UI and engines without
 * re-deciding names, grouping, or scope. **No new AI engine was built for any [NOT_STARTED] entry
 * in this pass** — per the standing "only add UI/architecture necessary to test the current
 * feature, don't build the entire future post-processing suite in one task" constraint. Building
 * one of these out means following [com.example.ai.pipeline.TranscriptAiCleanupEngine]'s
 * established shape: a dedicated engine interface, a real prompt contract with an explicit
 * MUST/MAY/MUST-NOT fidelity contract, validation before anything is accepted, and honest
 * three-tier fallback (rejected/unavailable/never attempted) — never a second, differently-shaped
 * mechanism.
 */
enum class TranscriptAiToolType(
    val category: TranscriptAiToolCategory,
    val label: String,
    /** Plain-language description, matching the Settings screen's own no-jargon copy style. */
    val description: String,
    val readiness: TranscriptAiToolReadiness
) {
    /** Backed today by [com.example.core.domain.ReprocessTranscriptCleanupUseCase] — re-runs
     * cleanup with the user's selected [TranscriptCleanupMode], already reachable via Meeting
     * Detail's "Re-clean Transcript" action. */
    CLEAN_TRANSCRIPT(
        TranscriptAiToolCategory.TRANSCRIPT, "Clean transcript",
        "Removes filler words and cleans up phrasing using your selected cleanup mode.",
        TranscriptAiToolReadiness.READY
    ),
    FIX_TRANSCRIPTION_ERRORS(
        TranscriptAiToolCategory.TRANSCRIPT, "Fix transcription errors",
        "Corrects likely misheard words using the surrounding transcript as evidence.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    IMPROVE_CLARITY(
        TranscriptAiToolCategory.TRANSCRIPT, "Improve clarity",
        "Rewrites hard-to-follow passages into clearer sentences without changing meaning.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    REMOVE_REPETITION(
        TranscriptAiToolCategory.TRANSCRIPT, "Remove repetition",
        "Collapses accidental repeated phrases and restated ideas.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    CONDENSE(
        TranscriptAiToolCategory.TRANSCRIPT, "Condense",
        "Shortens a verbose passage while keeping everything it actually said.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    EXPAND_CONTEXT(
        TranscriptAiToolCategory.TRANSCRIPT, "Expand context",
        "Shows more of the surrounding conversation around a passage.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    /** A general mechanism was deliberately NOT built as a separate engine — Moderate/Aggressive
     * cleanup already permit "correct an obvious ASR mistake when nearby context makes the
     * intended word unambiguous" as part of their own prompt permissiveness (see
     * `docs/AI_ARCHITECTURE.md` §8). A dedicated "replace this term everywhere" action is still
     * genuinely new work: it needs a target term, a scope (this transcript only), and its own
     * validator pass distinct from cleanup's. */
    FIX_TERMINOLOGY(
        TranscriptAiToolCategory.TRANSCRIPT, "Fix terminology",
        "Corrects a specific misspelled name or term everywhere it appears in this transcript.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),

    EXTRACT_KEY_POINTS(
        TranscriptAiToolCategory.ANALYSIS, "Extract key points",
        "Pulls out the main points discussed.",
        TranscriptAiToolReadiness.DATA_EXISTS_NEEDS_UI
    ),
    /** Data already computed by [com.example.ai.llm.RealMeetingIntelligenceEngine] during normal
     * processing and persisted as `DecisionEntity`; already shown on Meeting Detail's own tab. */
    FIND_DECISIONS(
        TranscriptAiToolCategory.ANALYSIS, "Find decisions",
        "Shows the decisions already found in this recording.",
        TranscriptAiToolReadiness.DATA_EXISTS_NEEDS_UI
    ),
    FIND_QUESTIONS(
        TranscriptAiToolCategory.ANALYSIS, "Find questions",
        "Shows the open questions already found in this recording.",
        TranscriptAiToolReadiness.DATA_EXISTS_NEEDS_UI
    ),
    FIND_ACTION_ITEMS(
        TranscriptAiToolCategory.ANALYSIS, "Find action items",
        "Shows the action items already found in this recording.",
        TranscriptAiToolReadiness.DATA_EXISTS_NEEDS_UI
    ),
    FIND_IMPORTANT_MOMENTS(
        TranscriptAiToolCategory.ANALYSIS, "Find important moments",
        "Highlights notable moments in the recording.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    /** Data already computed and persisted as `TopicEntity`. */
    IDENTIFY_TOPICS(
        TranscriptAiToolCategory.ANALYSIS, "Identify topics",
        "Shows the topics already found in this recording.",
        TranscriptAiToolReadiness.DATA_EXISTS_NEEDS_UI
    ),
    FIND_NAMES_ORGANIZATIONS(
        TranscriptAiToolCategory.ANALYSIS, "Find names & organisations",
        "Lists the people and organizations mentioned.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),

    EXPLAIN_THIS(
        TranscriptAiToolCategory.UTILITIES, "Explain this",
        "Explains a selected passage in plain language.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    REWRITE_PROFESSIONALLY(
        TranscriptAiToolCategory.UTILITIES, "Rewrite professionally",
        "Rewrites a passage in a more formal register.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    CREATE_NOTES(
        TranscriptAiToolCategory.UTILITIES, "Create notes",
        "Turns the transcript into structured notes.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    CREATE_OUTLINE(
        TranscriptAiToolCategory.UTILITIES, "Create outline",
        "Produces a hierarchical outline of the recording.",
        TranscriptAiToolReadiness.NOT_STARTED
    ),
    /** [com.example.core.common.MeetingTitleGenerator] already exists and already runs
     * automatically during processing — this entry is about making it callable ON DEMAND from the
     * AI Tools menu, which today it is not. */
    GENERATE_TITLE(
        TranscriptAiToolCategory.UTILITIES, "Generate title",
        "Suggests a title grounded in the transcript.",
        TranscriptAiToolReadiness.NOT_STARTED
    )
}

/** Single source of truth for rendering the "✨ AI Tools" menu — a future UI reads this instead of
 * hardcoding the tool list, so this file stays the one place that list is defined. */
object TranscriptAiToolRegistry {
    fun byCategory(): Map<TranscriptAiToolCategory, List<TranscriptAiToolType>> =
        TranscriptAiToolType.entries.groupBy { it.category }
}
