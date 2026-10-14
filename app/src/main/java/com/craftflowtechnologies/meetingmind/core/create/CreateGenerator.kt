package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import org.json.JSONObject

/** One written option: the words, and the scripture the app fetched for it (never the model). */
data class CreatePiece(val text: String, val scripture: ResolvedScripture? = null)

sealed interface CreateOutcome {
    /** [suggested] already went through [CreateVibePolicy]. [verseDropped] is true when the model named a verse the Bible library could not give. */
    data class Written(val options: List<CreatePiece>, val suggested: CreateVibe, val verseDropped: Boolean = false) : CreateOutcome
    /** No model to ask (no Internet mode or key). Not an error: the editor and the starters are the way forward. */
    data class Unavailable(val message: String) : CreateOutcome
    /** The model or the network failed. Always shown, with Retry. */
    data class Failed(val message: String = FAILED_MESSAGE) : CreateOutcome
}

sealed interface RemixOutcome {
    data class Done(val card: CreateCard, val scripture: ResolvedScripture?) : RemixOutcome
    data class Unavailable(val message: String) : RemixOutcome
    data class Failed(val message: String = FAILED_MESSAGE) : RemixOutcome
}

const val FAILED_MESSAGE = "Couldn't generate — try again or write your own."
const val UNAVAILABLE_MESSAGE = "Writing with AI needs Internet mode and a Gemini key. Write your own, or start from a starter."

/**
 * Create's engine (replaces the old Spark generator).
 *
 * Faith contract: scripture text always comes from the Bible provider, by reference. The model is
 * asked for a reference at most; any verse text it volunteers is ignored. A changed reference is
 * fetched again. Quotes from notes and selected text are checked to be verbatim.
 *
 * Failures are returned, never swapped for canned text. Curated starters exist ([CreateStarters])
 * but are only shown when the person asks, and labelled as starters.
 */
class CreateGenerator(
    private val model: CreateTextModel,
    private val scripture: CreateScriptureLookup,
    private val versionId: suspend () -> Int? = { null }
) {

    suspend fun generate(req: CreateRequest): CreateOutcome {
        val vibe = req.effectiveVibe
        // A verse source with no words is the verse itself: no model needed.
        if (req.source == CreateSourceKind.VERSE && req.text.isBlank()) return verseOnly(req, vibe)
        if (!model.available()) return CreateOutcome.Unavailable(UNAVAILABLE_MESSAGE)

        val fixedVerse = if (req.source == CreateSourceKind.VERSE && !req.reference.isNullOrBlank()) {
            when (val f = scripture.fetch(req.reference, versionId())) {
                is ScriptureFetch.Found -> f.scripture
                is ScriptureFetch.Invalid -> return CreateOutcome.Failed("That isn't a verse I can find: ${req.reference}.")
                is ScriptureFetch.Unavailable -> return CreateOutcome.Failed("Couldn't load ${f.reference} — try again or write your own.")
            }
        } else null

        val prompt = CreatePrompts.generate(req)
        val raw = when (val r = model.complete(prompt.system, prompt.user)) {
            is AiResult.Success -> r.value
            is AiResult.ModelUnavailable -> return CreateOutcome.Unavailable(UNAVAILABLE_MESSAGE)
            else -> return CreateOutcome.Failed()
        }
        val parsed = parse(raw) ?: return CreateOutcome.Failed()

        var verseDropped = false
        val pieces = mutableListOf<CreatePiece>()
        for (p in parsed.pieces) {
            val text = p.text.trim()
            if (text.isBlank() || text.length > MAX_TEXT) continue
            if (CreateGuards.violation(text, prompt.faith) != null) continue
            if (req.source.verbatim && !CreateGuards.isVerbatim(req.text, text)) continue
            val resolved: ResolvedScripture? = when {
                fixedVerse != null -> fixedVerse
                prompt.faith && !p.verseRef.isNullOrBlank() -> when (val f = scripture.fetch(p.verseRef, versionId())) {
                    is ScriptureFetch.Found -> f.scripture
                    else -> { verseDropped = true; null }
                }
                else -> null
            }
            pieces += CreatePiece(text, resolved)
        }
        // A verbatim source whose pieces were all rejected still has its own words: take the first sentences, verbatim by construction.
        if (pieces.isEmpty() && req.source.verbatim && req.text.isNotBlank()) {
            pieces += CreatePiece(CreateGuards.leadingExcerpt(req.text), fixedVerse)
        }
        if (pieces.isEmpty()) return CreateOutcome.Failed()
        val suggested = CreateVibePolicy.enforce(req.source, parsed.suggestedVibe ?: req.vibe, req.grief, req.goodFriday)
        return CreateOutcome.Written(pieces.distinctBy { it.text }.take(3), suggested, verseDropped)
    }

    private suspend fun verseOnly(req: CreateRequest, vibe: CreateVibe): CreateOutcome {
        val ref = req.reference ?: return CreateOutcome.Failed("Choose a verse first.")
        return when (val f = scripture.fetch(ref, versionId())) {
            is ScriptureFetch.Found -> CreateOutcome.Written(listOf(CreatePiece("", f.scripture)), vibe)
            is ScriptureFetch.Invalid -> CreateOutcome.Failed("That isn't a verse I can find: $ref.")
            is ScriptureFetch.Unavailable -> CreateOutcome.Failed("Couldn't load ${f.reference} — try again or write your own.")
        }
    }

    /** The person edited the reference: the text is fetched again from the Bible provider, never kept from before. */
    suspend fun resolveReference(reference: String): ScriptureFetch = scripture.fetch(reference, versionId())

    /** A saved card reopened: its reference is fetched again. */
    suspend fun scriptureFor(card: CreateCard): ScriptureFetch? =
        card.scriptureRef?.let { scripture.fetch(it, card.scriptureVersionId ?: versionId()) }

    /** Applies [action] to [card]'s current words and returns the card with a new version, or a visible failure. */
    suspend fun remix(card: CreateCard, action: RemixAction): RemixOutcome {
        if (!action.allowedFor(card.source)) return RemixOutcome.Failed("That note quote stays as written. You can add a verse to it.")
        if (card.text.isBlank() && action != RemixAction.ADD_VERSE) return RemixOutcome.Failed("Write something first.")
        if (!model.available()) return RemixOutcome.Unavailable(UNAVAILABLE_MESSAGE)
        val prompt = CreatePrompts.remix(card, action)
        val raw = when (val r = model.complete(prompt.system, prompt.user, 30_000L)) {
            is AiResult.Success -> r.value
            is AiResult.ModelUnavailable -> return RemixOutcome.Unavailable(UNAVAILABLE_MESSAGE)
            else -> return RemixOutcome.Failed()
        }
        val obj = parseObject(raw) ?: return RemixOutcome.Failed()
        val modelText = obj.optString("text").trim()
        val modelRef = obj.optString("verseRef").takeIf { it.isNotBlank() && !it.equals("null", true) }

        return when (action) {
            RemixAction.ADD_VERSE -> {
                val ref = modelRef ?: return RemixOutcome.Failed("Couldn't find a verse for that — try again or pick one yourself.")
                when (val f = scripture.fetch(ref, versionId())) {
                    is ScriptureFetch.Found -> RemixOutcome.Done(
                        card.withVersion(CardVersion(card.text, f.scripture.reference, action.label)).copy(scriptureVersionId = f.scripture.versionId), f.scripture
                    )
                    else -> RemixOutcome.Failed("Couldn't load that verse — try again or pick one yourself.")
                }
            }
            else -> {
                if (modelText.isBlank() || modelText.length > MAX_TEXT || CreateGuards.violation(modelText, prompt.faith) != null) return RemixOutcome.Failed()
                // The verse stays; its text is fetched again for the preview.
                val fetched = (scriptureFor(card) as? ScriptureFetch.Found)?.scripture
                RemixOutcome.Done(card.withVersion(CardVersion(modelText, card.scriptureRef, action.label)), fetched)
            }
        }
    }

    private class Parsed(val suggestedVibe: CreateVibe?, val pieces: List<RawPiece>)
    private class RawPiece(val text: String, val verseRef: String?)

    private fun parse(raw: String): Parsed? {
        val obj = parseObject(raw) ?: return null
        val arr = obj.optJSONArray("pieces") ?: return null
        val pieces = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            // Any "verseText" the model volunteers is ignored on purpose.
            RawPiece(o.optString("text"), o.optString("verseRef").takeIf { it.isNotBlank() && !it.equals("null", true) })
        }
        return Parsed(CreateVibe.parse(obj.optString("suggestedVibe")), pieces)
    }

    private fun parseObject(raw: String): JSONObject? = runCatching {
        val body = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        JSONObject(body.substring(body.indexOf('{'), body.lastIndexOf('}') + 1))
    }.getOrNull()

    companion object { const val MAX_TEXT = 280 }
}

