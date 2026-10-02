package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.notes.SourcePassage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TeachBackFeedbackEngineTest {
    private val passage = SourcePassage("p1", "Enzymes lower activation energy.", "Lecture")
    private val rubric = listOf(TeachBackRubricClaim("claim-1", "Explains that enzymes lower activation energy.", listOf("p1")))

    @Test fun `accepts fully cited typed feedback without a numeric grade`() = runBlocking {
        val result = TeachBackFeedbackEngine(FakeModel("""{"claims":[{"claimId":"claim-1","coverage":"COVERED","explanation":"You named the energy barrier.","sources":["p1"]}],"nextPrompt":"Explain why the enzyme is not consumed."}""")).assess("It lowers the energy barrier.", rubric, listOf(passage))
        assertTrue(result is AiResult.Success && result.value.overall == TeachBackCoverage.COVERED && result.value.modelId == "configured-learning-model")
    }

    @Test fun `rejects fabricated citations`() = runBlocking {
        val result = TeachBackFeedbackEngine(FakeModel("""{"claims":[{"claimId":"claim-1","coverage":"COVERED","explanation":"Unsupported.","sources":["p999"]}],"nextPrompt":"Try again."}""")).assess("words", rubric, listOf(passage))
        assertTrue(result is AiResult.Failed)
    }

    @Test fun `reports unavailable model honestly`() = runBlocking {
        val result = TeachBackFeedbackEngine(object : LanguageModel { override suspend fun generate(prompt: String, maxOutputTokens: Int) = AiResult.ModelUnavailable("local", "Install a model") }).assess("words", rubric, listOf(passage))
        assertTrue(result is AiResult.Failed)
    }

    private class FakeModel(private val response: String) : LanguageModel {
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> = AiResult.Success(response)
    }
}
