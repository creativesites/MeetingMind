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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApprovalGateTest {
    private lateinit var f: PulseFixture
    private lateinit var engine: RecipeEngine

    @Before
    fun setup() {
        f = PulseFixture()
        engine = RecipeEngine(f.db, clock = { f.now })
    }

    @After
    fun tearDown() {
        f.db.close()
    }

    @Test
    fun evaluationProducesPendingApprovalCardWithNoMutations() = runBlocking {
        val item = f.mine("Send API documentation", status = ItemStatus.OPEN)
        val context = RecipeContext(
            meetingId = "m1",
            projectId = "nb",
            meetingTitle = "Kickoff Review",
            items = listOf(item),
            now = f.now
        )

        // Evaluate Recipe #1 (The Wrap-Up)
        val card = engine.evaluate(RecipeDefaults.WRAP_UP, context)

        // Must be PENDING
        assertEquals(ApprovalStatus.PENDING, card.status)
        assertEquals(2, card.actions.size)

        // Crucial test: No tasks exist in the database prior to approval
        val tasksBefore = f.db.taskDao().exportAll()
        assertTrue(tasksBefore.isEmpty())
    }

    @Test
    fun callingApproveExecutesTasksAndSetsStatus() = runBlocking {
        val item = f.mine("Configure OAuth credentials", status = ItemStatus.OPEN)
        val context = RecipeContext(
            meetingId = "m1",
            projectId = "nb",
            meetingTitle = "Kickoff Review",
            items = listOf(item),
            now = f.now
        )

        val card = engine.evaluate(RecipeDefaults.WRAP_UP, context)
        val result = engine.approve(card)

        assertEquals(ApprovalStatus.APPROVED, card.status)
        assertTrue(result.executedActionsCount > 0)
        assertTrue(result.createdTaskIds.isNotEmpty())

        // Tasks are now safely written to Room
        val tasksAfter = f.db.taskDao().exportAll()
        assertEquals(1, tasksAfter.size)
        assertEquals("Configure OAuth credentials", tasksAfter.first().title)
        assertEquals("m1", tasksAfter.first().meetingId)
    }

    @Test
    fun callingRejectDiscardsActionsWithoutSideEffects() = runBlocking {
        val item = f.mine("Discarded commitment", status = ItemStatus.OPEN)
        val context = RecipeContext(
            meetingId = "m1",
            projectId = "nb",
            meetingTitle = "Kickoff Review",
            items = listOf(item),
            now = f.now
        )

        val card = engine.evaluate(RecipeDefaults.WRAP_UP, context)
        engine.reject(card)

        assertEquals(ApprovalStatus.REJECTED, card.status)

        // Attempting to approve rejected card must execute 0 actions
        val result = engine.approve(card)
        assertEquals(0, result.executedActionsCount)
        assertTrue(f.db.taskDao().exportAll().isEmpty())
    }
}
