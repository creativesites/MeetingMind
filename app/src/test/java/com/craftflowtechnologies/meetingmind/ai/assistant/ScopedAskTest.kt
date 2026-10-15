package com.craftflowtechnologies.meetingmind.ai.assistant

import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.FakeWorkModel
import com.craftflowtechnologies.meetingmind.core.work.FakeWorkModels
import com.craftflowtechnologies.meetingmind.core.work.ItemKind
import com.craftflowtechnologies.meetingmind.core.work.ItemStatus
import com.craftflowtechnologies.meetingmind.core.work.PackPrivacy
import com.craftflowtechnologies.meetingmind.core.work.PulseFixture
import com.craftflowtechnologies.meetingmind.core.work.addMeeting
import com.craftflowtechnologies.meetingmind.core.work.projectWorld
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ask, limited to a scope: the pack goes first, search stays inside the scope, and an uncited answer is not shown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScopedAskTest {
    private lateinit var f: PulseFixture
    private val project = AskScope(ContextType.PROJECT, "nb", label = "Acme launch")

    @Before fun setup() {
        f = PulseFixture(); f.projectWorld()
        // A second, unrelated project with its own recording that also mentions "deck".
        runBlocking { f.db.notebookDao().upsert(com.craftflowtechnologies.meetingmind.core.database.NotebookEntity("other", "Other client", "WORK", null, null, 1, 1, null, 0, kind = "PROJECT")) }
        f.addMeeting("m9", f.now - 2 * f.day, listOf("The deck for the other client is due."), project = "other")
        f.addMeeting("m8", f.now - f.day, listOf("Our deck for Acme needs the launch date."), project = "nb")
        f.item(ItemKind.COMMITMENT, ItemStatus.OPEN, "Other client's deck", project = "other", org = null, meeting = "m9")
    }
    @After fun tearDown() { f.db.close() }

    private fun ask(models: FakeWorkModels?, question: String = "What do I owe about the deck?", scope: AskScope = project, privacy: PackPrivacy = PackPrivacy { _, _ -> false }) =
        runBlocking { ScopedAsk(f.db, models, privacy, { f.now }).ask(question, scope) }

    @Test fun thePackComesBeforeTheTranscriptAndSearchStaysInScope() {
        val model = FakeWorkModel("You owe the deck [1].")
        val a = ask(FakeWorkModels(model))
        val prompt = model.prompts.single()
        assertTrue(prompt.startsWith("You are working with a transcript of a real recording"))
        val first = prompt.indexOf("[1] "); val transcript = prompt.indexOf("Our deck for Acme")
        assertTrue(first in 0 until transcript)
        assertTrue(prompt.contains("Send the deck"))
        assertFalse(prompt.contains("other client")) // neither its item nor its recording
        assertFalse(prompt.contains("Other client"))
        assertEquals(SourceKind.ITEM, a.sources.first().kind)
        assertTrue(a.sources.any { it.kind == SourceKind.RECORDING && it.meetingId == "m8" })
        assertEquals((1..a.sources.size).toList(), a.sources.map { it.key })
    }

    @Test fun aCitedAnswerIsShownWithItsSources() {
        val a = ask(FakeWorkModels(FakeWorkModel("You owe the deck, overdue [1]. Nothing else [99].")))
        assertEquals("You owe the deck, overdue [1]. Nothing else .".replace(" .", "."), a.text) // the invented marker is removed
        assertEquals(1, a.cited.size)
        assertNotNull(a.cited.first().meetingId.let { a.cited.first() })
    }

    @Test fun anAnswerWithoutACitationIsNotShown() {
        val a = ask(FakeWorkModels(FakeWorkModel("You owe the deck.")))
        assertNull(a.text)
        assertEquals(ScopedReason.UNCITED, a.reason)
        assertTrue(a.sources.isNotEmpty()) // the sources are still there to read
    }

    @Test fun noModelStillFindsTheSources() {
        val a = ask(FakeWorkModels(null))
        assertEquals(ScopedReason.NO_MODEL, a.reason); assertNull(a.text)
        assertTrue(a.sources.any { it.text.contains("Send the deck") })
        assertEquals(ScopedReason.NO_MODEL, ask(null).reason)
    }

    @Test fun nothingFoundInScopeIsSaidPlainly() {
        val empty = AskScope(ContextType.PERSON, "bo", label = "Bo")
        val a = ask(FakeWorkModels(FakeWorkModel("x [1]")), "What about pricing?", empty)
        assertEquals(ScopedReason.NOTHING_FOUND, a.reason)
        assertEquals("I couldn't find anything about that in Bo.", a.text)
    }

    @Test fun clearingTheScopeAsksAcrossAllWork() {
        val model = FakeWorkModel("Both decks [1][2].")
        val a = ask(FakeWorkModels(model), scope = AskScope())
        assertTrue(a.sources.any { it.text.contains("Other client's deck") } && a.sources.any { it.text.contains("Send the deck") })
        assertEquals("My work", AskScope().label)
    }

    @Test fun aDateRangeLimitsWhichRecordingsAreSearched() {
        val model = FakeWorkModel("x [1]")
        val a = ask(FakeWorkModels(model), scope = AskScope(from = f.now - 3 * f.day + 1, to = f.now - f.day - 1, label = "This week"))
        assertTrue(a.sources.any { it.meetingId == "m9" } || a.sources.none { it.meetingId == "m8" })
        assertFalse(a.sources.any { it.kind == SourceKind.RECORDING && it.meetingId == "m8" })
    }

    @Test fun whatMustStayOnThePhoneOnlyMeetsTheSensitivePath() {
        val models = FakeWorkModels(FakeWorkModel("You owe the deck [1]."))
        var scopeSeen: com.craftflowtechnologies.meetingmind.core.work.PackScope? = null
        ask(models, privacy = PackPrivacy { _, s -> scopeSeen = s; true })
        assertEquals(listOf(true), models.asked)
        assertEquals(project.packScope, scopeSeen)
    }
}
