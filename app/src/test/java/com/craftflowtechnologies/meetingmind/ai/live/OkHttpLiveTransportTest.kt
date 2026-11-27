package com.craftflowtechnologies.meetingmind.ai.live

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantTool
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OkHttpLiveTransportTest {

    private fun transport(
        onAudio: (ByteArray) -> Unit = {},
        onInterrupt: () -> Unit = {}
    ): OkHttpLiveTransport {
        val context: Context = ApplicationProvider.getApplicationContext()
        return OkHttpLiveTransport(
            credentials = GeminiCredentialStore(context),
            toolsJson = AssistantTool.nativeDeclarationsJson(listOf(AssistantTool.READ_NOTE, AssistantTool.CREATE_TASK)),
            onOutputAudioPcm24k = onAudio,
            onInterrupted = onInterrupt
        )
    }

    @Test
    fun buildSetupJsonIncludesToolsAndTranscriptions() {
        val setup = transport().buildSetupJson("gemini-3.8-live", "Be brief.")
        val inner = setup.getJSONObject("setup")
        assertEquals("models/gemini-3.8-live", inner.getString("model"))
        assertTrue(inner.has("inputAudioTranscription"))
        assertTrue(inner.has("outputAudioTranscription"))
        assertTrue(inner.getJSONArray("tools").length() == 1)
        assertEquals("Be brief.", inner.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test
    fun parseServerMessageExtractsSetupAudioInterruptionAndToolCalls() {
        val receivedAudio = mutableListOf<ByteArray>()
        var interruptedCount = 0
        val t = transport(
            onAudio = { receivedAudio += it },
            onInterrupt = { interruptedCount++ }
        )

        val setupEvents = t.parseServerMessage("""{"setupComplete":{}}""")
        assertTrue(setupEvents.single() is LiveServerEvent.Connected)

        val toolEvents = t.parseServerMessage(
            """{"toolCall":{"functionCalls":[{"id":"c1","name":"create_task","args":{"title":"Call Sam"}}]}}"""
        )
        val req = toolEvents.single() as LiveServerEvent.ToolCallRequested
        assertEquals("c1", req.call.id)
        assertEquals("create_task", req.call.name)
        assertTrue(req.call.argumentsJson.contains("Call Sam"))

        val contentEvents = t.parseServerMessage(
            """{"serverContent":{"interrupted":true,"inputTranscription":{"text":"call sam"},"outputTranscription":{"text":"Added task"},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"AQID"}}]},"turnComplete":true}}"""
        )
        assertEquals(1, interruptedCount)
        assertEquals(1, receivedAudio.size)
        assertEquals(listOf<Byte>(1, 2, 3), receivedAudio.single().toList())
        assertTrue(contentEvents.any { it is LiveServerEvent.InputTranscription && it.text == "call sam" })
        assertTrue(contentEvents.any { it is LiveServerEvent.OutputAudio && it.text == "Added task" })
        assertTrue(contentEvents.any { it is LiveServerEvent.TurnComplete })
    }
}
