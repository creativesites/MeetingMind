package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk
import com.craftflowtechnologies.meetingmind.core.audio.QueueItem
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class CPlayer : ReadPlayer {
    val plays = mutableListOf<Int>()
    val appended = mutableListOf<QueueItem>()
    override fun play(queueId: String, items: List<QueueItem>, startIndex: Int, startPositionMs: Long, expected: Int) { plays += items.size }
    override fun append(item: QueueItem) { appended += item }
    override fun setExpected(count: Int) = Unit
}

private class CVoice(override val provider: SpeechProvider, val failOn: MutableSet<String> = mutableSetOf(), val hang: kotlinx.coroutines.CompletableDeferred<Unit>? = null) : SectionVoice {
    override val fingerprintModel = "fake"
    override val voiceId = "v"
    val made = mutableListOf<String>()
    override suspend fun synthesize(partId: String, fingerprint: String, chunks: List<SpeechChunk>, onProgress: (Float) -> Unit): VoiceResult {
        hang?.await()
        if (partId in failOn) return VoiceResult.Failed("down", RecoveryPlan("Gemini voice isn't available right now.", listOf(Recovery.RETRY)))
        made += partId
        return VoiceResult.Done(Pcm(ShortArray(12000) { (it % 40).toShort() }, 24000))
    }
    override fun discard(partId: String) = Unit
}

