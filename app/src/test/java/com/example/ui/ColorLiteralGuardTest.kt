package com.example.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps hardcoded colours from creeping back (docs/PRD_M0.md §5). Screens use the theme's role
 * tokens (`Ink`, `SurfaceBase`, `Line`…) so they work in both themes; a literal colour is only for
 * content — artwork, highlights, a notebook's own colour — and those are counted in a baseline
 * that may only go down.
 */
class ColorLiteralGuardTest {

    private val literal = Regex("""Color\(0x[0-9A-Fa-f]{6,8}\)(?!\.forTheme)|Color\.White\b(?!\.copy)""")

    @Test
    fun `no screen gains a hardcoded colour`() {
        val root = File("src/main/java")
        val baseline = javaClass.classLoader!!.getResource("color-literal-baseline.txt")!!.readText()
            .lines().filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line -> line.substringBeforeLast(' ') to line.substringAfterLast(' ').toInt() }
        val problems = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("/ui/theme/") }
            .mapNotNull { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                val count = literal.findAll(file.readText()).count()
                val allowed = baseline[path] ?: 0
                if (count > allowed) "$path has $count hardcoded colours (allowed $allowed): use a theme token" else null
            }
            .toList()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
