package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import java.util.Calendar

/** Memory: counts and lists from the database; a cited story per month, written only when missing or when the month's item count changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryStoryCacheTest {
    private lateinit var f: PulseFixture
    private lateinit var memory: MemoryRepository
    private lateinit var models: FakeWorkModels

    private fun at(month: Int, day: Int) = Calendar.getInstance().apply { clear(); set(2026, month, day, 12, 0) }.timeInMillis

    @Before fun setup() {
        f = PulseFixture()
        models = FakeWorkModels(FakeWorkModel("{}"))
        memory = MemoryRepository(f.db, models, clock = { f.now }, zone = java.util.TimeZone.getDefault().toZoneId())
        // September: two items change. October: one.
        f.clockValue = at(Calendar.SEPTEMBER, 10); val a = f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Launch on October 14", quote = "We launch October 14.", startMs = 1000)
        f.clockValue = at(Calendar.SEPTEMBER, 20); val b = f.theirs("API docs")
        f.clockValue = at(Calendar.OCTOBER, 3); val c = f.item(ItemKind.RISK, ItemStatus.OPEN, "Vendor late")
        f.clockValue = f.now
        ids = listOf(a.id, b.id, c.id)
        fake.reply = "{\"story\":[{\"text\":\"Launch was set.\",\"cites\":[\"${a.id}\"]},{\"text\":\"Uncited claim.\",\"cites\":[]}]}"
    }
    @After fun tearDown() { f.db.close() }

    private lateinit var ids: List<String>
    private val fake get() = models.model!!
    private fun history(tell: Boolean = true) = runBlocking { memory.history(ContextType.PROJECT, "nb", tell) }

    @Test fun countsAndListsComeFromTheDatabaseWithNoModel() {
        val h = runBlocking { MemoryRepository(f.db, null, clock = { f.now }).history(ContextType.PROJECT, "nb") }
        assertEquals(MemoryCounts(meetings = 1, decisions = 1, commitments = 1, questions = 0, risks = 1), h.counts)
        assertEquals(listOf("2026-10", "2026-09"), h.months.map { it.month })
        assertEquals(listOf(1, 2), h.months.map { it.itemCount })
        assertTrue(h.months.all { it.text == null })
        assertEquals("Vendor late", h.mostImportant.first().text)
        assertEquals(setOf("API docs", "Vendor late"), h.stillOpen.map { it.text }.toSet())
    }

    @Test fun aStoryIsWrittenPerMonthKeepsOnlyCitedSentencesAndIsKept() {
        val first = history()
        assertEquals(2, fake.prompts.size) // September and October
        assertEquals("Launch was set.", first.months.first { it.month == "2026-09" }.text)
        assertEquals(listOf(ids[0]), first.months.first { it.month == "2026-09" }.cites)
        assertTrue(fake.prompts.first().startsWith("You are working with a transcript of a real recording"))
        history(); history()
        assertEquals(2, fake.prompts.size) // nothing changed: not asked again
        assertEquals("Launch was set.", runBlocking { f.db.memoryStoryDao().get("PROJECT", "nb", "2026-09") }!!.text)
    }

    @Test fun onlyTheMonthWhoseItemCountChangedIsWrittenAgain() {
        history()
        assertEquals(2, fake.prompts.size)
        f.clockValue = at(Calendar.OCTOBER, 12); f.item(ItemKind.QUESTION, ItemStatus.OPEN, "Which region?")
        history()
        assertEquals(3, fake.prompts.size) // October only
        assertTrue(fake.prompts.last().contains("2026-10"))
        assertEquals(2, runBlocking { f.db.memoryStoryDao().get("PROJECT", "nb", "2026-10") }!!.itemCount)
    }

    @Test fun aMonthWithNothingCitableIsKeptEmptyAndNotAskedAgain() {
        fake.reply = "{\"story\":[{\"text\":\"No citation.\",\"cites\":[]}]}"
        history(); history()
        assertEquals(2, fake.prompts.size)
        assertNull(history().months.first().text)
    }

    @Test fun readingWithoutTellingNeverAsksAModel() {
        history(tell = false)
        assertTrue(fake.prompts.isEmpty())
        assertTrue(models.asked.isEmpty())
    }

    @Test fun aLongHistoryWritesOnlyTheNewestFewMonthsPerOpen() {
        for (m in listOf(Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY)) { f.clockValue = at(m, 5); f.item(ItemKind.QUESTION, ItemStatus.OPEN, "Q$m") }
        f.clockValue = f.now
        history()
        assertEquals(MemoryRepository.MAX_WRITES_PER_OPEN, fake.prompts.size)
        history()
        assertEquals(2 * MemoryRepository.MAX_WRITES_PER_OPEN, fake.prompts.size)
    }

    @Test fun sensitiveHistoryIsWrittenOnlyThroughTheSensitivePath() {
        val local = FakeWorkModels(FakeWorkModel(fake.reply))
        val sensitive = MemoryRepository(f.db, local, PackPrivacy { _, _ -> true }, { f.now })
        runBlocking { sensitive.history(ContextType.PROJECT, "nb") }
        assertTrue(local.asked.isNotEmpty() && local.asked.all { it })
    }
}
