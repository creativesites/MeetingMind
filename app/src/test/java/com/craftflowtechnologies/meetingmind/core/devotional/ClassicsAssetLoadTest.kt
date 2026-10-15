package com.craftflowtechnologies.meetingmind.core.devotional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalUnavailable
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The bundled plan through the real packaged asset path: `context.assets` and the gzip parse, with no
 * `useForTest` shortcut. A blank devotional on a phone starts here: the loader failed on the packaged file
 * and the failure was hidden, so every day came out empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClassicsAssetLoadTest {
    private lateinit var db: MeetMindDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val date = LocalDate.now()
    private val day = LocalDay.of(date)
    private var builtInKey: String? = null

    @Before fun setUp() {
        ClassicDevotionals.useForTest(null)
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        // No AI set up: a tester key built into the app would offer Gemini.
        builtInKey = GeminiCredentialStore.systemKey
        GeminiCredentialStore.systemKey = null
    }

    @After fun tearDown() {
        GeminiCredentialStore.systemKey = builtInKey
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
        ClassicDevotionals.useForTest(null)
    }

    @Test fun `the default plan loads from the packaged asset with every day`() {
        val classics = ClassicDevotionals.get(context, DevotionalPlans.DEFAULT_ID)
        assertEquals("Morning and Evening", classics.title)
        assertEquals(732, classics.size)
        assertTrue(classics.readable)
        assertNotNull(classics.forDate(LocalDate.of(2026, 1, 1), evening = false))
        assertNotNull(classics.forDate(LocalDate.of(2026, 1, 1), evening = true))
    }

    @Test fun `every bundled plan loads from the packaged asset`() {
        for (plan in DevotionalPlans.available) {
            val classics = ClassicDevotionals.get(context, plan.id)
            assertEquals("plan ${plan.id} must load its own text", plan.id, classics.plan.id)
            assertTrue("plan ${plan.id} is empty", classics.size > 0)
        }
    }

    @Test fun `a fresh install has today's plan reading, never a blank devotional`() = runBlocking {
        val repo = DevotionalRepository(context, db)
        val d = repo.ensureBase(date)!!
        assertEquals(DevotionalOrigin.CLASSIC, d.devotional.origin)
        assertTrue(d.devotional.reflection.isNotEmpty())
        assertTrue(d.devotional.title.isNotBlank())
        assertEquals(d.note.id, repo.find(day)!!.note.id)
    }

    @Test fun `an old profile with AI and a retired plan still gets a readable plan reading`() = runBlocking {
        UserPreferencesManager(context).setDevotionalProfile(
            DevotionalProfile.fromJson("""{"enabled":true,"source":"AI","planId":"retired-plan"}""")
        )
        val d = DevotionalRepository(context, db).ensure(date)!!
        assertEquals(DevotionalOrigin.CLASSIC, d.devotional.origin)
        assertEquals(DevotionalPlans.DEFAULT_ID, d.note.metadata[META_PLAN])
        assertTrue(d.devotional.reflection.isNotEmpty())
    }

    @Test fun `when the bundled plan cannot be read, the day says so and nothing blank is saved`() = runBlocking {
        ClassicDevotionals.useForTest(ClassicDevotionals(emptyList()))
        val repo = DevotionalRepository(context, db)
        try {
            repo.ensureBase(date)
            fail("an unreadable plan must not file a blank devotional")
        } catch (e: DevotionalUnavailable) {
            assertTrue(e.message!!, e.message!!.contains("couldn't be read"))
        }
        assertEquals(null, repo.find(day))
        assertTrue(repo.recent().isEmpty())
    }
}
