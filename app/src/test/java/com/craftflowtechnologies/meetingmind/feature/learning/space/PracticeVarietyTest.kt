package com.craftflowtechnologies.meetingmind.feature.learning.space

import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Practice variety: format rotation, mixing, the difficulty ladder, distractors and the offline fallback. */
class PracticeVarietyTest {

    private val now = 1_763_000_000_000L

    private fun concept(
        id: String,
        name: String,
        definition: String,
        state: LearningMasteryState = LearningMasteryState.NEW,
        session: String = "s1",
        quotes: List<String> = emptyList()
    ) = LearningConcept(
        id = id, sessionId = session, name = name, definition = definition, state = state,
        evidence = quotes.map { LearningEvidence(quote = it) }, createdAt = now, updatedAt = now
    )

    private fun activity(
        id: String,
        conceptId: String?,
        type: LearningActivityType = LearningActivityType.RECALL,
        prompt: String = "Prompt $id",
        answer: String = "Answer $id",
        options: List<String> = emptyList(),
        session: String = "s1"
    ) = LearningActivity(
        id = id, sessionId = session, conceptId = conceptId, type = type, prompt = prompt,
        expectedAnswer = answer, options = options, createdAt = now, updatedAt = now
    )

    private fun attempt(activityId: String, conceptId: String?, correct: Boolean, feedback: String = "", at: Long = now) = ActivityAttempt(
        id = "att_${activityId}_$at", activityId = activityId, sessionId = "s1", conceptId = conceptId,
        userResponse = "x", isCorrect = correct, feedback = feedback, createdAt = at
    )

    private val competitive = concept(
        "c1", "Competitive inhibition",
        "A competitive inhibitor binds the active site and raises the apparent Km while Vmax stays the same."
    )
    private val nonCompetitive = concept(
        "c2", "Non-competitive inhibition",
        "A non-competitive inhibitor binds elsewhere on the enzyme and lowers Vmax while Km stays the same."
    )
    private val km = concept(
        "c3", "Michaelis constant",
        "Km is the substrate concentration at which the reaction runs at half its maximum rate."
    )
    private val allosteric = concept(
        "c4", "Allosteric regulation",
        "An effector binding at a site other than the active site changes the shape and activity of the enzyme."
    )
    private val lecture = listOf(competitive, nonCompetitive, km, allosteric)

    private fun conceptsWith(vararg replaced: LearningConcept): Map<String, LearningConcept> =
        (replaced.toList() + lecture).distinctBy { it.id }.associateBy { it.id }

    private val lectureActivities = listOf(
        activity("a1", "c1", prompt = "What does a competitive inhibitor do to Km and Vmax?", answer = "Km rises while Vmax stays the same"),
        activity("a2", "c2", prompt = "What does a non-competitive inhibitor do to Km and Vmax?", answer = "Vmax falls while Km stays the same"),
        activity("a3", "c3", prompt = "What is Km?", answer = "The substrate concentration at half of Vmax"),
        activity("a4", "c4", prompt = "What is allosteric regulation?", answer = "Control through a site other than the active site")
    )

    // ── Format rotation ──

    @Test
    fun `the same card is never asked the same way twice in a row`() {
        val learning = competitive.copy(state = LearningMasteryState.LEARNING)
        val first = buildPracticeDeck(
            lectureActivities.take(1), conceptsWith(learning),
            attempts = listOf(attempt("a1", "c1", true, PracticeFormat.RECALL.tag)),
            siblingActivities = lectureActivities
        ).single().variant
        assertNotEquals(PracticeFormat.RECALL, first.format)
        assertEquals(PracticeStage.RECALL, first.stage)

        val second = buildPracticeDeck(
            lectureActivities.take(1), conceptsWith(learning),
            attempts = listOf(attempt("a1", "c1", true, first.format.tag)),
            siblingActivities = lectureActivities
        ).single().variant
        assertNotEquals(first.format, second.format)
    }

