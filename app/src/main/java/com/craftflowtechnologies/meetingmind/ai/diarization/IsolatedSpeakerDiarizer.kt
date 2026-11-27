package com.craftflowtechnologies.meetingmind.ai.diarization

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.transcript.DiarizationTurn
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** How long the separate process may take to connect before the step gives up. */
private const val CONNECT_TIMEOUT_MS = 20_000L

/**
 * Runs speaker separation in its own process ([DiarizationService], `:diarizer`).
 *
 * The native speaker model has crashed on some recordings in a way the app cannot catch, and
 * that crash took the whole app down with it. Here the same [SherpaSpeakerDiarizer] runs in a
 * separate process. If it dies, this returns a failure, and the pipeline finishes the meeting
 * without speaker labels, as it already does for a timeout. The rest of the app keeps running.
 *
 * Progress and the result come back over a [Messenger]. Only the turns cross the boundary, as
 * plain values, so nothing native is shared.
 */
class IsolatedSpeakerDiarizer(
    private val context: Context,
    private val modelId: String = ModelCatalog.speakerDiarization.id
) : SpeakerDiarizer {

    override suspend fun diarize(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        knownSpeakers: List<Speaker>,
        expectedSpeakerCount: Int?
    ): AiResult<List<DiarizationTurn>> =
        diarizeWithProgress(audioFile, totalDurationMs, meetingId, knownSpeakers, expectedSpeakerCount) { }

    override suspend fun diarizeWithProgress(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        knownSpeakers: List<Speaker>,
        expectedSpeakerCount: Int?,
        onProgress: (Float) -> Unit
    ): AiResult<List<DiarizationTurn>> {
        // Known speakers are matched inside the diarizer by their voice data, which the service
        // does not receive. That case keeps the in-process path.
        if (knownSpeakers.isNotEmpty()) {
            return SherpaSpeakerDiarizer(LocalModelStorage(context), modelId)
                .diarizeWithProgress(audioFile, totalDurationMs, meetingId, knownSpeakers, expectedSpeakerCount, onProgress)
        }

        val outcome = CompletableDeferred<AiResult<List<DiarizationTurn>>>()
        val handler = Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                DiarizationProtocol.MSG_PROGRESS -> onProgress(message.arg1 / 100f)
                DiarizationProtocol.MSG_RESULT -> outcome.complete(AiResult.Success(turnsFrom(message.data)))
                DiarizationProtocol.MSG_FAILED -> {
                    val text = message.data.getString(DiarizationProtocol.KEY_MESSAGE) ?: "Speaker separation failed."
                    outcome.complete(
                        if (message.arg1 == DiarizationProtocol.FAILED_UNAVAILABLE) AiResult.ModelUnavailable(modelId, text)
                        else AiResult.Failed(text)
                    )
                }
            }
            true
        }
        val ourMessenger = Messenger(handler)
        var server: Messenger? = null
        val connected = CompletableDeferred<Unit>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                server = Messenger(binder)
                connected.complete(Unit)
                val request = Bundle().apply {
                    putString(DiarizationProtocol.KEY_AUDIO_PATH, audioFile.absolutePath)
                    putString(DiarizationProtocol.KEY_MEETING_ID, meetingId)
                    putLong(DiarizationProtocol.KEY_DURATION_MS, totalDurationMs)
                    putInt(DiarizationProtocol.KEY_EXPECTED_SPEAKERS, expectedSpeakerCount ?: -1)
                    putString(DiarizationProtocol.KEY_MODEL_ID, modelId)
                }
                val message = Message.obtain(null, DiarizationProtocol.MSG_START).apply {
                    data = request
                    replyTo = ourMessenger
                }
                runCatching { server?.send(message) }.onFailure {
                    outcome.complete(AiResult.Failed("Speaker separation could not start."))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // The separation process died: the native model crashed or the system stopped it.
                outcome.complete(AiResult.Failed("Speaker separation stopped unexpectedly."))
            }

            override fun onBindingDied(name: ComponentName) {
                outcome.complete(AiResult.Failed("Speaker separation stopped unexpectedly."))
            }
        }

        val bound = context.bindService(Intent(context, DiarizationService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) return AiResult.Failed("Speaker separation could not start.")
        try {
            // Without this, a process that never connects would hold the whole speaker budget
            // (up to 20 minutes) with the progress bar stuck. Fail fast into the no-labels path.
            if (withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } == null) {
                outcome.complete(AiResult.Failed("Speaker separation did not start on this phone."))
            }
            return outcome.await()
        } finally {
            // Unbinding while work is still running tells the service to stop, so a cancelled
            // meeting does not keep a model loaded in the background.
            runCatching { context.unbindService(connection) }
        }
    }

    private fun turnsFrom(data: Bundle): List<DiarizationTurn> {
        val speakers = data.getStringArrayList(DiarizationProtocol.KEY_SPEAKER_IDS).orEmpty()
        val starts = data.getLongArray(DiarizationProtocol.KEY_STARTS) ?: LongArray(0)
        val ends = data.getLongArray(DiarizationProtocol.KEY_ENDS) ?: LongArray(0)
        return speakers.indices.map { i ->
            DiarizationTurn(speakerId = speakers[i], startMs = starts[i], endMs = ends[i])
        }
    }
}

/** Message codes and bundle keys shared by [IsolatedSpeakerDiarizer] and [DiarizationService]. */
internal object DiarizationProtocol {
    const val MSG_START = 1
    const val MSG_PROGRESS = 2
    const val MSG_RESULT = 3
    const val MSG_FAILED = 4

    const val FAILED_GENERIC = 0
    const val FAILED_UNAVAILABLE = 1

    const val KEY_AUDIO_PATH = "audioPath"
    const val KEY_MEETING_ID = "meetingId"
    const val KEY_DURATION_MS = "durationMs"
    const val KEY_EXPECTED_SPEAKERS = "expectedSpeakers"
    const val KEY_MODEL_ID = "modelId"
    const val KEY_MESSAGE = "message"
    const val KEY_SPEAKER_IDS = "speakerIds"
    const val KEY_STARTS = "starts"
    const val KEY_ENDS = "ends"
}
