package com.craftflowtechnologies.meetingmind.feature.today.companion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every home gives the companion one line, and only one. */
class HomeCompanionLineGuardTest {

    private val root = File("src/main/java/com/craftflowtechnologies/meetingmind")

    private val homes = listOf(
        "feature/today/TodayScreen.kt",
        "feature/today/home/CalmHome.kt",
        "feature/work/ProfessionalHome.kt",
        "feature/learning/space/LearningSpaceScreen.kt",
        "feature/faith/FaithHome.kt"
    )

    @Test fun `every home places the companion line exactly once`() {
        val problems = homes.mapNotNull { path ->
            val count = Regex("""HomeCompanionLine\(""").findAll(File(root, path).readText()).count()
            "$path places HomeCompanionLine $count times".takeIf { count != 1 }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun `Today doesn't draw a second companion beside its hero`() {
        val today = File(root, "feature/today/TodayScreen.kt").readText()
        val call = today.substringAfter("HomeCompanionLine(").substringBefore("\n            )")
        assertTrue("Today's line should pass showCompanion = false", "showCompanion = false" in call)
    }

    @Test fun `the Faith home shows its private milestones`() {
        val faith = File(root, "feature/faith/FaithHome.kt").readText()
        assertTrue("FaithHome should place FaithMilestones", "FaithMilestones(" in faith)
    }

    @Test fun `Faith counts devotional days and every other home counts notes kept`() {
        assertTrue(rhythmFor(com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH) == com.craftflowtechnologies.meetingmind.core.companion.CompanionRhythm.Kind.FAITH_DAYS)
        assertTrue(rhythmFor(com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK) == com.craftflowtechnologies.meetingmind.core.companion.CompanionRhythm.Kind.NOTES_KEPT)
        assertTrue(rhythmFor(null) == com.craftflowtechnologies.meetingmind.core.companion.CompanionRhythm.Kind.NOTES_KEPT)
    }
}
