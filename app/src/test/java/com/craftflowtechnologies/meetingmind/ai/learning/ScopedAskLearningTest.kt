package com.craftflowtechnologies.meetingmind.ai.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NoteBlockEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScopedAskLearningTest {

    private class FakeLanguageModel(var response: String) : LanguageModel {
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            return AiResult.Success(response)
        }
    }

    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var fakeLlm: FakeLanguageModel
    private lateinit var scopedAsk: ScopedAskLearning
    private lateinit var repository: LearningRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeLlm = FakeLanguageModel("")
        scopedAsk = ScopedAskLearning(db, fakeLlm)
        repository = LearningRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `ask returns grounded answer with cited evidence`() = runBlocking {
        val session = repository.createTypedSession("Enzyme Lecture")
        val block = NoteBlockEntity(
            id = "b1",
            noteId = session.noteId,
            position = 0,
            type = "PARAGRAPH",
            text = "Enzymes function by lowering the activation energy barrier.",
            spans = "",
            payloadJson = "{}",
            source = "USER",
            sourceSegmentIdsJson = "[]",
            sectionKey = "notes",
            isUserEdited = false,
            indent = 0,
            checked = false,
            updatedAt = 1000L
        )
        db.noteDao().upsertBlocks(listOf(block))

        fakeLlm.response = """
        {
          "answer": "Enzymes work by lowering activation energy barriers.",
          "found": true,
          "suggestedConcept": "Activation Energy",
          "sources": ["p1"]
        }
        """

        val result = scopedAsk.ask(session.id, "How do enzymes work?")
        assertTrue(result is AiResult.Success)
        val answer = (result as AiResult.Success).value
        assertTrue(answer.foundInSource)
        assertTrue(answer.answer.contains("lowering activation energy"))
        assertEquals("Activation Energy", answer.suggestedConcept)
        assertEquals(1, answer.citedEvidence.size)
    }

    @Test
    fun `createQuizMeActivity generates practice activity with distractors`() = runBlocking {
        val session = repository.createTypedSession("Enzyme Lecture")
        fakeLlm.response = """
        {
          "prompt": "What barrier do enzymes lower to accelerate reactions?",
          "expectedAnswer": "Activation energy",
          "options": ["Activation energy", "Thermal energy", "Potential energy", "Bond energy"]
        }
        """

        val result = scopedAsk.createQuizMeActivity(
            sessionId = session.id,
            conceptName = "Activation Energy",
            answerText = "Enzymes lower the activation energy barrier.",
            evidence = listOf(LearningEvidence(noteId = session.noteId, quote = "lower activation energy"))
        )

        assertTrue(result is AiResult.Success)
        val activity = (result as AiResult.Success).value
        assertEquals(session.id, activity.sessionId)
        assertEquals("Activation energy", activity.expectedAnswer)
        assertEquals(4, activity.options.size)
        assertTrue(activity.options.contains("Activation energy"))
    }

    @Test
    fun `createQuizMeActivity rejects questions that leak the answer in prompt`() = runBlocking {
        val session = repository.createTypedSession("Enzyme Lecture")
        fakeLlm.response = """
        {
          "prompt": "How does activation energy affect reaction speed?",
          "expectedAnswer": "Activation energy",
          "options": ["Activation energy", "Thermal energy", "Kinetic energy", "Potential energy"]
        }
        """

        val result = scopedAsk.createQuizMeActivity(
            sessionId = session.id,
            conceptName = "Activation Energy",
            answerText = "Activation energy is the barrier.",
            evidence = listOf(LearningEvidence(noteId = session.noteId, quote = "lower activation energy"))
        )

        assertTrue("Leaked answer in prompt must be rejected", result is AiResult.Failed)
    }

    @Test
    fun `createQuizMeActivity rejects quiz with invalid option count or duplicate options`() = runBlocking {
        val session = repository.createTypedSession("Enzyme Lecture")
        fakeLlm.response = """
        {
          "prompt": "What barrier do enzymes lower?",
          "expectedAnswer": "Activation energy",
          "options": ["Activation energy", "Thermal energy", "Activation energy", "Bond energy"]
        }
        """

        val result = scopedAsk.createQuizMeActivity(
            sessionId = session.id,
            conceptName = "Activation Energy",
            answerText = "Enzymes lower the activation energy barrier.",
            evidence = listOf(LearningEvidence(noteId = session.noteId, quote = "lower activation energy"))
        )

        assertTrue("Duplicate options must be rejected", result is AiResult.Failed)
    }
}
