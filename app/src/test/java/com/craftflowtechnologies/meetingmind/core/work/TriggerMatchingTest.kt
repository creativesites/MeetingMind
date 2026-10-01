package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TriggerMatchingTest {

    @Test
    fun meetingProcessedMatchesWorkflowAndWildcard() {
        val specificTrigger = RecipeTrigger.MeetingProcessed(workflow = "CLIENT_CALL", projectId = "nb")
        assertTrue(specificTrigger.matches("CLIENT_CALL", "nb", "org1"))
        assertFalse(specificTrigger.matches("INTERNAL_MEETING", "nb", "org1"))
        assertFalse(specificTrigger.matches("CLIENT_CALL", "other_project", "org1"))

        val wildcardTrigger = RecipeTrigger.MeetingProcessed()
        assertTrue(wildcardTrigger.matches("CLIENT_CALL", "nb", "org1"))
        assertTrue(wildcardTrigger.matches("CONSULTATION", null, null))
    }

    @Test
    fun scheduleMatchesExactDayAndHour() {
        val friday16 = RecipeTrigger.Schedule(dayOfWeek = Calendar.FRIDAY, hour = 16, minute = 0)

        val calMatch = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(friday16.matches(calMatch))

        val calWrongDay = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(friday16.matches(calWrongDay))

        val calWrongHour = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(friday16.matches(calWrongHour))
    }

    @Test
    fun inboxTriggerMatchesKind() {
        val riskTrigger = RecipeTrigger.InboxItem(kind = ItemKind.RISK)
        val riskItem = ItemEntity(id = "1", kind = "RISK", status = "OPEN", text = "Delivery risk", createdAt = 0, updatedAt = 0)
        val decisionItem = ItemEntity(id = "2", kind = "DECISION", status = "ACTIVE", text = "Use Kotlin", createdAt = 0, updatedAt = 0)

        assertTrue(riskTrigger.matches(riskItem))
        assertFalse(riskTrigger.matches(decisionItem))

        val anyItemTrigger = RecipeTrigger.InboxItem(kind = null)
        assertTrue(anyItemTrigger.matches(riskItem))
        assertTrue(anyItemTrigger.matches(decisionItem))
    }

    @Test
    fun engineFindsMatchingRecipes() {
        val dummyDb = ItemsFixture.database()
        val engine = RecipeEngine(dummyDb)

        val clientCallTrigger = RecipeTrigger.MeetingProcessed(workflow = "CLIENT_CALL", projectId = "nb")
        val matches = engine.findMatching(clientCallTrigger)

        // Recipe #1 (Wrap up - any meeting) and Recipe #2 (Client meeting - workflow = "CLIENT_CALL") match
        val matchedIds = matches.map { it.id }.toSet()
        assertTrue(matchedIds.contains(RecipeDefaults.ID_WRAP_UP))
        assertTrue(matchedIds.contains(RecipeDefaults.ID_CLIENT_MEETING))
        assertFalse(matchedIds.contains(RecipeDefaults.ID_FRIDAY_REVIEW))
        dummyDb.close()
    }
}
