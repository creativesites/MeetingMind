package com.craftflowtechnologies.meetingmind.feature.personal

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.craftflowtechnologies.meetingmind.core.personal.PersonalSoundscape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight, zero-asset procedural ambient soundscape player for the Routine Player.
 *
 * Synthesizes gentle low-amplitude textures on a background coroutine via [AudioTrack]:
 * - [PersonalSoundscape.RAIN]: soft pink-filtered rain hiss
 * - [PersonalSoundscape.FIREPLACE]: warm brown-noise hearth rumble with occasional gentle crackles
 * - [PersonalSoundscape.FOREST]: slow breeze-modulated soft wind
 * - [PersonalSoundscape.DAWN]: warm fifth-interval ambient harmonic drone
 *
 * All audio calls are wrapped in `runCatching` so Robolectric, muted devices, or audio-focus
 * constraints never crash the Routine Player.
 */
internal class PersonalSoundscapePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var currentSoundscape: PersonalSoundscape = PersonalSoundscape.NONE

    fun setSoundscape(soundscape: PersonalSoundscape, playing: Boolean) {
        if (!playing || soundscape == PersonalSoundscape.NONE) {
            stop()
            currentSoundscape = soundscape
            return
        }
        if (currentSoundscape == soundscape && job?.isActive == true) return
        stop()
        currentSoundscape = soundscape
        job = scope.launch { runLoop(soundscape) }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Plays a gentle 0.9s warm harmonic chime when a routine step timer reaches 0:00. */
    fun playGentleBell() {
        scope.launch {
            runCatching {
                val sampleRate = 22_050
                val durationSec = 0.9
                val numSamples = (sampleRate * durationSec).toInt()
                val pcm = ShortArray(numSamples)
                val f1 = 528.0 // gentle warm C5-ish tone
                val f2 = 792.0 // soft fifth overtone
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 4.5) * (1.0 - exp(-t * 80.0))
                    val sample = (0.7 * sin(2.0 * PI * f1 * t) + 0.3 * sin(2.0 * PI * f2 * t)) * env * 0.22
                    pcm[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                }
                val track = buildTrack(sampleRate, pcm.size * 2) ?: return@runCatching
                try {
                    track.play()
                    track.write(pcm, 0, pcm.size)
                } finally {
                    runCatching { track.stop() }
                    runCatching { track.release() }
                }
            }
        }
    }

    private fun runLoop(soundscape: PersonalSoundscape) {
        val sampleRate = 22_050
        val chunkSamples = 2048
        val track = runCatching { buildTrack(sampleRate, chunkSamples * 4) }.getOrNull() ?: return
        val buffer = ShortArray(chunkSamples)
        val rng = Random(42)

        // Filter state
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var brown = 0.0
        var phase = 0L

        try {
            track.play()
            while (scope.isActive && job?.isActive == true) {
                for (i in 0 until chunkSamples) {
                    val t = phase.toDouble() / sampleRate
                    val white = rng.nextDouble(-1.0, 1.0)
                    val sample = when (soundscape) {
                        PersonalSoundscape.RAIN -> {
                            b0 = 0.997 * b0 + white * 0.029
                            b1 = 0.985 * b1 + white * 0.032
                            b2 = 0.950 * b2 + white * 0.048
                            (b0 + b1 + b2 + white * 0.018) * 0.14
                        }
                        PersonalSoundscape.FIREPLACE -> {
                            brown = (brown + white * 0.02).coerceIn(-1.0, 1.0) * 0.996
                            val crackle = if (rng.nextDouble() < 0.0007) rng.nextDouble(-0.45, 0.45) else 0.0
                            (brown * 0.28 + crackle * 0.18)
                        }
                        PersonalSoundscape.FOREST -> {
                            b0 = 0.992 * b0 + white * 0.024
                            val breeze = 0.55 + 0.45 * sin(2.0 * PI * 0.11 * t)
                            b0 * breeze * 0.22
                        }
                        PersonalSoundscape.DAWN -> {
                            val swell = 0.65 + 0.35 * sin(2.0 * PI * 0.08 * t)
                            val drone = 0.55 * sin(2.0 * PI * 174.0 * t) +
                                0.30 * sin(2.0 * PI * 261.6 * t) +
                                0.15 * sin(2.0 * PI * 348.0 * t)
                            drone * swell * 0.09
                        }
                        PersonalSoundscape.NONE -> 0.0
                    }
                    // Gentle 0.6s fade-in at start
                    val fadeIn = (t / 0.6).coerceIn(0.0, 1.0)
                    buffer[i] = (sample * fadeIn * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                    phase++
                }
                track.write(buffer, 0, buffer.size)
            }
        } catch (_: Throwable) {
            // Gracefully exit on interruption or audio device disconnect
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun buildTrack(sampleRate: Int, bufferBytes: Int): AudioTrack? {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, bufferBytes))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
}
