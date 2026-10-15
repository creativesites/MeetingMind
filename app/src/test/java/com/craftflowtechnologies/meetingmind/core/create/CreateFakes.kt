package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/** A scripted model: replies are consumed in order (the last one repeats). Records every prompt and how many calls overlapped. */
class FakeModel(
    private val replies: MutableList<AiResult<String>>,
    var available: Boolean = true,
    private val latencyMs: Long = 0
) : CreateTextModel {
    val prompts = mutableListOf<Pair<String, String>>()
    private val inFlight = AtomicInteger(0)
    @Volatile var maxInFlight = 0

    override suspend fun available() = available

    override suspend fun complete(system: String, prompt: String, timeoutMs: Long): AiResult<String> {
        val now = inFlight.incrementAndGet()
        maxInFlight = maxOf(maxInFlight, now)
        try {
            prompts += system to prompt
            if (latencyMs > 0) delay(latencyMs)
            return if (replies.size > 1) replies.removeAt(0) else replies.first()
        } finally { inFlight.decrementAndGet() }
    }

    companion object {
        fun json(json: String) = AiResult.Success(json)
        fun pieces(vararg texts: String, suggested: String = "Peaceful", verseRef: String? = null, verseText: String? = null) =
            AiResult.Success(
                """{"suggestedVibe":"$suggested","pieces":[${texts.joinToString(",") { t ->
                    """{"text":"$t","verseRef":${verseRef?.let { "\"$it\"" } ?: "null"}${verseText?.let { ""","verseText":"$it"""" } ?: ""}}"""
                }}]}"""
            )
        fun one(text: String, verseRef: String? = null) = AiResult.Success("""{"text":"$text","verseRef":${verseRef?.let { "\"$it\"" } ?: "null"}}""")
    }
}

/** The Bible provider: text is keyed by the canonical reference the parser produces. Records every fetch. */
class FakeScripture(private val verses: Map<String, String> = mapOf(
    "John 3:16" to "For God so loved the world...",
    "Psalm 23:1" to "The Lord is my shepherd; I shall not want.",
    "Romans 8:28" to "And we know that in all things God works for the good of those who love him."
)) : CreateScriptureLookup {
    val fetched = mutableListOf<String>()
    var offline = false

    override suspend fun fetch(reference: String, versionId: Int?): ScriptureFetch {
        fetched += reference
        val parsed = com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser.parse(reference)
            ?: return ScriptureFetch.Invalid(reference)
        val display = parsed.display()
        if (offline) return ScriptureFetch.Unavailable(display, "offline")
        val text = verses[display] ?: return ScriptureFetch.Unavailable(display, "no text")
        return ScriptureFetch.Found(ResolvedScripture(display, text, 3034, "BSB", "BSB attribution"))
    }
}
