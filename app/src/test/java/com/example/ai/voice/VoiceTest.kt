package com.example.ai.voice

import com.example.ai.cloud.GeminiInteractions
import com.example.core.devotional.Devotional
import com.example.core.devotional.DevotionalLabels
import com.example.core.devotional.DevotionalOrigin
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.LocalDay
import com.example.core.scripture.ScriptureReferenceParser
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceTest {

    @Test fun `wav round trip, concat with silence, and resampling`() {
        val a = Pcm(shortArrayOf(1, 2, 3, -4), 24_000)
        val back = Wav.read(Wav.bytes(a))!!
        assertArrayEquals(a.samples, back.samples)
        assertEquals(24_000, back.sampleRate)
        val joined = Wav.concat(listOf(a, Wav.silence(1, 24_000), a))!!
        assertEquals(4 + 24 + 4, joined.samples.size)
        val half = Wav.resample(Pcm(ShortArray(100) { 100 }, 48_000), 24_000)
        assertEquals(50, half.size)
        assertTrue(half.all { it.toInt() == 100 })
        assertNull(Wav.read(ByteArray(10)))
        assertEquals(1000L, Wav.silence(1000, 16_000).durationMs)
    }

    @Test fun `stereo wav is mixed to mono`() {
        val mono = Wav.bytes(Pcm(shortArrayOf(10, 20), 8000))
        // Hand-edit the header to 2 channels: the two samples become one frame (10+20)/2.
        val stereo = mono.copyOf().also { it[22] = 2 }
        assertArrayEquals(shortArrayOf(15), Wav.read(stereo)!!.samples)
    }

    private val devotional = Devotional(
        day = LocalDay("2026-09-24"), origin = DevotionalOrigin.CLOUD_AI, title = "Rest for the weary",
        scripture = listOf(ScriptureReferenceParser.parse("Matthew 11:28-30")!!),
        reflection = listOf("Jesus calls the tired.", "Rest is a gift."), application = listOf("Take a walk."),
        prayer = "Lord, teach me to rest. Amen.", motivation = "You are held.", question = "Where do you need rest?",
        label = DevotionalLabels.CLOUD
    )

    @Test fun `the script reads the real verse text, prays with an Amen, and ends with the word for today`() {
        val ref = devotional.scripture.first()
        val script = SpeechScript.build(devotional, mapOf(ref to "28 Come to Me, all you who are weary."), VoiceSettings(), "Ana", hour = 8)
        assertTrue(script.first().text.startsWith("Good morning, Ana."))
        val scripture = script.first { it.kind == SpeechSegment.Kind.SCRIPTURE }.text
        assertEquals("Matthew chapter 11, verses 28 to 30. Come to Me, all you who are weary.", scripture)
        val prayer = script.filter { it.kind == SpeechSegment.Kind.PRAYER }.map { it.text }
        assertEquals(listOf("Let's pray.", "Lord, teach me to rest.", "Amen."), prayer)
        assertEquals(SpeechSegment.Kind.MOTIVATION, script[script.size - 2].kind)
        val silent = SpeechScript.build(devotional, emptyMap(), VoiceSettings(speakPrayer = false))
        assertFalse(silent.any { it.kind == SpeechSegment.Kind.PRAYER })
        assertTrue(silent.any { it.text == "Our reading is Matthew chapter 11, verses 28 to 30." })
    }

    @Test fun `chunks respect the size limit and pause with silence, never tags`() {
        val script = SpeechScript.build(devotional.copy(reflection = List(20) { "A sentence that goes on for a while to fill space. And another one here." } + "Wait <short pause> and rest [pause] here."), emptyMap(), VoiceSettings())
        val chunks = SpeechScript.chunks(script, maxChars = 300)
        assertTrue(chunks.size > 3)
        assertTrue(chunks.all { it.text.length <= 300 })
        assertTrue(chunks.none { it.text.contains("<") || it.text.contains("pause", ignoreCase = true) })
        assertTrue(chunks.any { it.pauseAfterMs >= 1000 })
        assertTrue(chunks.joinToString(" ") { it.text }.contains("Go in peace."))
    }

    @Test fun `the greeting knows the time of day`() {
        assertTrue(SpeechScript.build(devotional, emptyMap(), VoiceSettings(), "Ana", hour = 16).first().text.startsWith("Good afternoon, Ana."))
        assertTrue(SpeechScript.build(devotional, emptyMap(), VoiceSettings(), "Ana", hour = 20).first().text.startsWith("Good evening, Ana."))
        assertEquals("Good morning", SpeechScript.greetingFor(7))
        assertEquals("Hello", SpeechScript.greetingFor(1))
    }

    @Test fun `speech request has the documented shape and media is found in the steps`() {
        val body = GeminiInteractions.speechRequest("gemini-3.8-flash-tts", "Hello <short pause> friend", "warm pastor", "Sulafat")
        assertEquals("gemini-3.8-flash-tts", body.getString("model"))
        val item = body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("speech_metadata", item.getJSONArray("annotations").getJSONObject(0).getString("type"))
        assertEquals("warm pastor", item.getJSONArray("annotations").getJSONObject(0).getString("style"))
        assertEquals("Sulafat", body.getJSONObject("generation_config").getJSONArray("speech_config").getJSONObject(0).getString("voice"))
        assertEquals("audio", body.getJSONObject("response_format").getString("type"))

        val response = JSONObject("""{"steps":[{"type":"thought"},{"type":"model_output","content":[{"type":"text","text":"x"},{"type":"audio","data":"QUJD","mime_type":"audio/wav"}]}]}""")
        assertEquals("QUJD" to "audio/wav", GeminiInteractions.findMedia(response, "audio"))
        assertNull(GeminiInteractions.findMedia(response, "image"))
        val image = GeminiInteractions.imageRequest("gemini-3.1-flash-lite-image", "a sunrise")
        assertEquals("9:16", image.getJSONObject("response_format").getString("aspect_ratio"))
    }

    @Test fun `voice settings live in the devotional profile`() {
        val p = DevotionalProfile(voice = VoiceSettings(PreacherStyle.STORYTELLER, VoiceGender.FEMALE, speakPrayer = false, autoVoice = true, rate = 1.15f))
        assertEquals(p, DevotionalProfile.fromJson(p.toJson()))
        assertEquals("Gacrux", p.voice.voiceName)
        assertTrue(p.voice.deliveryStyle.contains("brisker"))
    }
}
