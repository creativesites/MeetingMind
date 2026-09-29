package com.example.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AcousticAnalyzerTest {
    private val rate = 16_000
    private val rnd = java.util.Random(7)

    /** A sung chord: steady pitch, no gaps. */
    private fun music(seconds: Int) = FloatArray(rate * seconds) { i ->
        val t = i.toDouble() / rate
        (0.25 * sin(2 * PI * 220 * t) + 0.15 * sin(2 * PI * 277 * t) + 0.12 * sin(2 * PI * 330 * t) + 0.05 * sin(2 * PI * 440 * t)).toFloat()
    }

    /** Speech-like: voiced bursts of ~180 ms with a gliding pitch, separated by pauses. */
    private fun speech(seconds: Int) = FloatArray(rate * seconds) { i ->
        val t = i.toDouble() / rate
        val syllable = (t * 1000).toInt() % 300
        if (syllable > 180 || ((t * 1000).toInt() % 1700) > 1450) (rnd.nextGaussian() * 0.002).toFloat()
        else {
            val f0 = 130 + 30 * sin(2 * PI * 0.7 * t)
            val env = sin(PI * syllable / 180.0)
            (env * (0.3 * sin(2 * PI * f0 * t) + 0.15 * sin(2 * PI * 2 * f0 * t) + 0.08 * sin(2 * PI * 3 * f0 * t) + rnd.nextGaussian() * 0.02)).toFloat()
        }
    }

    private fun crowd(seconds: Int) = FloatArray(rate * seconds) { (rnd.nextGaussian() * 0.2).toFloat() }
    private fun silence(seconds: Int) = FloatArray(rate * seconds) { (rnd.nextGaussian() * 0.0005).toFloat() }

    private fun perSecond(samples: FloatArray) = (0 until samples.size / rate).map { s ->
        AcousticAnalyzer.classify(AcousticAnalyzer.features(samples.copyOfRange(s * rate, (s + 1) * rate), rate))
    }

    @Test fun `each kind of sound is recognised second by second`() {
        fun share(a: List<AcousticActivity>, x: AcousticActivity) = a.count { it == x }.toDouble() / a.size
        assertTrue(share(perSecond(music(10)), AcousticActivity.MUSIC) >= 0.8)
        assertTrue(share(perSecond(speech(10)), AcousticActivity.SPEECH) >= 0.7)
        assertTrue(share(perSecond(crowd(10)), AcousticActivity.CROWD) >= 0.8)
        assertTrue(share(perSecond(silence(10)), AcousticActivity.SILENCE) >= 0.9)
    }

    @Test fun `a service becomes speech, then worship, then speech — with boundaries near the truth`() {
        val service = speech(40) + music(60) + speech(40)
        val segs = AcousticAnalyzer.segments(perSecond(service))
        assertEquals(listOf(AcousticActivity.SPEECH, AcousticActivity.MUSIC, AcousticActivity.SPEECH), segs.map { it.activity })
        assertTrue(kotlin.math.abs(segs[1].startMs - 40_000) <= 4_000)
        assertTrue(kotlin.math.abs(segs[1].endMs - 100_000) <= 4_000)
    }

    @Test fun `short flickers join their neighbours`() {
        val a = List(30) { AcousticActivity.MUSIC } + List(3) { AcousticActivity.SPEECH } + List(30) { AcousticActivity.MUSIC }
        assertEquals(1, AcousticAnalyzer.segments(a).size)
    }
}
