package com.craftflowtechnologies.meetingmind.feature.learn.explain

import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class ExplainAloudViewModelTest {



    @Before
    fun setUp() {

        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private val concept = LearningConcept(
        id = "c-vmax",
        sessionId = "s1",
        name = "Vmax",
        definition = "Vmax is the maximum reaction rate when the enzyme is saturated. Binds the active site.",
        evidence = listOf(
            LearningEvidence(startMs = 754_000L, endMs = 770_000L, quote = "More substrate wins it back, but Vmax stays the same.")
        ),
        createdAt = 0L,
        updatedAt = 0L
    )
    private val content = ExplainContent(
        sessionId = "s1",
        concept = concept,
        subjectLine = "BIO 201 · Inhibition",
        meetingId = "m1",
        lectureTitle = "Inhibition"
    )
    private val rubric = ExplainRubric.build(concept)

    private fun newVm(
        speech: FakeSpeech = FakeSpeech(),
        assessor: FakeAssessor = FakeAssessor(),
        store: FakeStore = FakeStore(),
        played: MutableList<Long> = mutableListOf()
    ) = ExplainAloudViewModel(
        content = content,
        speech = speech,
        assessor = assessor,
        store = store,
        playAt = { played += it },
        clock = { 0L },
        tickMs = 250L
    )

    private val judgedAllPartly = ExplainAssessment.Judged(
        statuses = listOf(IdeaCoverage.COVERED, IdeaCoverage.PARTLY, IdeaCoverage.NOT_YET),
        summary = "You got the mechanism. The rest needs the lecture's point.",
        nextQuestion = "What happens to Vmax when there is more substrate?",
        overallCovered = false
    )

    @Test
    fun `the checklist is built from the rubric, all not yet, before anything is said`() = runTest {
        val vm = newVm()
        val state = vm.state.value
        assertEquals(ExplainPhase.READY, state.phase)
        assertEquals(rubric.size, state.ideas.size)
        assertTrue(state.ideas.all { it.coverage == IdeaCoverage.NOT_YET })
        assertEquals("Explain Vmax as if to a friend. Aim for about a minute.", state.prompt)
        assertEquals("BIO 201 · Inhibition", state.subjectLine)
    }

    @Test
    fun `a concept with nothing to check against goes straight to no evidence`() = runTest {
        val bare = concept.copy(definition = "", evidence = emptyList(), name = "It")
        val vm = ExplainAloudViewModel(content.copy(concept = bare), FakeSpeech(), FakeAssessor(), FakeStore(), {}, { 0L })
        assertEquals(ExplainPhase.NO_EVIDENCE, vm.state.value.phase)
        vm.onStartTapped(micGranted = true)
        assertEquals(ExplainPhase.NO_EVIDENCE, vm.state.value.phase)
    }

    @Test
    fun `with the microphone allowed, start records and the checklist fills from partial words`() = runTest {
        val speech = FakeSpeech()
        val vm = newVm(speech = speech)
        vm.onStartTapped(micGranted = true)

        assertEquals(ExplainPhase.RECORDING, vm.state.value.phase)
        assertEquals(1, speech.started)

        speech.emit(SpeechEvent.Partial("Vmax is the maximum reaction rate when saturated"))
        val byLabel = vm.state.value.ideas.associate { it.claim to it.coverage }
        assertEquals(IdeaCoverage.COVERED, byLabel[rubric[0].claim])
        assertEquals(IdeaCoverage.NOT_YET, byLabel[rubric[1].claim])
        assertEquals("Vmax is the maximum reaction rate when saturated", vm.state.value.transcript)
        vm.onStartOver() // a live timer must not outlive the test
    }

    @Test
    fun `levels push the bars along and stay the same length`() = runTest {
        val speech = FakeSpeech()
        val vm = newVm(speech = speech)
        vm.onStartTapped(micGranted = true)
        speech.emit(SpeechEvent.Level(0.9f))
        assertEquals(ExplainUiState.LEVEL_BARS, vm.state.value.levels.size)
        assertEquals(0.9f, vm.state.value.levels.last(), 0.0001f)
        vm.onStartOver()
    }

    @Test
    fun `without the microphone the round asks first, and refusing falls back to typing`() = runTest {
        val speech = FakeSpeech()
        val vm = newVm(speech = speech)
        vm.onStartTapped(micGranted = false)
        assertEquals(ExplainPhase.NEEDS_MIC, vm.state.value.phase)
        assertEquals(0, speech.started)

        vm.onMicPermissionResult(granted = false)
        assertEquals(ExplainPhase.TYPING, vm.state.value.phase)
        assertNotNull(vm.state.value.message)
        assertEquals(0, speech.started)
    }

    @Test
    fun `a recognizer that cannot start falls back to typing with a plain message`() = runTest {
        val speech = FakeSpeech(failOnStart = SpeechFailure.NO_RECOGNIZER)
        val vm = newVm(speech = speech)
        vm.onStartTapped(micGranted = true)
        assertEquals(ExplainPhase.TYPING, vm.state.value.phase)
        assertTrue(vm.state.value.message!!.contains("Type it instead"))
    }

    @Test
    fun `stop waits for the final words and judges them, with covered partly and not yet`() = runTest {
        val assessor = FakeAssessor(next = judgedAllPartly)
        val store = FakeStore()
        val speech = FakeSpeech(finalOnStop = SpeechEvent.Final("Vmax is the maximum reaction rate"))
        val vm = newVm(speech = speech, assessor = assessor, store = store)
        vm.onStartTapped(micGranted = true)
        vm.onStop()

        assertEquals("Vmax is the maximum reaction rate", assessor.lastTranscript)
        assertEquals(ExplainPhase.RESULT, vm.state.value.phase)
        assertEquals(
            listOf(IdeaCoverage.COVERED, IdeaCoverage.PARTLY, IdeaCoverage.NOT_YET),
            vm.state.value.ideas.map { it.coverage }
        )
        assertEquals(judgedAllPartly.summary, vm.state.value.summary)
        assertEquals("What happens to Vmax when there is more substrate?", vm.state.value.nextPrompt)
    }

    @Test
    fun `the result shows the lecturer's words and a play button for each missing idea`() = runTest {
        val played = mutableListOf<Long>()
        val speech = FakeSpeech(finalOnStop = SpeechEvent.Final("Vmax is the maximum reaction rate"))
        val vm = newVm(speech = speech, assessor = FakeAssessor(next = judgedAllPartly), played = played)
        vm.onStartTapped(micGranted = true)
        vm.onStop()

        val missing = vm.state.value.ideas.filter { it.coverage != IdeaCoverage.COVERED }
        assertEquals(2, missing.size)
        assertEquals("More substrate wins it back, but Vmax stays the same", missing[1].claim)
        assertEquals(754_000L, missing[1].startMs)
        assertEquals("More substrate wins it back, but Vmax stays the same.", missing[1].quote)

        vm.onPlayIdea(2)
        assertEquals(listOf(754_000L), played)
    }

    @Test
    fun `a judged attempt is saved once with the transcript and the covered flag`() = runTest {
        val store = FakeStore()
        val speech = FakeSpeech(finalOnStop = SpeechEvent.Final("Vmax is the maximum reaction rate"))
        val vm = newVm(speech = speech, assessor = FakeAssessor(next = judgedAllPartly), store = store)
        vm.onStartTapped(micGranted = true)
        vm.onStop()

        assertEquals(1, store.judged.size)
        assertEquals("Vmax is the maximum reaction rate", store.judged.single().transcript)
        assertFalse(store.judged.single().covered)
        assertTrue(store.selfRated.isEmpty())
    }

    @Test
    fun `when no model answers the round says so and asks the learner to rate it`() = runTest {
        val store = FakeStore()
        val speech = FakeSpeech(finalOnStop = SpeechEvent.Final("I think it is the max rate"))
        val vm = newVm(speech = speech, assessor = FakeAssessor(next = ExplainAssessment.AiUnavailable), store = store)
        vm.onStartTapped(micGranted = true)
        vm.onStop()

        val state = vm.state.value
        assertEquals(ExplainPhase.SELF_RATE, state.phase)
        assertTrue(state.message!!.contains("AI isn't available"))
        assertTrue(store.judged.isEmpty())
        assertTrue(store.selfRated.isEmpty()) // nothing saved until the learner rates it
    }

    @Test
    fun `an used-up allowance is said plainly, and the self-rating is saved as a rating`() = runTest {
        val store = FakeStore()
        val speech = FakeSpeech(finalOnStop = SpeechEvent.Final("the max rate"))
        val vm = newVm(speech = speech, assessor = FakeAssessor(next = ExplainAssessment.AllowanceUsed), store = store)
        vm.onStartTapped(micGranted = true)
        vm.onStop()

        assertTrue(vm.state.value.message!!.contains("allowance for this month is used up"))

        vm.onRate(RecallButton.GOT_IT)
        assertEquals(ExplainPhase.SAVED, vm.state.value.phase)
        assertEquals(RecallButton.GOT_IT, vm.state.value.rated)
        assertEquals(listOf(RecallButton.GOT_IT), store.selfRated.map { it.rating })
        assertEquals("the max rate", store.selfRated.single().transcript)
    }

    @Test
    fun `a missed self-rating is saved as not correct`() = runTest {
        val store = FakeStore()
        val speech = FakeSpeech(finalOnStop = SpeechEvent.Final("no idea"))
        val vm = newVm(speech = speech, assessor = FakeAssessor(next = ExplainAssessment.AiUnavailable), store = store)
        vm.onStartTapped(micGranted = true)
        vm.onStop()
        vm.onRate(RecallButton.MISSED)
        assertEquals(RecallButton.MISSED, store.selfRated.single().rating)
    }

    @Test
    fun `the audio file the recognizer wrote is deleted once its words are read`() = runTest {
        val audio = File.createTempFile("explain", ".wav").apply { writeBytes(ByteArray(64)) }
        val speech = FakeSpeech(finalOnStop = SpeechEvent.Final("Vmax is fast", audioFile = audio))
        val vm = newVm(speech = speech, assessor = FakeAssessor(next = ExplainAssessment.AiUnavailable))
        vm.onStartTapped(micGranted = true)
        vm.onStop()
        assertFalse("audio should be gone", audio.exists())
    }

    @Test
    fun `a final result that arrives on its own, without stop, is still judged and its audio deleted`() = runTest {
        val audio = Files.createTempFile("explain", ".wav").toFile().apply { writeBytes(ByteArray(8)) }
        val assessor = FakeAssessor(next = judgedAllPartly)
        val speech = FakeSpeech()
        val vm = newVm(speech = speech, assessor = assessor)
        vm.onStartTapped(micGranted = true)
        speech.emit(SpeechEvent.Final("Vmax is the maximum reaction rate", audioFile = audio))

        assertEquals(ExplainPhase.RESULT, vm.state.value.phase)
        assertEquals("Vmax is the maximum reaction rate", assessor.lastTranscript)
        assertFalse(audio.exists())
    }

    @Test
    fun `when the recognizer is slow to finish, stop judges the last words it had`() = runTest {
        val assessor = FakeAssessor(next = judgedAllPartly)
        val speech = FakeSpeech() // never sends a final
        val vm = newVm(speech = speech, assessor = assessor)
        vm.onStartTapped(micGranted = true)
        speech.emit(SpeechEvent.Partial("Vmax is the maximum"))
        vm.onStop()
        assertEquals(ExplainPhase.THINKING, vm.state.value.phase)

        testScheduler.advanceTimeBy(ExplainAloudViewModel.FINAL_WAIT_MS + 1)
        assertEquals("Vmax is the maximum", assessor.lastTranscript)
        assertEquals(ExplainPhase.RESULT, vm.state.value.phase)
    }

    @Test
    fun `a failed recognizer mid-round drops to typing with the words so far`() = runTest {
        val speech = FakeSpeech()
        val vm = newVm(speech = speech)
        vm.onStartTapped(micGranted = true)
        speech.emit(SpeechEvent.Partial("Vmax is"))
        speech.emit(SpeechEvent.Failed(SpeechFailure.PERMISSION))
        assertEquals(ExplainPhase.TYPING, vm.state.value.phase)
        assertEquals("Vmax is", vm.state.value.transcript)
        assertEquals(1, speech.cancelled)
    }

    @Test
    fun `typed fallback is judged the same way and an empty answer asks again`() = runTest {
        val assessor = FakeAssessor(next = judgedAllPartly)
        val store = FakeStore()
        val vm = newVm(speech = FakeSpeech(), assessor = assessor, store = store)
        vm.onStartTapped(micGranted = false)
        vm.onMicPermissionResult(granted = false)

        vm.onSubmitTyped("   ")
        assertEquals(ExplainPhase.TYPING, vm.state.value.phase)
        assertTrue(vm.state.value.message!!.contains("didn't catch anything"))
        assertEquals(0, assessor.calls)

        vm.onSubmitTyped("Vmax is the maximum rate")
        assertEquals(ExplainPhase.RESULT, vm.state.value.phase)
        assertEquals(1, store.judged.size)
    }

    @Test
    fun `start over drops the words, cancels the microphone and saves nothing`() = runTest {
        val speech = FakeSpeech()
        val store = FakeStore()
        val vm = newVm(speech = speech, store = store)
        vm.onStartTapped(micGranted = true)
        speech.emit(SpeechEvent.Partial("Vmax is"))
        vm.onStartOver()

        assertEquals(ExplainPhase.READY, vm.state.value.phase)
        assertEquals("", vm.state.value.transcript)
        assertEquals(1, speech.cancelled)
        assertTrue(store.judged.isEmpty())
    }

    @Test
    fun `the hint names a word the explanation has not used, never the answer`() = runTest {
        val speech = FakeSpeech()
        val vm = newVm(speech = speech)
        vm.onStartTapped(micGranted = true)
        speech.emit(SpeechEvent.Partial("it is the maximum"))
        vm.onHint()
        val hint = vm.state.value.hint!!
        assertTrue(hint.startsWith("Try to mention "))
        assertFalse(hint.contains("maximum"))
        vm.onStartOver()
    }

    @Test
    fun `a save that fails still shows the result, with a plain message`() = runTest {
        val store = FakeStore(fail = true)
        val speech = FakeSpeech(finalOnStop = SpeechEvent.Final("Vmax is the maximum reaction rate"))
        val vm = newVm(speech = speech, assessor = FakeAssessor(next = judgedAllPartly), store = store)
        vm.onStartTapped(micGranted = true)
        vm.onStop()
        assertEquals(ExplainPhase.RESULT, vm.state.value.phase)
        assertEquals(ExplainAloudViewModel.SAVE_FAILED, vm.state.value.message)
    }

    @Test
    fun `leaving the round releases the microphone`() = runTest {
        val speech = FakeSpeech()
        val vm = newVm(speech = speech)
        vm.onStartTapped(micGranted = true)
        vm.onStartOver()
        assertNull(vm.state.value.nextPrompt)
        assertEquals(1, speech.cancelled)
    }

    // ----------------------------------------------------------------- fakes

    private class FakeSpeech(
        private val finalOnStop: SpeechEvent.Final? = null,
        private val failOnStart: SpeechFailure? = null
    ) : ExplainSpeech {
        var started = 0
        var stopped = 0
        var cancelled = 0
        private var sink: ((SpeechEvent) -> Unit)? = null

        override fun start(onEvent: (SpeechEvent) -> Unit) {
            started++
            sink = onEvent
            failOnStart?.let { onEvent(SpeechEvent.Failed(it)) }
        }

        override fun stop() {
            stopped++
            finalOnStop?.let { sink?.invoke(it) }
        }

        override fun cancel() {
            cancelled++
            sink = null
        }

        fun emit(event: SpeechEvent) {
            sink?.invoke(event)
        }
    }

    private class FakeAssessor(private val next: ExplainAssessment = ExplainAssessment.AiUnavailable) : TeachBackAssessor {
        var calls = 0
        var lastTranscript: String? = null
        override suspend fun assess(rubric: List<TeachBackCriterion>, transcript: String, concept: LearningConcept): ExplainAssessment {
            calls++
            lastTranscript = transcript
            return next
        }
    }

    private class FakeStore(private val fail: Boolean = false) : ExplainAttemptStore {
        data class Judged(val transcript: String, val covered: Boolean)
        data class SelfRated(val transcript: String, val rating: RecallButton)

        val judged = mutableListOf<Judged>()
        val selfRated = mutableListOf<SelfRated>()

        override suspend fun saveJudged(concept: LearningConcept, rubric: List<TeachBackCriterion>, transcript: String, covered: Boolean) {
            if (fail) error("disk full")
            judged += Judged(transcript, covered)
        }

        override suspend fun saveSelfRated(concept: LearningConcept, rubric: List<TeachBackCriterion>, transcript: String, rating: RecallButton) {
            if (fail) error("disk full")
            selfRated += SelfRated(transcript, rating)
        }
    }
}
