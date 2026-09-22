package com.example.ai.tools

import com.example.core.model.TranscriptAiToolType
import com.example.core.model.TranscriptSegment

/**
 * The prompt contract for every model-backed transcript tool.
 *
 * ### The contract
 *
 * Every prompt here is built from the same three parts, in the same order:
 *
 * 1. **The fidelity contract** — identical for every tool, never weakened by any of them. It is
 *    what stops a tool from inventing content, and a tool that needed it relaxed would be a tool
 *    that shouldn't exist.
 * 2. **The tool's own instruction** — what this specific tool is for.
 * 3. **The transcript**, with the segment ids the model must cite.
 *
 * This mirrors the shape `TranscriptAiCleanupEngine` and `RealMeetingIntelligenceEngine` already
 * established: a stated contract, a narrow per-task instruction, real data, and structured output
 * — never a free-form request whose result has to be parsed out of prose.
 */
internal object TranscriptToolPrompts {

    /**
     * Never varied per tool. Every clause here exists because its absence is a way a transcript
     * tool can quietly corrupt a recording of something someone actually said.
     */
    const val FIDELITY_CONTRACT = """
You are working with a transcript of a real recording of real people.

You MUST NOT:
- add any fact, name, number, date, decision or commitment that is not in the transcript
- change any quantity, date, time, currency amount, name or technical term
- attribute anything to a speaker who did not say it
- report something as decided or agreed when the transcript only shows it discussed
- answer from general knowledge; only this transcript counts

You MUST:
- cite the id of every transcript paragraph that supports what you report
- leave a list empty when the transcript does not support any entry — an empty result is correct
- keep the speaker's own meaning, even when you change the wording
"""

    /** What this specific tool is being asked to do. */
    fun instructionFor(tool: TranscriptAiToolType): String = when (tool) {
        TranscriptAiToolType.FIX_TRANSCRIPTION_ERRORS ->
            "Speech recognition sometimes mishears a word. Where the surrounding transcript makes " +
                "the intended word unambiguous, correct it. Where it does not, leave the text exactly " +
                "as it is — a wrong correction is worse than an uncorrected mishearing. Change nothing else."

        TranscriptAiToolType.IMPROVE_CLARITY ->
            "Rewrite passages that are hard to follow into clearer sentences. Keep every fact, every " +
                "name and every number, and keep the speaker's own voice and register. Do not shorten " +
                "for the sake of it and do not add explanation the speaker did not give."

        TranscriptAiToolType.REMOVE_REPETITION ->
            "Collapse accidental repetition: a phrase restarted, a word stuttered, the same sentence " +
                "said twice in a row. Deliberate repetition for emphasis is content — keep it. Keep " +
                "everything said only once."

        TranscriptAiToolType.CONDENSE ->
            "Shorten verbose passages while keeping everything the speaker actually said. Remove " +
                "padding and circling, not substance. If a passage is already tight, leave it alone."

        TranscriptAiToolType.REWRITE_PROFESSIONALLY ->
            "Rewrite the passage in a more formal register, as if for a written record. Keep every " +
                "fact and every number. Do not add conclusions, politeness or hedging the speaker " +
                "did not express."

        TranscriptAiToolType.EXPLAIN_THIS ->
            "Explain what this passage means in plain language, for someone who was not in the room. " +
                "Explain only what the passage says. Where it relies on context the transcript does " +
                "not contain, say that rather than filling the gap."

        TranscriptAiToolType.EXTRACT_KEY_POINTS ->
            "List the main points actually discussed, each as one sentence, in the order they came up."

        TranscriptAiToolType.FIND_IMPORTANT_MOMENTS ->
            "List the moments that would matter to someone reviewing this recording: a turning point, " +
                "a commitment, a disagreement, a decision, a strong statement. Say briefly why each " +
                "one matters."

        TranscriptAiToolType.FIND_NAMES_ORGANIZATIONS ->
            "List the people and organizations mentioned. Give each one exactly as it was said. Do not " +
                "expand an abbreviation, correct a spelling, or add anyone you infer was present but " +
                "who is never named."

        TranscriptAiToolType.CREATE_NOTES ->
            "Write structured notes on this recording in Markdown: short headed sections, bullet points " +
                "under them, nothing that was not discussed."

        TranscriptAiToolType.CREATE_OUTLINE ->
            "Write a hierarchical outline of this recording in Markdown, following the order it was " +
                "actually discussed in."

        TranscriptAiToolType.GENERATE_TITLE ->
            "Suggest a short, specific title for this recording — at most eight words, describing what " +
                "it was actually about. No date, no generic word like \"Meeting\" or \"Discussion\" on its own."

        // Tools below never reach this function: they are deterministic, backed by an existing
        // use case, or read data already persisted. The branch exists so adding a tool type
        // without deciding how it runs is a compile error rather than a silent fallthrough.
        TranscriptAiToolType.CLEAN_TRANSCRIPT,
        TranscriptAiToolType.FIX_TERMINOLOGY,
        TranscriptAiToolType.EXPAND_CONTEXT,
        TranscriptAiToolType.FIND_DECISIONS,
        TranscriptAiToolType.FIND_QUESTIONS,
        TranscriptAiToolType.FIND_ACTION_ITEMS,
        TranscriptAiToolType.IDENTIFY_TOPICS ->
            error("${tool.name} does not use a model prompt — see TranscriptToolEngine's dispatch.")
    }

