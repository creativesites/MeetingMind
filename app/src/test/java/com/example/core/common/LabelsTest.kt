package com.example.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelsTest {
    @Test fun `empty and junk labels are dropped`() {
        listOf(null, "", "  ", "[]", "{}", "null", "N/A", "\"\"", "--", "[ ]").forEach { assertNull(it, Labels.clean(it)) }
    }

    @Test fun `real labels are kept, trimmed of wrapping`() {
        assertEquals("leadership", Labels.clean(" leadership "))
        assertEquals("grace", Labels.clean("[\"grace\"]"))
    }
}
