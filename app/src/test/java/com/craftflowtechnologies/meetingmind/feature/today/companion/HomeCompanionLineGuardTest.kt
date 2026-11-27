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

    /**
     * The Learning home (Study design) has no HomeCompanionLine: its companion line is Zuri in the
     * round card (or the first-use card), so a second line would double it. One Zuri, never two.
     */
    @Test fun `the Learning home keeps one Zuri line and no HomeCompanionLine`() {
        val learn = File(root, "feature/learn/ui/home/LearningHomeScreen.kt").readText()
        assertTrue("the Learning home should not draw HomeCompanionLine", "HomeCompanionLine(" !in learn)
        assertTrue("the Learning home should draw Zuri", "ZuriSlot(" in learn)
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
