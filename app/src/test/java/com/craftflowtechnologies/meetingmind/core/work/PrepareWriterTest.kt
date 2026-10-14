package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Prepare's prose follows the same rules as a brief: cited to the pack, or dropped; none without a model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrepareWriterTest {
    private lateinit var f: PulseFixture
    private lateinit var w: Map<String, com.craftflowtechnologies.meetingmind.core.database.ItemEntity>
    private val scope = PackScope.Entity(ContextType.PROJECT, "nb")

    @Before fun setup() { f = PulseFixture(); w = f.projectWorld() }
    @After fun tearDown() { f.db.close() }

    private fun prep() = runBlocking { Prepare(f.db) { f.now }.forEntity(ContextType.PROJECT, "nb", f.now) }
    private fun write(models: WorkModels?, privacy: PackPrivacy = PackPrivacy { _, _ -> false }) = runBlocking { PrepareWriter(f.db, models, privacy) { f.now }.write(scope, prep()) }

    @Test fun aCitedLastTimeLineAndAgendaWordingAreKept() {
        val reply = """{"lastTime":[{"text":"You left it with the launch moved to October 21.","cites":["${w.getValue("new").id}"]},{"text":"Extra.","cites":[]}],
            "agenda":[{"text":"Send the deck you owe.","cites":["${w.getValue("deck").id}"]},{"text":"Uncited filler.","cites":[]},{"text":"Made up.","cites":["nope"]}]}"""
        val model = FakeWorkModel(reply)
        val prose = write(FakeWorkModels(model))
        assertEquals("You left it with the launch moved to October 21.", prose.lastTime!!.text)
        assertEquals(listOf("Send the deck you owe."), prose.agenda.map { it.text })
        assertTrue(model.prompts.single().startsWith("You are working with a transcript of a real recording"))
    }

    @Test fun noModelOrNothingToPrepareMeansNoProse() {
        assertTrue(write(null).isEmpty)
        assertTrue(write(FakeWorkModels(null)).isEmpty)
        val models = FakeWorkModels(FakeWorkModel("{}"))
        val empty = runBlocking { PrepareWriter(f.db, models) { f.now }.write(PackScope.Entity(ContextType.PERSON, "bo"), Prepare(f.db) { f.now }.forEntity(ContextType.PERSON, "bo", f.now)) }
        assertTrue(empty.isEmpty); assertTrue(models.model!!.prompts.isEmpty())
    }

    @Test fun sensitiveMaterialUsesTheSensitivePath() {
        val models = FakeWorkModels(FakeWorkModel("{}"))
        write(models, PackPrivacy { _, _ -> true })
        assertEquals(listOf(true), models.asked)
    }
}
