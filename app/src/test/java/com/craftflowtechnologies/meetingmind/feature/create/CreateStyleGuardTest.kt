package com.craftflowtechnologies.meetingmind.feature.create

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Create UI uses tokens only, and the old Spark names are gone from the UI. */
class CreateStyleGuardTest {
    private val root = File("src/main/java/com/craftflowtechnologies/meetingmind")
    private val literals = listOf(
        Regex("""fontSize\s*=\s*[0-9.]+\.sp"""),
        Regex("""Color\(0x"""),
        Regex("""Color\.(White|Black)\b"""),
        Regex("""RoundedCornerShape\(\s*[0-9.]+\.dp"""),
        Regex("""\.padding\([^)]*[0-9]+\.dp""")
    )

    @Test fun `create UI files contain no style literals`() {
        val files = File(root, "feature/create").listFiles { f -> f.extension == "kt" }.orEmpty().toList()
        assertTrue("no create files", files.size >= 4)
        val problems = files.flatMap { f -> literals.filter { it.containsMatchIn(f.readText()) }.map { "${f.name} matches ${it.pattern}" } }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun `old Spark names are gone`() {
        val banned = Regex("""\b(Spark Studio|SparkStudioSheet|SparkGenerator|SparkVibe|Motivational Sermon)\b|"Spark"""")
        val hits = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { banned.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue("old Spark names in $hits", hits.isEmpty())
    }
}
