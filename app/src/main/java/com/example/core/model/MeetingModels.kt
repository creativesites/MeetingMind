package com.example.core.model

/**
 * What the user is actually capturing. MeetingMind is a general-purpose voice capture tool, not a
 * meeting-only recorder — this drives both UI copy (no hardcoded "Meeting" language) and the
 * focus guidance given to [com.example.ai.llm.RealMeetingIntelligenceEngine]'s extraction prompt.
 * [GENERAL] is the default for "Quick Record" when the user skips picking a type.
 */
enum class RecordingType(val displayName: String, val shortDescription: String) {
    MEETING("Meeting", "Track decisions and action items"),
    INTERVIEW("Interview", "Capture questions, answers, and notable quotes"),
    LECTURE("Lecture", "Capture key concepts and structure"),
    VOICE_MEMO("Voice Memo", "A quick personal note"),
    IDEA("Idea", "A single thought worth keeping"),
    BRAINSTORM("Brainstorm", "Capture generated ideas, even unfinished ones"),
    DICTATION("Dictation", "Clean transcription, minimal analysis"),
    CONVERSATION("Conversation", "General conversation summary"),
    RESEARCH("Research", "Notes and findings"),
    JOURNAL("Journal", "A personal, private entry"),
    CUSTOM("Custom", "Tell MeetingMind what to focus on"),
    GENERAL("General", "No specific focus");

    /**
     * Additional guidance appended to the Meeting Intelligence extraction prompt. This only ever
     * narrows *what to pay attention to* — it never weakens the grounding requirement that the
     * model may only report what the transcript actually supports. See
     * [com.example.ai.llm.RealMeetingIntelligenceEngine.buildExtractionPrompt].
     */
    fun focusGuidance(): String = when (this) {
        MEETING -> "This is a meeting. Focus on decisions made, action items assigned, and who is responsible for what."
        INTERVIEW -> "This is an interview. Focus on the questions asked and the answers given, and note any particularly notable quotes or assessment-relevant statements."
        LECTURE -> "This is a lecture or class. Focus on the key concepts taught, the structure of the material, and important facts stated — not on decisions or action items, which are unlikely to apply."
        VOICE_MEMO -> "This is a personal voice memo. Focus on the notes, reminders, or ideas the speaker recorded for themselves."
        IDEA -> "This is a single recorded idea. Focus on clearly capturing what the idea actually is."
        BRAINSTORM -> "This is a brainstorming session. Focus on capturing every idea generated, including unfinished or exploratory ones — do not discard an idea just because it wasn't fully resolved."
        DICTATION -> "This is dictation. Prioritize an accurate, clean transcript over heavy analysis; keep extraction minimal unless the content clearly contains real decisions or action items."
        CONVERSATION -> "This is a general conversation. Summarize what was actually discussed without forcing it into a formal meeting structure."
        RESEARCH -> "This is a research recording. Focus on findings, sources, and open questions actually stated."
        JOURNAL -> "This is a personal journal entry. Focus on summarizing what the speaker actually said, respectfully and without embellishment."
        CUSTOM -> "" // Replaced by the user's own custom context text, see below.
        GENERAL -> ""
    }

    /**
     * A soft starting suggestion for the speaker-count picker (recording start and the
     * before-processing prompt) — never a forced value. Types that are almost always recorded
     * solo default to "Just me" so the common case needs zero taps; everything else defaults to
     * unspecified ("Not sure"), which the user can always override either way.
     */
    fun suggestedSpeakerCount(): Int? = when (this) {
        IDEA, VOICE_MEMO, DICTATION, JOURNAL -> 1
        else -> null
    }