    @Test
    fun `older attempts without a tag rotate away from the activity type`() {
        val learning = competitive.copy(state = LearningMasteryState.LEARNING)
        val variant = buildPracticeDeck(
            lectureActivities.take(1), conceptsWith(learning),
            attempts = listOf(attempt("a1", "c1", true)),
            siblingActivities = lectureActivities
        ).single().variant
        assertNotEquals(PracticeFormat.RECALL, variant.format)
    }

    @Test
    fun `a set does not put the same format back to back when it can avoid it`() {
        val deck = buildPracticeDeck(lectureActivities, conceptsWith(), attempts = emptyList(), seed = 7L)
        assertEquals(4, deck.size)
        deck.zipWithNext().forEach { (a, b) -> assertNotEquals(a.variant.format, b.variant.format) }
    }

    @Test
    fun `a lone candidate is used even if it was used last time`() {
        val only = nativeVariant(activity("x", null))
        val chosen = chooseVariant(listOf(only), PracticeStage.EXPLAIN, VariantChoiceState(lastFormat = PracticeFormat.RECALL), seed = 3L)
        assertEquals(only, chosen)
    }

    @Test
    fun `format tags survive the round trip and unknown tags are ignored`() {
        PracticeFormat.entries.forEach { assertEquals(it, PracticeFormat.fromTag(it.tag)) }
        assertNull(PracticeFormat.fromTag(""))
        assertNull(PracticeFormat.fromTag(null))
        assertNull(PracticeFormat.fromTag("format:NOPE"))
        assertNull(PracticeFormat.fromTag("Great answer"))
    }

    // ── Mixing ──

    @Test
    fun `a backlog in one lecture cannot crowd out the others`() {
        val items = (1..30).map { "A$it" } + (1..5).map { "B$it" }
        val picked = selectMixed(items, 12) { it.take(1) }
        assertEquals(12, picked.size)
        assertEquals(5, picked.count { it.startsWith("B") })
        assertEquals(listOf("A1", "A2", "A3"), picked.filter { it.startsWith("A") }.take(3))
    }

    @Test
    fun `interleaving alternates sources and keeps each source in order`() {
        data class Item(val id: String, val source: String)
        val items = listOf(Item("a1", "A"), Item("a2", "A"), Item("a3", "A"), Item("a4", "A"), Item("b1", "B"), Item("b2", "B"))
        val out = interleaveBySource(items, { it.source }, { null })
        assertEquals(items.toSet(), out.toSet())
        out.take(5).zipWithNext().forEach { (x, y) -> assertNotEquals(x.source, y.source) }
        assertEquals(listOf("a1", "a2", "a3", "a4"), out.filter { it.source == "A" }.map { it.id })
    }

    @Test
    fun `interleaving within one lecture avoids two items on the same concept in a row`() {
        data class Item(val id: String, val concept: String)
        val items = listOf(Item("1", "c1"), Item("2", "c1"), Item("3", "c2"), Item("4", "c2"))
        val out = interleaveBySource(items, { "s1" }, { it.concept })
        out.zipWithNext().forEach { (x, y) -> assertNotEquals(x.concept, y.concept) }
    }

    @Test
    fun `daily recall sets mix lectures`() {
        val other = (1..3).map { activity("o$it", null, session = "s2") }
        val backlog = (1..20).map { activity("b$it", null, session = "s1") }
        val deck = buildPracticeDeck(backlog + other, emptyMap(), emptyList(), maxItems = PracticeSetSize)
        assertEquals(PracticeSetSize, deck.size)
        assertEquals(3, deck.count { it.activity.sessionId == "s2" })
        val sessions = deck.map { it.activity.sessionId }
        assertNotEquals(sessions[0], sessions[1])
    }

    // ── Difficulty ladder ──

