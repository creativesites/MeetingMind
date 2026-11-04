package com.craftflowtechnologies.meetingmind.feature.notes.editor

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicBlocksModelTest {

    @Test
    fun noteBlockTypesIncludeAcademicBlocks() {
        val types = NoteBlockType.entries.map { it.name }.toSet()
        assertTrue(types.contains("MATH"))
        assertTrue(types.contains("CALLOUT"))
        assertTrue(types.contains("DIAGRAM"))
    }

    @Test
    fun blockSourcesIncludeEnriched() {
        val sources = BlockSource.entries.map { it.name }.toSet()
        assertTrue(sources.contains("ENRICHED"))
    }

    @Test
    fun noteCodecPlainTextIncludesAcademicBlocks() {
        val blocks = listOf(
            NoteBlock(
                id = "b1",
                noteId = "n1",
                position = 0,
                type = NoteBlockType.PARAGRAPH,
                content = RichText.plain("Introduction to calculus")
            ),
            NoteBlock(
                id = "b2",
                noteId = "n1",
                position = 1,
                type = NoteBlockType.MATH,
                content = RichText.plain("\\frac{d}{dx}[x^2] = 2x")
            ),
            NoteBlock(
                id = "b3",
                noteId = "n1",
                position = 2,
                type = NoteBlockType.CALLOUT,
                content = RichText.plain("The derivative of x^n is n*x^(n-1)"),
                payload = mapOf(
                    NoteBlock.PAYLOAD_CALLOUT_KIND to "THEOREM",
                    NoteBlock.PAYLOAD_CALLOUT_TITLE to "Power Rule"
                )
            ),
            NoteBlock(
                id = "b4",
                noteId = "n1",
                position = 3,
                type = NoteBlockType.DIAGRAM,
                content = RichText.plain("graph TD\n    A[Input] --> B[Output]")
            )
        )

        val plain = NoteCodec.plainTextOf(blocks)
        assertTrue(plain.contains("Introduction to calculus"))
        assertTrue(plain.contains("\\frac{d}{dx}[x^2] = 2x"))
        assertTrue(plain.contains("Power Rule: The derivative of x^n is n*x^(n-1)"))
        assertTrue(plain.contains("graph TD"))
    }

    @Test
    fun latexMathParserParsesFractionsAndAlignedSteps() {
        val frac = LatexMathParser.parse("\\frac{a}{b}")
        assertNotNull(frac)
        val text = frac.toPlainText()
        assertTrue(text.contains("a") && text.contains("b"))

        val aligned = LatexMathParser.parse("\\begin{aligned}\nf'(x) &= 2x && \\text{Power rule}\n\\end{aligned}")
        assertTrue(aligned is LatexNode.Aligned)
        val alignedNode = aligned as LatexNode.Aligned
        assertEquals(1, alignedNode.rows.size)
        assertEquals("Power rule", alignedNode.rows[0].reason)
    }

    @Test
    fun mermaidDiagramParserExtractsNodesAndEdges() {
        val source = """
            graph TD
                A[Start] --> B{Check}
                B -->|Yes| C[End]
        """.trimIndent()
        val diagram = MermaidDiagramParser.parse(source)
        assertEquals(FlowDirection.TOP_DOWN, diagram.direction)
        assertEquals(3, diagram.nodes.size)
        assertTrue(diagram.nodes.any { it.label == "Start" })
        assertTrue(diagram.nodes.any { it.label == "Check" })
        assertTrue(diagram.nodes.any { it.label == "End" })
        assertEquals(2, diagram.edges.size)
    }

    @Test
    fun lessonViewFilterFiltersEnrichedAndHighYield() {
        val lectureBlock = NoteBlock(
            id = "b1",
            noteId = "n1",
            position = 0,
            type = NoteBlockType.PARAGRAPH,
            source = BlockSource.USER,
            content = RichText.plain("Professor explained eigenvalues.")
        )
        val enrichedBlock = NoteBlock(
            id = "b2",
            noteId = "n1",
            position = 1,
            type = NoteBlockType.MATH,
            source = BlockSource.ENRICHED,
            content = RichText.plain("A v = \\lambda v"),
            payload = mapOf(NoteBlock.PAYLOAD_UNPACKED to "0")
        )
        val examTipBlock = NoteBlock(
            id = "b3",
            noteId = "n1",
            position = 2,
            type = NoteBlockType.CALLOUT,
            source = BlockSource.AI,
            content = RichText.plain("Exam questions always test det(A - lambda I) = 0"),
            payload = mapOf(NoteBlock.PAYLOAD_CALLOUT_KIND to "EXAM_TIP", "examHighYield" to "1")
        )

        val all = listOf(lectureBlock, enrichedBlock, examTipBlock)

        // 1. ALL
        val allFiltered = LessonViewFilter.ALL.apply(all)
        assertEquals(3, allFiltered.size)

        // 2. LECTURE_ONLY
        val lectureOnly = LessonViewFilter.LECTURE_ONLY.apply(all)
        assertEquals(2, lectureOnly.size)
        assertFalse(lectureOnly.any { it.source == BlockSource.ENRICHED })

        // 3. EXAM_HIGH_YIELD
        val examHighYield = LessonViewFilter.EXAM_HIGH_YIELD.apply(all)
        assertTrue(examHighYield.contains(examTipBlock))
        assertTrue(examHighYield.contains(enrichedBlock)) // Math is a high yield type
    }

    @Test
    fun enrichedBlockIsFoldedByDefaultUntilUnpacked() {
        val enrichedBlock = NoteBlock(
            id = "b_enrich_1",
            noteId = "n1",
            position = 0,
            type = NoteBlockType.CALLOUT,
            source = BlockSource.ENRICHED,
            content = RichText.plain("Skipped algebraic step bridge"),
            payload = mapOf(NoteBlock.PAYLOAD_ENRICH_LABEL to "Step derivation")
        )

        // Default: PAYLOAD_UNPACKED is not "1"
        assertFalse(enrichedBlock.payload[NoteBlock.PAYLOAD_UNPACKED] == "1")

        // Unpack:
        val unpacked = enrichedBlock.copy(
            payload = enrichedBlock.payload + (NoteBlock.PAYLOAD_UNPACKED to "1")
        )
        assertTrue(unpacked.payload[NoteBlock.PAYLOAD_UNPACKED] == "1")
    }
}