    /**
     * What kind of AI output actually makes sense for this recording type — the single source of
     * truth for the LLM's extraction schema, which Meeting Detail tabs/sections appear, and what
     * the processing screen says it's doing. See [IntelligenceProfile] for what each flag controls.
     */
    fun intelligenceProfile(): IntelligenceProfile = when (this) {
        MEETING -> IntelligenceProfile(
            extractDecisions = true, extractActionItems = true, extractQuestions = true, extractFollowUps = true,
            sectionTitle = "Meeting Intelligence", topicsLabel = "Key Topics",
            analyzingStageLabel = "Extracting decisions & action items..."
        )
        INTERVIEW -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = true, extractFollowUps = true,
            sectionTitle = "Interview Insights", topicsLabel = "Key Topics",
            analyzingStageLabel = "Extracting key answers & highlights..."
        )
        LECTURE -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = true, extractFollowUps = false,
            sectionTitle = "Lecture Notes", topicsLabel = "Key Concepts",
            analyzingStageLabel = "Extracting key concepts & study notes..."
        )
        VOICE_MEMO -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = false, extractFollowUps = false,
            sectionTitle = "Voice Memo Insights", topicsLabel = "Key Points",
            analyzingStageLabel = "Organizing your thoughts..."
        )
        IDEA -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = false, extractFollowUps = false,
            sectionTitle = "Idea Insights", topicsLabel = "Key Points",
            analyzingStageLabel = "Organizing thoughts & generating insights..."
        )
        BRAINSTORM -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = false, extractFollowUps = false,
            sectionTitle = "Brainstorm Insights", topicsLabel = "Ideas Generated",
            analyzingStageLabel = "Collecting generated ideas..."
        )
        DICTATION -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = false, extractFollowUps = false,
            sectionTitle = "Notes", topicsLabel = "Key Points",
            analyzingStageLabel = "Generating notes..."
        )
        CONVERSATION -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = true, extractFollowUps = false,
            sectionTitle = "Conversation Insights", topicsLabel = "Topics",
            analyzingStageLabel = "Summarizing the conversation..."
        )
        RESEARCH -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = true, extractFollowUps = false,
            sectionTitle = "Research Notes", topicsLabel = "Key Findings",
            analyzingStageLabel = "Extracting findings & open questions..."
        )
        JOURNAL -> IntelligenceProfile(
            extractDecisions = false, extractActionItems = false, extractQuestions = false, extractFollowUps = false,
            sectionTitle = "Reflections", topicsLabel = "Themes",
            analyzingStageLabel = "Summarizing your entry..."
        )
        // The user told MeetingMind what to focus on directly — leave every category available
        // rather than guessing which ones their own instructions might need.
        CUSTOM -> IntelligenceProfile(
            extractDecisions = true, extractActionItems = true, extractQuestions = true, extractFollowUps = true,
            sectionTitle = "AI Insights", topicsLabel = "Key Topics",
            analyzingStageLabel = "Analyzing your recording..."
        )
        // Genuinely unknown content — nothing is suppressed pre-emptively, since doing so risks
        // hiding a real decision or task the recording actually contains.
        GENERAL -> IntelligenceProfile(
            extractDecisions = true, extractActionItems = true, extractQuestions = true, extractFollowUps = true,
            sectionTitle = "AI Insights", topicsLabel = "Key Topics",
            analyzingStageLabel = "Analyzing your recording..."
        )
    }

    /**
     * Type-specific guidance appended to [com.example.ai.pipeline.TranscriptAiCleanupEngine]'s
     * cleanup prompt — same profile-driven-policy pattern as [focusGuidance] and
     * [transcriptMergePolicy], never a separate implementation per type. This only ever narrows
     * *how to read paragraph/turn structure*; the MUST/MAY/MUST NOT fidelity contract in the
     * cleanup prompt itself is identical for every recording type and is never weakened here.
     */
    fun cleanupGuidance(): String = when (this) {
        IDEA, VOICE_MEMO, JOURNAL, DICTATION, RESEARCH ->
            "This is solo narration. Prioritize natural paragraphs and preserve the speaker's first-person voice; a pause within one thought is not a reason to break it into separate paragraphs."
        LECTURE ->
            "This is an explanatory monologue. Prioritize coherent paragraphs; preserve definitions and examples exactly as stated."
        MEETING ->
            "This is a multi-speaker meeting. Preserve each speaker's turn boundaries; only merge fragments within one person's own turn."
        INTERVIEW ->
            "This is an interview. Preserve the question/answer structure and each speaker's turn boundaries."
        CONVERSATION, BRAINSTORM ->
            "This is a multi-speaker conversation. Preserve genuine speaker changes; only eliminate meaningless micro-fragmentation within one person's turn."
        CUSTOM, GENERAL -> ""
    }

    /**
     * How aggressively [com.example.ai.pipeline.TranscriptStructureEngine] should merge raw ASR
     * fragments into readable paragraphs for this recording type. Distinct from
     * [intelligenceProfile] — this governs the transcript's own shape, not what the LLM is asked
     * to extract from it. See [TranscriptMergePolicy] for what each field controls.
     */
    fun transcriptMergePolicy(): TranscriptMergePolicy = when (this) {
        // Solo narration/notes: natural thinking pauses are extremely common and must not read as
        // paragraph breaks — merge aggressively into long, natural paragraphs.
        IDEA, VOICE_MEMO, JOURNAL, DICTATION, RESEARCH -> TranscriptMergePolicy(
            maxGapMs = 3_000L, extendedGapMs = 7_000L,
            maxParagraphDurationMs = 90_000L, maxParagraphChars = 1_200
        )
        // Explanatory monologue: favor long, coherent paragraphs over frequent breaks.
        LECTURE -> TranscriptMergePolicy(
            maxGapMs = 3_500L, extendedGapMs = 8_000L,
            maxParagraphDurationMs = 120_000L, maxParagraphChars = 1_600
        )
        // Multi-speaker exchange: a real turn boundary (speaker change) is the primary structural
        // signal, so gap tolerance stays close to natural conversational pacing — merge the
        // fragments *within* one person's turn, but don't paper over genuinely separate turns with
        // an overly generous gap.
        MEETING, CONVERSATION, BRAINSTORM -> TranscriptMergePolicy(
            maxGapMs = 1_500L, extendedGapMs = 3_000L,
            maxParagraphDurationMs = 45_000L, maxParagraphChars = 700
        )
        // Question/answer exchanges tend to alternate quickly — a slightly tighter base gap keeps
        // those boundaries crisp, while the same incomplete-sentence extension still protects a
        // mid-answer pause from being cut off.
        INTERVIEW -> TranscriptMergePolicy(
            maxGapMs = 1_200L, extendedGapMs = 2_500L,
            maxParagraphDurationMs = 45_000L, maxParagraphChars = 700
        )
        // Genuinely unknown content — the same balanced defaults meeting-like recordings use.
        CUSTOM, GENERAL -> TranscriptMergePolicy(
            maxGapMs = 1_500L, extendedGapMs = 3_000L,
            maxParagraphDurationMs = 45_000L, maxParagraphChars = 700
        )
    }
}

