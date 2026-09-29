package com.craftflowtechnologies.meetingmind.ai.assistant

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AskEverythingTest {
    private val sources = listOf(
        AskSource(1, SourceKind.NOTE, "Sunday notes", "Grace comes before effort.", noteId = "n1", faith = true),
        AskSource(2, SourceKind.TASK, "Call Mary", "Open", taskId = "t1")
    )

    @Test fun keepsRealNumbersAndDropsInventedOnes() {
        val a = AskEverything.parse("Grace comes first [1]. You planned to call Mary [2]. Also fasting [7].", sources)
        assertEquals(listOf(1, 2), a.cited.map { it.key })
        assertEquals(listOf(7), a.dropped)
        assertFalse(a.text.contains("[7]"))
        assertFalse(a.unverified)
    }

    @Test fun noCitationIsUnverifiedUnlessItSaysNotFound() {
        assertTrue(AskEverything.parse("You are doing great.", sources).unverified)
        assertFalse(AskEverything.parse("I couldn't find that in your notes or recordings.", sources).unverified)
    }

    @Test fun termsDropQuestionWords() {
        assertEquals(listOf("sermon", "grace"), AskEverything.terms("What did the sermon say about grace?"))
    }

    @Test fun parsesQueryExpansion() {
        assertEquals(listOf("grace", "Romans 5"), AskEverything.parseQueries("```json\n{\"queries\":[\"grace\",\"Romans 5\",\"grace\"]}\n```"))
        assertTrue(AskEverything.parseQueries("not json").isEmpty())
    }

    private class Script(val replies: List<String>) : GeminiTransport {
        val requests = mutableListOf<GeminiRequest>()
        override suspend fun execute(request: GeminiRequest): AiResult<String> { requests += request; return AiResult.Success(replies[requests.size - 1]) }
        override fun isConfigured() = true
    }

    @Test fun searchesWithExpandedQueriesAndAnswersFromNumberedSources() = runBlocking {
        val t = Script(listOf("{\"queries\":[\"grace\"]}", "Grace comes before effort [1]."))
        val searched = mutableListOf<String>()
        val engine = AskEverythingEngine(t, search = { q -> searched += q; if (q == "grace") sources else emptyList() }, dateLabel = { "today" })
        val r = engine.ask("What did I note about grace?") as AiResult.Success
        assertTrue("grace" in searched)
        assertEquals(listOf("n1"), r.value.cited.map { it.noteId })
        // Faith material brings the theology contract with it.
        assertTrue(t.requests[1].systemInstruction.contains("Never claim to speak for God"))
        assertTrue(t.requests[1].prompt.contains("[1] Note · Sunday notes"))
    }

    @Test fun nothingFoundNeedsNoAnswerCall() = runBlocking {
        val t = Script(listOf("{\"queries\":[]}"))
        val r = AskEverythingEngine(t, search = { emptyList() }, dateLabel = { "" }).ask("Where is my passport?") as AiResult.Success
        assertEquals(1, t.requests.size)
        assertTrue(r.value.text.startsWith("I couldn't find"))
    }
}
