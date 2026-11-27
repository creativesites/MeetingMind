package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.feature.learn.StressReason
import com.craftflowtechnologies.meetingmind.feature.learn.TimeRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class StudyKitGeneratorTest {

    private val s1 = "Enzymes lower the activation energy of a reaction by binding substrate."
    private val s2 = "Competitive inhibitors bind to the active site and block substrate."
    private val s3 = "Remember this for the exam, it will definitely be tested."
    private val s4 = "Vmax is the maximum rate when the enzyme is saturated."

    private val lecture = listOf(
        seg("s1", 0, 10_000, s1),
        seg("s2", 10_000, 20_000, s2),
        seg("s3", 20_000, 30_000, s3, conf = 0.3f),
        seg("s4", 30_000, 40_000, s4)
    )

    private fun seg(id: String, start: Long, end: Long, text: String, conf: Float? = null) = TranscriptSegment(
        id = id, meetingId = "m1", startMs = start, endMs = end, text = text, confidence = conf
    )

    /** Routes each prompt to its answer by the sentence that only that stage's prompt contains. */
    private inner class FakeModel(
        var reading: (String) -> String = { readingJson() },
        var activities: (String) -> String = { activityJson(activityObj("Which process do competitive inhibitors block?")) },
        var summary: String = SUMMARY,
        var unavailable: Boolean = false
    ) : LanguageModel {
        val prompts = mutableListOf<String>()

        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            prompts += prompt
            if (unavailable) return AiResult.ModelUnavailable("local-llm", "No model installed.")
            return AiResult.Success(
                when {
                    prompt.contains("part of a lecture transcript") -> reading(prompt)
                    prompt.contains("quick checks") -> activities(prompt)
                    prompt.contains("Write a summary") -> summary
                    else -> "{}"
                }
            )
        }

        fun count(stage: String) = prompts.count { p ->
            when (stage) {
                "reading" -> p.contains("part of a lecture transcript")
                "activity" -> p.contains("quick checks")
                else -> p.contains("Write a summary")
            }
        }
    }

    private var ids = 0
    private fun generator(model: LanguageModel, contextTokens: Int = 4096) = StudyKitGenerator(
        model = model,
        modelId = "model-x",
        contextLengthTokens = contextTokens,
        now = { 1_000L },
        newId = { "id-${ids++}" }
    )

    private fun run(model: LanguageModel, segments: List<TranscriptSegment> = lecture, contextTokens: Int = 4096): StudyKitResult =
        runBlocking { generator(model, contextTokens).generate(StudyLectureInput("sess", "m1", segments)) }

    private fun ready(model: LanguageModel): GeneratedStudyContent =
        (run(model) as? StudyKitResult.Ready)?.content ?: error("expected a ready kit, got ${run(FakeModel())}")

    // ------------------------------------------------------------- happy path

    @Test
    fun `a grounded lecture produces a kit, concepts and checks that all validate`() {
        val content = ready(FakeModel())

        assertEquals(1, content.concepts.size)
        val concept = content.concepts.single()
        assertEquals("Competitive inhibition", concept.name)
        assertEquals("Active site", concept.relationships.single())
        assertEquals("m1", concept.evidence.single().meetingId)
        assertEquals(10_000L, concept.evidence.single().startMs)

        assertEquals(1, content.activities.size)
        val activity = content.activities.single()
        assertEquals(LearningActivityType.MULTIPLE_CHOICE, activity.type)
        assertEquals(4, activity.options.size)
        assertEquals("Substrate binding at the active site", activity.expectedAnswer)
        assertEquals(concept.id, activity.conceptId)

        val kit = content.kit
        assertEquals("model-x", kit.modelId)
        assertEquals("Enzymes speed reactions. Inhibitors block the active site.", kit.summary)
        assertEquals(1, kit.sections.size)
        assertEquals(listOf("active site"), kit.sections.single().terms.map { it.text })
        assertEquals(concept.id, kit.sections.single().terms.single().conceptId)
        assertEquals(10_000L, kit.sections.single().startMs)
        assertEquals("What do competitive inhibitors block?", kit.sections.single().quickCheck?.prompt)
        assertEquals(StressReason.NAMED_EXAM, kit.stressed.single().reason)
        assertEquals(concept.id, kit.stressed.single().conceptId)
        assertEquals("Inhibitors occupy the active site.", kit.rightWhys[activity.id])
        assertEquals("Heat is not an inhibitor here.", kit.optionWhys.single { it.optionIndex == 1 }.why)
        assertEquals(listOf(TimeRange(20_000, 30_000)), kit.hardToHear)
    }

    @Test
    fun `progress is reported in order with the plain captions`() {
        val steps = mutableListOf<String>()
        runBlocking {
            generator(FakeModel()).generate(StudyLectureInput("sess", "m1", lecture)) { steps += it }
        }
        assertEquals(
            listOf(
                StudyKitGenerator.CAPTION_READING,
                StudyKitGenerator.CAPTION_STRESSED,
                StudyKitGenerator.CAPTION_CHECKS
            ),
            steps.distinct()
        )
        assertEquals("Reading your lecture", StudyKitGenerator.CAPTION_READING)
        assertEquals("Finding what your lecturer stressed", StudyKitGenerator.CAPTION_STRESSED)
        assertEquals("Writing your quick checks", StudyKitGenerator.CAPTION_CHECKS)
    }

    // ------------------------------------------------------------- validation rules

    @Test
    fun `a concept whose quote does not match the transcript near its timestamp is dropped`() {
        val model = FakeModel(reading = { readingJson(quote = "Mitochondria produce ATP in the cell") })
        val content = ready(model)
        assertTrue(content.concepts.isEmpty())
        assertTrue(content.activities.isEmpty())
        assertTrue(content.kit.sections.single().terms.isEmpty())
    }

    @Test
    fun `a quote far from its timestamp does not match`() {
        val lines = lecture.map { Triple(it.startMs, it.endMs, it.text) }
        assertTrue(StudyKitGenerator.quoteMatches(s2, 10_000, 20_000, lines))
        assertFalse(StudyKitGenerator.quoteMatches(s2, 35_000, 36_000, lines))
        assertFalse(StudyKitGenerator.quoteMatches("banana orange kiwi grape mango", 10_000, 20_000, lines))
        // Five words, three of them in the transcript: 0.6, which is a match.
        assertTrue(StudyKitGenerator.quoteMatches("competitive inhibitors bind xylophone zebra", 10_000, 20_000, lines))
    }

    @Test
    fun `a timestamp outside the lecture is dropped`() {
        val model = FakeModel(activities = { singleActivity(endMs = 999_999_000) })
        assertTrue(ready(model).activities.isEmpty())
    }

    @Test
    fun `a term that does not occur in its section body is dropped`() {
        val model = FakeModel(reading = { readingJson(term = "mitochondria") })
        val section = ready(model).kit.sections.single()
        assertEquals("Inhibition", section.title)
        assertTrue(section.terms.isEmpty())
    }

    @Test
    fun `a correct index out of range drops the question`() {
        assertTrue(ready(FakeModel(activities = { singleActivity(correct = 7) })).activities.isEmpty())
        assertTrue(ready(FakeModel(activities = { singleActivity(correct = -1) })).activities.isEmpty())
    }

    @Test
    fun `a question that states the right answer is dropped`() {
        val model = FakeModel(
            activities = {
                singleActivity(prompt = "Is substrate binding at the active site what inhibitors do?")
            }
        )
        assertTrue(ready(model).activities.isEmpty())
    }

    @Test
    fun `near-identical questions are kept once`() {
        val model = FakeModel(
            activities = {
                activityJson(
                    activityObj("Which process do competitive inhibitors block?"),
                    activityObj("Which process do competitive inhibitors block here?")
                )
            }
        )
        assertEquals(1, ready(model).activities.size)
    }

    @Test
    fun `questions are never sourced from hard-to-hear ranges`() {
        val hard = "Remember this for the exam, it will definitely be tested."
        val model = FakeModel(
            activities = {
                activityJson(activityObj("Why will this be tested?", startMs = 20_000, endMs = 30_000, quote = hard))
            }
        )
        assertTrue(ready(model).activities.isEmpty())
    }

    @Test
    fun `hard-to-hear ranges come from ASR confidence and merge when they run together`() {
        val segments = listOf(
            seg("a", 0, 1_000, "clear", conf = 0.9f),
            seg("b", 20_000, 25_000, "mumbled", conf = 0.3f),
            seg("c", 25_200, 30_000, "garbled", conf = 0.2f),
            seg("d", 60_000, 61_000, "noise", conf = 0.1f),
            seg("e", 62_000, 63_000, "fine")
        )
        assertEquals(
            listOf(TimeRange(20_000, 30_000), TimeRange(60_000, 61_000)),
            StudyKitGenerator.hardToHearRanges(segments)
        )
    }

    @Test
    fun `at most twelve concepts and sixteen questions are kept`() {
        val manyConcepts = FakeModel(reading = { readingWithConcepts((1..15).map { "Concept $it" }) })
        assertEquals(12, ready(manyConcepts).concepts.size)

        val manyQuestions = FakeModel(
            activities = {
                activityJson(*(1..20).map { activityObj("Question number $it about inhibitors?") }.toTypedArray())
            }
        )
        assertEquals(16, ready(manyQuestions).activities.size)
    }

    @Test
    fun `answer leakage check is case-insensitive and catches embedded answers`() {
        assertTrue(StudyKitGenerator.answerLeaks("Tell me about SUBSTRATE binding today", "substrate binding"))
        assertFalse(StudyKitGenerator.answerLeaks("Which enzyme is saturated?", "Vmax"))
    }

    // ------------------------------------------------------------- failures and cost

    @Test
    fun `an empty transcript is reported as no transcript without calling the model`() {
        val model = FakeModel()
        assertEquals(StudyKitResult.NoTranscript, run(model, segments = listOf(seg("x", 0, 1000, "   "))))
        assertEquals(0, model.prompts.size)
    }

    @Test
    fun `no model available is reported as needs AI`() {
        val result = run(FakeModel(unavailable = true))
        assertTrue(result is StudyKitResult.NeedsAi)
    }

    @Test
    fun `an unreadable reply on every stage fails honestly`() {
        val result = run(FakeModel(reading = { "not json at all" }))
        assertTrue(result is StudyKitResult.Failed)
    }

    @Test
    fun `a long lecture is read in chunks, and checks come from a bounded sample`() {
        val long = (0 until 30).map { i -> seg("L$i", i * 10_000L, i * 10_000L + 9_000L, "Filler sentence number $i. ".repeat(12)) }
        val model = FakeModel(reading = { """{"concepts":[],"sections":[],"stressed":[]}""" })
        val result = run(model, segments = long, contextTokens = 2000)
        assertTrue(result is StudyKitResult.Ready)
        assertTrue("reading chunks ${model.count("reading")}", model.count("reading") >= 2)
        assertTrue(model.count("activity") <= 4)
        assertEquals(1, model.count("summary"))
    }

    @Test
    fun `the summary is written after the merged content`() {
        val model = FakeModel()
        run(model)
        assertTrue(model.prompts.last().contains("Write a summary"))
    }

    // ------------------------------------------------------------- fixtures

    private fun readingJson(
        concept: String = "Competitive inhibition",
        quote: String = s2,
        term: String = "active site"
    ): String = """{
  "concepts": [{"name": "$concept", "definition": "Binds the active site.", "emphasis": "", "relationships": ["Active site"], "startMs": 10000, "endMs": 20000, "quote": "$quote"}],
  "sections": [{"title": "Inhibition", "startMs": 10000, "body": "$s2", "terms": [{"text": "$term", "conceptName": "$concept"}], "quickCheck": {"prompt": "What do competitive inhibitors block?", "answer": "Substrate binding", "conceptName": "$concept"}}],
  "stressed": [{"startMs": 20000, "endMs": 30000, "quote": "$s3", "reason": "NAMED_EXAM", "conceptName": "$concept"}]
}"""

    private fun readingWithConcepts(names: List<String>): String {
        val items = names.joinToString(",") {
            """{"name": "$it", "definition": "A definition.", "emphasis": "", "relationships": [], "startMs": 10000, "endMs": 20000, "quote": "$s2"}"""
        }
        return """{"concepts": [$items], "sections": [], "stressed": []}"""
    }

    private fun activityObj(
        prompt: String,
        startMs: Long = 10_000,
        endMs: Long = 20_000,
        quote: String = s2,
        correct: Int = 0
    ): String {
        val options = """["Substrate binding at the active site", "Enzyme denaturation by heat", "Allosteric activation", "Product release"]"""
        return """{"type": "MULTIPLE_CHOICE", "conceptName": "Competitive inhibition", "prompt": "$prompt", "options": $options, "correctIndex": $correct, "rightWhy": "Inhibitors occupy the active site.", "wrongWhys": {"1": "Heat is not an inhibitor here."}, "startMs": $startMs, "endMs": $endMs, "quote": "$quote"}"""
    }

    private fun activityJson(vararg objects: String): String =
        """{"activities": [${objects.joinToString(",")}]}"""

    /** One activity, with the given prompt, end time or correct index. */
    private fun singleActivity(
        prompt: String = "Which process do competitive inhibitors block?",
        endMs: Long = 20_000,
        correct: Int = 0
    ): String = activityJson(activityObj(prompt, endMs = endMs, correct = correct))

    companion object {
        const val SUMMARY = "{\"summary\": \"Enzymes speed reactions. Inhibitors block the active site.\"}"
    }
}
