package com.example.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTypeTest {

    @Test
    fun `every type except CUSTOM and GENERAL has non-blank focus guidance`() {
        val exempt = setOf(RecordingType.CUSTOM, RecordingType.GENERAL)
        RecordingType.entries.filterNot { it in exempt }.forEach { type ->
            assertTrue("${type.name} must have real focus guidance for the extraction prompt", type.focusGuidance().isNotBlank())
        }
    }

    @Test
    fun `CUSTOM and GENERAL have empty built-in guidance since they defer to user text or nothing`() {
        assertTrue(RecordingType.CUSTOM.focusGuidance().isEmpty())
        assertTrue(RecordingType.GENERAL.focusGuidance().isEmpty())
    }

    @Test
    fun `every type has a non-blank display name and short description for the picker UI`() {
        RecordingType.entries.forEach { type ->
            assertTrue("${type.name} needs a display name", type.displayName.isNotBlank())
            assertTrue("${type.name} needs a short description", type.shortDescription.isNotBlank())
        }
    }
}
