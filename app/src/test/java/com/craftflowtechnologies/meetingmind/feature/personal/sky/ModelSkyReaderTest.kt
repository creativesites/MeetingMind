package com.craftflowtechnologies.meetingmind.feature.personal.sky

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zuri's read runs only on the on-device model. The reader is given a local-only source, so the
 * only model it can ever resolve is the on-device one, and no cloud path exists to call.
 */
class ModelSkyReaderTest {

    /** A fake on-device model that records what it was asked and answers with [reply]. */
    private class FakeDeviceModel(private val reply: String?) : LanguageModel {
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            prompts += prompt
            return if (reply != null) AiResult.Success(reply) else AiResult.ModelUnavailable("none", "no model in test")
        }
    }

    /** The only source the reader gets. It counts resolutions, so a test can see the local path was used. */
    private class CountingLocal(private val model: LanguageModel?) : LocalSkyModel {
        var resolves = 0
        override suspend fun resolve(): LanguageModel? {
            resolves++
            return model
        }
    }

    @Test fun `the read is written by the on-device model only`() = runTest {
        val device = FakeDeviceModel("You keep coming back to the inbox.")
        val local = CountingLocal(device)
        val reader = ModelSkyReader(local)

        val outcome = reader.read("Inbox", listOf("Inbox was loud after the call."))

        assertEquals(SkyReadOutcome.Written("You keep coming back to the inbox."), outcome)
        assertEquals(1, local.resolves) // read asks only the on-device source, once
        assertEquals(1, device.prompts.size)
        assertTrue(device.prompts.single().contains("Inbox was loud after the call."))
    }

    @Test fun `no on-device model means no read and an offer of Setup`() = runTest {
        val local = CountingLocal(null)
        val reader = ModelSkyReader(local)

        assertEquals(SkyRoute.NONE, reader.route())
        assertEquals(SkyReadOutcome.NoModel, reader.read("Inbox", listOf("text")))
    }

    @Test fun `the route is on this phone when the on-device model is installed`() = runTest {
        assertEquals(SkyRoute.ON_DEVICE, ModelSkyReader(CountingLocal(FakeDeviceModel("x"))).route())
    }

    @Test fun `a model that answers nothing usable gives an honest failure, not a made-up read`() = runTest {
        val reader = ModelSkyReader(CountingLocal(FakeDeviceModel(null)))
        assertEquals(SkyReadOutcome.Failed(ModelSkyReader.FAILED), reader.read("Inbox", listOf("text")))
    }
}
