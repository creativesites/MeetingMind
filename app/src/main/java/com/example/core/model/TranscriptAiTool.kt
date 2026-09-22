package com.example.core.model

/** How the "✨ AI Tools" menu on the transcript workspace groups its actions. */
enum class TranscriptAiToolCategory { TRANSCRIPT, ANALYSIS, UTILITIES }

/**
 * What runs a [TranscriptAiToolType].
 *
 * Three states rather than a boolean because the differences are ones a caller acts on: a
 * database read is instant and always available, a deterministic use case cannot fail for want of
 * a model, and a model-backed tool needs one and can report that it has none.
 */
enum class TranscriptAiToolReadiness {
    /** Backed by a dedicated, deterministic use case of its own. */
    READY,
    /** Reads back data the processing pipeline already computed and persisted. No model runs. */
    DATA_EXISTS_NEEDS_UI,
    /** Runs through [com.example.ai.tools.TranscriptToolEngine]: the shared fidelity contract, a
     * tool-specific instruction, structured output, and validation before the user sees anything. */
    MODEL_BACKED
}

/**
 * The "✨ AI Tools" menu on the transcript workspace: the single source of truth for what the menu
 * contains, how it is grouped, and what backs each entry.
 *
 * Every entry here runs. [TranscriptAiToolReadiness] no longer describes how finished a tool is —
 * it describes *what kind of thing* runs it, which is what a caller actually needs to know:
 * a dedicated use case, a database read, or the shared model-backed
 * [com.example.ai.tools.TranscriptToolEngine]. Nothing in this menu is a placeholder, and nothing
 * fabricates a result: a model-backed tool with no available model reports that, the same way
 * every other AI surface in this app does.
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
        TranscriptAiToolReadiness.MODEL_BACKED
    ),
    IMPROVE_CLARITY(
        TranscriptAiToolCategory.TRANSCRIPT, "Improve clarity",
        "Rewrites hard-to-follow passages into clearer sentences without changing meaning.",
        TranscriptAiToolReadiness.MODEL_BACKED
    ),
    REMOVE_REPETITION(
        TranscriptAiToolCategory.TRANSCRIPT, "Remove repetition",
        "Collapses accidental repeated phrases and restated ideas.",
        TranscriptAiToolReadiness.MODEL_BACKED
    ),
    CONDENSE(
        TranscriptAiToolCategory.TRANSCRIPT, "Condense",
        "Shortens a verbose passage while keeping everything it actually said.",
        TranscriptAiToolReadiness.MODEL_BACKED
    ),
    /** Deterministic: widens a selection by whole paragraphs on each side. No model involved —
     * see [com.example.ai.tools.DeterministicTranscriptTools.expandContext]. */
    EXPAND_CONTEXT(
        TranscriptAiToolCategory.TRANSCRIPT, "Expand context",
        "Shows more of the surrounding conversation around a passage.",
        TranscriptAiToolReadiness.READY
    ),
    /** Backed by [com.example.core.domain.FixTerminologyUseCase] (Phase 15 §6) — deliberately not
     * a new LLM prompt contract. Moderate/Aggressive cleanup already permit "correct an obvious
     * ASR mistake when nearby context makes the intended word unambiguous" (see
     * `docs/AI_ARCHITECTURE.md` §8); this tool is the deterministic complement: it applies every
     * correction [com.example.core.repository.VocabularyRepository] has already learned (via
     * Replace All) to this transcript in one pass, via the same exact-match replace Replace All
     * itself uses — never a fuzzy/AI-guessed substitution. */
    FIX_TERMINOLOGY(
        TranscriptAiToolCategory.TRANSCRIPT, "Fix terminology",
        "Applies every correction you've taught MeetingMind to this transcript.",
        TranscriptAiToolReadiness.READY
    ),

    EXTRACT_KEY_POINTS(
        TranscriptAiToolCategory.ANALYSIS, "Extract key points",
        "Pulls out the main points discussed.",
        TranscriptAiToolReadiness.MODEL_BACKED
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
        TranscriptAiToolReadiness.MODEL_BACKED
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
        TranscriptAiToolReadiness.MODEL_BACKED
    ),

    EXPLAIN_THIS(
        TranscriptAiToolCategory.UTILITIES, "Explain this",
        "Explains a selected passage in plain language.",
        TranscriptAiToolReadiness.MODEL_BACKED
    ),
    REWRITE_PROFESSIONALLY(
        TranscriptAiToolCategory.UTILITIES, "Rewrite professionally",
        "Rewrites a passage in a more formal register.",
        TranscriptAiToolReadiness.MODEL_BACKED
    ),
    CREATE_NOTES(
        TranscriptAiToolCategory.UTILITIES, "Create notes",
        "Turns the transcript into structured notes.",
        TranscriptAiToolReadiness.MODEL_BACKED
    ),
    CREATE_OUTLINE(
        TranscriptAiToolCategory.UTILITIES, "Create outline",
        "Produces a hierarchical outline of the recording.",
        TranscriptAiToolReadiness.MODEL_BACKED
    ),
    /** Title generation also runs automatically during processing; this is the on-demand entry
     * point. Both paths validate the candidate through the same
     * [com.example.core.common.MeetingTitleGenerator], so an on-demand title is held to exactly
     * the standard an automatic one is. */
    GENERATE_TITLE(
        TranscriptAiToolCategory.UTILITIES, "Generate title",
        "Suggests a title grounded in the transcript.",
        TranscriptAiToolReadiness.MODEL_BACKED
    )
}

/** Single source of truth for rendering the "✨ AI Tools" menu — a future UI reads this instead of
 * hardcoding the tool list, so this file stays the one place that list is defined. */
object TranscriptAiToolRegistry {
    fun byCategory(): Map<TranscriptAiToolCategory, List<TranscriptAiToolType>> =
        TranscriptAiToolType.entries.groupBy { it.category }
}