    @Test
    fun `the ladder follows the mastery state`() {
        assertEquals(PracticeStage.RECOGNISE, targetStage(LearningMasteryState.NEW))
        assertEquals(PracticeStage.RECALL, targetStage(LearningMasteryState.LEARNING))
        assertEquals(PracticeStage.RECALL, targetStage(LearningMasteryState.NEEDS_REVIEW))
        assertEquals(PracticeStage.APPLY, targetStage(LearningMasteryState.DEVELOPING))
        assertEquals(PracticeStage.EXPLAIN, targetStage(LearningMasteryState.STRONG))
        assertEquals(PracticeStage.RECOGNISE, targetStage(null, 0))
        assertEquals(PracticeStage.APPLY, targetStage(null, 2))
        assertEquals(PracticeStage.EXPLAIN, targetStage(null, 5))
    }

    @Test
    fun `new concepts are recognised and strong ones are explained`() {
        val asNew = buildPracticeDeck(lectureActivities.take(1), conceptsWith(), emptyList(), siblingActivities = lectureActivities)
        assertEquals(PracticeStage.RECOGNISE, asNew.single().variant.stage)

        val strong = competitive.copy(state = LearningMasteryState.STRONG)
        val asStrong = buildPracticeDeck(lectureActivities.take(1), conceptsWith(strong), emptyList(), siblingActivities = lectureActivities)
        assertEquals(PracticeFormat.EXPLAIN, asStrong.single().variant.format)
        assertEquals("Competitive inhibition", asStrong.single().variant.subject)
    }

    @Test
    fun `developing concepts are applied to the lecture own words`() {
        val developing = competitive.copy(
            state = LearningMasteryState.DEVELOPING,
            evidence = listOf(LearningEvidence(quote = "So when you add this kind of inhibitor it simply competes with the substrate for the same site, which is why flooding the system with substrate overcomes it."))
        )
        val variant = buildPracticeDeck(lectureActivities.take(1), conceptsWith(developing), emptyList(), siblingActivities = lectureActivities)
            .single().variant
        assertEquals(PracticeFormat.SCENARIO, variant.format)
        assertEquals("Competitive inhibition", variant.options[variant.correctIndex!!])
    }

    @Test
    fun `one concept practice climbs from recognition upwards`() {
        val acts = listOf(
            activity("e1", "c1", LearningActivityType.WORKED_PROBLEM, answer = "1. Plot the data\n2. Read the intercept\n3. Compare Km"),
            activity("e2", "c1", LearningActivityType.RECALL, answer = "Km rises"),
            activity("e3", "c1", LearningActivityType.MULTIPLE_CHOICE, options = listOf("Km rises", "Km falls", "No change"), answer = "A")
        )
        val deck = buildPracticeDeck(acts, conceptsWith(competitive.copy(state = LearningMasteryState.LEARNING)), emptyList(), interleave = false, rampUp = true)
        val stages = deck.map { it.variant.stage.ordinal }
        assertEquals(stages.sorted(), stages)
    }

    @Test
    fun `the next stage on the ladder starts where the state is`() {
        assertEquals(PracticeStage.RECOGNISE, nextStageFor(LearningMasteryState.NEW, emptySet()))
        assertEquals(PracticeStage.APPLY, nextStageFor(LearningMasteryState.DEVELOPING, setOf(PracticeStage.RECOGNISE, PracticeStage.RECALL)))
        assertEquals(PracticeStage.RECOGNISE, nextStageFor(LearningMasteryState.DEVELOPING, setOf(PracticeStage.APPLY, PracticeStage.EXPLAIN)))
        assertNull(nextStageFor(LearningMasteryState.STRONG, PracticeStage.entries.toSet()))
    }

    @Test
    fun `stages come from correct answers, tagged or inferred`() {
        val attempts = listOf(
            attempt("a1", "c1", true),
            attempt("a1", "c1", true, PracticeFormat.EXPLAIN.tag),
            attempt("m1", "c2", false, PracticeFormat.CHOICE.tag)
        )
        val stages = stagesReached(attempts, mapOf("a1" to LearningActivityType.RECALL, "m1" to LearningActivityType.MULTIPLE_CHOICE))
        assertEquals(setOf(PracticeStage.RECALL, PracticeStage.EXPLAIN), stages["c1"])
        assertNull(stages["c2"])
    }

