package com.example.core.model

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
    fun `clean transcript is the only tool marked ready today`() {
        val ready = TranscriptAiToolType.entries.filter { it.readiness == TranscriptAiToolReadiness.READY }
        assertTrue(ready == listOf(TranscriptAiToolType.CLEAN_TRANSCRIPT))
    }
}
