package com.example.ai.transcript

/**
 * Merges the per-window ASR outputs into one chronological word stream, removing the text that
 * overlapping decode windows inevitably produce twice.
 *
 * Overlap is deliberate (see [AsrWindowConfig.overlapMs]) — it is what gives a word at a window
 * boundary real acoustic context. The price is that the same words come back from two decodes,
 * with slightly different timings and occasionally slightly different text. This component pays
 * that price deterministically:
 *
 * ```
 * window A  ... we still need to finish the API
 * window B                need to finish the API integration before Friday
 * result    ... we still need to finish the API integration before Friday
 * ```
 *
 * The join point is found by **anchoring on the longest run of words the two windows agree on**
 * inside the overlap, not by trusting timestamps (which drift between decodes) and not by asking a
 * model (which would invent). If the windows agree on nothing, it falls back to a timestamp cut,
 * which can never duplicate a word even though it may lose one at the seam — losing a word at a
 * seam is recoverable; silently repeating a phrase is the defect users actually notice.
 */
object AsrWindowReconciler {

    /** Shortest run of matching words accepted as a join anchor. One matching word is far too
     * easy to hit by chance ("the", "and") and would splice in the wrong place. */
    private const val MIN_ANCHOR_WORDS = 2

    /**
     * @param windowResults One list of words per [AsrWindow], in window order, each with
     *   timestamps already absolute to the recording.
     * @return A single chronological stream. Ids are reassigned positionally, so the returned
     *   words carry the transcript-wide ids every later layer references.
     */
    fun reconcile(windowResults: List<List<CanonicalWord>>): List<CanonicalWord> {
        val nonEmpty = windowResults.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()

        var accumulated = nonEmpty.first().toMutableList()
        for (window in nonEmpty.drop(1)) {
            accumulated = joinWindow(accumulated, window)
        }
        return accumulated
            .sortedBy { it.startMs }
            .mapIndexed { index, word -> word.copy(id = canonicalWordId(index)) }
    }

    /**
     * Appends [next] to [accumulated], cutting whichever prefix of [next] duplicates the tail of
     * [accumulated].
     */
    private fun joinWindow(
        accumulated: MutableList<CanonicalWord>,
        next: List<CanonicalWord>
    ): MutableList<CanonicalWord> {
        val previousEndMs = accumulated.maxOf { it.endMs }
        val overlapStartMs = next.first().startMs
        if (overlapStartMs >= previousEndMs) {
            // No overlap in time at all (two separate speech spans) — nothing can be duplicated.
            accumulated += next
            return accumulated
        }

        val tailStartIndex = accumulated.indexOfFirst { it.endMs > overlapStartMs }
            .let { if (it < 0) accumulated.size else it }
        val tail = accumulated.subList(tailStartIndex, accumulated.size).toList()
        val headEndIndex = next.indexOfFirst { it.startMs >= previousEndMs }
            .let { if (it < 0) next.size else it }
        val head = next.subList(0, headEndIndex).toList()

        val anchor = findAnchor(tail, head)
        if (anchor != null) {
            // Keep everything the earlier window produced up to the start of the agreed run, then
            // let the later window (which decoded these words with more right-hand context)
            // continue from the same point.
            while (accumulated.size > tailStartIndex + anchor.tailIndex) {
                accumulated.removeAt(accumulated.lastIndex)
            }
            accumulated += next.subList(anchor.headIndex, next.size)
            return accumulated
        }

        // No agreement: cut on time. Every word of the later window that starts before the earlier
        // window ended is dropped, so the seam can lose a word but can never repeat one.
        accumulated += next.filter { it.startMs >= previousEndMs }
        return accumulated
    }

    private data class Anchor(val tailIndex: Int, val headIndex: Int, val length: Int)

    /**
     * Finds the longest run of consecutive words appearing in both the tail of the earlier window
     * and the head of the later one. Ties are broken toward the run that appears earliest in the
     * later window, which keeps as much of the later (better-contextualised) decode as possible.
     */
    private fun findAnchor(tail: List<CanonicalWord>, head: List<CanonicalWord>): Anchor? {
        if (tail.isEmpty() || head.isEmpty()) return null
        val tailKeys = tail.map { normalize(it.text) }
        val headKeys = head.map { normalize(it.text) }

        var best: Anchor? = null
        for (t in tailKeys.indices) {
            for (h in headKeys.indices) {
                if (tailKeys[t] != headKeys[h] || tailKeys[t].isEmpty()) continue
                var length = 0
                while (t + length < tailKeys.size &&
                    h + length < headKeys.size &&
                    tailKeys[t + length] == headKeys[h + length]
                ) {
                    length++
                }
                if (length >= MIN_ANCHOR_WORDS && (best == null || length > best.length)) {
                    best = Anchor(tailIndex = t, headIndex = h, length = length)
                }
            }
        }

        // A single-word anchor is accepted only when it is all there is *and* the word is long
        // enough not to be a function word that matches by coincidence.
        if (best == null) {
            for (t in tailKeys.indices) {
                val h = headKeys.indexOf(tailKeys[t])
                if (h >= 0 && tailKeys[t].length >= 5) {
                    return Anchor(tailIndex = t, headIndex = h, length = 1)
                }
            }
        }
        return best
    }

    /** Case- and punctuation-insensitive comparison: two decodes of the same word routinely differ
     * in capitalisation and trailing punctuation without disagreeing about the word. */
    internal fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }
}
