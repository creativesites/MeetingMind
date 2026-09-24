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
        assertEquals("Hello", JSONObject(GeminiLiveVoice.textMessage("Hello")).getJSONObject("realtimeInput").getString("text"))
        assertTrue(JSONObject(GeminiLiveVoice.audioEndMessage()).getJSONObject("realtimeInput").getBoolean("audioStreamEnd"))
    }

    @Test fun `live failures read in plain words`() {
        assertTrue(GeminiLiveVoice.explain(1007, "API key not valid. Please pass a valid API key.", null).contains("API key"))
        assertTrue(GeminiLiveVoice.explain(1011, "Resource has been exhausted (e.g. check quota).", null).contains("quota"))
        assertTrue(GeminiLiveVoice.explain(null, null, "timeout").contains("timeout"))
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

    @Test fun `live setup lets prayer breathe with low sensitivity and a long silence before the turn ends`() {
        val aad = GeminiLiveVoice.setupMessage("x", "Kore").getJSONObject("setup").getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection")
        assertEquals("END_SENSITIVITY_LOW", aad.getString("endOfSpeechSensitivity"))
        assertEquals("START_SENSITIVITY_LOW", aad.getString("startOfSpeechSensitivity"))
        assertTrue(aad.getInt("silenceDurationMs") >= 1000)
    }

    @Test fun `transcript pieces keep their spaces, even a lone one, and a new speaker starts a line`() {
        var lines = emptyList<PrayLine>()
        listOf("Hello,", " Winston.", " It", " ", "is", " so good", " .").forEach { lines = PrayLines.append(lines, false, it) }
        assertEquals("Hello, Winston. It is so good.", lines.single().text)
        lines = PrayLines.append(lines, true, " Amen")
        assertEquals(PrayLine(true, "Amen"), lines.last())
        assertEquals(lines, PrayLines.append(lines, false, ""))
        // Pieces arriving with a server's lone-space piece — the one that used to be dropped.
        assertEquals(listOf(" "), GeminiLiveVoice.parse("""{"serverContent":{"outputTranscription":{"text":" "}}}""") {}.filterIsInstance<LiveVoiceEvent.Said>().map { it.text })
    }

    @Test fun `the mic stays closed to echo while the companion talks, and opens when you really speak`() {
        val gate = com.example.ai.live.BargeIn(threshold = 0.16f, framesNeeded = 3)
        assertTrue(gate.decide(0.02f, companionAudible = false)) // quiet room, nobody talking: open
        // Companion talking: its echo (moderate levels) is held back…
        repeat(10) { assertFalse(gate.decide(0.10f, companionAudible = true)) }
        // …a single loud blip isn't enough…
        assertFalse(gate.decide(0.3f, companionAudible = true))
        assertFalse(gate.decide(0.05f, companionAudible = true))
        // …but a sustained voice is: after three loud frames the person has the floor.
        assertFalse(gate.decide(0.3f, true)); assertFalse(gate.decide(0.3f, true)); assertTrue(gate.decide(0.3f, true))
        assertTrue(gate.decide(0.12f, true)) // and keeps it through softer words
    }

    @Test fun `worship first, a persona, and singing only what may be sung`() {
        val s = PraySetup(worship = "Amazing Grace", persona = "a passionate African Pentecostal preacher")
        val sys = PrayerCompanion.systemInstruction(s)
        assertTrue(sys.contains("Pentecostal preacher") && sys.contains("public-domain hymns") && sys.contains("don't sing out its full lyrics"))
        assertTrue(PrayerCompanion.opening(s).contains("sing \"Amazing Grace\""))
        assertTrue(PrayerCompanion.opening(s.copy(worship = com.example.core.prayer.WorshipSongs.THEIR_OWN)).contains("follow me"))
        assertTrue(PrayerCompanion.singNow(null).contains("follow me"))
        assertTrue(com.example.core.prayer.WorshipSongs.hymns.none { it.contains("Great Is Thy Faithfulness") })
    }

    @Test fun `church voices include Pentecostal with a Zambian accent, Catholic, and more`() {
        val pente = com.example.ai.voice.PreacherStyle.PENTECOSTAL
        assertTrue(pente.style.contains("Zambian") && pente.tradition.startsWith("Pentecostal"))
        assertTrue(com.example.ai.voice.PreacherStyle.entries.map { it.tradition }.toSet().containsAll(setOf("Catholic", "Evangelical & Baptist", "Anglican, Methodist & Reformed")))
    }

    @Test fun `live session looks alive`() {
        val vm = PrayWithMeViewModel(ApplicationProvider.getApplicationContext())
        val lines = listOf(
            PrayLine(false, "Hello, Winston. It is so good to be with you. What is on your heart that you'd like to bring before the Lord today?"),
            PrayLine(true, "My mum's surgery on Friday."),
            PrayLine(false, "Lord, we lift up Winston's mother to you. Guide the hands of every doctor and nurse, and fill her with your peace.")
        )
        val base = PrayUi(available = true, started = true, lines = lines, startedAt = System.currentTimeMillis() - 192_000, mode = PrayMode.TOGETHER)
        compose.setContent { MeetMindTheme { PraySessionContent(vm, base.copy(state = com.example.ai.live.LiveVoiceState.SPEAKING), {}, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/pray_live_speaking.png")
    }

    @Test fun `live session while singing, and when it ends`() {
        val vm = PrayWithMeViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            MeetMindTheme {
                PraySessionContent(vm, PrayUi(available = true, started = true, singing = true, song = "Amazing Grace", state = com.example.ai.live.LiveVoiceState.SPEAKING,
                    lines = listOf(PrayLine(false, "Amazing grace, how sweet the sound, that saved a wretch like me…")), startedAt = System.currentTimeMillis() - 40_000), {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/pray_live_singing.png")
    }

    @Test fun `live session ended`() {
        val vm = PrayWithMeViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            MeetMindTheme {
                PraySessionContent(vm, PrayUi(available = true, started = true, state = com.example.ai.live.LiveVoiceState.ENDED,
                    lines = listOf(PrayLine(false, "Go in peace, Winston. Amen.")), startedAt = System.currentTimeMillis() - 610_000), {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/pray_live_ended.png")
    }
}
