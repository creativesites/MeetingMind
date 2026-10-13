package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk
import com.craftflowtechnologies.meetingmind.core.audio.QueueItem
import com.craftflowtechnologies.meetingmind.core.audio.QueueProgress
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private class FakePlayer : ReadPlayer {
    val plays = mutableListOf<PlayCall>()
    val appended = mutableListOf<QueueItem>()
    var expected: Int? = null
    data class PlayCall(val queueId: String, val items: List<QueueItem>, val startIndex: Int, val positionMs: Long, val expected: Int)
    override fun play(queueId: String, items: List<QueueItem>, startIndex: Int, startPositionMs: Long, expected: Int) { plays += PlayCall(queueId, items, startIndex, startPositionMs, expected) }
    override fun append(item: QueueItem) { appended += item }
    override fun setExpected(count: Int) { expected = count }
}

private class FakeVoice(
    override val provider: SpeechProvider = SpeechProvider.LOCAL,
    override val voiceId: String = "v1",
    val failOn: MutableSet<String> = mutableSetOf(),
    val hang: CompletableDeferred<Unit>? = null
) : SectionVoice {
    override val fingerprintModel = "fake"
    val made = mutableListOf<String>()
    val spoken = mutableMapOf<String, List<String>>()
    val discarded = mutableListOf<String>()
    override suspend fun synthesize(partId: String, fingerprint: String, chunks: List<SpeechChunk>, onProgress: (Float) -> Unit): VoiceResult {
        hang?.await()
        if (partId in failOn) return VoiceResult.Failed("could not read $partId", RecoveryPlan("It stopped.", listOf(Recovery.RETRY)))
        made += partId
        spoken[partId] = chunks.map { it.text }
        return VoiceResult.Done(Pcm(ShortArray(12000) { (it % 40).toShort() }, 24000))
    }
    override fun discard(partId: String) { discarded += partId }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingRunnerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var store: ReadingStore
    private lateinit var player: FakePlayer
    private lateinit var runner: ReadingRunner
    private lateinit var persistent: File
    private lateinit var ephemeral: File

    @Before fun setUp() {
        persistent = tmp.newFolder("p"); ephemeral = tmp.newFolder("e")
        store = ReadingStore(persistent, ephemeral)
        player = FakePlayer()
        runner = ReadingRunner(store, player)
    }

    private fun blocks(secondText: String = "Second section text.") = Blocks().apply {
        add(HEADING_2, "One", id = "h1"); add(PARAGRAPH, "First section text.", id = "p1")
        add(HEADING_2, "Two", id = "h2"); add(PARAGRAPH, secondText, id = "p2")
        add(HEADING_2, "Three", id = "h3"); add(PARAGRAPH, "Third section text.", id = "p3")
    }

    private fun plan(
        b: Blocks = blocks(), generic: Boolean = false, ephemeral: Boolean = false,
        settings: ReadToMeSettings = ReadToMeSettings(), split: Boolean = false
    ): ReadPlan {
        val parts = ReadingPlan.parts(extract(b), split)
        return ReadPlan("n1", parts, "exact", DeliverySpec(DeliveryPreset.WARM_COMPANION, 3, 1f), "en", generic, ephemeral, settings)
    }

    private fun run(plan: ReadPlan, voice: SectionVoice, resume: ResumeTarget? = null, already: Int = 0) =
        runBlocking { runner.run(plan, voice, resume, already) }

    @Test fun `parts are made in order, the first starts playback and the rest are appended`() {
        val voice = FakeVoice()
        val outcome = run(plan(), voice)
        assertEquals(RunOutcome.Completed(3), outcome)
        assertEquals(listOf("h1", "h2", "h3"), voice.made)
        assertEquals(1, player.plays.size)
        val call = player.plays.single()
        assertEquals("read:n1", call.queueId); assertEquals(1, call.items.size); assertEquals(0, call.startIndex); assertEquals(3, call.expected)
        assertEquals(listOf("Two", "Three"), player.appended.map { it.title })
        assertEquals(3, player.expected)
    }

    @Test fun `what is spoken is exactly the note's words with the fixed labels`() {
        val voice = FakeVoice()
        run(plan(), voice)
        assertEquals(listOf("One", "First section text."), voice.spoken["h1"])
    }

    @Test fun `every part is saved and the manifest is complete`() {
        run(plan(), FakeVoice())
        val m = store.load("n1")!!
        assertEquals(listOf(SectionStatus.READY, SectionStatus.READY, SectionStatus.READY), m.sections.map { it.status })
        assertEquals(3, m.expectedSections)
        assertEquals("LOCAL", m.provider)
        m.sections.forEach { assertNotNull(store.audioFile("n1", it)) }
    }

    @Test fun `a second run reuses everything that is still current`() {
        run(plan(), FakeVoice())
        player.plays.clear(); player.appended.clear()
        val again = FakeVoice()
        assertEquals(RunOutcome.Completed(3), run(plan(), again))
        assertTrue(again.made.isEmpty())
        assertEquals(1, player.plays.size); assertEquals(2, player.appended.size)
    }

    @Test fun `editing one section regenerates only that section`() {
        run(plan(), FakeVoice())
        val again = FakeVoice()
        run(plan(blocks("Second section, edited.")), again)
        assertEquals(listOf("h2"), again.made)
    }

    @Test fun `changing the voice or delivery regenerates everything`() {
        run(plan(), FakeVoice())
        val other = FakeVoice(voiceId = "v2")
        run(plan(), other)
        assertEquals(listOf("h1", "h2", "h3"), other.made)
        val delivered = FakeVoice()
        run(plan().copy(delivery = DeliverySpec(DeliveryPreset.CALM_TEACHER, 3, 1f)), delivered)
        assertEquals(3, delivered.made.size)
    }

    @Test fun `old audio is cleaned up after an edit`() {
        run(plan(), FakeVoice())
        val before = File(persistent, "n1").list()!!.filter { it != "manifest.json" }.toSet()
        run(plan(blocks("Second section, edited.")), FakeVoice())
        val after = File(persistent, "n1").list()!!.filter { it != "manifest.json" }.toSet()
        assertEquals(3, after.size)
        assertEquals(2, before.intersect(after).size) // the two untouched parts kept, the edited one replaced
    }

    @Test fun `a failure stops at that part, keeps what is done and queued, and tries nothing else`() {
        val voice = FakeVoice(failOn = mutableSetOf("h2"))
        val outcome = run(plan(), voice) as RunOutcome.Failed
        assertEquals(1, outcome.partIndex)
        assertTrue(outcome.recovery.options.contains(Recovery.RETRY))
        assertEquals(listOf("h1"), voice.made) // the third part was never attempted
        assertEquals(1, player.plays.size); assertTrue(player.appended.isEmpty())
        val m = store.load("n1")!!
        assertEquals(listOf(SectionStatus.READY, SectionStatus.FAILED, SectionStatus.PENDING), m.sections.map { it.status })
        assertEquals("could not read h2", m.sections[1].error)
        assertNull(player.expected) // the reading is not complete
    }

    @Test fun `a failure on the first part never starts playback`() {
        val outcome = run(plan(), FakeVoice(failOn = mutableSetOf("h1")))
        assertTrue(outcome is RunOutcome.Failed)
        assertTrue(player.plays.isEmpty())
    }

    @Test fun `retrying after a failure carries on without redoing finished parts`() {
        run(plan(), FakeVoice(failOn = mutableSetOf("h2")))
        player.plays.clear()
        val retry = FakeVoice()
        assertEquals(RunOutcome.Completed(3), run(plan(), retry))
        assertEquals(listOf("h2", "h3"), retry.made)
    }

    @Test fun `cancelling keeps the finished parts and rethrows`() = runTest {
        val hang = CompletableDeferred<Unit>()
        val voice = FakeVoice(hang = hang)
        val job = launch { runner.run(plan(), voice) }
        testScheduler.advanceUntilIdle()
        job.cancel()
        testScheduler.advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertTrue(player.plays.isEmpty())
        assertNotNull(store.load("n1")) // a manifest exists, nothing half-written
    }

    @Test fun `cancelling after some parts keeps them`() {
        val gate = CompletableDeferred<Unit>()
        val voice = object : SectionVoice by FakeVoice() {
            override suspend fun synthesize(partId: String, fingerprint: String, chunks: List<SpeechChunk>, onProgress: (Float) -> Unit): VoiceResult {
                if (partId == "h2") gate.await()
                return VoiceResult.Done(Pcm(ShortArray(12000) { 1 }, 24000))
            }
        }
        runBlocking {
            val job = launch(kotlinx.coroutines.Dispatchers.Default) { runner.run(plan(), voice) }
            while (store.load("n1")?.sections?.firstOrNull()?.status != SectionStatus.READY) kotlinx.coroutines.delay(20)
            job.cancel(); job.join()
        }
        assertEquals(SectionStatus.READY, store.load("n1")!!.sections[0].status)
        assertEquals(1, player.plays.size)
    }

    @Test fun `resuming starts at the saved part and position when earlier parts are still current`() {
        run(plan(), FakeVoice())
        player.plays.clear(); player.appended.clear()
        val again = FakeVoice()
        run(plan(), again, resume = ResumeTarget(partIndex = 1, positionMs = 4_200))
        assertTrue(again.made.isEmpty())
        val call = player.plays.single()
        assertEquals(1, call.startIndex); assertEquals(4_200L, call.positionMs)
        assertEquals(2, call.items.size) // parts 0 and 1 are queued so Previous works
        assertEquals(listOf("Three"), player.appended.map { it.title })
    }

    @Test fun `resuming is ignored when an earlier part has changed, so the person starts from the top`() {
        run(plan(), FakeVoice())
        player.plays.clear()
        run(plan(), FakeVoice(voiceId = "other"), resume = ResumeTarget(2, 1000))
        assertEquals(0, player.plays.single().startIndex)
        assertEquals(0L, player.plays.single().positionMs)
    }

    @Test fun `a switch to the phone voice redoes only the parts not yet queued and says so`() {
        val gemini = FakeVoice(provider = SpeechProvider.GEMINI, voiceId = "Kore", failOn = mutableSetOf("h3"))
        run(plan(), gemini)
        player.plays.clear(); player.appended.clear()
        val phone = FakeVoice(provider = SpeechProvider.LOCAL, voiceId = "default")
        val outcome = run(plan(), phone, already = 2)
        assertEquals(RunOutcome.Completed(3), outcome)
        assertEquals(listOf("h3"), phone.made)
        assertTrue(player.plays.isEmpty())
        assertEquals(listOf("Three"), player.appended.map { it.title })
        val m = store.load("n1")!!
        assertEquals(2, m.switchedToPhoneAtSection)
        assertEquals("LOCAL", m.provider)
    }

    @Test fun `private readings are queued and saved under a generic title`() {
        run(plan(generic = true), FakeVoice())
        assertTrue(player.plays.single().items.all { it.title == "Read to Me" })
        assertTrue(player.appended.all { it.title == "Read to Me" })
        assertTrue(store.load("n1")!!.genericTitle)
    }

    @Test fun `audio of licensed text goes to the short-lived area`() {
        run(plan(ephemeral = true), FakeVoice())
        val m = store.load("n1")!!
        assertTrue(m.sections.all { it.ephemeral })
        assertTrue(File(ephemeral, "n1").list()!!.isNotEmpty())
        assertTrue(File(persistent, "n1").list()!!.toList() == listOf("manifest.json"))
    }

    @Test fun `a reflection question can pause the queue at the end of its part`() {
        val b = Blocks().apply {
            add(HEADING_2, "Reflection", "reflection_questions", id = "h"); add(BULLET, "What stands out?", "reflection_questions", id = "q")
            add(HEADING_2, "Prayer", "prayer", id = "h2"); add(PARAGRAPH, "Lord, help us", "prayer", edited = true, id = "p")
        }
        run(plan(b, settings = ReadToMeSettings(pauseAfterQuestion = PauseAfterQuestion.UNTIL_RESUME), split = true), FakeVoice())
        val items = player.plays.single().items + player.appended
        assertEquals(listOf(true, false), items.map { it.pauseAfter })
    }

    @Test fun `where the person is stays saved while a reading is regenerated`() {
        run(plan(), FakeVoice())
        store.saveProgress(QueueProgress("read:n1", "x", 1, 7_000, false))
        run(plan(blocks("Second section, edited.")), FakeVoice())
        assertEquals(7_000L, store.load("n1")!!.progress!!.positionMs)
    }

    @Test fun `scripture is spoken from the resolved words`() {
        val b = Blocks().apply { scripture("John 3:16-17", id = "s1") }
        val src = extract(b, lookupOf("John 3:16-17" to john316))
        val voice = FakeVoice()
        run(ReadPlan("n1", ReadingPlan.parts(src, false), "exact", DeliverySpec(DeliveryPreset.AUTO, 3, 1f), "en", false, false, ReadToMeSettings()), voice)
        val spoken = voice.spoken.values.single().joinToString(" ")
        assertTrue(spoken.contains("For God so loved the world that He gave His one and only Son"))
        assertTrue(spoken.startsWith("Scripture. John chapter 3, verses 16 to 17. Berean Standard Bible."))
    }

    @Test fun `nothing speakable means nothing is generated`() {
        val voice = FakeVoice()
        val empty = ReadPlan("n1", emptyList(), "exact", DeliverySpec(DeliveryPreset.AUTO, 3, 1f), null, false, false, ReadToMeSettings())
        assertEquals(RunOutcome.Completed(0), run(empty, voice))
        assertTrue(voice.made.isEmpty()); assertTrue(player.plays.isEmpty())
        assertFalse(File(persistent, "n1").exists() && File(persistent, "n1").list()!!.any { it.endsWith(".m4a") })
    }
}