    /** Renders the transcript with the ids the model must cite. */
    fun renderTranscript(segments: List<TranscriptSegment>): String = buildString {
        for (segment in segments) {
            val speaker = segment.speakerName ?: "Unknown speaker"
            appendLine("[${segment.id}] $speaker: ${segment.cleanedText ?: segment.text}")
        }
    }

    /**
     * Schema for a tool that proposes text changes. Segment ids are echoed back so each proposed
     * rewrite is anchored to the paragraph it replaces — a rewrite that cannot be matched to a
     * real segment is discarded rather than applied somewhere plausible.
     */
    const val REVISION_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "revisions": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "segmentId": { "type": "string" },
              "text": { "type": "string" }
            },
            "required": ["segmentId", "text"]
          }
        }
      },
      "required": ["revisions"]
    }
    """

    const val FINDINGS_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "findings": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "text": { "type": "string" },
              "detail": { "type": "string" },
              "sourceSegmentIds": { "type": "array", "items": { "type": "string" } }
            },
            "required": ["text", "sourceSegmentIds"]
          }
        }
      },
      "required": ["findings"]
    }
    """

    const val DOCUMENT_SCHEMA = """
    {
      "type": "object",
      "properties": { "markdown": { "type": "string" } },
      "required": ["markdown"]
    }
    """

    const val TITLE_SCHEMA = """
    {
      "type": "object",
      "properties": { "title": { "type": "string" } },
      "required": ["title"]
    }
    """

    /** How the tool's output should come back. */
    enum class OutputShape { REVISION, FINDINGS, DOCUMENT, TITLE }

    fun outputShapeFor(tool: TranscriptAiToolType): OutputShape = when (tool) {
        TranscriptAiToolType.FIX_TRANSCRIPTION_ERRORS,
        TranscriptAiToolType.IMPROVE_CLARITY,
        TranscriptAiToolType.REMOVE_REPETITION,
        TranscriptAiToolType.CONDENSE,
        TranscriptAiToolType.REWRITE_PROFESSIONALLY -> OutputShape.REVISION

        TranscriptAiToolType.EXTRACT_KEY_POINTS,
        TranscriptAiToolType.FIND_IMPORTANT_MOMENTS,
        TranscriptAiToolType.FIND_NAMES_ORGANIZATIONS -> OutputShape.FINDINGS

        TranscriptAiToolType.EXPLAIN_THIS,
        TranscriptAiToolType.CREATE_NOTES,
        TranscriptAiToolType.CREATE_OUTLINE -> OutputShape.DOCUMENT

        TranscriptAiToolType.GENERATE_TITLE -> OutputShape.TITLE

        else -> error("${tool.name} does not use a model prompt — see TranscriptToolEngine's dispatch.")
    }

    fun schemaFor(tool: TranscriptAiToolType): String = when (outputShapeFor(tool)) {
        OutputShape.REVISION -> REVISION_SCHEMA
        OutputShape.FINDINGS -> FINDINGS_SCHEMA
        OutputShape.DOCUMENT -> DOCUMENT_SCHEMA
        OutputShape.TITLE -> TITLE_SCHEMA
    }

    fun build(tool: TranscriptAiToolType, segments: List<TranscriptSegment>): String = buildString {
        appendLine(FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine("Task: ${instructionFor(tool)}")
        appendLine()
        appendLine("Answer with JSON matching this schema, and nothing else:")
        appendLine(schemaFor(tool).trim())
        appendLine()
        appendLine("Transcript:")
        append(renderTranscript(segments))
    }
}
