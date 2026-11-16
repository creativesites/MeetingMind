package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import org.json.JSONArray
import org.json.JSONObject

/**
 * `[n]` citation markers in AI-written notes.
 *
 * The polish prompt numbers the transcript lines it shows the model and asks it to cite them as
 * `[3]`. [resolve] takes the markers out of a block's text and keeps the moments they point to in
 * the block's payload, so the words read cleanly (in the note, in exports and aloud) and the editor
 * shows each source as a chip that plays that moment. A marker that points at no line is dropped.
 *
 * Older notes have markers the model invented with no numbered transcript behind them; [hidden]
 * finds those so the editor can hide them.
 */
object NoteCitations {

    /** Payload key: a JSON array of the moments a block cites. */
    const val PAYLOAD = "citations"

    data class Cite(val n: Int, val startMs: Long, val endMs: Long?, val segmentId: String?, val quote: String)

    /**
     * `[3]`, `[1, 4]` or `[2][5]`, with the space before it; never a link (`[text](url)`), a
     * footnote label (`[2]:`) or an index (`a[0]`, `x[1][2]`).
     */
    private val MARKER = Regex("""\s?(?<![\p{L}\p{N}\]_`])((?:\[\d{1,3}(?:\s*,\s*\d{1,3})*])+)(?![(:])""")
    private val NUMBER = Regex("""\d{1,3}""")

    /** The markers in [text], each with its range (a leading space included) and the numbers it cites. */
    fun markers(text: String): List<Pair<IntRange, List<Int>>> =
        MARKER.findAll(text).map { m -> m.range to NUMBER.findAll(m.groupValues[1]).map { it.value.toInt() }.toList() }.toList()

    /** `[Speaker 3]`: a model's speaker tag, noise in notes (the speaker is shown with the transcript). */
    private val SPEAKER_TAG = Regex("""\s?\[speaker\s*\d{1,2}]""", RegexOption.IGNORE_CASE)

    /** Ranges of [text] to hide in AI text: citation markers with no known source, and speaker tags. */
    fun hidden(text: String): List<IntRange> = (markers(text).map { it.first } + speakerTags(text)).sortedBy { it.first }

    private fun speakerTags(text: String): List<IntRange> = SPEAKER_TAG.findAll(text).map { it.range }.toList()

    /**
     * [content] with its markers taken out, and the moments they cite in [numbered] (line number →
     * segment). Numbers with no line are dropped. Duplicates keep their first place.
     */
    fun resolve(content: RichText, numbered: Map<Int, TranscriptSegment>): Pair<RichText, List<Cite>> {
        val found = markers(content.text)
        if (found.isEmpty() && speakerTags(content.text).isEmpty()) return content to emptyList()
        var out = content
        // Speaker tags go too; ranges never overlap (a tag is not a number list).
        val cuts = (found.map { it.first } + speakerTags(content.text)).sortedBy { it.first }
        for (range in cuts.asReversed()) {
            out = out.withEditedText(out.text.removeRange(range))
        }
        val cites = found.flatMap { it.second }.distinct().mapNotNull { n ->
            numbered[n]?.let { seg -> Cite(n, seg.startMs, seg.endMs.takeIf { it > seg.startMs }, seg.id, (seg.cleanedText ?: seg.text).trim().take(QUOTE_MAX)) }
        }
        return out to cites
    }

    fun encode(cites: List<Cite>): String = JSONArray(cites.map { c ->
        JSONObject().apply {
            put("n", c.n); put("startMs", c.startMs)
            c.endMs?.let { put("endMs", it) }
            c.segmentId?.let { put("seg", it) }
            put("quote", c.quote)
        }
    }).toString()

    fun decode(raw: String?): List<Cite> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            if (!o.has("startMs")) return@mapNotNull null
            Cite(
                n = o.optInt("n", i + 1),
                startMs = o.getLong("startMs"),
                endMs = if (o.has("endMs")) o.getLong("endMs") else null,
                segmentId = o.optString("seg").ifBlank { null },
                quote = o.optString("quote")
            )
        }
    }.getOrDefault(emptyList())

    /** The moments [block] cites. */
    fun of(block: NoteBlock): List<Cite> = decode(block.payload[PAYLOAD])

    private const val QUOTE_MAX = 280
}
