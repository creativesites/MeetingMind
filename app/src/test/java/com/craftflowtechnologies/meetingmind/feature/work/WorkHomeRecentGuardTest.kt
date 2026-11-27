package com.craftflowtechnologies.meetingmind.feature.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A just-recorded work meeting shows on Work Home the same way as on the Work page: through the shared
 * Recent section, which covers every state, not a strip that only shows while notes are being written.
 */
class WorkHomeRecentGuardTest {

    private val home = File("src/main/java/com/craftflowtechnologies/meetingmind/feature/work/ProfessionalHome.kt").readText()

    @Test fun `Work Home shows the shared Recent section`() {
        assertTrue("ProfessionalHome should draw WorkRecentSection", "WorkRecentSection(" in home)
        assertTrue("ProfessionalHome should read WorkViewModel.recentWork", "work.recentWork" in home)
    }

    @Test fun `the old processing strip is gone`() {
        assertFalse("the Processing strip only showed while a job ran", "\"Processing · \${j.progressPercent}%\"" in home)
    }

    @Test fun `each recent row opens where its state says`() {
        val call = home.substringAfter("WorkRecentSection(").substringBefore("onAll")
        listOf("WorkRecent.Target.WRAP_UP", "WorkRecent.Target.PROCESSING").forEach {
            assertTrue("Work Home's Recent doesn't handle $it", it in call)
        }
    }
}
