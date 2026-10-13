package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.core.audio.QueueProgress
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000_000L
    private lateinit var persistent: File
    private lateinit var ephemeral: File
    private lateinit var store: ReadingStore
    private val settings = FingerprintSettings("exact", SpeechProvider.LOCAL, "device", "default", "plain")

    @org.junit.Before fun setUp() {
        persistent = tmp.newFolder("p"); ephemeral = tmp.newFolder("e")
        store = ReadingStore(persistent, ephemeral) { now }
    }

    private fun pcm(ms: Int = 500) = Pcm(ShortArray(24 * ms) { (it % 50).toShort() }, 24000)

    private fun manifest(noteId: String = "n1", sections: List<SectionEntry> = emptyList(), progress: ReadingProgress? = null) = ReadingManifest(
        noteId, "exact", "LOCAL", null, "default", "plain", "en", genericTitle = false, createdAt = now, updatedAt = now,
        expectedSections = sections.size, sections = sections, progress = progress
    )

    private fun entry(id: String, fp: String = "f".repeat(16), file: String? = "x.wav") =
        SectionEntry(id, "Title $id", fp, SectionStatus.READY, file, 500, listOf("b1"), listOf("John 3:16"), ephemeral = false, pauseAfter = true)

    private fun section(text: String = "Grace is enough") =
        extract(Blocks().apply { add(HEADING_2, "Study", id = "h"); add(PARAGRAPH, text, id = "p") }).sections.single()

    // --- manifest ---

    @Test fun `a manifest survives a round trip with every field`() {
        val m = manifest(
            sections = listOf(entry("s1"), entry("s2").copy(status = SectionStatus.FAILED, error = "boom", ephemeral = true, fileName = null)),
            progress = ReadingProgress("read:n1:s1:ffff", 1, 4242, false, 77)
        ).copy(model = "gemini-3.8-flash-tts", switchedToPhoneAtSection = 3, genericTitle = true, language = null)
        store.save(m)
        assertEquals(m, store.load("n1"))
    }

    @Test fun `an unreadable or foreign-schema manifest is forgotten, not trusted`() {
        File(persistent, "n1").mkdirs()
        File(persistent, "n1/manifest.json").writeText("{ not json")
        assertNull(store.load("n1"))
        assertFalse(File(persistent, "n1").exists())

        store.save(manifest())
        val f = File(persistent, "n1/manifest.json")
        f.writeText(f.readText().replace("\"schema\":1", "\"schema\":99"))
        assertNull(store.load("n1"))
    }

    @Test fun `saving leaves no temporary file behind`() {
        store.save(manifest())
        assertEquals(listOf("manifest.json"), File(persistent, "n1").list()!!.toList())
    }

    // --- audio ---

    @Test fun `committed audio is a ready section whose file exists`() {
        val e = store.commitAudio("n1", "s1", "Study", "a".repeat(32), pcm(), listOf("b1"), emptyList(), ephemeral = false, pauseAfter = false)
        assertEquals(SectionStatus.READY, e.status)
        assertEquals(500L, e.durationMs)
        assertNotNull(store.audioFile("n1", e))
        assertTrue(e.fileName!!.startsWith("s1-" + "a".repeat(12)))
    }

    @Test fun `licensed-text audio goes to the short-lived area`() {
        val e = store.commitAudio("n1", "s1", "Study", "a".repeat(32), pcm(), emptyList(), emptyList(), ephemeral = true, pauseAfter = false)
        assertTrue(e.ephemeral)
        assertTrue(store.audioFile("n1", e)!!.absolutePath.startsWith(ephemeral.absolutePath))
        assertTrue(File(persistent, "n1").list().isNullOrEmpty())
    }

    // --- freshness ---

    @Test fun `audio is fresh while the note is unchanged, stale after an edit, missing when the file is gone`() {
        val sec = section()
        val fp = ReadFingerprint.section(sec, settings)
        val e = store.commitAudio("n1", sec.id, sec.title, fp, pcm(), sec.blockIds, sec.references, false, false)
        val m = manifest(sections = listOf(e))
        val source = SpeechSource(listOf(sec))
        assertEquals(listOf(Freshness.FRESH), store.freshness(m, source, settings))

        val edited = SpeechSource(listOf(section("Grace is enough, edited")))
        assertEquals(listOf(Freshness.STALE), store.freshness(m, edited, settings))

        assertEquals(listOf(Freshness.STALE), store.freshness(m, source, settings.copy(voiceId = "other")))

        store.audioFile("n1", e)!!.delete()
        assertEquals(listOf(Freshness.MISSING), store.freshness(m, source, settings))
    }

    @Test fun `with no saved reading every section is missing, and failed sections do not count`() {
        val sec = section()
        assertEquals(listOf(Freshness.MISSING), store.freshness(null, SpeechSource(listOf(sec)), settings))
        val failed = entry(sec.id, ReadFingerprint.section(sec, settings)).copy(status = SectionStatus.FAILED)
        assertEquals(listOf(Freshness.MISSING), store.freshness(manifest(sections = listOf(failed)), SpeechSource(listOf(sec)), settings))
    }

    // --- progress ---

    @Test fun `progress is saved into the manifest`() {
        store.save(manifest())
        now += 5000
        store.saveProgress(QueueProgress("read:n1", "read:n1:s2:abc", 2, 9000, false))
        val p = store.load("n1")!!.progress!!
        assertEquals(2, p.sectionIndex); assertEquals(9000L, p.positionMs); assertEquals("read:n1:s2:abc", p.mediaId); assertEquals(now, p.updatedAt)
    }

    @Test fun `progress for other queues or unknown notes is ignored`() {
        store.saveProgress(QueueProgress("devotional:n1", "m", 0, 1, false))
        store.saveProgress(QueueProgress("read:ghost", "m", 0, 1, false))
        assertTrue(persistent.list().isNullOrEmpty())
    }

    @Test fun `the last played reading is the one with the newest progress`() {
        store.save(manifest("a")); store.save(manifest("b")); store.save(manifest("c"))
        assertNull(store.lastPlayed())
        now += 1; store.saveProgress(QueueProgress("read:a", "m", 0, 1, false))
        now += 1; store.saveProgress(QueueProgress("read:b", "m", 0, 1, false))
        now += 1; store.saveProgress(QueueProgress("read:a", "m", 0, 2, false))
        assertEquals("a", store.lastPlayed()!!.noteId)
    }

    // --- cleanup ---

    @Test fun `deleting a note's reading removes audio from both areas`() {
        val a = store.commitAudio("n1", "s1", "T", "a".repeat(32), pcm(), emptyList(), emptyList(), false, false)
        val b = store.commitAudio("n1", "s2", "T", "b".repeat(32), pcm(), emptyList(), emptyList(), true, false)
        store.save(manifest(sections = listOf(a, b)))
        store.delete("n1")
        assertNull(store.load("n1"))
        assertEquals(0L, store.usedBytes())
    }

    @Test fun `trimming removes the least recently played first and protects the current reading`() {
        listOf("old", "mid", "new").forEachIndexed { i, id ->
            val e = store.commitAudio(id, "s", "T", "c".repeat(32), pcm(2000), emptyList(), emptyList(), false, false)
            now = 1000L * (i + 1)
            store.save(manifest(id, listOf(e)))
            store.saveProgress(QueueProgress("read:$id", e.fileName!!, 0, 1, false))
        }
        val perReading = store.usedBytes() / 3
        val removed = store.trimTo(maxBytes = perReading * 2 + perReading / 2)
        assertEquals(1, removed)
        assertNull(store.load("old"))
        assertNotNull(store.load("mid")); assertNotNull(store.load("new"))

        val protectedRun = store.trimTo(maxBytes = 0, protectNoteId = "new")
        assertEquals(1, protectedRun) // "mid" goes, "new" stays
        assertNotNull(store.load("new"))
    }

    @Test fun `clearing removes everything`() {
        store.save(manifest("a")); store.save(manifest("b"))
        store.clearAll()
        assertNull(store.lastPlayed()); assertNull(store.load("a"))
    }

    @Test fun `short-lived audio expires`() {
        val e = store.commitAudio("n1", "s1", "T", "a".repeat(32), pcm(), emptyList(), emptyList(), ephemeral = true, pauseAfter = false)
        val f = store.audioFile("n1", e)!!
        val day = 24L * 60 * 60 * 1000
        now = 10 * day
        f.setLastModified(now - 2 * day)
        assertEquals(1, store.purgeExpiredEphemeral(day))
        assertNull(store.audioFile("n1", e))

        val fresh = store.commitAudio("n1", "s2", "T", "b".repeat(32), pcm(), emptyList(), emptyList(), ephemeral = true, pauseAfter = false)
        store.audioFile("n1", fresh)!!.setLastModified(now)
        assertEquals(0, store.purgeExpiredEphemeral(day))
    }

    // --- ids ---

    @Test fun `media ids change with the content and queue ids round-trip`() {
        assertEquals("n1", ReadingStore.noteIdOf(ReadingStore.queueId("n1")))
        assertNull(ReadingStore.noteIdOf("devotional:n1"))
        assertNull(ReadingStore.noteIdOf("read:"))
        assertTrue(ReadingStore.mediaId("n1", "s1", "a".repeat(32)) != ReadingStore.mediaId("n1", "s1", "b".repeat(32)))
    }

    // --- resume after the process was killed ---

    private fun readyManifest(titleGeneric: Boolean = false, progress: ReadingProgress? = null): ReadingManifest {
        val e1 = store.commitAudio("n1", "s1", "Passage", "a".repeat(32), pcm(60_000), emptyList(), emptyList(), false, false)
        val e2 = store.commitAudio("n1", "s2", "Prayer", "b".repeat(32), pcm(60_000), emptyList(), emptyList(), false, false)
        val gone = SectionEntry("s3", "Gone", "c".repeat(32), SectionStatus.READY, "missing.m4a", 1, emptyList(), emptyList(), false, false)
        return manifest(sections = listOf(e1, e2, gone), progress = progress).copy(genericTitle = titleGeneric).also { store.save(it) }
    }

    @Test fun `resume rebuilds the queue and lands where the person stopped`() {
        val m = readyManifest(progress = ReadingProgress(ReadingStore.mediaId("n1", "s2", "b".repeat(32)), 1, 20_000, false, 5))
        val r = runBlocking { ReadingResumptionSource(store).last() }!!
        assertEquals(2, r.items.size) // the section whose file is gone is left out
        assertEquals(ReadingStore.mediaId("n1", "s1", "a".repeat(32)), r.items[0].mediaId)
        assertEquals(1, r.startIndex)
        assertEquals(20_000L, r.startPositionMs)
        assertEquals("Passage", r.items[0].mediaMetadata.title.toString())
        assertNotNull(m)
    }

    @Test fun `resume at the end of a part moves on to the next`() {
        readyManifest(progress = ReadingProgress(ReadingStore.mediaId("n1", "s1", "a".repeat(32)), 0, 59_500, false, 5))
        val r = runBlocking { ReadingResumptionSource(store).last() }!!
        assertEquals(1, r.startIndex); assertEquals(0L, r.startPositionMs)
    }

    @Test fun `a private reading resumes under a generic title`() {
        readyManifest(titleGeneric = true, progress = ReadingProgress(null, 0, 0, false, 5))
        val r = runBlocking { ReadingResumptionSource(store).last() }!!
        assertTrue(r.items.all { it.mediaMetadata.title.toString() == "Read to Me" })
    }

    @Test fun `nothing played means nothing to resume`() {
        readyManifest(progress = null)
        assertNull(runBlocking { ReadingResumptionSource(store).last() })
    }
}
