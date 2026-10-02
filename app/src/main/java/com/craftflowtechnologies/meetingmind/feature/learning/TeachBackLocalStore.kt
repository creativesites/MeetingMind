package com.craftflowtechnologies.meetingmind.feature.learning

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.audio.AudioRecorder
import com.craftflowtechnologies.meetingmind.core.audio.RecorderState
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local-only metadata for a spoken explanation. There is no cloud transport in this path. */
data class TeachBackExplanation(
    val id: String,
    val sessionId: String,
    val conceptId: String,
    val audioPath: String,
    val durationMs: Long,
    val transcript: String? = null,
    val createdAt: Long
)

/**
 * Small adapter around the established [AudioRecorder]. It deliberately has no background
 * service: capture exists only while its owning screen is open and the learner is in control.
 */
class TeachBackRecorder(context: Context) {
    private val recorder = AudioRecorder(context.applicationContext)
    val state: StateFlow<RecorderState> = recorder.state
    val durationMs: StateFlow<Long> = recorder.durationMs

    fun start(explanationId: String): File = recorder.startRecording("teach_back/$explanationId")
    fun pause() = recorder.pauseRecording()
    fun resume() = recorder.resumeRecording()
    fun stop(): File? = recorder.stopRecording()
    fun discard() = recorder.discardRecording()
}

/**
 * Device-local retention and deletion controls. JSON is intentionally separate from Room so the
 * Release 1.1 capture feature does not need to mutate the Learning schema while R1 is hardening.
 */
class TeachBackLocalStore(private val context: Context) {
    private val file = File(context.filesDir, "teach_back/explanations.json")

    fun save(explanation: TeachBackExplanation) {
        val all = load().filterNot { it.id == explanation.id } + explanation
        write(all)
    }

    fun load(sessionId: String? = null, conceptId: String? = null): List<TeachBackExplanation> = runCatching {
        val arr = JSONArray(file.readText())
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                add(TeachBackExplanation(
                    id = item.getString("id"), sessionId = item.getString("sessionId"), conceptId = item.getString("conceptId"),
                    audioPath = item.getString("audioPath"), durationMs = item.optLong("durationMs"),
                    transcript = item.optString("transcript").takeIf { it.isNotBlank() }, createdAt = item.getLong("createdAt")
                ))
            }
        }.filter { (sessionId == null || it.sessionId == sessionId) && (conceptId == null || it.conceptId == conceptId) }
            .sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    fun updateTranscript(id: String, transcript: String) {
        load().firstOrNull { it.id == id }?.let { save(it.copy(transcript = transcript)) }
    }

    /** Deletes both metadata and local audio; this is irreversible by design and must be UI-confirmed. */
    fun delete(id: String) {
        val all = load()
        all.firstOrNull { it.id == id }?.let { File(it.audioPath).delete() }
        write(all.filterNot { it.id == id })
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun write(items: List<TeachBackExplanation>) {
        file.parentFile?.mkdirs()
        val arr = JSONArray()
        items.forEach { value -> arr.put(JSONObject().apply {
            put("id", value.id); put("sessionId", value.sessionId); put("conceptId", value.conceptId)
            put("audioPath", value.audioPath); put("durationMs", value.durationMs); put("transcript", value.transcript); put("createdAt", value.createdAt)
        }) }
        file.writeText(arr.toString())
    }
}