    // ── Distractors ──

    @Test
    fun `distractors are plausible, distinct and never the answer`() {
        val pool = listOf(
            "Km rises while Vmax stays the same",
            "Km rises, while Vmax stays the same!",
            "Vmax falls while Km stays the same",
            "Both Km and Vmax fall",
            "The enzyme is destroyed permanently by prolonged heating at a high temperature over many hours of incubation",
            "B",
            ""
        )
        val picked = pickDistractors("Km rises while Vmax stays the same", pool, 3)
        assertEquals(3, picked.size)
        assertEquals("Vmax falls while Km stays the same", picked.first())
        assertFalse(picked.any { it.startsWith("Km rises") })
        assertFalse(picked.contains("B"))
        assertEquals(picked.size, picked.map { it.lowercase() }.toSet().size)
    }

    @Test
    fun `too few distractors means no choice question is invented`() {
        assertTrue(pickDistractors("Km", listOf("km", "KM."), 3).isEmpty())
        val lone = activity("r1", null, answer = "Km rises")
        val formats = candidateVariants(lone, VariantContext(concept = null, siblingAnswers = listOf("Km falls"))).map { it.format }
        assertFalse(PracticeFormat.CHOICE in formats)
    }

    @Test
    fun `every planned choice is answerable`() {
        val deck = buildPracticeDeck(lectureActivities, conceptsWith(), emptyList(), seed = 11L)
        deck.forEach { item ->
            val v = item.variant
            if (v.format == PracticeFormat.CHOICE || v.format == PracticeFormat.SCENARIO) {
                val idx = v.correctIndex
                assertNotNull(idx)
                assertEquals(v.answer, v.options[idx!!])
                assertEquals(v.options.size, v.options.toSet().size)
            }
            if (v.format == PracticeFormat.MATCH) {
                assertEquals(1, v.pairs.count { it.isPrimary })
                assertEquals(v.pairs.indices.toSet(), v.displayOrder.toSet())
            }
            if (v.format == PracticeFormat.TRUE_FALSE && v.statementIsTrue == false) {
                assertTrue(v.correctionOwner.isNotBlank())
                assertEquals(1, v.correctIndex)
            }
        }
    }

    // ── Offline fallback ──

    @Test
    fun `with nothing extracted every item is still asked as written`() {
        val bare = listOf(
            activity("r1", null, answer = "Km rises"),
            activity("m1", null, LearningActivityType.MULTIPLE_CHOICE, options = listOf("Km falls", "Km rises", "Vmax falls"), answer = "B"),
            activity("s1", null, LearningActivityType.APPLICATION, answer = "Add more substrate")
        )
        val deck = buildPracticeDeck(bare, emptyMap(), emptyList())
        assertEquals(3, deck.size)
        val byId = deck.associate { it.activity.id to it.variant }
        assertEquals(PracticeFormat.RECALL, byId.getValue("r1").format)
        assertEquals(PracticeFormat.CHOICE, byId.getValue("m1").format)
        assertEquals(1, byId.getValue("m1").correctIndex)
        assertEquals("Km rises", byId.getValue("m1").answer)
        assertTrue(byId.getValue("s1").format in setOf(PracticeFormat.SHORT, PracticeFormat.RECALL))
    }

    @Test
    fun `the activity own question is always a candidate`() {
        val a = activity("r1", "c1", answer = "Km rises while Vmax stays the same")
        val candidates = candidateVariants(a, VariantContext(competitive, lecture, emptyList(), seed = 5L))
        assertEquals(nativeVariant(a), candidates.first())
        assertTrue(candidates.size >= 4)
    }

    // ── Fill the gap ──