/**
 * The gap/length thresholds [com.example.ai.pipeline.TranscriptStructureEngine] uses to decide
 * whether two consecutive same-speaker ASR fragments belong in one transcript paragraph. Two gap
 * thresholds, not one: [maxGapMs] applies when the accumulated text already reads as a complete
 * sentence, [extendedGapMs] applies when it doesn't (see the engine's incomplete-sentence
 * detection) — a longer pause is far more likely to be someone still forming a thought than a new
 * one starting, and the fixed-threshold approach this replaces couldn't tell the two apart.
 */
data class TranscriptMergePolicy(
    val maxGapMs: Long,
    val extendedGapMs: Long,
    val maxParagraphDurationMs: Long,
    val maxParagraphChars: Int
)

/**
 * Drives what MeetingMind actually asks the LLM to extract, which Meeting Detail
 * tabs/sections show up, and what the processing screen's "Analyzing" step says — one source of
 * truth instead of the three independently-hardcoded copies this replaces. Every flag here must
 * correspond to a real extraction/UI path; a flag with nothing behind it would just be a
 * different way of lying about what the app does. See [RecordingType.intelligenceProfile].
 */
data class IntelligenceProfile(
    val extractDecisions: Boolean,
    val extractActionItems: Boolean,
    val extractQuestions: Boolean,
    val extractFollowUps: Boolean,
    /** Heading shown over the AI-generated sections on Meeting Detail's Overview tab. */
    val sectionTitle: String,
    /** Label for the always-present "key points/concepts/topics" list (MeetingSummary.topics). */
    val topicsLabel: String,
    /** What the processing screen's Analyzing step says it's doing, for this recording type. */
    val analyzingStageLabel: String
)

/**
 * What the user told MeetingMind about a recording before AI processing starts: what kind of
 * recording this is, how many people are expected to speak, and (for [RecordingType.CUSTOM]) what
 * to focus on. Captured once — at recording start, on import, or via the one-time "before
 * processing" prompt if skipped — and persisted on the [Meeting] itself (see
 * [Meeting.speakerCountPreference]) so it is never asked for twice and never scattered across
 * separate ViewModel fields. This is the first-class input the whole processing pipeline is built
 * around, not an afterthought bolted onto individual stages.
 */
