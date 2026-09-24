package com.example.core.devotional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.devotional.DevotionalAsk
import com.example.ai.devotional.DevotionalUnavailable
import com.example.ai.devotional.DevotionalWriter
import com.example.core.database.MeetMindDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Several devotionals a day, written only when asked, and never lost to a failed write. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevotionalDayTest {
    private lateinit var db: MeetMindDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val date = LocalDate.now()
    private val day = LocalDay.of(date)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        ClassicDevotionals.useForTest(java.io.File("src/main/assets/${ClassicDevotionals.ASSET}").inputStream().use { ClassicDevotionals.parse(java.util.zip.GZIPInputStream(it)) })
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close(); ClassicDevotionals.useForTest(null) }

    @Test fun `new ones are added to the day, a failed one changes nothing, and any can become today's`() = runBlocking {
        val repo = DevotionalRepository(context, db)
        val first = repo.ensure(date, ask = DevotionalAsk(writer = DevotionalWriter.CLASSIC))!!
        // Asking again without "another" keeps the day's devotional: nothing is rewritten on its own.
        assertEquals(first.note.id, repo.ensure(date)!!.note.id)

        val second = repo.ensure(date, another = true, ask = DevotionalAsk(writer = DevotionalWriter.CLASSIC))!!
        assertNotEquals(first.note.id, second.note.id)
        assertEquals(second.note.id, repo.find(day)!!.note.id)
        assertEquals(listOf(first.note.id, second.note.id), repo.observeDay(day).first().map { it.note.id })

        // No phone model here: choosing "On this phone" fails plainly and leaves the day as it was.
        try {
            repo.ensure(date, another = true, ask = DevotionalAsk(writer = DevotionalWriter.DEVICE))
            fail("expected the phone writer to be unavailable")
        } catch (e: DevotionalUnavailable) {
            assertTrue(e.message!!.contains("language model"))
        }
        assertEquals(2, repo.observeDay(day).first().size)
        assertEquals(second.note.id, repo.find(day)!!.note.id)
        assertTrue(repo.observeDay(day).first().none { it.devotional.origin == DevotionalOrigin.MINE })

        repo.makeCurrent(repo.observeDay(day).first().first { it.note.id == first.note.id })
        assertEquals(first.note.id, repo.find(day)!!.note.id)
        assertEquals(2, repo.observeDay(day).first().size)
    }

    @Test fun `the writers on offer match what's set up`() = runBlocking {
        val w = DevotionalRepository(context, db).writersAvailable()
        assertTrue(DevotionalWriter.CLASSIC in w && DevotionalWriter.AUTO in w)
        assertTrue(DevotionalWriter.DEVICE !in w && DevotionalWriter.GEMINI !in w)
    }
}
