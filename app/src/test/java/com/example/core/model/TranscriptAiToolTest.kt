package com.example.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture-prep only (see [TranscriptAiTool.kt]'s own doc) — this just pins down the registry
 * itself stays internally consistent as entries are added, not any AI behavior.
 */
class TranscriptAiToolTest {

    @Test
    fun `every tool has a non-blank label and description`() {
        TranscriptAiToolType.entries.forEach { tool ->
            assertTrue("${tool.name} label must not be blank", tool.label.isNotBlank())
            assertTrue("${tool.name} description must not be blank", tool.description.isNotBlank())
        }
    }

    @Test
    fun `byCategory groups every tool exactly once`() {
        val grouped = TranscriptAiToolRegistry.byCategory()
        val total = grouped.values.sumOf { it.size }
        assertTrue(total == TranscriptAiToolType.entries.size)
        assertTrue(TranscriptAiToolCategory.entries.all { it in grouped })
    }

    @Test
    fun `every tool in the menu is backed by something that actually runs`() {
        // Readiness now says what runs a tool, not how finished it is. There are no placeholders
        // left in this menu, so there is no state meaning "does nothing yet" for a row to be in.
        assertTrue(TranscriptAiToolType.entries.all { it.readiness in TranscriptAiToolReadiness.entries })
        assertTrue(
            "every category should have at least one tool",
            TranscriptAiToolCategory.entries.all { category ->
                TranscriptAiToolType.entries.any { it.category == category }
            }
        )
    }

    @Test
    fun `the tools that need no model are the deterministic and stored-data ones`() {
        val noModel = TranscriptAiToolType.entries.filter { it.readiness != TranscriptAiToolReadiness.MODEL_BACKED }

        assertTrue(TranscriptAiToolType.CLEAN_TRANSCRIPT in noModel)
        assertTrue(TranscriptAiToolType.FIX_TERMINOLOGY in noModel)
        assertTrue(TranscriptAiToolType.EXPAND_CONTEXT in noModel)
        assertTrue(TranscriptAiToolType.FIND_DECISIONS in noModel)
        assertTrue(TranscriptAiToolType.FIND_QUESTIONS in noModel)
        assertTrue(TranscriptAiToolType.FIND_ACTION_ITEMS in noModel)
        assertTrue(TranscriptAiToolType.IDENTIFY_TOPICS in noModel)
        assertEquals(7, noModel.size)
    }

    @Test
    fun `every tool has a label and a plain-language description`() {
        for (tool in TranscriptAiToolType.entries) {
            assertTrue("${tool.name} has no label", tool.label.isNotBlank())
            assertTrue("${tool.name} has no description", tool.description.isNotBlank())
        }
    }
}
