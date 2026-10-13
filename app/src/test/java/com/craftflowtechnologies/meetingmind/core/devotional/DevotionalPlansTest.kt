package com.craftflowtechnologies.meetingmind.core.devotional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalAsk
import com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalUnavailable
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.zip.GZIPInputStream

/** Reading plans: every bundled plan is complete, the profile migrates, and a day is never empty. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevotionalPlansTest {
    private lateinit var db: MeetMindDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()
    // Today: changing plan rewrites *today's* reading (setProfile uses the clock), so the test day must be today.
    private val date = LocalDate.now()
    private val day = LocalDay.of(date)
    private var builtInKey: String? = null

    private fun load(plan: DevotionalPlan): ClassicDevotionals =
        File("src/main/assets/${plan.asset}").inputStream().use { ClassicDevotionals.parse(GZIPInputStream(it), plan) }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        // App assets aren't packaged in unit tests: hand the loader the bundled files directly.
        DevotionalPlans.available.forEach { ClassicDevotionals.useForTest(load(it)) }
        // These tests are about a phone with no AI set up. A tester key built into the app would make AI available.
        builtInKey = GeminiCredentialStore.systemKey
        GeminiCredentialStore.systemKey = null
    }

    @After fun tearDown() {
        GeminiCredentialStore.systemKey = builtInKey
        MeetMindDatabase.setInstanceForTest(null); db.close(); ClassicDevotionals.useForTest(null)
        runBlocking { UserPreferencesManager(context).setDevotionalProfile(DevotionalProfile()) }
    }

    private fun setProfile(p: DevotionalProfile) = runBlocking { UserPreferencesManager(context).setDevotionalProfile(p) }

    // ---------------------------------------------------------------- the catalogue

    @Test fun `every bundled plan has a reading for every day of the year, morning and evening`() {
        assertTrue(DevotionalPlans.available.size >= 2)
        val leapYear = generateSequence(LocalDate.of(2028, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == 2028 }.toList()
        assertEquals(366, leapYear.size)
        for (plan in DevotionalPlans.available) {
            val classics = load(plan)
            for (d in leapYear) for (evening in listOf(false, true)) {
                val r = classics.forDate(d, evening)
                assertNotNull("${plan.id} has no ${if (evening) "evening" else "morning"} reading for $d", r)
                assertTrue("${plan.id} $d is empty", r!!.paragraphs.isNotEmpty() && r.paragraphs.all { it.isNotBlank() })
            }
            // Ordinary years too: 29 February falls back to the 28th.
            assertNotNull(classics.forDate(LocalDate.of(2027, 2, 28), false))
            assertEquals(classics.forDate(LocalDate.of(2027, 2, 28), false), classics.forDate(LocalDate.of(2027, 2, 28), false))
        }
        assertEquals(732, load(DevotionalPlans.default).size)
        assertEquals(366, load(DevotionalPlans.byId(DevotionalPlans.FAITHS_CHECKBOOK)!!).size)
    }

    @Test fun `a bundled plan names its source and licence, and one with its own titles has them`() {
        for (plan in DevotionalPlans.available) {
            assertTrue(plan.id, plan.sourceUrl.startsWith("https://"))
            assertTrue(plan.id, plan.licence.contains("ublic domain"))
            assertTrue(plan.id, plan.attribution.contains(plan.title))
            assertNotNull(plan.id, plan.asset)
        }
        val checkbook = load(DevotionalPlans.byId(DevotionalPlans.FAITHS_CHECKBOOK)!!)
        assertEquals("The Bible’s First Promise", checkbook.forDate(LocalDate.of(2026, 1, 1))!!.heading)
    }

    @Test fun `plans that could not be verified are in the catalogue but never offered`() {
        val daily = DevotionalPlans.byId(DevotionalPlans.DAILY_LIGHT)!!
        val streams = DevotionalPlans.byId(DevotionalPlans.STREAMS_IN_THE_DESERT)!!
        for (p in listOf(daily, streams)) {
            assertEquals(PlanStatus.UNAVAILABLE, p.status)
            assertNull(p.asset)
            assertFalse(p.available)
            assertTrue(p.unavailableReason!!.isNotBlank())
            assertTrue(p !in DevotionalPlans.available)
        }
        assertTrue("My Utmost for His Highest is never bundled", DevotionalPlans.all.none { it.title.contains("Utmost") })
    }

    @Test fun `an unknown or unavailable plan id reads as Morning and Evening`() {
        assertEquals(DevotionalPlans.DEFAULT_ID, DevotionalPlans.resolve("no-such-plan").id)
        assertEquals(DevotionalPlans.DEFAULT_ID, DevotionalPlans.resolve(DevotionalPlans.DAILY_LIGHT).id)
        assertEquals(DevotionalPlans.DEFAULT_ID, DevotionalPlans.resolve(null).id)
        assertEquals(DevotionalPlans.FAITHS_CHECKBOOK, DevotionalPlans.resolve(DevotionalPlans.FAITHS_CHECKBOOK).id)
    }

    // ---------------------------------------------------------------- the profile

    @Test fun `a new user starts on Morning and Evening`() {
        val p = DevotionalProfile()
        assertEquals(DevotionalSource.CLASSIC, p.source)
        assertEquals("morning-and-evening", p.planId)
        assertEquals("Morning and Evening", p.readingName)
        assertEquals(DevotionalProfile(), DevotionalProfile.fromJson(null))
    }

    @Test fun `an existing classic user keeps Morning and Evening, an AI user keeps AI`() {
        val classic = DevotionalProfile.fromJson("""{"enabled":true,"source":"CLASSIC","tradition":"CATHOLIC"}""")
        assertEquals(DevotionalSource.CLASSIC, classic.source)
        assertEquals("morning-and-evening", classic.planId)
        assertEquals(Tradition.CATHOLIC, classic.tradition)
        assertTrue(classic.enabled)

        val ai = DevotionalProfile.fromJson("""{"enabled":true,"source":"AI"}""")
        assertEquals(DevotionalSource.AI, ai.source)
        // The bundled plan is still their fallback.
        assertEquals("morning-and-evening", ai.plan.id)
        assertEquals(DevotionalSource.MIX, DevotionalProfile.fromJson("""{"source":"MIX"}""").source)
        assertEquals(DevotionalSource.MINE, DevotionalProfile.fromJson("""{"source":"MINE"}""").source)
    }

    @Test fun `the chosen plan survives a save and a reload`() {
        val p = DevotionalProfile(enabled = true, source = DevotionalSource.CLASSIC, planId = DevotionalPlans.FAITHS_CHECKBOOK)
        val back = DevotionalProfile.fromJson(p.toJson())
        assertEquals(p, back)
        assertEquals("Faith's Checkbook", back.readingName)
        // A plan id this build doesn't know is kept as stored (a newer build may), and reads as the default.
        val future = DevotionalProfile.fromJson("""{"source":"CLASSIC","planId":"from-the-future"}""")
        assertEquals("from-the-future", future.planId)
        assertEquals(DevotionalPlans.DEFAULT_ID, future.plan.id)
    }

    @Test fun `AI writes on AI days only`() {
        val ai = DevotionalProfile(source = DevotionalSource.AI)
        val mix = DevotionalProfile(source = DevotionalSource.MIX)
        val sunday = LocalDate.of(2026, 10, 11)
        val monday = LocalDate.of(2026, 10, 12)
        assertTrue(ai.writesWithAiOn(sunday) && ai.writesWithAiOn(monday))
        assertFalse(mix.writesWithAiOn(sunday)); assertTrue(mix.writesWithAiOn(monday))
        assertFalse(DevotionalProfile().writesWithAiOn(monday))
    }

    // ---------------------------------------------------------------- never empty

    @Test fun `ensure returns the plan reading for any date with no network and no model`() = runBlocking {
        val repo = DevotionalRepository(context, db)
        val dates = (1..12).map { LocalDate.of(2027, it, 15) } + LocalDate.of(2028, 2, 29) + LocalDate.of(2027, 1, 1) + LocalDate.of(2027, 12, 31)
        for (d in dates) {
            val daily = repo.ensure(d)
            assertNotNull("nothing written for $d", daily)
            assertEquals(DevotionalOrigin.CLASSIC, daily!!.devotional.origin)
            assertTrue(daily.devotional.reflection.isNotEmpty())
            assertNull("a plan reading needs no fallback line", daily.note.metadata["devotionalFallback"])
            assertEquals("Morning and Evening", daily.devotional.engine!!.substringAfter(", "))
        }
    }

    @Test fun `the plan reading is the chosen plan's`() = runBlocking {
        setProfile(DevotionalProfile(planId = DevotionalPlans.FAITHS_CHECKBOOK))
        val daily = DevotionalRepository(context, db).ensure(date)!!
        assertEquals("The first sentence of the day is the plan's", "Faith's Checkbook", daily.devotional.engine!!.substringAfter(", "))
        assertEquals(DevotionalPlans.FAITHS_CHECKBOOK, daily.note.metadata[META_PLAN])
        assertTrue(daily.devotional.label.contains("Faith's Checkbook"))
        Unit
    }

    @Test fun `an AI source with no model set up still gets the plan reading, and a plain line says why`() = runBlocking {
        setProfile(DevotionalProfile(source = DevotionalSource.AI))
        val repo = DevotionalRepository(context, db)
        val daily = repo.ensure(date)!!
        assertEquals(DevotionalOrigin.CLASSIC, daily.devotional.origin)
        assertTrue(daily.note.metadata["devotionalFallback"]!!.contains("No AI is set up"))
        assertEquals(1, repo.observeDay(day).first().size)
    }

    @Test fun `the base write never leaves a day empty and never writes twice`() = runBlocking {
        setProfile(DevotionalProfile(source = DevotionalSource.AI))
        val repo = DevotionalRepository(context, db)
        for (offset in 0L until 40L) {
            val d = date.plusDays(offset)
            val first = repo.ensureBase(d)
            assertNotNull("$d left empty", first)
            assertEquals(first!!.note.id, repo.ensureBase(d)!!.note.id)
            assertEquals(first.note.id, repo.find(LocalDay.of(d))!!.note.id)
        }
    }

    @Test fun `the scheduled worker writes the plan reading offline and succeeds`() = runBlocking {
        setProfile(DevotionalProfile(enabled = true, source = DevotionalSource.AI))
        val worker = TestListenableWorkerBuilder<DevotionalWorker>(context)
            .setInputData(workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_WRITE)).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        val today = DevotionalRepository(context, db).find(LocalDay.today())
        assertNotNull("the 00:01 write left today empty", today)
        assertEquals(DevotionalOrigin.CLASSIC, today!!.devotional.origin)
        Unit
    }

    @Test fun `the AI step with no model is retried, then leaves the plan reading standing`() = runBlocking {
        setProfile(DevotionalProfile(enabled = true, source = DevotionalSource.AI))
        val repo = DevotionalRepository(context, db)
        val plan = repo.ensureBase(LocalDate.now())!!
        val worker = TestListenableWorkerBuilder<DevotionalWorker>(context)
            .setInputData(workDataOf(DevotionalWorker.KEY_MODE to DevotionalWorker.MODE_AI)).build()
        val result = worker.doWork()
        assertTrue(result == ListenableWorker.Result.retry() || result == ListenableWorker.Result.success())
        assertEquals(plan.note.id, repo.find(LocalDay.today())!!.note.id)
    }

    // ---------------------------------------------------------------- Write one with AI, and changing plan

    @Test fun `Write one with AI says so plainly when no AI is set up, and the plan reading stays`() = runBlocking {
        val repo = DevotionalRepository(context, db)
        val plan = repo.ensure(date)!!
        try {
            repo.ensure(date, another = true, ask = DevotionalAsk(aiOnly = true))
            fail("expected a plain refusal")
        } catch (e: DevotionalUnavailable) {
            assertTrue(e.message!!.contains("No AI is set up"))
        }
        assertEquals(plan.note.id, repo.find(day)!!.note.id)
        assertEquals(1, repo.observeDay(day).first().size)
    }

    @Test fun `changing plan swaps today's reading and keeps the archive`() = runBlocking {
        val repo = DevotionalRepository(context, db)
        val morningEvening = repo.ensure(date)!!
        repo.setProfile(DevotionalProfile(planId = DevotionalPlans.FAITHS_CHECKBOOK))
        val now = repo.find(day)!!
        assertEquals(DevotionalPlans.FAITHS_CHECKBOOK, now.note.metadata[META_PLAN])
        assertTrue(now.note.id != morningEvening.note.id)
        val all = repo.observeDay(day).first().map { it.note.id }
        assertTrue("the earlier reading stays in the archive", morningEvening.note.id in all)
        assertTrue(repo.archive().any { it.noteId == morningEvening.note.id })
        // Choosing the same plan again changes nothing.
        repo.setProfile(DevotionalProfile(planId = DevotionalPlans.FAITHS_CHECKBOOK))
        assertEquals(now.note.id, repo.find(day)!!.note.id)
        assertEquals(2, repo.observeDay(day).first().size)
    }

    @Test fun `changing plan leaves an AI devotional or the person's own page alone`() = runBlocking {
        val repo = DevotionalRepository(context, db)
        setProfile(DevotionalProfile(source = DevotionalSource.MINE))
        val mine = repo.ensure(date)!!
        assertEquals(DevotionalOrigin.MINE, mine.devotional.origin)
        repo.setProfile(DevotionalProfile(source = DevotionalSource.CLASSIC, planId = DevotionalPlans.FAITHS_CHECKBOOK))
        assertEquals(mine.note.id, repo.find(day)!!.note.id)
    }
}
