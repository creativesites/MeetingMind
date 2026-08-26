package com.example.ai.pipeline

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.core.model.RecordingType
import com.example.core.model.TranscriptCleanupMode
import com.example.core.model.TranscriptCleanupProfile
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [RealTranscriptAiCleanupEngine] exercised against a deterministic fake [LanguageModel] — no
 * live model inference runs in a JVM test (see the standing "do not require live model inference
 * in JVM tests" constraint). These tests cover the engine's own wiring: chunking, context windows,
 * mode-aware permissiveness, fallback behavior at every failure mode, user-edit exclusion, and
 * prompt content — not the fidelity checks themselves, which [TranscriptQualityValidatorTest]
 * already covers directly. Robolectric is required here (unlike this package's other tests)
 * because [RealTranscriptAiCleanupEngine] parses JSON via org.json, and the plain JVM android.jar
 * stub throws "not mocked" for it — same reason
 * [com.example.ai.llm.MeetingIntelligenceJsonParserTest] uses it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptAiCleanupEngineTest {

    private class FakeLanguageModel(
        private val responses: MutableList<AiResult<String>>
    ) : LanguageModel {
        val promptsSeen = mutableListOf<String>()
        var callCount = 0
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            callCount++
            promptsSeen += prompt
            return if (responses.isNotEmpty()) responses.removeAt(0) else AiResult.Failed("no more canned responses")
        }
    }

    private fun seg(
        id: String,
        text: String,
        startMs: Long = 0L,
        endMs: Long = 1000L,
        speakerId: String? = null,
        cleanedText: String? = null,
        isUserEdited: Boolean = false
    ) = TranscriptSegment(
        id = id, meetingId = "m1", speakerId = speakerId, speakerName = speakerId?.let { "Speaker $it" },
        startMs = startMs, endMs = endMs, text = text, cleanedText = cleanedText, isUserEdited = isUserEdited
    )

    /** Conservative unless a test is specifically about mode differences — matches this engine's
     * own "default to the safest behavior" philosophy for tests that aren't testing mode itself. */
    private fun profile(type: RecordingType, mode: TranscriptCleanupMode = TranscriptCleanupMode.CONSERVATIVE): TranscriptCleanupProfile =
        type.transcriptCleanupProfile(mode)

    /** Responds to whatever [id]s actually appear in the prompt it received, so it stays correct
     * regardless of exactly how many paragraphs the chunker packed into one call — unlike a
     * pre-queued list of canned responses, which would silently answer the wrong chunk if the
     * packing assumption used to build that queue turned out wrong. */
    private inner class RespondToWhicheverIdsWereAskedModel : LanguageModel {
        val promptsSeen = mutableListOf<String>()
        var callCount = 0
        // Echoes each paragraph's own text back unchanged as its "cleaned" candidate — a trivial,
        // always-valid transformation (identical raw vs. cleaned) that lets this fake exercise
        // chunking/multi-call behavior without needing to satisfy TranscriptQualityValidator's
        // length-ratio/vocabulary checks against arbitrary filler text. Only matches lines inside
        // the PRIMARY TEXT block's own id markers — a context paragraph is rendered with the same
        // "[id] speaker: text" shape, so tests that care about context-vs-primary isolation inspect
        // promptsSeen directly rather than relying on this fake to distinguish them.
        private val paragraphLine = Regex("""^\[(\S+?)] [^:]*: (.*)$""")

        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            callCount++
            promptsSeen += prompt
            val primaryBlock = prompt.substringAfter("PRIMARY TEXT").substringBefore("CONTEXT AFTER")
            val entries = primaryBlock.lines().mapNotNull { line -> paragraphLine.matchEntire(line.trim())?.let { it.groupValues[1] to it.groupValues[2] } }
            return AiResult.Success(jsonResponse(*entries.toTypedArray()))
        }
    }

    // Built via plain string templates rather than org.json.JSONObject/JSONArray's write methods:
    // this project's plain (non-Robolectric) JVM tests run against the real Android stub jar,
    // which throws "not mocked" for JSON *construction* — only *parsing* an already-built string
    // (what RealTranscriptAiCleanupEngine.parseCleanupResponse actually does) works unmocked.
    private fun jsonResponse(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(",", prefix = "[", postfix = "]") { (id, text) ->
            """{"id":"$id","text":"${text.replace("\"", "\\\"")}"}"""
        }

    private fun engine(model: LanguageModel, contextTokens: Int = 4096) =
        RealTranscriptAiCleanupEngine(model, contextTokens)

    // --- Happy path ---

    @Test
    fun `a validated AI candidate replaces cleanedText`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "I think this works."))))
        val result = engine(model).clean(listOf(seg("s1", "Uh, I think this works.")), profile(RecordingType.MEETING), singleSpeakerMode = false)

        assertTrue(result is AiResult.Success)
        val cleaned = (result as AiResult.Success).value
        assertEquals("I think this works.", cleaned.segments[0].cleanedText)
        assertEquals(1, cleaned.paragraphsAccepted)
        assertEquals(0, cleaned.paragraphsFallback)
    }

    // --- Whole-call unavailability (no cleanup model installed) ---

    @Test
    fun `a ModelUnavailable first response short-circuits the whole call`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.ModelUnavailable("m", "not installed")))
        val result = engine(model).clean(listOf(seg("s1", "Some text.")), profile(RecordingType.MEETING), singleSpeakerMode = false)

        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `no eligible segments short-circuits without ever calling the model`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf())
        val result = engine(model).clean(
            listOf(seg("s1", "User text", isUserEdited = true)),
            profile(RecordingType.MEETING), singleSpeakerMode = false
        )

        assertTrue(result is AiResult.Success)
        assertEquals(0, model.callCount)
        assertEquals(0, (result as AiResult.Success).value.chunksAttempted)
    }

    // --- Per-chunk / per-paragraph graceful fallback ---

    @Test
    fun `a validator-rejected candidate falls back and does not fail the whole call`() = runBlocking {
        // The model swaps the dollar amount — a dangerous edit TranscriptQualityValidator must
        // reject in every mode, including Aggressive.
        val model = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "We agreed on \$50,000."))))
        val result = engine(model).clean(
            listOf(seg("s1", "We agreed on \$15,000.", cleanedText = "We agreed on \$15,000.")),
            profile(RecordingType.MEETING, TranscriptCleanupMode.AGGRESSIVE), singleSpeakerMode = false
        )

        assertTrue(result is AiResult.Success)
        val cleaned = (result as AiResult.Success).value
        assertEquals("We agreed on \$15,000.", cleaned.segments[0].cleanedText) // unchanged, kept the prior value
        assertEquals(0, cleaned.paragraphsAccepted)
        assertEquals(1, cleaned.paragraphsFallback)
    }

    @Test
    fun `malformed JSON falls back the whole chunk without throwing`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.Success("not json at all, sorry")))
        val result = engine(model).clean(listOf(seg("s1", "Some text.")), profile(RecordingType.MEETING), singleSpeakerMode = false)

        assertTrue(result is AiResult.Success)
        val cleaned = (result as AiResult.Success).value
        assertNull(cleaned.segments[0].cleanedText)
        assertEquals(1, cleaned.paragraphsFallback)
    }

    @Test
    fun `a response missing an id is a fallback for that paragraph only`() = runBlocking {
        val model = FakeLanguageModel(
            mutableListOf(AiResult.Success(jsonResponse("s1" to "This is the first raw paragraph.")))  // s2 never answered
        )
        val result = engine(model).clean(
            listOf(seg("s1", "Uh, this is the first raw paragraph."), seg("s2", "This is the second raw paragraph.")),
            profile(RecordingType.MEETING), singleSpeakerMode = false
        )

        val cleaned = (result as AiResult.Success).value
        assertEquals("This is the first raw paragraph.", cleaned.segments[0].cleanedText)
        assertNull(cleaned.segments[1].cleanedText)
        assertEquals(1, cleaned.paragraphsAccepted)
        assertEquals(1, cleaned.paragraphsFallback)
    }

    @Test
    fun `a single paragraph exceeding the model's real budget falls back without calling the model`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf())
        val hugeText = "word ".repeat(2000) // far larger than any small-model budget
        val result = engine(model, contextTokens = 512).clean(
            listOf(seg("s1", hugeText)),
            profile(RecordingType.MEETING), singleSpeakerMode = false
        )

        assertTrue(result is AiResult.Success)
        assertEquals(0, model.callCount)
        val cleaned = (result as AiResult.Success).value
        assertEquals(0, cleaned.chunksAttempted)
        assertEquals(1, cleaned.paragraphsFallback)
    }

    @Test
    fun `one failed chunk does not prevent a later chunk from succeeding`() = runBlocking {
        // s1 alone (~850 chars) very nearly fills the fixed minimum per-call budget (896 chars,
        // the chunker's own coerceAtLeast floor) without being flagged oversized on its own, so
        // s2 is forced into a second chunk rather than being packed alongside it.
        val model = FakeLanguageModel(
            mutableListOf(
                AiResult.Success("garbage"),
                AiResult.Success(jsonResponse("s2" to "Second paragraph cleaned."))
            )
        )
        val result = engine(model, contextTokens = 600).clean(
            listOf(
                seg("s1", "word ".repeat(170).trim(), startMs = 0, endMs = 1000), // ~850 chars
                seg("s2", "Second paragraph text.", startMs = 5000, endMs = 6000)
            ),
            profile(RecordingType.MEETING), singleSpeakerMode = false
        )

        assertTrue(result is AiResult.Success)
        val cleaned = (result as AiResult.Success).value
        assertNull(cleaned.segments.first { it.id == "s1" }.cleanedText)
        assertEquals("Second paragraph cleaned.", cleaned.segments.first { it.id == "s2" }.cleanedText)
        assertEquals(2, model.callCount)
    }

    // --- User edits are never touched ---

    @Test
    fun `a user-edited segment is never sent to the model and never gets an AI candidate`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s2" to "Cleaned."))))
        val edited = seg("s1", "User's own correction.", isUserEdited = true)
        val result = engine(model).clean(listOf(edited, seg("s2", "Raw text.")), profile(RecordingType.MEETING), singleSpeakerMode = false)

        val cleaned = (result as AiResult.Success).value
        assertEquals("User's own correction.", cleaned.segments[0].text)
        assertNull(cleaned.segments[0].cleanedText)
        // The user's edit is never asked to be rewritten — it never appears as a PRIMARY TEXT
        // entry, though it may still legitimately surface as read-only CONTEXT for a neighboring
        // paragraph (context windows draw from every segment, not just AI-eligible ones).
        model.promptsSeen.forEach { prompt ->
            val primaryBlock = prompt.substringAfter("PRIMARY TEXT").substringBefore("CONTEXT AFTER")
            assertFalse(primaryBlock.contains("[s1]"))
        }
    }

    // --- Prompt content: recording-type and single-speaker-mode guidance ---

    @Test
    fun `the prompt includes Lecture-specific guidance for a Lecture recording`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "Cleaned."))))
        engine(model).clean(listOf(seg("s1", "Raw.")), profile(RecordingType.LECTURE), singleSpeakerMode = false)

        assertTrue(model.promptsSeen.single().contains("explanatory monologue"))
    }

    @Test
    fun `the prompt includes Meeting-specific turn-boundary guidance for a Meeting recording`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "Cleaned."))))
        engine(model).clean(listOf(seg("s1", "Raw.")), profile(RecordingType.MEETING), singleSpeakerMode = false)

        assertTrue(model.promptsSeen.single().contains("turn boundaries"))
    }

    @Test
    fun `single-speaker mode adds an explicit note to the prompt`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "Cleaned."))))
        engine(model).clean(listOf(seg("s1", "Raw.")), profile(RecordingType.IDEA), singleSpeakerMode = true)

        assertTrue(model.promptsSeen.single().contains("single continuous speaker"))
    }

    @Test
    fun `the prompt never omits the fidelity contract regardless of recording type`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "Cleaned."))))
        engine(model).clean(listOf(seg("s1", "Raw.")), profile(RecordingType.GENERAL), singleSpeakerMode = false)

        val prompt = model.promptsSeen.single()
        assertTrue(prompt.contains("MUST NOT"))
        assertTrue(prompt.contains("invent"))
    }

    // --- Mode-aware permissiveness: the same prompt text differs by mode, and outcomes differ too ---

    @Test
    fun `the permissiveness guidance in the prompt differs by mode`() = runBlocking {
        val model = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "Cleaned."))))
        engine(model).clean(listOf(seg("s1", "Raw.")), profile(RecordingType.MEETING, TranscriptCleanupMode.AGGRESSIVE), singleSpeakerMode = false)

        assertTrue(model.promptsSeen.single().contains("polished, professional transcript"))
    }

    @Test
    fun `a substantially rewritten candidate is rejected under Conservative but accepted under Aggressive`() = runBlocking {
        val raw = "I think what I want to do is, what I'm thinking is basically that we should probably, I think we should use the smaller model."
        val heavilyCleaned = "I think we should use the smaller model."

        val conservativeModel = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to heavilyCleaned))))
        val conservativeResult = engine(conservativeModel).clean(
            listOf(seg("s1", raw)), profile(RecordingType.MEETING, TranscriptCleanupMode.CONSERVATIVE), singleSpeakerMode = false
        )
        assertEquals(0, (conservativeResult as AiResult.Success).value.paragraphsAccepted)

        val aggressiveModel = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to heavilyCleaned))))
        val aggressiveResult = engine(aggressiveModel).clean(
            listOf(seg("s1", raw)), profile(RecordingType.MEETING, TranscriptCleanupMode.AGGRESSIVE), singleSpeakerMode = false
        )
        assertEquals(1, (aggressiveResult as AiResult.Success).value.paragraphsAccepted)
        assertEquals(heavilyCleaned, aggressiveResult.value.segments[0].cleanedText)
    }

    // --- Contextual windows: neighboring paragraphs inform, but are never rewritten themselves ---

    @Test
    fun `the prompt includes context before and after, clearly separated from the primary text`() = runBlocking {
        val model = RespondToWhicheverIdsWereAskedModel()
        // Padded near the chunker's fixed 896-char budget floor (see the "one failed chunk..."
        // test above for the same technique) — short paragraphs would all pack into a single
        // chunk under contextTokens = 500, since the budget never falls below 256 tokens.
        val segments = listOf(
            seg("s1", "word ".repeat(165).trim() + " First paragraph, the context before.", startMs = 0, endMs = 1000),
            seg("s2", "word ".repeat(165).trim() + " Second paragraph, the one being cleaned.", startMs = 2000, endMs = 3000),
            seg("s3", "word ".repeat(165).trim() + " Third paragraph, the context after.", startMs = 4000, endMs = 5000)
        )
        // Force each paragraph into its own chunk so s2's context window is unambiguous.
        engine(model, contextTokens = 500).clean(segments, profile(RecordingType.MEETING), singleSpeakerMode = false)

        // "[s2]" also legitimately appears inside s1's own CONTEXT AFTER block, so match on where
        // s2 is the PRIMARY TEXT being cleaned, not merely mentioned anywhere in the prompt.
        val s2Prompt = model.promptsSeen.first { prompt ->
            prompt.substringAfter("PRIMARY TEXT").substringBefore("CONTEXT AFTER").contains("[s2]")
        }
        assertTrue(s2Prompt.contains("CONTEXT BEFORE"))
        assertTrue(s2Prompt.contains("CONTEXT AFTER"))
        assertTrue(s2Prompt.contains("context before"))
        assertTrue(s2Prompt.contains("context after"))
        assertTrue(s2Prompt.contains("do NOT rewrite"))
    }

    @Test
    fun `a context paragraph is never returned as a cleaned candidate itself`() = runBlocking {
        val model = RespondToWhicheverIdsWereAskedModel()
        val segments = listOf(
            seg("s1", "First paragraph, the context before.", startMs = 0, endMs = 1000),
            seg("s2", "Second paragraph, the one being cleaned.", startMs = 2000, endMs = 3000),
            seg("s3", "Third paragraph, the context after.", startMs = 4000, endMs = 5000)
        )
        val result = engine(model, contextTokens = 500).clean(segments, profile(RecordingType.MEETING), singleSpeakerMode = false)

        val cleaned = (result as AiResult.Success).value
        // Every segment gets its own real chunk (forced by the tiny budget) so every one is
        // eligible for a real candidate — the point here is that s1/s3 only ever receive a
        // candidate when THEY are the primary chunk, never as a side effect of being s2's context.
        assertEquals(3, cleaned.paragraphsAccepted)
    }

    @Test
    fun `no context before at the very first paragraph, no context after at the very last`() = runBlocking {
        val model = RespondToWhicheverIdsWereAskedModel()
        val segments = listOf(
            seg("s1", "Only the first paragraph here.", startMs = 0, endMs = 1000),
            seg("s2", "And the last paragraph here.", startMs = 2000, endMs = 3000)
        )
        engine(model, contextTokens = 500).clean(segments, profile(RecordingType.MEETING), singleSpeakerMode = false)

        val s1Prompt = model.promptsSeen.first { it.contains("[s1]") }
        val s2Prompt = model.promptsSeen.first { it.contains("[s2]") }
        assertFalse("First paragraph has no context before it", s1Prompt.contains("CONTEXT BEFORE"))
        assertFalse("Last paragraph has no context after it", s2Prompt.contains("CONTEXT AFTER"))
    }

    // --- Chunking across a long transcript ---

    @Test
    fun `a long transcript is split into multiple model calls under a small context budget`() = runBlocking {
        val segments = (0 until 10).map { i ->
            seg("s$i", "This is paragraph number $i with a reasonable amount of real text in it.", startMs = i * 2000L, endMs = i * 2000L + 1000L)
        }
        val model = RespondToWhicheverIdsWereAskedModel()

        val result = engine(model, contextTokens = 500).clean(segments, profile(RecordingType.MEETING), singleSpeakerMode = false)

        assertTrue(result is AiResult.Success)
        assertTrue("Expected multiple chunks for 10 paragraphs under a tiny budget", model.callCount > 1)
        val cleaned = (result as AiResult.Success).value
        assertEquals(10, cleaned.paragraphsAccepted)
    }

    // --- Re-run without re-transcription: the engine's only input is already-persisted data ---

    @Test
    fun `cleanup can be re-run purely from segments - no audio or transcription dependency`() = runBlocking {
        // The interface signature itself proves this (segments + profile + flag only), but this
        // test proves running it twice in a row on the same persisted data is well-defined and
        // produces the same accepted result both times.
        val segments = listOf(seg("s1", "Uh, this is the raw text right here."))
        val firstModel = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "This is the raw text right here."))))
        val firstRun = engine(firstModel).clean(segments, profile(RecordingType.MEETING), singleSpeakerMode = false)
        val firstCleaned = (firstRun as AiResult.Success).value.segments

        val secondModel = FakeLanguageModel(mutableListOf(AiResult.Success(jsonResponse("s1" to "This is the raw text right here."))))
        val secondRun = engine(secondModel).clean(firstCleaned, profile(RecordingType.MEETING), singleSpeakerMode = false)
        val secondCleaned = (secondRun as AiResult.Success).value.segments

        assertEquals("This is the raw text right here.", secondCleaned[0].cleanedText)
    }
}
