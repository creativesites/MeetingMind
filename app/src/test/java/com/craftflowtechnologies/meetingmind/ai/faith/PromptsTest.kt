package com.craftflowtechnologies.meetingmind.ai.faith

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Keeps the manifest honest: every prompt asset on disk is listed, and every id on the list loads. */
class PromptsTest {
    @Test fun `ALL lists exactly the prompt assets on disk`() {
        val onDisk = File("src/main/assets/prompts").listFiles { f -> f.extension == "md" }
            .orEmpty().map { it.nameWithoutExtension }.toSet()
        assertEquals(onDisk, Prompts.ALL.toSet())
    }

    @Test fun `every listed prompt loads`() {
        Prompts.ALL.forEach { id -> Prompts.get(id) }
    }
}