data class RecordingContext(
    val recordingType: RecordingType = RecordingType.GENERAL,
    /** Null = unspecified ("Not sure") — the diarization engine decides the speaker count for
     *  itself via automatic clustering. 1 = exactly one speaker — diarization is skipped entirely
     *  rather than run and discarded (see MeetingProcessingPipeline). 2+ = a specific count the
     *  user is confident about, forcing the diarization engine's clustering to exactly that many. */
    val speakerCountPreference: Int? = null,
    val customContext: String? = null,
    val title: String? = null
)

data class Meeting(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val source: MeetingSource,
    val audioFilePath: String?,
    val status: MeetingStatus,
    val participantCount: Int = 1,
    val language: String = "en",
    val summaryPreview: String? = null,
    val actionItemsCount: Int = 0,
    val decisionsCount: Int = 0,
    val recordingType: RecordingType = RecordingType.GENERAL,
    /** Free-text focus guidance the user typed for [RecordingType.CUSTOM]. Null otherwise. */
    val customContext: String? = null,
    /** What the user told MeetingMind about expected speakers — see [RecordingContext]. Null means
     * unspecified; the diarization engine (if one runs) decides for itself. */
    val speakerCountPreference: Int? = null
)

data class TranscriptSegment(
    val id: String,
    val meetingId: String,
    // Null until real speaker diarization is implemented — never a fabricated "Speaker 1" label.
    val speakerId: String? = null,
    val speakerName: String? = null,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    // Null when the ASR engine doesn't provide a confidence score for a segment.
    val confidence: Float? = null,
    // True once the user has hand-corrected this segment's text — the transcript is user-owned
    // data, and this flag is how the app (and any future reprocess flow) can tell "ASR output"
    // apart from "a person already fixed this" without ever silently discarding the correction.
    val isUserEdited: Boolean = false,
    // Cached output of TranscriptCleanupEngine — never the source of truth, never persisted over
    // [text]. Null when cleanup hasn't run yet, was rejected by TranscriptQualityValidator, or
    // this segment is user-edited (a cleanup of text the user has since corrected is stale and is
    // never generated for or shown over an edited segment). Callers that want the best available
    // reading text should use [cleanedText] ?: [text], never the reverse.
    val cleanedText: String? = null,
    // The raw ASR fragment id(s) this paragraph was built from, earliest-first — populated by
    // [com.example.ai.pipeline.TranscriptStructureEngine] regardless of whether a merge actually
    // happened (a paragraph with exactly one source still lists its own id). Never empty for a
    // persisted segment; empty only as the domain-model default before structuring has run. Exists
    // so provenance survives a merge even though the original per-fragment rows are never
    // persisted individually — and so a future word-level-timestamp feature has something to
    // anchor to.
    val sourceSegmentIds: List<String> = emptyList()
)