    @Test
    fun `a gap is cut from the lecture sentence that names the concept`() {
        val quoted = competitive.copy(evidence = quotes(
            "Remember that competitive inhibition can be beaten by adding more substrate. The rest is detail."
        ))
        val cloze = buildCloze(quoted, quoted.evidence.map { it.quote })
        assertNotNull(cloze)
        assertTrue(cloze!!.fromSource)
        assertEquals("Remember that $ClozeBlank can be beaten by adding more substrate.", cloze.text)
        assertEquals("Competitive inhibition", cloze.answer)
    }

    @Test
    fun `without a quote the gap comes from the definition`() {
        val cloze = buildCloze(km, emptyList())
        assertNotNull(cloze)
        assertFalse(cloze!!.fromSource)
        assertTrue(cloze.text.contains(ClozeBlank))
        assertFalse(cloze.text.contains(cloze.answer))
    }

    @Test
    fun `gap answers forgive case, an article and a small slip`() {
        assertTrue(clozeMatches("Competitive inhibition", "competitive inhibiton"))
        assertTrue(clozeMatches("mitochondria", "The mitochondria"))
        assertTrue(clozeMatches("Km", "km"))
        assertFalse(clozeMatches("Km", "Kd"))
        assertFalse(clozeMatches("Vmax", "Km"))
        assertFalse(clozeMatches("substrate", ""))
    }

    // ── Steps ──

    @Test
    fun `steps are read from numbered lines, inline numbers or sequence words`() {
        assertEquals(
            listOf("Substrate binds", "Complex forms", "Product is released"),
            extractSteps("1. Substrate binds\n2. Complex forms\n3. Product is released")
        )
        assertEquals(listOf("Bind", "Convert", "Release"), extractSteps("Three stages 1) Bind 2) Convert 3) Release"))
        assertEquals(3, extractSteps("First the substrate binds. Then the complex forms. Finally the product leaves.").size)
        assertTrue(extractSteps("Km is a constant for a given enzyme.").isEmpty())
    }

    @Test
    fun `a shuffled order never gives the answer away`() {
        (0L..60L).forEach { seed ->
            val order = derangement(3, seed)
            assertNotEquals(listOf(0, 1, 2), order)
            assertEquals(setOf(0, 1, 2), order.toSet())
        }
    }

    // ── Summary and momentum ──

    @Test
    fun `the summary reads how the set went`() {
        val mostly = digestOf(listOf(true, true, false, true, true, true).mapIndexed { i, ok -> AnsweredItem("c$i", ok, PracticeFormat.RECALL) })
        assertEquals(SessionMood.MOSTLY_RIGHT, mostly.mood)
        assertEquals(3, mostly.bestRun)
        assertEquals(listOf("c2"), mostly.revisit)
        assertEquals(listOf(PracticeFormat.RECALL), mostly.formats)

        assertEquals(SessionMood.ALL_RIGHT, digestOf(listOf(AnsweredItem("c1", true, null))).mood)
        assertEquals(SessionMood.TOUGH, digestOf(listOf(false, false, true).map { AnsweredItem(null, it, null) }).mood)
        assertEquals(SessionMood.NONE, digestOf(emptyList()).mood)
    }

    @Test
    fun `the latest answer on a concept decides whether it stuck`() {
        val d = digestOf(
            listOf(
                AnsweredItem("c1", false, PracticeFormat.CHOICE),
                AnsweredItem("c2", true, PracticeFormat.CLOZE),
                AnsweredItem("c1", true, PracticeFormat.MATCH)
            )
        )
        assertEquals(listOf("c1", "c2"), d.stuck)
        assertTrue(d.revisit.isEmpty())
        assertEquals(listOf(PracticeFormat.CHOICE, PracticeFormat.CLOZE, PracticeFormat.MATCH), d.formats)
    }

    @Test
    fun `momentum is mentioned only at a few runs`() {
        assertEquals(3, momentumMilestone(3))
        assertNull(momentumMilestone(4))
        assertEquals(2, trailingStreak(listOf(false, true, true)))
        assertEquals(0, trailingStreak(listOf(true, false)))
    }

    private fun quotes(vararg q: String): List<LearningEvidence> = q.map { LearningEvidence(quote = it) }
}