/** Local checks on what a model returned. The prompt asks; this enforces. */
object CreateGuards {
    private val revelation = listOf(
        "god told me", "god is telling you", "god says to you", "the lord says to you", "thus says the lord",
        "i prophesy", "i declare over you", "god has revealed that you"
    )
    private val promises = Regex("""\b(guarantee[sd]?|will (definitely|certainly) (heal|cure|make you rich))\b""", RegexOption.IGNORE_CASE)
    private val medicalFinancial = Regex("""\b(cures?|diagnos\w+|prescri\w+|invest in|guaranteed returns?)\b""", RegexOption.IGNORE_CASE)

    /** A reason this text must not go on a card, or null. */
    fun violation(text: String, faith: Boolean): String? {
        val t = text.lowercase()
        if (faith && revelation.any { it in t }) return "presents words as divine revelation"
        if (faith && quotesScripture(text)) return "contains what looks like quoted scripture"
        if (promises.containsMatchIn(text)) return "promises an outcome"
        if (!faith && medicalFinancial.containsMatchIn(text)) return "makes a medical or financial claim"
        return null
    }

    /** A reference plus a long quotation: verse text the model wrote itself. */
    fun quotesScripture(text: String): Boolean {
        val hasRef = ScriptureReferenceParser.findAll(text).isNotEmpty()
        val quoted = Regex("""["“]([^"”]{40,})["”]""").containsMatchIn(text)
        return hasRef && quoted
    }

    private fun normalise(s: String) = s.lowercase().replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
        .replace(Regex("""[\s]+"""), " ").trim().trim('"', '\'', '.', ',', '…').trim()

    /** True when [quote] (a model's pick) appears word for word in [source]. */
    fun isVerbatim(source: String, quote: String): Boolean {
        val q = normalise(quote.replace("…", " "))
        return q.isNotEmpty() && normalise(source).contains(q)
    }

    /** The first sentences of [source] up to about 220 characters: verbatim by construction. */
    fun leadingExcerpt(source: String, limit: Int = 220): String {
        val s = source.trim().replace(Regex("""\s+"""), " ")
        if (s.length <= limit) return s
        val cut = s.take(limit)
        val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("? "), cut.lastIndexOf("! "))
        return if (end > limit / 3) cut.take(end + 1) else cut.substringBeforeLast(' ') + "…"
    }
}