data class Transcript(
    val meetingId: String,
    val segments: List<TranscriptSegment>,
    val language: String = "en",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A diarized speaker within one meeting. [id] is the stable diarization identity (never shown to
 * the user, never overwritten by a rename); [customName] is what the user sees and can rename —
 * keeping these separate means renaming "Speaker 1" to "Winston" never disturbs the underlying
 * transcript-to-speaker assignment. Null [confidence] means the diarization engine (or the
 * absence of one) didn't provide a meaningful score — never fabricated.
 */
data class Speaker(
    val id: String,
    val meetingId: String,
    val speakerIndex: Int,
    val originalLabel: String,
    val customName: String,
    val colorHex: String,
    val confidence: Float? = null
)

/**
 * Distinguishes a firm agreement from something merely discussed or proposed — collapsing this
 * distinction is exactly the kind of overclaiming the Meeting Intelligence engine must avoid.
 * See docs/AI_ARCHITECTURE.md "No-Hallucination Requirement".
 */
enum class DecisionType { DECISION, SUGGESTION, DISCUSSION, POSSIBILITY }

data class ActionItem(
    val id: String,
    val meetingId: String,
    val task: String,
    val assigneeSpeakerId: String? = null,
    val assigneeName: String? = null,
    val deadline: String? = null,
    // Null when the source (LLM extraction, or a user manually adding one) doesn't provide a
    // meaningful confidence — never a fabricated default.
    val confidence: Float? = null,
    val isCompleted: Boolean = false,
    val sourceSegmentIds: List<String> = emptyList()
)

data class Decision(
    val id: String,
    val meetingId: String,
    val text: String,
    val type: DecisionType = DecisionType.DISCUSSION,
    val confidence: Float? = null,
    val sourceSegmentIds: List<String> = emptyList()
)

data class Question(
    val id: String,
    val meetingId: String,
    val text: String,
    val askedBySpeakerId: String? = null,
    val resolved: Boolean = false,
    val answer: String? = null,
    val sourceSegmentIds: List<String> = emptyList()
)

data class FollowUp(
    val id: String,
    val meetingId: String,
    val description: String,
    val ownerSpeakerId: String? = null,
    val deadline: String? = null,
    val sourceSegmentIds: List<String> = emptyList()
)

data class Topic(
    val id: String,
    val meetingId: String,
    val name: String,
    val relevance: Float = 1.0f
)

data class MeetingSummary(
    val title: String,
    val summary: String,
    val topics: List<String>,
    val decisions: List<Decision>,
    val actionItems: List<ActionItem>,
    val questions: List<Question>,
    val followUps: List<FollowUp> = emptyList()
)

data class ChatMessage(
    val id: String,
    val meetingId: String,
    val isUser: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceTimestamps: List<Long> = emptyList(),
    val sourceQuotes: List<String> = emptyList()
)

/**
 * One downloadable component of a model (e.g. a Transducer ASR model is encoder + decoder +
 * joiner + tokens — four separate files, all required before the model can be considered
 * installed). [fileName] is the name it is stored under inside
 * `ModelStorage.getModelDirectory(modelId)`.
 */
data class ModelFileSpec(
    val fileName: String,
    val downloadUrl: String,
    /** SHA-256 of the final installed file at [fileName] — of the extracted entry, not the archive, when [archiveEntryPath] is set. */
    val sha256: String,
    /** Size of the final installed file at [fileName]. */
    val sizeBytes: Long,
    /** Raw HTTP payload size, when it differs from [sizeBytes] (i.e. [downloadUrl] points at an archive). Defaults to [sizeBytes]. */
    val downloadSizeBytes: Long? = null,
    /** When set, [downloadUrl] points at a `.tar.bz2` archive; this is the path of the entry to extract and rename to [fileName]. Null means [downloadUrl] is the file itself. */
    val archiveEntryPath: String? = null
)

/**
 * User-facing quality/size tier — currently only meaningful within [ModelCapability.SUMMARIZATION],
 * where more than one real model exists to choose between. Every other capability has exactly one
 * real option today, so [ModelTier.RECOMMENDED] on those is just "the only choice," not a claim
 * that alternatives were compared and this won.
 *
 * Declared smallest-first so `sortedBy { it.tier.ordinal }` gives a natural ladder in the UI.
 */
enum class ModelTier(val displayName: String, val shortDescription: String) {
    LIGHTWEIGHT("Lightweight", "Smallest download, works on older phones. Best for short notes."),
    RECOMMENDED("Recommended", "The best balance of quality and size for most phones."),
    HIGH_QUALITY("Highest quality", "Noticeably better summaries and action items. Large download, needs a recent high-RAM phone.")
}

data class AiModelInfo(
    val id: String,
    val name: String,
    val capability: Set<ModelCapability>,
    /** One or more files that together make up this model. Empty only for not-yet-sourced candidates. */
    val files: List<ModelFileSpec> = emptyList(),
    val minimumRamMb: Int,
    val recommendedRamMb: Int,
    val version: String,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val description: String = "",
    val parameterCount: String = "",
    val quantization: String = "q4_0",
    /** Maximum total tokens (prompt + generated) this specific installed model build supports, when known — e.g. an LLM's real KV-cache size. Null for non-LLM models. */
    val contextLengthTokens: Int? = null,
    val tier: ModelTier = ModelTier.RECOMMENDED
) {
    val sizeBytes: Long get() = files.sumOf { it.sizeBytes }

    /** A model can only actually be downloaded once every one of its files has a real URL + checksum. */
    val isDownloadable: Boolean
        get() = files.isNotEmpty() && files.all { it.downloadUrl.isNotBlank() && it.sha256.isNotBlank() }
}

data class ProcessingJob(
    val id: String,
    val meetingId: String,
    val meetingTitle: String,
    val currentStep: String,
    val progressPercent: Int,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
    val startedAt: Long = System.currentTimeMillis()
)

data class DeviceCapabilities(
    val totalRamGb: Float,
    val availableRamGb: Float,
    val cpuArch: String,
    val isArm64: Boolean,
    val recommendedAsrModelId: String,
    val recommendedLlmModelId: String,
    val devicePerformanceTier: String
)
