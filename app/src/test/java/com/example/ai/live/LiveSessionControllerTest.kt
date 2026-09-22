package com.example.ai.live

import com.example.ai.common.AiResult
import com.example.core.model.ProcessingProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The property these tests exist for: **`turnComplete` is not idleness.**
 *
 * A client that ends the interaction when the model stops speaking truncates an extended-thinking
 * model mid-thought, and does so in a way that is easy to miss in testing because short answers
 * look fine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionControllerTest {

    private class RecordingTransport(
        private val events: Flow<LiveServerEvent> = emptyFlow(),
        private val configured: Boolean = true
    ) : LiveTransport {
        val sent = mutableListOf<LiveClientCommand>()
        override suspend fun connect(modelId: String, systemInstruction: String): AiResult<Flow<LiveServerEvent>> =
            if (configured) AiResult.Success(events) else AiResult.ModelUnavailable(modelId, "not configured")
        override suspend fun send(command: LiveClientCommand) { sent += command }
        override suspend fun close() = Unit
        override fun isConfigured() = configured
    }

    private fun controller(
        transport: LiveTransport = RecordingTransport(),
        scope: TestScope,
        executor: LiveToolExecutor? = null,
        profile: ProcessingProfile = ProcessingProfile.LIVE
    ) = LiveSessionController(transport, scope, executor, profile)

    @Test
    fun `turnComplete during an active interaction is background reasoning, not idle`() = runTest {
        val c = controller(scope = this)
        c.onEvent(LiveServerEvent.Connected)
        c.onEvent(LiveServerEvent.InteractionStatusChanged(InteractionStatus.IN_PROGRESS))
        c.onEvent(LiveServerEvent.OutputAudio("partial answer"))

        c.onEvent(LiveServerEvent.TurnComplete)

        assertEquals(LiveSessionState.BACKGROUND_REASONING, c.state.value)
    }

    @Test
    fun `only an explicit IDLE from the server ends the interaction`() = runTest {
        val c = controller(scope = this)
        c.onEvent(LiveServerEvent.Connected)
        c.onEvent(LiveServerEvent.InteractionStatusChanged(InteractionStatus.IN_PROGRESS))
        c.onEvent(LiveServerEvent.TurnComplete)
        assertEquals(LiveSessionState.BACKGROUND_REASONING, c.state.value)

        c.onEvent(LiveServerEvent.InteractionStatusChanged(InteractionStatus.IDLE))

        assertEquals(LiveSessionState.IDLE, c.state.value)
    }

    @Test
    fun `output arriving after turnComplete is accepted, not dropped`() = runTest {
        val c = controller(scope = this)
        c.onEvent(LiveServerEvent.Connected)
        c.onEvent(LiveServerEvent.InteractionStatusChanged(InteractionStatus.IN_PROGRESS))
        c.onEvent(LiveServerEvent.TurnComplete)

        c.onEvent(LiveServerEvent.OutputAudio("the rest of the answer"))

        assertEquals(LiveSessionState.MODEL_SPEAKING, c.state.value)
    }

    @Test
    fun `a tool call does not block the session from consuming further events`() = runTest(UnconfinedTestDispatcher()) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val transport = RecordingTransport()
        val c = controller(transport, this, { call ->
            gate.await()
            LiveToolResult(call.id, """{"ok":true}""")
        })
        c.onEvent(LiveServerEvent.Connected)
        c.onEvent(LiveServerEvent.ToolCallRequested(LiveToolCall("t1", "lookup", "{}")))

        // The tool is still running; the session must still be able to process what arrives next.
        c.onEvent(LiveServerEvent.InputTranscription("and another thing", isFinal = false))
        assertEquals(1, c.inFlightToolCount())
        assertTrue(c.inputTranscript.value.contains("and another thing"))

        gate.complete(Unit)
        assertEquals(0, c.inFlightToolCount())
        assertTrue(transport.sent.any { it is LiveClientCommand.SendToolResult })
    }

    @Test
    fun `several tool calls can be in flight at once`() = runTest(UnconfinedTestDispatcher()) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val c = controller(RecordingTransport(), this, { call ->
            gate.await()
            LiveToolResult(call.id, "{}")
        })
        c.onEvent(LiveServerEvent.ToolCallRequested(LiveToolCall("t1", "a", "{}")))
        c.onEvent(LiveServerEvent.ToolCallRequested(LiveToolCall("t2", "b", "{}")))

        assertEquals(2, c.inFlightToolCount())

        gate.complete(Unit)
        assertEquals(0, c.inFlightToolCount())
    }

    @Test
    fun `an IDLE arriving while a tool runs does not end the turn - the result is still owed`() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val c = controller(RecordingTransport(), this, { call ->
                gate.await()
                LiveToolResult(call.id, "{}")
            })
            c.onEvent(LiveServerEvent.ToolCallRequested(LiveToolCall("t1", "a", "{}")))

            c.onEvent(LiveServerEvent.InteractionStatusChanged(InteractionStatus.IDLE))
            assertEquals(LiveSessionState.TOOL_EXECUTION, c.state.value)

            gate.complete(Unit)
            assertEquals(LiveSessionState.IDLE, c.state.value)
        }

    @Test
    fun `a failing tool reports an error result instead of leaving the model waiting forever`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = RecordingTransport()
            val c = controller(transport, this, { error("tool exploded") })

            c.onEvent(LiveServerEvent.ToolCallRequested(LiveToolCall("t1", "a", "{}")))

            val result = transport.sent.filterIsInstance<LiveClientCommand.SendToolResult>().single()
            assertTrue(result.result.isError)
            assertEquals("t1", result.result.callId)
            assertEquals(0, c.inFlightToolCount())
        }

    @Test
    fun `a tool call with no executor configured is answered, not silently dropped`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = RecordingTransport()
            val c = controller(transport, this, executor = null)

            c.onEvent(LiveServerEvent.ToolCallRequested(LiveToolCall("t1", "a", "{}")))

            assertTrue(transport.sent.filterIsInstance<LiveClientCommand.SendToolResult>().single().result.isError)
        }

    @Test
    fun `an unconfigured transport reports unavailable rather than pretending to connect`() = runTest {
        val c = controller(RecordingTransport(configured = false), this)

        val result = c.connect()

        assertTrue(result is AiResult.ModelUnavailable)
        assertEquals(LiveSessionState.DISCONNECTED, c.state.value)
    }

    @Test
    fun `a disconnect clears in-flight state rather than leaving the machine mid-turn`() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val c = controller(RecordingTransport(), this, { call -> gate.await(); LiveToolResult(call.id, "{}") })
            c.onEvent(LiveServerEvent.ToolCallRequested(LiveToolCall("t1", "a", "{}")))

            c.onEvent(LiveServerEvent.Disconnected)

            assertEquals(LiveSessionState.DISCONNECTED, c.state.value)
            assertEquals(0, c.inFlightToolCount())
            gate.complete(Unit)
        }

    @Test
    fun `an error event surfaces the message and moves to ERROR`() = runTest {
        val c = controller(scope = this)

        c.onEvent(LiveServerEvent.Error("session closed by server"))

        assertEquals(LiveSessionState.ERROR, c.state.value)
        assertEquals("session closed by server", c.lastError.value)
    }
}
