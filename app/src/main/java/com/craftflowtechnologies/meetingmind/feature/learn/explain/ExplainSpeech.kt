package com.craftflowtechnologies.meetingmind.feature.learn.explain

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import java.io.File

/** What the recognizer reports while the learner talks. Level is 0..1 for the bars. */
sealed interface SpeechEvent {
    data class Level(val value: Float) : SpeechEvent
    /** The words so far. May change many times. */
    data class Partial(val text: String) : SpeechEvent
    /** The finished explanation. [audioFile], when an engine wrote one, is deleted by the caller after reading. */
    data class Final(val text: String, val audioFile: File? = null) : SpeechEvent
    data class Failed(val reason: SpeechFailure) : SpeechEvent
}

enum class SpeechFailure {
    /** This phone has no speech recognizer at all. */
    NO_RECOGNIZER,
    /** The microphone was taken away or refused while listening. */
    PERMISSION,
    /** Something else went wrong; the learner can type the explanation instead. */
    OTHER
}

/** Turns the learner's voice into text. One explanation per start/stop cycle. */
interface ExplainSpeech {
    fun start(onEvent: (SpeechEvent) -> Unit)
    /** Stops listening; a [SpeechEvent.Final] follows once the words are settled. */
    fun stop()
    /** Drops everything said so far and releases the microphone. No event follows. */
    fun cancel()
}

/**
 * Android's on-device recognizer (API 31+), so the audio never leaves the phone. Where the phone has
 * none, there is no recognizer and the screen offers typing instead.
 *
 * It writes no audio file of its own, so [SpeechEvent.Final.audioFile] is always null here. It keeps
 * listening across the short pauses that end a recognition session, until [stop] is called.
 * Must be used from the main thread, as the platform recognizer requires.
 */
class AndroidExplainSpeech(private val context: Context) : ExplainSpeech {

    private var recognizer: android.speech.SpeechRecognizer? = null
    private var onEvent: ((SpeechEvent) -> Unit)? = null
    private var committed = ""
    private var listening = false
    private var stopping = false

    override fun start(onEvent: (SpeechEvent) -> Unit) {
        release()
        this.onEvent = onEvent
        committed = ""
        stopping = false
        listening = true
        val rec = createRecognizer()
        if (rec == null) {
            listening = false
            onEvent(SpeechEvent.Failed(SpeechFailure.NO_RECOGNIZER))
            return
        }
        rec.setRecognitionListener(listener)
        recognizer = rec
        listen()
    }

    override fun stop() {
        if (!listening) return
        stopping = true
        recognizer?.stopListening()
    }

    override fun cancel() {
        listening = false
        release()
    }

    private fun createRecognizer(): android.speech.SpeechRecognizer? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            // The screen promises "Your voice stays on this phone". The default recognizer may send
            // audio to a server even with EXTRA_PREFER_OFFLINE, so without an on-device recognizer
            // there is none: the learner types the explanation instead (NO_RECOGNIZER).
            null
        }
    }.getOrNull()

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        recognizer?.startListening(intent)
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun joined(part: String): String = listOf(committed, part.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private fun finish(text: String) {
        listening = false
        val sink = onEvent
        release()
        sink?.invoke(SpeechEvent.Final(text))
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            onEvent?.invoke(SpeechEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val best = partialResults?.bestText() ?: return
            onEvent?.invoke(SpeechEvent.Partial(joined(best)))
        }

        override fun onResults(results: Bundle?) {
            val best = results?.bestText().orEmpty()
            if (stopping || !listening) {
                finish(joined(best))
            } else {
                committed = joined(best)
                listen()
            }
        }

        override fun onError(error: Int) {
            when {
                stopping -> finish(committed)
                listening && (error == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                    error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) -> listen()
                else -> {
                    val reason = when (error) {
                        android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.PERMISSION
                        else -> SpeechFailure.OTHER
                    }
                    listening = false
                    val sink = onEvent
                    release()
                    sink?.invoke(SpeechEvent.Failed(reason))
                }
            }
        }

        private fun Bundle.bestText(): String? =
            getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
