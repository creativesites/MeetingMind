package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipePrivacyTest {
    private lateinit var f: PulseFixture

    @Before
    fun setup() {
        f = PulseFixture()
    }

    @After
    fun tearDown() {
        f.db.close()
    }

    @Test
    fun clinicalProfileSuppressesEmailDraftSkill() = runBlocking {
        val clinicalEngine = RecipeEngine(f.db, workProfileProvider = { WorkProfile.CLINICAL }, clock = { f.now })
        val item = f.mine("Medical follow-up", status = ItemStatus.OPEN)
        val context = RecipeContext(
            meetingId = "m1",
            projectId = "nb",
            meetingTitle = "Patient Consultation",
            items = listOf(item),
            now = f.now
        )

        val card = clinicalEngine.evaluate(RecipeDefaults.CLIENT_MEETING, context)
        val hasEmailDraft = card.actions.any { it is ProposedAction.DraftEmailAction }

        // Must be suppressed for patient confidentiality
        assertFalse("Clinical profile must suppress outbound email draft actions", hasEmailDraft)
    }

    @Test
    fun legalProfileSuppressesEmailDraftSkill() = runBlocking {
        val legalEngine = RecipeEngine(f.db, workProfileProvider = { WorkProfile.LEGAL }, clock = { f.now })
        val item = f.mine("Privileged advice", status = ItemStatus.OPEN)
        val context = RecipeContext(
            meetingId = "m1",
            projectId = "nb",
            meetingTitle = "Client Legal Matter",
            items = listOf(item),
            now = f.now
        )

        val card = legalEngine.evaluate(RecipeDefaults.CLIENT_MEETING, context)
        val hasEmailDraft = card.actions.any { it is ProposedAction.DraftEmailAction }

        assertFalse("Legal profile must suppress outbound email draft actions", hasEmailDraft)
    }

    @Test
    fun clientWorkProfileEnablesEmailDraftSkill() = runBlocking {
        val clientEngine = RecipeEngine(f.db, workProfileProvider = { WorkProfile.CLIENT_WORK }, clock = { f.now })
        val item = f.mine("Deliver project specs", status = ItemStatus.OPEN)
        val context = RecipeContext(
            meetingId = "m1",
            projectId = "nb",
            meetingTitle = "Client Sprint Review",
            items = listOf(item),
            now = f.now
        )

        val card = clientEngine.evaluate(RecipeDefaults.CLIENT_MEETING, context)
        val hasEmailDraft = card.actions.any { it is ProposedAction.DraftEmailAction }

        assertTrue("Client work profile allows follow-up email drafts", hasEmailDraft)
    }
}
