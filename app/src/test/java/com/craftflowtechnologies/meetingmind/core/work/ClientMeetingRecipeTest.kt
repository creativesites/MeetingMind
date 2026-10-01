package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Definition of done for W15 (docs/AGENT_BRIEF_PRO.md §W15):
 * "the client-meeting recipe runs end to end on a fixture, stopping at approval."
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClientMeetingRecipeTest {
    private lateinit var f: PulseFixture
    private lateinit var engine: RecipeEngine

    @Before
    fun setup() {
        f = PulseFixture()
        engine = RecipeEngine(f.db, workProfileProvider = { WorkProfile.CLIENT_WORK }, clock = { f.now })
    }

    @After
    fun tearDown() {
        f.db.close()
    }

    @Test
    fun clientMeetingRecipeRunsEndToEndStoppingAtApproval() = runBlocking {
        // 1. Fixture: A client meeting ("Acme review") processed with commitments and decisions
        val commitment1 = f.mine("Send the finalized architecture diagram", status = ItemStatus.OPEN)
        val commitment2 = f.mine("Deploy staging build for client QA", status = ItemStatus.OPEN)
        val decision = f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Standardize on PostgreSQL")

        val trigger = RecipeTrigger.MeetingProcessed(
            workflow = "CLIENT_CALL",
            projectId = "nb",
            orgId = "org1"
        )

        // 2. Trigger matching: find matching recipes
        val matchedRecipes = engine.findMatching(trigger)
        val clientRecipe = matchedRecipes.find { it.id == RecipeDefaults.ID_CLIENT_MEETING }
        assertNotNull("Client meeting recipe must match a CLIENT_CALL trigger", clientRecipe)

        // 3. Assemble context from the processed meeting
        val context = RecipeContext(
            meetingId = "m1",
            projectId = "nb",
            orgId = "org1",
            noteId = "n1",
            meetingTitle = "Acme review",
            recordingType = "CLIENT_CALL",
            personIds = listOf("ana"),
            items = listOf(commitment1, commitment2, decision),
            now = f.now
        )

        // 4. Run recipe evaluation
        val approvalCard = engine.evaluate(clientRecipe!!, context)

        // 5. Verification: Recipe stopped strictly at approval
        assertEquals("Client Meeting Follow-Through", approvalCard.recipeTitle)
        assertEquals(ApprovalStatus.PENDING, approvalCard.status)

        // Check that all 5 skill actions were generated
        assertEquals(5, approvalCard.actions.size)

        val minutesAction = approvalCard.actions.filterIsInstance<ProposedAction.ExportMinutesAction>().firstOrNull()
        assertNotNull("Minutes must be drafted", minutesAction)
        assertTrue(minutesAction!!.content.contains("Minutes: Acme review"))
        assertTrue(minutesAction.content.contains("Send the finalized architecture diagram"))

        val emailDraftAction = approvalCard.actions.filterIsInstance<ProposedAction.DraftEmailAction>().firstOrNull()
        assertNotNull("Follow-up email must be drafted", emailDraftAction)
        assertEquals("ana", emailDraftAction!!.recipient)
        assertTrue(emailDraftAction.body.contains("Send the finalized architecture diagram"))

        val createTasksAction = approvalCard.actions.filterIsInstance<ProposedAction.CreateTasksAction>().firstOrNull()
        assertNotNull("Task creation action must be generated", createTasksAction)
        assertEquals(2, createTasksAction!!.taskTitles.size)
        assertTrue(createTasksAction.taskTitles.contains("Send the finalized architecture diagram"))
        assertTrue(createTasksAction.taskTitles.contains("Deploy staging build for client QA"))

        val updateProjectAction = approvalCard.actions.filterIsInstance<ProposedAction.UpdateProjectAction>().firstOrNull()
        assertNotNull("Project update action must be generated", updateProjectAction)
        assertEquals("nb", updateProjectAction!!.projectId)

        val reminderAction = approvalCard.actions.filterIsInstance<ProposedAction.ScheduleReminderAction>().firstOrNull()
        assertNotNull("Friday reminder must be scheduled", reminderAction)
        assertTrue(reminderAction!!.text.contains("Acme review"))

        // Critical safety check: NOTHING has changed in Room before user approval
        val tasksBeforeApproval = f.db.taskDao().allTasks()
        assertTrue("No tasks should be created before explicit approval", tasksBeforeApproval.isEmpty())

        // 6. User Approves the actions
        val executionResult = engine.approve(approvalCard)

        // Verify side effects now occur
        assertEquals(ApprovalStatus.APPROVED, approvalCard.status)
        assertTrue(executionResult.isDraftCreated)
        assertEquals(5, executionResult.executedActionsCount)
        assertEquals(2, executionResult.createdTaskIds.size)

        // Tasks are now successfully in Room
        val tasksAfterApproval = f.db.taskDao().allTasks()
        assertEquals(2, tasksAfterApproval.size)
        val titles = tasksAfterApproval.map { it.title }.toSet()
        assertTrue(titles.contains("Send the finalized architecture diagram"))
        assertTrue(titles.contains("Deploy staging build for client QA"))
    }
}
