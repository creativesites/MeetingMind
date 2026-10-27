package com.craftflowtechnologies.meetingmind.ai.diarization

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors

/**
 * Speaker separation, run in the `:diarizer` process so a crash in the native speaker model ends
 * only that process. One request at a time; the client gets progress and then one result.
 *
 * If the client unbinds before the work is done (a cancelled meeting), this process is stopped,
 * which frees the model at once. The process is used for nothing else.
 */
class DiarizationService : Service() {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "diarization-service").apply { isDaemon = true } }
    @Volatile private var finished = false

    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == DiarizationProtocol.MSG_START) {
            val reply = message.replyTo
            val request = message.data
            worker.execute { separate(reply, request) }
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        if (!finished) {
            // The client left before the result came back: stop this process so the model is freed.
            Breadcrumbs.log("speakers", "service: client left before the result, stopping process")
            Process.killProcess(Process.myPid())
        }
        return false
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun separate(reply: Messenger?, request: Bundle) {
        val audioPath = request.getString(DiarizationProtocol.KEY_AUDIO_PATH)
        val meetingId = request.getString(DiarizationProtocol.KEY_MEETING_ID).orEmpty()
        val durationMs = request.getLong(DiarizationProtocol.KEY_DURATION_MS)
        val expected = request.getInt(DiarizationProtocol.KEY_EXPECTED_SPEAKERS, -1).takeIf { it > 0 }
        val modelId = request.getString(DiarizationProtocol.KEY_MODEL_ID) ?: return
        if (audioPath == null || reply == null) return

        Breadcrumbs.log("speakers", "service: separating $meetingId")
        val result = runCatching {
            val diarizer = SherpaSpeakerDiarizer(LocalModelStorage(applicationContext), modelId)
            runBlocking {
                diarizer.diarizeWithProgress(File(audioPath), durationMs, meetingId, emptyList(), expected) { fraction ->
                    send(reply, DiarizationProtocol.MSG_PROGRESS, (fraction * 100).toInt().coerceIn(0, 100))
                }
            }
        }.getOrElse { AiResult.Failed(it.message ?: "Speaker separation failed.", it) }

        when (result) {
            is AiResult.Success -> {
                val data = Bundle().apply {
                    putStringArrayList(DiarizationProtocol.KEY_SPEAKER_IDS, ArrayList(result.value.map { it.speakerId }))
                    putLongArray(DiarizationProtocol.KEY_STARTS, result.value.map { it.startMs }.toLongArray())
                    putLongArray(DiarizationProtocol.KEY_ENDS, result.value.map { it.endMs }.toLongArray())
                }
                finished = true
                send(reply, DiarizationProtocol.MSG_RESULT, 0, data)
            }
            is AiResult.ModelUnavailable -> {
                finished = true
                send(reply, DiarizationProtocol.MSG_FAILED, DiarizationProtocol.FAILED_UNAVAILABLE, failure(result.message))
            }
            else -> {
                val text = (result as? AiResult.Failed)?.message ?: "Speaker separation failed."
                finished = true
                send(reply, DiarizationProtocol.MSG_FAILED, DiarizationProtocol.FAILED_GENERIC, failure(text))
            }
        }
        Breadcrumbs.log("speakers", "service: finished, ${result::class.simpleName}")
        stopSelf()
    }

    private fun failure(text: String): Bundle = Bundle().apply { putString(DiarizationProtocol.KEY_MESSAGE, text) }

    private fun send(reply: Messenger, what: Int, arg1: Int, data: Bundle? = null) {
        val message = Message.obtain(null, what, arg1, 0)
        if (data != null) message.data = data
        runCatching { reply.send(message) }
    }
}
