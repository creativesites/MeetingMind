package com.craftflowtechnologies.meetingmind.feature.notes.editor

import com.craftflowtechnologies.meetingmind.feature.moments.noteMomentSubject
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteEditorMomentHelperTest {
    @Test fun `a loaded unlocked note plans on its first recording`() {
        assertEquals("m2", noteMomentSubject(true, false, listOf("m2", "m3")))
    }

    @Test fun `a note that is loading or locked plans nothing yet`() {
        assertEquals("", noteMomentSubject(false, false, listOf("m2")))
        assertEquals("", noteMomentSubject(true, true, listOf("m2")))
    }

    @Test fun `a note with no recording plans nothing`() {
        assertEquals("", noteMomentSubject(true, false, emptyList()))
    }
}