private class CHost(
    override val store: ReadingStore,
    override val player: CPlayer,
    var note: LoadedNote?,
    var env: Environment,
    var settings: ReadToMeSettings = ReadToMeSettings(disclosureAccepted = true)
) : ReadingHost {
    var gemini = CVoice(SpeechProvider.GEMINI)
    val local = CVoice(SpeechProvider.LOCAL)
    var geminiCreatedWithCloudAllowed: Boolean? = null
    var localOffline: Boolean? = null
    override suspend fun loadNote(noteId: String, options: ExtractOptions) = note
    override suspend fun environment() = env
    override fun settings() = settings
    var profile = com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadToMeProfile()
    override fun profile() = profile
    override fun updateProfile(change: (com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadToMeProfile) -> com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadToMeProfile): com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadToMeProfile { profile = change(profile); return profile }
    override fun updateSettings(change: (ReadToMeSettings) -> ReadToMeSettings): ReadToMeSettings { settings = change(settings); return settings }
    override fun localVoice(settings: ReadToMeSettings, requireOffline: Boolean): SectionVoice { localOffline = requireOffline; return local }
    var shaperAllowCloud: Boolean? = null
    override fun shaper(noteId: String, allowCloud: Boolean, passages: List<String>): com.craftflowtechnologies.meetingmind.ai.voice.read.smart.SmartShaper {
        shaperAllowCloud = allowCloud
        return com.craftflowtechnologies.meetingmind.ai.voice.read.smart.SmartShaper(null, com.craftflowtechnologies.meetingmind.ai.voice.read.smart.ScriptStore(store.scriptFile(noteId)), passages)
    }
    override fun geminiVoice(settings: ReadToMeSettings, delivery: DeliverySpec, routeAllowsCloud: Boolean): SectionVoice { geminiCreatedWithCloudAllowed = routeAllowsCloud; return gemini }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingCoordinatorTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var host: CHost
    private lateinit var scope: TestScope
    private lateinit var coordinator: ReadingCoordinator

    private val online = Environment(ProcessingProfile.INTERNET, geminiConfigured = true, entitled = true, keyTier = GeminiKeyTier.PAID, usingBuiltInKey = false)
    private val facts = NoteFacts(notePrivate = false, notebookConfidential = false, workPrivacyOffline = false, faithLocked = false)

    private fun loaded(f: NoteFacts = facts, b: Blocks = defaultBlocks()) =
        LoadedNote("A note", NotebookSpace.LEARNING, extract(b), f, "en")

    private fun defaultBlocks() = Blocks().apply {
        add(HEADING_2, "One", id = "h1"); add(PARAGRAPH, "First section text.", id = "p1")
        add(HEADING_2, "Two", id = "h2"); add(PARAGRAPH, "Second section text.", id = "p2")
    }

    @Before fun setUp() {
        host = CHost(ReadingStore(tmp.newFolder("p"), tmp.newFolder("e")), CPlayer(), loaded(), online)
        scope = TestScope(StandardTestDispatcher())
        coordinator = ReadingCoordinator(host, scope)
    }

    private fun TestScope.go(block: () -> Unit) { block(); advanceUntilIdle() }

    @Test fun `cloud allowed reads with the Gemini voice and finishes`() = scope.runTest {
        go { coordinator.start("n1") }
        val s = coordinator.state.value as ReadingUiState.Finished
        assertEquals(SpeechProvider.GEMINI, s.provider)
        assertEquals(listOf("h1", "h2"), host.gemini.made)
        assertTrue(host.local.made.isEmpty())
        assertEquals(1, host.player.plays.size)
        assertEquals(1, host.player.appended.size)
    }

    @Test fun `offline mode never makes the Gemini voice and demands an offline phone voice`() = scope.runTest {
        host.env = online.copy(profile = ProcessingProfile.OFFLINE)
        go { coordinator.start("n1") }
        assertEquals(SpeechProvider.LOCAL, (coordinator.state.value as ReadingUiState.Finished).provider)
        assertEquals(null, host.geminiCreatedWithCloudAllowed)
        assertEquals(true, host.localOffline)
        assertTrue(host.gemini.made.isEmpty())
    }

    @Test fun `private note goes to the phone voice and the lock screen title is generic`() = scope.runTest {
        host.note = loaded(facts.copy(notePrivate = true))
        go { coordinator.start("n1") }
        assertEquals(SpeechProvider.LOCAL, (coordinator.state.value as ReadingUiState.Finished).provider)
        assertTrue(host.gemini.made.isEmpty())
        assertTrue(host.store.load("n1")!!.genericTitle)
    }

    @Test fun `disclosure is asked first, then accepted and remembered`() = scope.runTest {
        host.settings = ReadToMeSettings(disclosureAccepted = false)
        go { coordinator.start("n1") }
        assertTrue(coordinator.state.value is ReadingUiState.NeedsDisclosure)
        assertTrue(host.gemini.made.isEmpty())
        go { coordinator.acceptDisclosure() }
        assertTrue(coordinator.state.value is ReadingUiState.Finished)
        assertTrue(host.settings.disclosureAccepted)
    }

    @Test fun `faith lock closed generates nothing`() = scope.runTest {
        host.note = loaded(facts.copy(faithLocked = true))
        go { coordinator.start("n1") }
        assertEquals(ReadingUiState.Locked, coordinator.state.value)
        assertTrue(host.player.plays.isEmpty())
    }

    @Test fun `empty note and missing note are reported`() = scope.runTest {
        host.note = loaded(b = Blocks())
        go { coordinator.start("n1") }
        assertEquals(ReadingUiState.Empty, coordinator.state.value)
        host.note = null
        go { coordinator.start("n1") }
        assertEquals(ReadingUiState.NotFound, coordinator.state.value)
    }

    @Test fun `a Gemini failure stops, offers retry and switch, and does not change voice by itself`() = scope.runTest {
        host.gemini.failOn += "h2"
        go { coordinator.start("n1") }
        val s = coordinator.state.value as ReadingUiState.Failed
        assertEquals(1, s.partIndex)
        assertEquals(SpeechProvider.GEMINI, s.provider)
        assertTrue(Recovery.RETRY in s.options && Recovery.SWITCH_TO_PHONE_VOICE in s.options)
        assertTrue(host.local.made.isEmpty())
    }

    @Test fun `retry continues with the same voice and only the missing parts`() = scope.runTest {
        host.gemini.failOn += "h2"
        go { coordinator.start("n1") }
        host.gemini.failOn.clear()
        go { coordinator.retry() }
        assertTrue(coordinator.state.value is ReadingUiState.Finished)
        assertEquals(listOf("h1", "h2"), host.gemini.made)
        assertEquals(1, host.player.plays.size)
        assertEquals(1, host.player.appended.size)
    }

    @Test fun `explicit switch finishes the unplayed parts on the phone and records it`() = scope.runTest {
        host.gemini.failOn += "h2"
        go { coordinator.start("n1") }
        go { coordinator.switchToPhoneVoice() }
        val s = coordinator.state.value as ReadingUiState.Finished
        assertEquals(SpeechProvider.LOCAL, s.provider)
        assertTrue(s.switchedToPhone)
        assertEquals(listOf("h1"), host.gemini.made)
        assertEquals(listOf("h2"), host.local.made)
        assertEquals(1, host.store.load("n1")!!.switchedToPhoneAtSection)
    }

    @Test fun `phone voice has no switch option when it fails`() = scope.runTest {
        host.env = online.copy(profile = ProcessingProfile.OFFLINE)
        host.local.failOn += "h1"
        go { coordinator.start("n1") }
        val s = coordinator.state.value as ReadingUiState.Failed
        assertFalse(Recovery.SWITCH_TO_PHONE_VOICE in s.options)
    }

    @Test fun `private sections ask for a choice, and the cloud-without-private choice leaves them out`() = scope.runTest {
        val b = Blocks().apply {
            add(HEADING_2, "Notes", id = "h1"); add(PARAGRAPH, "Shared text.", id = "p1")
            add(HEADING_2, "Mine", key = "private_k", id = "h2"); add(PARAGRAPH, "Secret words.", key = "private_k", id = "p2")
        }
        host.note = LoadedNote("A note", NotebookSpace.LEARNING, extract(b, privateKeys = setOf("private_k")), facts, "en")
        go { coordinator.start("n1") }
        assertTrue(coordinator.state.value is ReadingUiState.NeedsChoice)
        go { coordinator.chooseCloudWithoutSensitive() }
        assertTrue(coordinator.state.value is ReadingUiState.Finished)
        assertEquals(listOf("h1"), host.gemini.made)
    }

    @Test fun `cancel keeps finished parts and a later start reuses them`() = scope.runTest {
        go { coordinator.start("n1") }
        val before = host.gemini.made.size
        go { coordinator.cancel() }
        assertEquals(ReadingUiState.Idle, coordinator.state.value)
        go { coordinator.start("n1") }
        assertEquals(before, host.gemini.made.size)
    }

    @Test fun `offer reports sections and the voice without generating`() = scope.runTest {
        val offer = coordinator.offer("n1")!!
        assertEquals(listOf("h1" to "One", "h2" to "Two"), offer.sections)
        assertEquals(SpeechProvider.GEMINI, offer.provider)
        assertTrue(host.gemini.made.isEmpty())
    }

    @Test fun `a hung Gemini request can be left for the phone voice by the person`() = scope.runTest {
        host.gemini = CVoice(SpeechProvider.GEMINI, hang = kotlinx.coroutines.CompletableDeferred())
        go { coordinator.start("n1") }
        val working = coordinator.state.value as ReadingUiState.Working
        assertEquals(SpeechProvider.GEMINI, working.provider)
        assertEquals(0, working.done)
        go { coordinator.choosePhoneVoice() }
        val done = coordinator.state.value as ReadingUiState.Finished
        assertEquals(SpeechProvider.LOCAL, done.provider)
        assertEquals(listOf("h1", "h2"), host.local.made)
    }

    @Test fun `Smart Read may use a text model only where the cloud is allowed`() = scope.runTest {
        go { coordinator.start("n1") }
        assertEquals(true, host.shaperAllowCloud)
        host.shaperAllowCloud = null
        host.env = online.copy(profile = ProcessingProfile.OFFLINE)
        go { coordinator.start("n1") }
        assertEquals(false, host.shaperAllowCloud)
        host.shaperAllowCloud = null
        host.env = online
        host.note = loaded(facts.copy(notePrivate = true))
        go { coordinator.start("n1") }
        assertEquals(false, host.shaperAllowCloud)
    }

    @Test fun `Read Exactly never builds the wording step`() = scope.runTest {
        host.settings = host.settings.copy(smartRead = false)
        host.shaperAllowCloud = null
        go { coordinator.start("n1") }
        assertEquals(null, host.shaperAllowCloud)
        assertEquals("exact", host.store.load("n1")!!.mode)
    }

    @Test fun `phone voice choice with disclosure not accepted keeps Smart Read off the cloud`() = scope.runTest {
        host.settings = host.settings.copy(disclosureAccepted = false, prefersLocal = true)
        go { coordinator.start("n1") }
        assertEquals(false, host.shaperAllowCloud)
    }
}
