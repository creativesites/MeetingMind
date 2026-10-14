package com.craftflowtechnologies.meetingmind.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** New design-system component files use tokens only: no font sizes, colour or corner-radius literals. */
class DesignSystemGuardTest {
    private val literals = listOf(
        Regex("""fontSize\s*=\s*[0-9.]+\.sp"""),
        Regex("""Color\(0x"""),
        Regex("""RoundedCornerShape\(\s*[0-9.]+\.dp""")
    )

    @Test
    fun `component files contain no style literals`() {
        val root = File("src/main/java/com/craftflowtechnologies/meetingmind")
        val files = File(root, "core/ui/mm").listFiles { f -> f.extension == "kt" }.orEmpty().toList() +
            File(root, "ui/theme/DesignSystem.kt")
        assertTrue("no component files found", files.size >= 8)
        val problems = files.flatMap { f ->
            literals.filter { it.containsMatchIn(f.readText()) }.map { "${f.name} matches ${it.pattern}" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `Work page files contain no style literals`() {
        val root = File("src/main/java/com/craftflowtechnologies/meetingmind/feature/work")
        val files = root.listFiles { f -> f.extension == "kt" && (f.name.startsWith("WorkPage") || f.name == "WorkSheets.kt") }.orEmpty().toList()
        assertTrue("no Work page files found", files.size >= 7)
        val problems = files.flatMap { f ->
            literals.filter { it.containsMatchIn(f.readText()) }.map { "${f.name} matches ${it.pattern}" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
