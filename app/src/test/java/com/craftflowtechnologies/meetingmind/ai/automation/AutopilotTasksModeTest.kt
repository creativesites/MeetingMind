package com.craftflowtechnologies.meetingmind.ai.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Autopilot's tasks mode defaults to review (spec F-3), and Work offers only its two recipes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutopilotTasksModeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `tasks go to review unless the person chose to create them`() {
        assertEquals(AutopilotTasksMode.REVIEW, AutopilotPreferences.tasksMode(context))
        assertTrue(!AutopilotPreferences.shouldAutoCreateTasks(context))
    }

    @Test fun `choosing create directly is kept, and choosing review again restores the default`() {
        AutopilotPreferences.setTasksMode(context, AutopilotTasksMode.CREATE)
        assertEquals(AutopilotTasksMode.CREATE, AutopilotPreferences.tasksMode(context))
        assertTrue(AutopilotPreferences.shouldAutoCreateTasks(context))
        AutopilotPreferences.setAutoCreateTasks(context, false)
        assertEquals(AutopilotTasksMode.REVIEW, AutopilotPreferences.tasksMode(context))
    }

    @Test fun `work offers only the executive debrief and the client sync`() {
        assertEquals(
            listOf(AutopilotRecipe.EXECUTIVE_DEBRIEF, AutopilotRecipe.CLIENT_CRM_LOG),
            AutopilotRecipe.WORK_RECIPES
        )
    }
}
