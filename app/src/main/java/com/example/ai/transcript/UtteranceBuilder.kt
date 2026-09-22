package com.example.ai.transcript

/** Tuning for [UtteranceBuilder]. Exposed so the benchmark suite can sweep it. */
data class UtteranceConfig(
    /**
     * A silence at least this long *inside one speaker's turn*, with nothing in the words on
     * either side suggesting the thought is still open, ends an utterance.
     *
     * This is where a sentence boundary is allowed to be decided — a layer that has the words,
     * their punctuation and their speaker, not the VAD layer that only had energy.
     */
    val sentenceGapMs: Long = 1_200L,

    /** The same silence when the text *does* read as unfinished (no terminal punctuation, or a
     * trailing conjunction). People pause mid-clause constantly; this keeps the clause together. */
    val openThoughtGapMs: Long = 2_500L,

    /** Hard ceiling, so a speaker who never pauses still produces readable units. */
    val maxUtteranceMs: Long = 45_000L,

    /** Hard ceiling on characters, same reason. */
    val maxUtteranceChars: Int = 600
)

/**
 * Splits each [SpeakerTurn] into sentence-sized [Utterance]s.
 *
 * The brief's requirement — "do not immediately turn every speaker turn into a paragraph" — is
 * implemented here. A turn is a span of one person talking, which can be thirty seconds long; an
 * utterance is one thing they said. Both exist, and the paragraph layer groups utterances.
 *
 * Boundaries come from language, not from audio energy: terminal punctuation on a word, a silence
 * long enough to end a sentence given what the words look like, or a hard ceiling. A turn that is
 * nothing but a short acknowledgement is emitted as a single utterance flagged
 * [Utterance.isBackchannel], so the layers above can treat "Yeah." as a complete contribution
 * rather than as an unfinished fragment to be glued onto a neighbour.
 */
object UtteranceBuilder {

    /** Acknowledgement tokens that constitute a whole conversational turn on their own. */
    internal val BACKCHANNELS = setOf(
        "yes", "yeah", "yep", "yup", "no", "nope", "right", "ok", "okay", "sure", "exactly",
        "correct", "true", "agreed", "absolutely", "definitely", "mhm", "mmhm", "uhhuh", "hmm",
        "got", "gotcha", "understood", "thanks", "perfect", "great", "nice", "cool", "indeed"
    )

    private const val MAX_BACKCHANNEL_WORDS = 3

    fun build(
        turns: List<SpeakerTurn>,
        words: List<CanonicalWord>,
        config: UtteranceConfig = UtteranceConfig()
    ): List<Utterance> {
        if (turns.isEmpty() || words.isEmpty()) return emptyList()
        val wordsById = words.associateBy { it.id }

        val utterances = mutableListOf<Utterance>()
        for (turn in turns) {
            val turnWords = turn.wordIds.mapNotNull { wordsById[it] }
            if (turnWords.isEmpty()) continue
            for (group in splitTurn(turnWords, config)) {
                utterances += toUtterance(
                    id = "utt${utterances.size}",
                    turn = turn,
                    group = group,
                    isWholeTurn = group.size == turnWords.size
                )
            }
        }
        return utterances
    }

    private fun splitTurn(turnWords: List<CanonicalWord>, config: UtteranceConfig): List<List<CanonicalWord>> {
        val groups = mutableListOf<List<CanonicalWord>>()
        var current = mutableListOf(turnWords.first())

        for (word in turnWords.drop(1)) {
            val previous = current.last()
            val gapMs = word.startMs - previous.endMs
            val textSoFar = current.joinToString(" ") { it.text }
            val thoughtOpen = !endsSentence(textSoFar) || endsWithContinuationCue(textSoFar)
            val gapThreshold = if (thoughtOpen) config.openThoughtGapMs else config.sentenceGapMs

            val hitCeiling = word.endMs - current.first().startMs > config.maxUtteranceMs ||
                textSoFar.length + 1 + word.text.length > config.maxUtteranceChars
            // Punctuation from the engine is the strongest signal there is — but only when the
            // following word actually starts something (a capital or a real pause). A stray "."
            // inside "U.S." must not end an utterance.
            val punctuationBoundary = endsSentence(previous.text) &&
                (gapMs > 0 || word.text.firstOrNull()?.isUpperCase() == true)

            if (punctuationBoundary || gapMs >= gapThreshold || hitCeiling) {
                groups += current
                current = mutableListOf(word)
            } else {
                current += word
            }
        }
        groups += current
        return groups
    }

    private fun toUtterance(
        id: String,
        turn: SpeakerTurn,
        group: List<CanonicalWord>,
        isWholeTurn: Boolean
    ): Utterance {
        val text = joinWords(group)
        return Utterance(
            id = id,
            turnId = turn.id,
            speakerId = turn.speakerId,
            startMs = group.first().startMs,
            endMs = group.last().endMs,
            text = text,
            wordIds = group.map { it.id },
            isBackchannel = isWholeTurn && isBackchannel(group)
        )
    }

    /**
     * A backchannel is a *whole turn* of at most [MAX_BACKCHANNEL_WORDS] words, all of which are
     * acknowledgement tokens. "Yeah, but we're almost done" is not one — only the first word
     * matches — and neither is "Exactly." spoken in the middle of a longer turn, because that is
     * part of the speaker's own sentence, not an acknowledgement of someone else's.
     */
    private fun isBackchannel(group: List<CanonicalWord>): Boolean {
        if (group.size > MAX_BACKCHANNEL_WORDS) return false
        return group.all { AsrWindowReconciler.normalize(it.text) in BACKCHANNELS }
    }

    /** Joins words into readable text: the engine's own spacing conventions, nothing invented. */
    internal fun joinWords(words: List<CanonicalWord>): String =
        words.joinToString(" ") { it.text.trim() }.trim()

    internal fun endsSentence(text: String): Boolean {
        val trimmed = text.trimEnd()
        return trimmed.isNotEmpty() && trimmed.last() in TERMINAL_PUNCTUATION
    }

    /**
     * A trailing conjunction, preposition or article is strong evidence the speaker has not
     * finished, whatever punctuation the engine did or did not emit. Mirrors the same signal used
     * by [com.example.ai.pipeline.DeterministicTranscriptStructureEngine] one layer up, so the two
     * layers agree about what an unfinished thought looks like.
     */
    internal fun endsWithContinuationCue(text: String): Boolean {
        val lastWord = text.trim().substringAfterLast(' ')
            .trimEnd(*TERMINAL_PUNCTUATION, ',')
            .lowercase()
        return lastWord.isNotEmpty() && lastWord in CONTINUATION_WORDS
    }

    private val TERMINAL_PUNCTUATION = charArrayOf('.', '?', '!')

    private val CONTINUATION_WORDS = setOf(
        "and", "but", "or", "so", "because", "if", "when", "while", "that", "which",
        "the", "a", "an", "to", "of", "in", "on", "with", "for", "as", "at", "by", "from"
    )
}
