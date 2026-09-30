package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With no model at all, the brief still renders: its structure is the database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BriefNoModelTest {
    private lateinit var f: PulseFixture

    @Before fun setup() { f = PulseFixture(); f.projectWorld() }
    @After fun tearDown() { f.db.close() }

    private fun build(models: WorkModels?) = runBlocking { BriefBuilder(f.db, models) { f.now }.build(BriefTarget.forProject("nb")) }

    @Test fun noModelMeansNoProseButEveryOtherSection() {
        val brief = build(null)
        assertFalse(brief.hasProse)
        assertTrue(brief.executive.isEmpty() && brief.recommended.isEmpty())
        assertTrue(listOf(brief.changes, brief.decisions, brief.youOwe, brief.theyOwe, brief.risks, brief.questions, brief.next7, brief.decisionsRequired, brief.evidence).all { it.isNotEmpty() })
        assertEquals(0, runBlocking { f.db.briefDao().count() })
    }

    @Test fun aModelThatIsNotInstalledOrAnswersBadlyIsTheSameAsNone() {
        val none = build(FakeWorkModels(null))
        val garbage = build(FakeWorkModels(FakeWorkModel("I would say things are fine.")))
        assertEquals(build(null).copy(generatedAt = 0), none.copy(generatedAt = 0))
        assertEquals(none.copy(generatedAt = 0), garbage.copy(generatedAt = 0))
        assertEquals(0, runBlocking { f.db.briefDao().count() }) // nothing usable, nothing kept
    }

    @Test fun anEmptyProjectIsAnEmptyBriefNotAModelCall() {
        val models = FakeWorkModels(FakeWorkModel("{}"))
        val brief = runBlocking { BriefBuilder(f.db, models) { f.now }.build(BriefTarget.forPerson("bo")) }
        assertTrue(brief.citedItemIds.isEmpty() && brief.evidence.isEmpty())
        assertTrue(models.model!!.prompts.isEmpty())
        assertEquals(BriefStatus.ON_TRACK, brief.status)
    }

    @Test fun theSameBriefExportsToMarkdownWithoutProse() {
        val md = BriefExport.markdown(build(null))
        assertFalse(md.contains("Executive picture"))
        assertFalse(md.contains("Recommended next conversation"))
        assertTrue(md.contains("## YOU OWE".lowercase().replaceFirstChar { it.uppercase() }.let { "Send the deck" }))
        assertTrue(md.contains("Send the deck") && md.contains("Vendor may be late") && md.contains("Evidence"))
    }
}
