package com.craftflowtechnologies.meetingmind.feature.learn.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The study pack a Circle receives: title, course, summary, key ideas and the stressed moments, as markdown. */
class StudyPackMarkdownTest {

    @Test
    fun `the pack has its sections in order, with the moments as timestamps and quotes`() {
        val md = studyPackMarkdown(
            title = "Enzyme kinetics",
            courseName = "BIO 201 Biochemistry",
            summary = "Enzymes speed reactions. Km is half speed.",
            ideas = listOf(StudyPackIdea("Km", "Substrate concentration at half Vmax.")),
            stressed = listOf(StudyPackMoment(842_000L, "This will be on the exam."))
        )

        assertEquals(
            """
            # Enzyme kinetics
            *BIO 201 Biochemistry*

            ## In 30 seconds
            Enzymes speed reactions. Km is half speed.

            ## Key ideas
            - **Km**: Substrate concentration at half Vmax.

            ## What the lecturer stressed
            - 14:02 "This will be on the exam."
            """.trimIndent() + "\n",
            md
        )
    }

    @Test
    fun `empty sections are left out, and a blank title gets a plain one`() {
        val md = studyPackMarkdown(title = " ", courseName = null, summary = "", ideas = emptyList(), stressed = emptyList())

        assertEquals("# Study pack\n", md)
        assertFalse(md.contains("## "))
    }

    @Test
    fun `an idea without a definition is just its name`() {
        val md = studyPackMarkdown("T", null, "", listOf(StudyPackIdea("Vmax", "")), emptyList())

        assertTrue(md.contains("- **Vmax**\n"))
    }
}
