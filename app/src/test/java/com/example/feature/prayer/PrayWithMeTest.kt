package com.example.feature.prayer

import android.content.Context
import android.util.Base64
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ai.devotional.DevotionalAsk
import com.example.ai.devotional.DevotionalEngine
import com.example.ai.live.GeminiLiveVoice
import com.example.ai.live.LiveVoiceEvent
import com.example.ai.voice.VoiceSection
import com.example.core.audio.PlaybackPhase
import com.example.core.audio.PlaybackState
import com.example.core.devotional.ClassicDevotionals
import com.example.core.devotional.DevotionalOrigin
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.DevotionalTone
import com.example.core.prayer.PrayMode
import com.example.core.prayer.PraySetup
import com.example.core.prayer.PrayerCompanion
import com.example.core.share.BackgroundLibrary
import com.example.core.ui.MiniPlayerBar
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize
import java.time.LocalDate
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class PrayWithMeTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `live setup follows the documented protocol`() {
        val setup = GeminiLiveVoice.setupMessage("Be kind.", "Sulafat").getJSONObject("setup")
        assertEquals("models/gemini-3.8-live", setup.getString("model"))
        assertEquals("AUDIO", setup.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0))
        assertEquals("Sulafat", setup.getJSONObject("generationConfig").getJSONObject("speechConfig").getJSONObject("voiceConfig").getJSONObject("prebuiltVoiceConfig").getString("voiceName"))
        assertEquals("Be kind.", setup.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        assertTrue(setup.has("inputAudioTranscription") && setup.has("outputAudioTranscription"))
        val audio = JSONObject(GeminiLiveVoice.audioMessage(byteArrayOf(1, 2))).getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", audio.getString("mimeType"))
        val text = JSONObject(GeminiLiveVoice.textMessage("Hello")).getJSONObject("clientContent")
        assertTrue(text.getBoolean("turnComplete"))
    }

    @Test fun `server messages become events and audio`() {
        val audio = mutableListOf<ByteArray>()
        val pcm = byteArrayOf(10, 20, 30, 40)
        val raw = """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"${Base64.encodeToString(pcm, Base64.NO_WRAP)}"}}]},"outputTranscription":{"text":"Lord, "},"inputTranscription":{"text":"Amen"},"turnComplete":true}}"""
        val events = GeminiLiveVoice.parse(raw) { audio += it }
        assertArrayEquals(pcm, audio.single())
        assertTrue(LiveVoiceEvent.Said("Lord, ") in events && LiveVoiceEvent.Heard("Amen") in events && LiveVoiceEvent.TurnDone in events)
        assertEquals(listOf(LiveVoiceEvent.Ready), GeminiLiveVoice.parse("""{"setupComplete":{}}""") {})
        assertTrue(GeminiLiveVoice.parse("""{"serverContent":{"interrupted":true}}""") {}.contains(LiveVoiceEvent.Interrupted))
        assertTrue(GeminiLiveVoice.parse("not json") {}.isEmpty())
    }

    @Test fun `the companion is told its boundaries, the mode and what to pray about`() {
        val s = PraySetup(mode = PrayMode.ONE_AT_A_TIME, about = "Peace for exams", requests = listOf("Mum's surgery"), name = "Ana")
        val text = PrayerCompanion.systemInstruction(s)
        listOf("never claim to speak for God", "988", "Peace for exams", "Mum's surgery", "one at a time", "Ana").forEach { assertTrue(it, text.contains(it, ignoreCase = true)) }
        assertTrue(PrayerCompanion.opening(s).contains("first thing"))
        assertTrue(PrayerCompanion.opening(PraySetup()).contains("ask what's on my heart"))
        assertFalse(PrayMode.TALK_IT_THROUGH in PrayMode.prayerModes)
    }

    @Test fun `voice sections encode, decode and give ranges`() {
        val marks = listOf(VoiceSection.SCRIPTURE to 0L, VoiceSection.REFLECTION to 30_000L, VoiceSection.PRAYER to 90_000L, VoiceSection.WORD to 120_000L)
        val back = VoiceSection.decode(VoiceSection.encode(marks))
        assertEquals(marks, back)
        assertEquals(90_000L to 120_000L, VoiceSection.range(back, VoiceSection.PRAYER))
        assertEquals(120_000L to null, VoiceSection.range(back, VoiceSection.WORD))
        assertNull(VoiceSection.range(back, VoiceSection.APPLY))
        assertTrue(VoiceSection.decode("junk,PRAYER:x").isEmpty())
    }

    @Test fun `asks round trip, and "a different one" really differs even offline`() = runBlocking {
        val ask = DevotionalAsk("new job nerves", "Psalm 46", setOf("Courage"), DevotionalTone.FRIEND, 7, 2)
        assertEquals(ask, DevotionalAsk.fromJson(ask.toJson()))
        assertNull(DevotionalAsk.fromJson(null))
        val classics = File("src/main/assets/${ClassicDevotionals.ASSET}").inputStream().use { ClassicDevotionals.parse(GZIPInputStream(it)) }
        val engine = DevotionalEngine({ emptyList() }, { null }, classics, emptyList())
        val date = LocalDate.of(2026, 9, 24)
        val first = engine.write(date, DevotionalProfile(), ask = DevotionalAsk(variant = 0))
        val second = engine.write(date, DevotionalProfile(), ask = DevotionalAsk(variant = 1))
        val third = engine.write(date, DevotionalProfile(), ask = DevotionalAsk(variant = 2))
        assertEquals(DevotionalOrigin.CLASSIC, second.origin)
        assertNotEquals(first.reflection, second.reflection)
        assertNotEquals(second.reflection, third.reflection)
        assertEquals("2026-09-24", third.day.iso)
    }

    @Test fun `built-in backgrounds are all there and credited`() {
        val all = BackgroundLibrary.builtIn(context)
        assertEquals(26, all.size)
        assertTrue(all.all { it.file.exists() && it.file.length() > 5_000 && it.credit!!.contains("Unsplash") })
        assertEquals(BackgroundLibrary.forDay(context, 100, 1), BackgroundLibrary.forDay(context, 100, 1))
    }

    @Test fun `setup screen`() {
        compose.setContent {
            MeetMindTheme {
                PraySetupContent(PrayUi(available = true, requests = listOf("a" to "Mum's surgery", "b" to "Exams")), null, {}, {}) { _, _ -> }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/pray_setup.png")
    }

    @Test fun `mini player`() {
        compose.setContent {
            MeetMindTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    MiniPlayerBar(PlaybackState(phase = PlaybackPhase.PLAYING, recordingId = "devotional:n1", title = "Rest for the weary", durationMs = 300_000, positionMs = 95_000), {}, {}, {},
                        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/mini_player.png")
    }
}

