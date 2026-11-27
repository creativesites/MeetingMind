package com.craftflowtechnologies.meetingmind.feature.learn

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.learning.StudyModelSource
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.routing.ResolvedLanguageModel
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.learning.StudyKitStore
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.feature.learn.LessonSection
import com.craftflowtechnologies.meetingmind.feature.learn.LessonKitStatus
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainMode
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learn.LessonViewModel
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeScope
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeViewModel
import com.craftflowtechnologies.meetingmind.feature.learn.ListenViewModel
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeViewModel
import com.craftflowtechnologies.meetingmind.feature.learn.ListenVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyViewModelsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var db: MeetMindDatabase
    private lateinit var kits: StudyKitStore
    private lateinit var model: ScriptedModel
    private lateinit var services: StudyServices

    private val s1 = "Enzymes lower the activation energy of a reaction."
    private val s2 = "Competitive inhibitors bind to the active site and block substrate."
    private val s3 = "Vmax is the maximum rate when the enzyme is saturated."

    /** Answers each stage of the Study pipeline by its prompt. */
    private class ScriptedModel : LanguageModel {
        var unavailable = false
        var calls = 0
        var reading = ""
        var activities = ""
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            calls++
            if (unavailable) return AiResult.ModelUnavailable("local-llm", "No local language model is installed on this device.")
            return AiResult.Success(
                when {
                    prompt.contains("part of a lecture transcript") -> reading
                    prompt.contains("quick checks") -> activities
                    prompt.contains("Write a summary") -> "{\"summary\": \"Inhibitors block the active site.\"}"
                    prompt.contains("explained another way") -> "{\"text\": \"Inhibitors compete with substrate.\"}"
                    else -> "{}"
                }
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        kits = StudyKitStore(File(folder.root, "study/kits"))
        model = ScriptedModel()
        val resolved = ResolvedLanguageModel(
            languageModel = model,
            modelId = "fake-model",
            contextLengthTokens = 4096,
            // Cloud, so these tests never load the on-device engine.
            isCloud = true
        )
        services = StudyServices(
            context = app,
            database = db,
            kits = kits,
            models = StudyModelSource { resolved }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    // ------------------------------------------------------------- fixtures

    private val now: Long get() = System.currentTimeMillis()

    private fun segment(id: String, start: Long, end: Long, text: String) = TranscriptSegmentEntity(
        id = id, meetingId = MEET, speakerId = null, speakerName = null,
        startMs = start, endMs = end, text = text, confidence = null
    )

    private suspend fun seedLecture(
        courseName: String? = "BIO 201 Biochemistry",
        withAudioPath: String? = null
    ) {
        val t = now
        db.noteDao().upsert(
            NoteEntity(
                id = NOTE, title = "Enzyme kinetics", workflow = RecordingType.LECTURE.name, notebookId = null,
                createdAt = t, updatedAt = t, eventDate = t, pinned = false, isPrivate = false, status = "OPEN",
                answeredAt = null, metadataJson = "{}", archivedAt = null, plainText = ""
            )
        )
        db.meetingDao().insertMeeting(
            MeetingEntity(
                id = MEET, title = "Enzyme Lecture", createdAt = t, durationMs = 40_000L,
                source = "LOCAL_RECORDING", audioFilePath = withAudioPath, status = "READY",
                participantCount = 1, language = "en", summaryText = null, noteId = NOTE
            )
        )
        db.transcriptDao().insertSegments(
            listOf(
                segment("seg-1", 0, 10_000, s1),
                segment("seg-2", 10_000, 20_000, s2),
                segment("seg-3", 20_000, 30_000, s3)
            )
        )
        services.learning.getOrCreateSessionForNote(NOTE, courseName)
    }

    private suspend fun seedDueConcept(): String {
        val session = services.learning.getOrCreateSessionForNote(NOTE)
        val concept = LearningConcept(
            id = "concept-1", sessionId = session.id, name = "Competitive inhibition",
            definition = "An inhibitor that competes for the active site.",
            relationships = emptyList(),
            evidence = listOf(LearningEvidence(meetingId = MEET, segmentIds = listOf("seg-2"), startMs = 10_000, endMs = 20_000, quote = s2)),
            createdAt = now, updatedAt = now
        )
        services.learning.saveConcepts(listOf(concept))
        val activity = LearningActivity(
            id = "act-1", sessionId = session.id, conceptId = concept.id,
            type = LearningActivityType.MULTIPLE_CHOICE,
            prompt = "Which process do competitive inhibitors block?",
            expectedAnswer = "Substrate binding at the active site",
            options = listOf("Substrate binding at the active site", "Enzyme denaturation by heat", "Allosteric activation", "Product release"),
            evidence = concept.evidence,
            createdAt = now, updatedAt = now
        )
        services.learning.saveActivities(listOf(activity))
        return session.id
    }

    private fun readingJson() = """{
  "concepts": [{"name": "Competitive inhibition", "definition": "Binds the active site.", "emphasis": "", "relationships": [], "startMs": 10000, "endMs": 20000, "quote": "$s2"}],
  "sections": [{"title": "Inhibition", "startMs": 10000, "body": "$s2", "terms": [{"text": "active site", "conceptName": "Competitive inhibition"}], "quickCheck": {"prompt": "What do competitive inhibitors block?", "answer": "Substrate binding", "conceptName": "Competitive inhibition"}}],
  "stressed": []
}"""

    private fun activitiesJson() = """{"activities": [{"type": "MULTIPLE_CHOICE", "conceptName": "Competitive inhibition", "prompt": "Which process do competitive inhibitors block?", "options": ["Substrate binding at the active site", "Enzyme denaturation by heat", "Allosteric activation", "Product release"], "correctIndex": 0, "rightWhy": "Inhibitors occupy the active site.", "wrongWhys": {"1": "Heat is not an inhibitor here."}, "startMs": 10000, "endMs": 20000, "quote": "$s2"}]}"""

    /**
     * Answers the current item correctly, whatever its format. The planner may ask a concept as a
     * choice, a true or false, a recall card and so on, so the tests answer each one the right way.
     */
    private fun answerRight(vm: PracticeViewModel) {
        when (val item = vm.state.value.current) {
            is PracticeItem.Choice -> vm.answerChoice(item.correctIndex)
            is PracticeItem.Recall -> { vm.reveal(); vm.rate(com.craftflowtechnologies.meetingmind.feature.learn.RecallButton.GOT_IT) }
            is com.craftflowtechnologies.meetingmind.feature.learn.PracticeTrueFalse ->
                vm.answer(com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer.Pick(item.correctIndex))
            is com.craftflowtechnologies.meetingmind.feature.learn.PracticeWhichIdea ->
                vm.answer(com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer.Pick(item.correctIndex))
            is com.craftflowtechnologies.meetingmind.feature.learn.PracticeFillGap ->
                vm.answer(com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer.Fill(item.answer))
            is com.craftflowtechnologies.meetingmind.feature.learn.PracticeMatch ->
                vm.answer(com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer.Match(item.terms.indices.associateWith { it }))
            is com.craftflowtechnologies.meetingmind.feature.learn.PracticeOrder ->
                vm.answer(com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer.Order(item.steps.indices.toList()))
            null -> Unit
            else -> { vm.reveal(); vm.rate(com.craftflowtechnologies.meetingmind.feature.learn.RecallButton.GOT_IT) }
        }
    }

    /** Answers the current item wrongly: a wrong option for a choice, otherwise "Not sure" (a miss). */
    private fun answerWrong(vm: PracticeViewModel) {
        val item = vm.state.value.current
        if (item is PracticeItem.Choice) vm.answerChoice((item.correctIndex + 1) % 4) else vm.notSure()
    }

    /** Polls [condition] for up to five seconds, letting background work finish. */
    private fun awaitTrue(condition: () -> Boolean) = runBlocking {
        repeat(250) {
            if (condition()) return@runBlocking
            delay(20)
        }
        assertTrue("condition not reached in time", condition())
    }

    // ------------------------------------------------------------- home

    @Test
    fun `home summarises the due round, the sky, the course and the week`() {
        runBlocking {
            seedLecture()
            seedDueConcept()
        }
        val vm = StudyHomeViewModel(app, services)
        awaitTrue { !vm.state.value.loading }

        val home: StudyHomeState = vm.state.value
        assertNotNull(home.round)
        assertEquals(1, home.round!!.dueCount)
        assertEquals("1 card is due. A quick round?", home.companionLine)
        assertEquals(1, home.sky.totalIdeas)
        assertEquals(1, home.courses.size)
        assertEquals("BIO 201", home.courses.single().code)
        assertEquals(7, home.studiedDays.size)
        assertTrue(home.hasAnyLesson)
    }

    @Test
    fun `home says all caught up when nothing is due`() {
        runBlocking { seedLecture() }
        val vm = StudyHomeViewModel(app, services)
        awaitTrue { !vm.state.value.loading }
        assertNull(vm.state.value.round)
        assertEquals("All caught up. Nice work.", vm.state.value.companionLine)
    }

    // ------------------------------------------------------------- lesson

    @Test
    fun `lesson generates the kit on first open, then works offline from the stored kit`() {
        runBlocking {
            seedLecture()
            model.reading = readingJson()
            model.activities = activitiesJson()
        }
        val lesson = LessonViewModel(app, NOTE, services)
        awaitTrue { lesson.state.value.status == LessonKitStatus.Ready && lesson.state.value.kit != null }

        val state = lesson.state.value
        assertEquals("Inhibitors block the active site.", state.kit!!.summary)
        assertEquals(1, state.concepts.size)
        assertEquals(1, state.practiceCount)
        assertEquals("Competitive inhibition", state.concepts.single().name)
        assertTrue(state.waveform.isNotEmpty())
        assertTrue(kits.load(state.sessionId) != null)

        // Offline: the model is gone, and a new page still opens from the stored kit alone.
        model.unavailable = true
        val calls = model.calls
        val again = LessonViewModel(app, NOTE, services)
        awaitTrue { again.state.value.kit != null }
        assertEquals(LessonKitStatus.Ready, again.state.value.status)
        assertEquals(calls, model.calls)
    }

    @Test
    fun `lesson shows needs AI honestly when no model is available`() {
        runBlocking { seedLecture() }
        model.unavailable = true
        val lesson = LessonViewModel(app, NOTE, services)
        awaitTrue { lesson.state.value.status is LessonKitStatus.NeedsAi }
        assertNull(lesson.state.value.kit)
    }

    @Test
    fun `lesson explains a concept from its evidence`() {
        runBlocking {
            seedLecture()
            model.reading = readingJson()
            model.activities = activitiesJson()
        }
        val lesson = LessonViewModel(app, NOTE, services)
        awaitTrue { lesson.state.value.concepts.isNotEmpty() }
        lesson.explain(lesson.state.value.concepts.single().id, ExplainMode.SIMPLER)
        awaitTrue { lesson.explain.value.text != null || lesson.explain.value.error != null }
        assertEquals("Inhibitors compete with substrate.", lesson.explain.value.text)
        assertEquals("0:10", lesson.explain.value.sourceLabel)
    }

    @Test
    fun `lesson without a transcript says so`() {
        runBlocking {
            val t = now
            db.noteDao().upsert(
                NoteEntity(
                    id = NOTE, title = "Typed notes", workflow = RecordingType.LECTURE.name, notebookId = null,
                    createdAt = t, updatedAt = t, eventDate = t, pinned = false, isPrivate = false, status = "OPEN",
                    answeredAt = null, metadataJson = "{}", archivedAt = null, plainText = ""
                )
            )
        }
        val lesson = LessonViewModel(app, NOTE, services)
        awaitTrue { lesson.state.value.status == LessonKitStatus.NoTranscript }
    }

    // ------------------------------------------------------------- practice

    @Test
    fun `practice records the answer and finishes the round with a summary`() {
        val sessionId = runBlocking {
            seedLecture()
            seedDueConcept()
        }
        val vm = PracticeViewModel(app, PracticeScope.AllDue, services)
        awaitTrue { vm.state.value.current != null }

        answerRight(vm)
        assertTrue(vm.state.value.feedback!!.correct)
        awaitTrue { runBlocking { services.learning.getAttemptsForSession(sessionId).size == 1 } }

        vm.continueNext()
        awaitTrue { vm.state.value.result != null }
        val result = vm.state.value.result!!
        assertEquals(1, result.total)
        assertEquals(1, result.firstTryCorrect)
        assertTrue(result.allCleared)
    }

    @Test
    fun `a miss is asked again three places later, and the attempt is kept`() {
        val sessionId = runBlocking {
            seedLecture()
            seedDueConcept()
        }
        val vm = PracticeViewModel(app, PracticeScope.OneSession(sessionId), services)
        awaitTrue { vm.state.value.current != null }

        answerWrong(vm)
        assertEquals(false, vm.state.value.feedback!!.correct)
        assertEquals("This one comes back in 3 questions", vm.state.value.feedback!!.nextLine)
        awaitTrue { runBlocking { services.learning.getAttemptsForSession(sessionId).size == 1 } }
        assertEquals(2, vm.state.value.total)
    }

    @Test
    fun `quiz me on this uses one concept, due or not`() {
        val sessionId = runBlocking {
            seedLecture()
            seedDueConcept()
        }
        val vm = PracticeViewModel(app, PracticeScope.OneConcept(sessionId, "concept-1"), services)
        awaitTrue { vm.state.value.current != null || vm.state.value.emptyMessage != null }
        assertEquals(1, vm.state.value.total)
    }

    @Test
    fun `practice with nothing due says all caught up`() {
        runBlocking { seedLecture() }
        val vm = PracticeViewModel(app, PracticeScope.AllDue, services)
        awaitTrue { !vm.state.value.loading }
        assertEquals("All caught up. Nice work.", vm.state.value.emptyMessage)
    }

    // ------------------------------------------------------------- listen

    @Test
    fun `listen shows read-along lines and the section you are in, with no audio`() {
        runBlocking {
            seedLecture()
            kits.save(
                StudyKit(
                    sessionId = services.learning.getOrCreateSessionForNote(NOTE).id,
                    summary = "Summary.",
                    stressed = emptyList(),
                    sections = listOf(
                        LessonSection(0, "Enzymes", "Body", startMs = 0L),
                        LessonSection(1, "Inhibition", "Body", startMs = 10_000L)
                    ),
                    modelId = "fake-model",
                    createdAt = now
                )
            )
        }
        val listen = ListenViewModel(app, NOTE, services)
        awaitTrue { listen.state.value.lines.isNotEmpty() }

        assertEquals(3, listen.state.value.lines.size)
        assertEquals(listOf(ListenVoice.LECTURER), listen.voices)
        assertEquals(false, listen.state.value.hasLecturerAudio)

        listen.seekTo(15_000)
        assertEquals("Inhibition", listen.state.value.sectionLabel)
        assertEquals(1, listen.state.value.currentLine)
        listen.seekBy(-15_000)
        assertEquals("Enzymes", listen.state.value.sectionLabel)
    }

    // ------------------------------------------------------------- practice engine

    /** Three more activities under the due concept, of other types, so a round has variety. */
    private suspend fun seedVariedRound(sessionId: String) {
        val evidence = listOf(LearningEvidence(meetingId = MEET, segmentIds = listOf("seg-2"), startMs = 10_000, endMs = 20_000, quote = s2))
        services.learning.saveActivities(
            listOf(
                LearningActivity(
                    id = "act-2", sessionId = sessionId, conceptId = "concept-1", type = LearningActivityType.RECALL,
                    prompt = "What does a competitive inhibitor do to the active site?",
                    expectedAnswer = "It binds it and blocks substrate.", evidence = evidence, createdAt = now, updatedAt = now
                ),
                LearningActivity(
                    id = "act-3", sessionId = sessionId, conceptId = "concept-1", type = LearningActivityType.APPLICATION,
                    prompt = "A lab adds an inhibitor. Which value changes?", expectedAnswer = "Apparent Km",
                    options = listOf("Apparent Km", "Vmax", "Enzyme mass", "pH"), evidence = evidence, createdAt = now, updatedAt = now
                ),
                LearningActivity(
                    id = "act-4", sessionId = sessionId, conceptId = "concept-1", type = LearningActivityType.RECALL,
                    prompt = "Define Vmax.", expectedAnswer = "The maximum rate when the enzyme is saturated.",
                    evidence = evidence, createdAt = now, updatedAt = now
                )
            )
        )
    }

    @Test
    fun `a round never asks the same format twice in a row, and draws only its own items`() {
        runBlocking {
            seedLecture()
            seedVariedRound(seedDueConcept())
        }
        val sessionId = runBlocking { services.learning.getOrCreateSessionForNote(NOTE).id }
        val vm = PracticeViewModel(app, PracticeScope.OneSession(sessionId), services)
        awaitTrue { !vm.state.value.loading }

        val formats = vm.roundFormats()
        assertEquals(vm.roundItems().size, formats.size)
        assertTrue(formats.size >= 3)
        assertTrue("same format twice in a row: $formats", formats.zipWithNext().none { (a, b) -> a == b })
        assertTrue(vm.roundItems().all { it.activityId in setOf("act-1", "act-2", "act-3", "act-4") })
    }

    @Test
    fun `an answer is saved with the format it was asked in, so Learning sees it`() {
        runBlocking {
            seedLecture()
            seedVariedRound(seedDueConcept())
        }
        val sessionId = runBlocking { services.learning.getOrCreateSessionForNote(NOTE).id }
        val vm = PracticeViewModel(app, PracticeScope.OneSession(sessionId), services)
        awaitTrue { vm.state.value.current != null }
        val asked = vm.roundFormats().first()

        vm.notSure()
        awaitTrue { runBlocking { services.learning.getAttemptsForSession(sessionId).size == 1 } }
        val saved = runBlocking { services.learning.getAttemptsForSession(sessionId).single() }
        assertEquals(asked, com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeFormat.fromTag(saved.feedback))
    }

    @Test
    fun `a course round draws on that course only`() {
        runBlocking { seedLecture(courseName = "BIO 201 Biochemistry"); seedDueConcept() }
        val inCourse = PracticeViewModel(app, PracticeScope.OneCourse("BIO 201 Biochemistry"), services)
        awaitTrue { !inCourse.state.value.loading }
        assertTrue(inCourse.state.value.total > 0)

        val elsewhere = PracticeViewModel(app, PracticeScope.OneCourse("Chemistry 110"), services)
        awaitTrue { !elsewhere.state.value.loading }
        assertEquals("There is nothing to practise here yet.", elsewhere.state.value.emptyMessage)
    }
}

private const val NOTE = "note-1"
private const val MEET = "meet-1"
