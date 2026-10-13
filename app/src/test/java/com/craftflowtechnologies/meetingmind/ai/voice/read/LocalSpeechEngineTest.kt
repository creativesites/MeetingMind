package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.speech.tts.TextToSpeech
import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk
import com.craftflowtechnologies.meetingmind.ai.voice.Wav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

/** Scriptable stand-in for the phone's engine. */
private class FakeTts(
    var initOk: Boolean = true,
    var language: LanguageStatus = LanguageStatus.AVAILABLE,
    var installed: List<LocalVoice> = emptyList(),
    var max: Int = 40,
    /** Outcome for the n-th synthesize call (0-based); default Done. */
    val script: MutableMap<Int, SynthOutcome> = mutableMapOf(),
    val hang: CompletableDeferred<Unit>? = null
) : LocalTtsClient {
    val spoken = mutableListOf<String>()
    var calls = 0
    var shutdowns = 0
    var stops = 0
    var voiceSet: String? = null
    var rateSet: Float? = null
    var pitchSet: Float? = null
    var languageSet: Locale? = null
    val files = mutableListOf<File>()

    override suspend fun init(timeoutMs: Long) = initOk
    override val maxInputLength get() = max
    override fun languageStatus(locale: Locale) = language
    override fun setLanguage(locale: Locale): Boolean { languageSet = locale; return true }
    override fun voices() = installed
    override fun setVoice(name: String): Boolean { voiceSet = name; return true }
    override fun setRate(rate: Float) { rateSet = rate }
    override fun setPitch(pitch: Float) { pitchSet = pitch }
    override suspend fun synthesize(text: String, file: File, timeoutMs: Long): SynthOutcome {
        val n = calls++
        files += file
        hang?.await()
        val outcome = script[n] ?: SynthOutcome.Done
        if (outcome == SynthOutcome.Done) {
            spoken += text
            Wav.write(Pcm(ShortArray(2400) { 1 }, 24000), file)
        }
        return outcome
    }
    override fun stop() { stops++ }
    override fun shutdown() { shutdowns++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LocalSpeechEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun engine(vararg clients: FakeTts): LocalSpeechEngine {
        val queue = ArrayDeque(clients.toList())
        return LocalSpeechEngine({ queue.removeFirst() }, tmp.newFolder())
    }

    private fun request(vararg texts: String, pause: Long = 0, offline: Boolean = false, voice: String? = null, locale: Locale = Locale.US) =
        LocalSpeechRequest(texts.map { SpeechChunk(it, pause) }, locale, voice, rate = 0.9f, pitch = 1.1f, requireOffline = offline)

    private fun voice(name: String, net: Boolean = false, q: Int = 400, locale: Locale = Locale.US) = LocalVoice(name, locale, net, q)

    @Test fun `reads every chunk in order and applies rate and pitch`() = runTest {
        val tts = FakeTts(max = 1000)
        val result = engine(tts).synthesize(request("First.", "Second.", "Third."))
        assertTrue(result is LocalSpeechResult.Done)
        assertEquals(listOf("First.", "Second.", "Third."), tts.spoken)
        assertEquals(0.9f, tts.rateSet)
        assertEquals(1.1f, tts.pitchSet)
        assertEquals(Locale.US, tts.languageSet)
        assertEquals(1, tts.shutdowns)
    }

    @Test fun `long text is split under the engine's limit and keeps its order`() = runTest {
        val tts = FakeTts(max = 30)
        val text = "Alpha beta gamma. Delta epsilon zeta. Eta theta iota. Kappa lambda mu."
        engine(tts).synthesize(request(text))
        assertTrue(tts.spoken.size > 1)
        assertTrue(tts.spoken.all { it.length < 30 })
        assertEquals(text.filter { !it.isWhitespace() }, tts.spoken.joinToString("").filter { !it.isWhitespace() })
    }

    @Test fun `pauses are real silence after a chunk`() = runTest {
        val tts = FakeTts(max = 1000)
        val result = engine(tts).synthesize(request("One.", "Two.", pause = 1000)) as LocalSpeechResult.Done
        // two clips of 2400 samples at 24 kHz (100 ms) plus two 1000 ms pauses
        assertEquals(2 * 2400 + 2 * 24000, result.audio.samples.size)
    }

    @Test fun `init failure is reported`() = runTest {
        val tts = FakeTts(initOk = false)
        val r = engine(tts).synthesize(request("Hi.")) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.INIT_FAILED, r.kind)
        assertEquals(1, tts.shutdowns)
    }

    @Test fun `an unsupported language is reported before anything is spoken`() = runTest {
        val tts = FakeTts(language = LanguageStatus.NOT_SUPPORTED)
        val r = engine(tts).synthesize(request("Hi.", locale = Locale("xx"))) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.LANGUAGE_UNSUPPORTED, r.kind)
        assertEquals(0, tts.calls)
    }

    @Test fun `missing voice data offers the install screen`() = runTest {
        val tts = FakeTts(language = LanguageStatus.MISSING_DATA)
        val r = engine(tts).synthesize(request("Hi.")) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.LANGUAGE_DATA_MISSING, r.kind)
        assertTrue(r.canInstallVoiceData)
    }

    @Test fun `a named voice that is not installed is reported`() = runTest {
        val tts = FakeTts(installed = listOf(voice("en-us-x-a")))
        val r = engine(tts).synthesize(request("Hi.", voice = "en-us-x-missing")) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.VOICE_UNAVAILABLE, r.kind)
    }

    @Test fun `a named installed voice is selected`() = runTest {
        val tts = FakeTts(installed = listOf(voice("en-us-x-a"), voice("en-us-x-b")))
        engine(tts).synthesize(request("Hi.", voice = "en-us-x-b"))
        assertEquals("en-us-x-b", tts.voiceSet)
    }

    @Test fun `offline mode never uses a voice that needs the internet`() = runTest {
        val onlineOnly = FakeTts(installed = listOf(voice("net", net = true)))
        val r = engine(onlineOnly).synthesize(request("Hi.", offline = true, voice = "net")) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.VOICE_UNAVAILABLE, r.kind)
        assertEquals(0, onlineOnly.calls)

        val none = FakeTts(installed = listOf(voice("net", net = true)))
        val r2 = engine(none).synthesize(request("Hi.", offline = true)) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.VOICE_UNAVAILABLE, r2.kind)
        assertEquals(0, none.calls)

        val mixed = FakeTts(installed = listOf(voice("net", net = true, q = 500), voice("local", q = 300)))
        engine(mixed).synthesize(request("Hi.", offline = true))
        assertEquals("local", mixed.voiceSet)
    }

    @Test fun `a failed chunk stops the reading and reports what was made`() = runTest {
        val tts = FakeTts(max = 1000, script = mutableMapOf(2 to SynthOutcome.Error(TextToSpeech.ERROR_SYNTHESIS)))
        val r = engine(tts).synthesize(request("One.", "Two.", "Three.", "Four.")) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.SYNTHESIS_FAILED, r.kind)
        assertEquals(2, r.chunkIndex)
        assertEquals(2, r.completed.size)
        assertEquals(listOf("One.", "Two."), tts.spoken) // Four. was never attempted, Three. was not skipped over
        assertEquals(3, tts.calls)
    }

    @Test fun `a timeout is reported`() = runTest {
        val tts = FakeTts(max = 1000, script = mutableMapOf(0 to SynthOutcome.Timeout))
        assertEquals(LocalFailure.TIMEOUT, (engine(tts).synthesize(request("One.")) as LocalSpeechResult.Failed).kind)
    }

    @Test fun `speech stopped by something else is a failure, not a cancellation`() = runTest {
        val tts = FakeTts(max = 1000, script = mutableMapOf(1 to SynthOutcome.Stopped))
        val r = engine(tts).synthesize(request("One.", "Two.")) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.INTERRUPTED, r.kind)
        assertEquals(1, r.chunkIndex)
    }

    @Test fun `a lost engine connection is retried once on a fresh client`() = runTest {
        val first = FakeTts(max = 1000, script = mutableMapOf(1 to SynthOutcome.Error(TextToSpeech.ERROR_SERVICE)))
        val second = FakeTts(max = 1000)
        val r = engine(first, second).synthesize(request("One.", "Two.", "Three."))
        assertTrue(r is LocalSpeechResult.Done)
        assertEquals(listOf("One."), first.spoken)
        assertEquals(listOf("Two.", "Three."), second.spoken) // the failed chunk was redone, not skipped
        assertEquals(1, first.shutdowns)
        assertEquals(1, second.shutdowns)
        assertEquals(0.9f, second.rateSet) // settings re-applied
    }

    @Test fun `a second service loss is reported`() = runTest {
        val first = FakeTts(max = 1000, script = mutableMapOf(0 to SynthOutcome.Error(TextToSpeech.ERROR_SERVICE)))
        val second = FakeTts(max = 1000, script = mutableMapOf(0 to SynthOutcome.Error(TextToSpeech.ERROR_SERVICE)))
        val r = engine(first, second).synthesize(request("One.")) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.SERVICE_LOST, r.kind)
    }

    @Test fun `not-installed-yet means voice data is missing`() = runTest {
        val tts = FakeTts(max = 1000, script = mutableMapOf(0 to SynthOutcome.Error(TextToSpeech.ERROR_NOT_INSTALLED_YET)))
        val r = engine(tts).synthesize(request("One.")) as LocalSpeechResult.Failed
        assertEquals(LocalFailure.LANGUAGE_DATA_MISSING, r.kind)
        assertTrue(r.canInstallVoiceData)
    }

    @Test fun `cancellation stops the engine, shuts it down and removes temp files`() = runTest {
        val hang = CompletableDeferred<Unit>()
        val tts = FakeTts(max = 1000, hang = hang)
        val dir = tmp.newFolder()
        val engine = LocalSpeechEngine({ tts }, dir)
        val job = launch { engine.synthesize(request("One.", "Two.")) }
        testScheduler.advanceUntilIdle()
        job.cancel()
        testScheduler.advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertEquals(1, tts.stops)
        assertEquals(1, tts.shutdowns)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun `temp files are removed after a normal run`() = runTest {
        val tts = FakeTts(max = 1000)
        val dir = tmp.newFolder()
        LocalSpeechEngine({ tts }, dir).synthesize(request("One.", "Two."))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertTrue(tts.files.none { it.exists() })
    }

    @Test fun `nothing to read is reported`() = runTest {
        val r = engine(FakeTts()).synthesize(request("  ")) as LocalSpeechResult.Failed
        assertNotNull(r.message)
        assertFalse(r.canInstallVoiceData)
    }

    @Test fun `voice choice picks the best offline voice for the language`() {
        val voices = listOf(voice("en-gb", locale = Locale.UK, q = 500), voice("en-us-low", q = 200), voice("en-us-hi", q = 400), voice("fr", locale = Locale.FRANCE, q = 600))
        val chosen = LocalSpeechEngine.chooseVoice(voices, request("x", offline = true, locale = Locale.US))
        assertEquals("en-us-hi", chosen?.name)
        assertEquals(null, LocalSpeechEngine.chooseVoice(voices, request("x", offline = false)))
    }

    @Test fun `error codes map to failures`() {
        assertEquals(LocalFailure.SERVICE_LOST, LocalSpeechEngine.classify(TextToSpeech.ERROR_SERVICE))
        assertEquals(LocalFailure.VOICE_UNAVAILABLE, LocalSpeechEngine.classify(TextToSpeech.ERROR_NETWORK))
        assertEquals(LocalFailure.VOICE_UNAVAILABLE, LocalSpeechEngine.classify(TextToSpeech.ERROR_NETWORK_TIMEOUT))
        assertEquals(LocalFailure.SYNTHESIS_FAILED, LocalSpeechEngine.classify(TextToSpeech.ERROR_INVALID_REQUEST))
    }
}
