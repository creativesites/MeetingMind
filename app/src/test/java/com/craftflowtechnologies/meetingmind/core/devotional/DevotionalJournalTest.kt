package com.craftflowtechnologies.meetingmind.core.devotional

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalBrief
import com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalContract
import com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalEngine
import com.craftflowtechnologies.meetingmind.ai.devotional.ModelCandidate
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.biblestudy.JournalMeta
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionKind
import com.craftflowtechnologies.meetingmind.core.biblestudy.StudyJournal
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.zip.GZIPInputStream

/** D-1: "Live it today", and the devotional reflection that lives in the study journal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevotionalJournalTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var db: MeetMindDatabase
    private lateinit var notes: NoteRepository
    private lateinit var repo: DevotionalRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        notes = NoteRepository(app, db)
        repo = DevotionalRepository(app, db, notes)
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    private val classics = File("src/main/assets/${ClassicDevotionals.ASSET}").inputStream().use { ClassicDevotionals.parse(GZIPInputStream(it)) }

    private fun devotional(day: String = "2026-09-24", title: String = "Rest for the weary", live: String? = "Put your phone down for ten minutes and breathe.") = Devotional(
        day = LocalDay(day), origin = DevotionalOrigin.CLOUD_AI, title = title,
        scripture = listOf(ScriptureReferenceParser.parse("Matthew 11:28-30")!!),
        reflection = listOf("Jesus calls the tired."), liveItToday = live,
        prayer = "Lord, teach me to rest. Amen.", question = "Where do you need rest?", label = DevotionalLabels.CLOUD
    )

    /** Saves [d] as a devotional note, the way the repository does, and reads it back. */
    private fun saved(d: Devotional): DailyDevotional = runBlocking {
        val note = notes.createNote(
            workflow = RecordingType.DEVOTIONAL, title = d.title, metadata = DevotionalNotes.metadata(d),
            initialBlocks = listOf(com.craftflowtechnologies.meetingmind.core.model.NoteBlock("tmp", "tmp", 0, com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH)),
            useTemplate = false
        )
        val (blocks, refs) = DevotionalNotes.build(note.id, d)
        notes.saveBlocks(note.id, blocks)
        notes.addScriptureRefs(refs)
        repo.byId(note.id)!!
    }

    private fun journal() = runBlocking { StudyJournal(notes).observe().first() }

    // ------------------------------------------------------------------ Live it today

    @Test fun `Live it today is read from the answer, one line, and an old answer without it still parses`() {
        val withIt = DevotionalContract.parse("""{"title":"T","reflection":["R"],"liveItToday":"Call your sister today.\nAnd then some more."}""")!!
        assertEquals("Call your sister today.", withIt.liveItToday)
        val without = DevotionalContract.parse("""{"title":"T","reflection":["R"],"application":["Take a walk"]}""")!!
        assertNull(without.liveItToday)
        assertEquals(listOf("Take a walk"), without.application)
    }

    @Test fun `the prompt asks for one Live it today line`() {
        val brief = DevotionalBrief(ScriptureReferenceParser.parse("Psalm 23")!!, null, DevotionalProfile(), null, "Friday", emptyList(), null)
        assertTrue(DevotionalContract.prompt(brief).contains("\"liveItToday\""))
    }

    @Test fun `an AI devotional keeps its line, and one that left it out shows none rather than an invented one`() = runBlocking {
        class Fake(val answer: String) : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> = AiResult.Success(answer)
        }
        fun engine(answer: String) = DevotionalEngine({ listOf(ModelCandidate(Fake(answer), "gemini-test", true)) }, { null }, classics, emptyList())
        val date = LocalDate.of(2026, 9, 24)
        val with = engine("""{"title":"T","reflection":["R one."],"liveItToday":"Send one kind message today."}""").write(date, DevotionalProfile())
        assertEquals("Send one kind message today.", with.liveItToday)
        val without = engine("""{"title":"T","reflection":["R one."]}""").write(date, DevotionalProfile())
        assertEquals(DevotionalOrigin.CLOUD_AI, without.origin)
        assertNull(without.liveItToday)
    }

    @Test fun `a classic, or anything read offline, gets a curated fallback line`() {
        val engine = DevotionalEngine({ emptyList() }, { null }, classics, emptyList())
        val date = LocalDate.of(2026, 9, 24)
        val c = engine.classic(date, false, DevotionalProfile())!!
        assertEquals(LiveItToday.fallback(LocalDay.of(date)), c.liveItToday)
        assertTrue(c.liveItToday in LiveItToday.FALLBACKS)
        // Steady within a day, different on the next.
        assertEquals(LiveItToday.fallback(LocalDay("2026-09-24")), LiveItToday.fallback(LocalDay("2026-09-24")))
        assertNotEquals(LiveItToday.fallback(LocalDay("2026-09-24")), LiveItToday.fallback(LocalDay("2026-09-25")))
        assertTrue(LiveItToday.FALLBACKS.all { it.length in 20..110 && '\n' !in it })
    }

    @Test fun `the line is stored as its own block, reads back, copies, and old notes without it render`() {
        val d = devotional()
        val (blocks, _) = DevotionalNotes.build("n1", d)
        val note = com.craftflowtechnologies.meetingmind.core.model.Note("n1", d.title, RecordingType.DEVOTIONAL, null, 0, 0, 0, false, false, com.craftflowtechnologies.meetingmind.core.model.NoteStatus.OPEN, null, DevotionalNotes.metadata(d))
        val doc = com.craftflowtechnologies.meetingmind.core.model.NoteDocument(note, blocks, emptyList(), emptyList(), emptyList())
        assertEquals(d.liveItToday, DevotionalNotes.read(doc)!!.liveItToday)
        assertEquals(d.liveItToday, DevotionalText.block(d, DevotionalBlock.LIVE))
        assertTrue(DevotionalText.all(d).contains("LIVE IT TODAY\n${d.liveItToday}"))
        // A devotional saved before the block existed: no line, still reads, still copies.
        val old = d.copy(liveItToday = null, application = listOf("Take a walk"))
        val (oldBlocks, _) = DevotionalNotes.build("n2", old)
        val oldDoc = doc.copy(blocks = oldBlocks)
        val back = DevotionalNotes.read(oldDoc)!!
        assertNull(back.liveItToday)
        assertEquals(listOf("Take a walk"), back.application)
        assertNull(DevotionalText.block(old, DevotionalBlock.LIVE))
        assertTrue(DevotionalText.all(old).contains("TODAY I WILL"))
    }

    // ------------------------------------------------------------------ the journal

    @Test fun `an untouched devotional leaves nothing, and the first keystroke creates the entry with the words in it`() = runBlocking {
        val daily = saved(devotional())
        assertEquals("nothing is made on opening", 0, journal().size)
        repo.saveReflection(daily, "")
        repo.saveReflection(daily, "   ")
        assertTrue("blank text makes no note", journal().isEmpty())
        assertNull(repo.journalFor(daily.note.id))

        repo.saveReflection(daily, "R")
        val entry = repo.journalFor(daily.note.id)
        assertNotNull(entry)
        assertEquals(RecordingType.BIBLE_STUDY, entry!!.workflow)
        assertEquals(SessionKind.DEVOTIONAL.key, entry.metadata[JournalMeta.SESSION_KIND])
        assertEquals(ScriptureReferenceParser.parse("Matthew 11:28-30")!!.passageId(), entry.metadata[JournalMeta.PASSAGE_REF])
        assertEquals(daily.note.id, entry.metadata[JournalMeta.DEVOTIONAL_NOTE])
        val doc = notes.getDocument(entry.id)!!
        assertTrue("no blank paragraph", doc.blocks.none { it.type == com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH && it.content.text.isBlank() })
        assertEquals("R", DevotionalJournal.textOf(doc.blocks))
        assertTrue("it answers the devotional's question", doc.blocks.any { it.content.text == "Where do you need rest?" })
        assertEquals(1, journal().size)
    }

    @Test fun `more typing updates the same entry, and reopening shows it and continues it`() = runBlocking {
        val daily = saved(devotional())
        repo.saveReflection(daily, "Tired")
        repo.saveReflection(daily, "Tired, and glad to be told it's allowed.")
        val first = repo.journalFor(daily.note.id)!!
        assertEquals("Tired, and glad to be told it's allowed.", repo.reflectionOf(daily))

        // A new repository (the screen was closed and reopened) finds it and keeps editing the same note.
        val reopened = DevotionalRepository(app, db, notes)
        val again = reopened.byId(daily.note.id)!!
        assertEquals("Tired, and glad to be told it's allowed.", reopened.reflectionOf(again))
        reopened.saveReflection(again, "Tired, and glad to be told it's allowed.\nRest first.")
        assertEquals(first.id, reopened.journalFor(daily.note.id)!!.id)
        assertEquals(1, journal().size)
        assertEquals("Tired, and glad to be told it's allowed.\nRest first.", reopened.reflectionOf(again))
    }

    @Test fun `the devotional's journal entry shows in the one study journal, labelled Devotional`() = runBlocking {
        val daily = saved(devotional())
        repo.saveReflection(daily, "Held.")
        val entry = journal().single()
        assertTrue(entry.title, entry.title.endsWith("· Devotional"))
        assertTrue(DevotionalJournal.isReflection(entry))
    }

    @Test fun `clearing the reflection clears the entry rather than leaving stale words`() = runBlocking {
        val daily = saved(devotional())
        repo.saveReflection(daily, "Something")
        repo.saveReflection(daily, "")
        assertEquals("", repo.reflectionOf(daily))
    }

    @Test fun `a response written before reflections moved to the journal is still shown`() = runBlocking {
        val daily = saved(devotional())
        repo.saveResponse(daily, "Old words.")
        val again = repo.byId(daily.note.id)!!
        assertEquals("Old words.", repo.reflectionOf(again))
    }

    // ------------------------------------------------------------------ the archive

    @Test fun `the archive maps each devotional to date, passage, title and the first line of its reflection`() = runBlocking {
        val a = saved(devotional("2026-09-24", "Rest for the weary"))
        val b = saved(devotional("2026-09-25", "Bread for today"))
        repo.saveReflection(a, "First line of mine.\nSecond line.")
        val items = repo.archive()
        assertEquals(listOf("2026-09-25", "2026-09-24"), items.map { it.day })
        val withIt = items.first { it.noteId == a.note.id }
        assertEquals("Rest for the weary", withIt.title)
        assertEquals("Matthew 11:28–30", withIt.passage)
        assertEquals("First line of mine.", withIt.reflectionLine)
        assertTrue(withIt.hasReflection)
        val without = items.first { it.noteId == b.note.id }
        assertNull(without.reflectionLine)
        assertFalse(without.hasReflection)
        // The filter keeps only days with a reflection; search finds the person's own words.
        assertEquals(listOf(a.note.id), items.filter { com.craftflowtechnologies.meetingmind.feature.devotional.ArchiveFilter.Reflections.accepts(it) }.map { it.noteId })
        assertEquals(listOf(a.note.id), items.filter { it.matches("second line") }.map { it.noteId })
    }

    // ------------------------------------------------------------------ Personal touch

    @Test fun `what the person writes in a reflection is never fed back into a devotional's prompt`() = runBlocking {
        val secret = "Quincunx-reflection: I am afraid about the biopsy"
        val daily = saved(devotional())
        repo.saveReflection(daily, secret)
        val signals = repo.signals(LocalDate.of(2026, 9, 25))
        val all = signals.general + signals.private
        assertTrue("signals: $all", all.none { it.contains("Quincunx") || it.contains("biopsy") })

        // And with the Personal touch off, even signals handed to the prompt are not written into it.
        val brief = DevotionalBrief(
            ScriptureReferenceParser.parse("Psalm 23")!!, null, DevotionalProfile(personalTouch = PersonalTouch.OFF, aboutMe = "Flamingo-aboutme"),
            null, "Friday", listOf("Wrote: $secret"), "Ottoline", date = LocalDate.of(2026, 9, 25)
        )
        val prompt = DevotionalContract.prompt(brief)
        assertFalse(prompt.contains("Quincunx")); assertFalse(prompt.contains("biopsy")); assertFalse(prompt.contains("Flamingo"))
    }
}
