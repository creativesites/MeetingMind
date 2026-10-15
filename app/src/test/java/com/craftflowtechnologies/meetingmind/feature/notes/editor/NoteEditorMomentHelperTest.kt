package com.craftflowtechnologies.meetingmind.feature.notes.editor

import com.craftflowtechnologies.meetingmind.feature.moments.noteMomentSubject
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteEditorMomentHelperTest {
    @Test fun `a loaded unlocked note plans on its first recording`() {
        assertEquals("m2", noteMomentSubject(true, false, listOf("m2", "m3"), noteId = "n1"))
    }

    @Test fun `a note that is loading or locked plans nothing yet`() {
        assertEquals("", noteMomentSubject(false, false, listOf("m2"), noteId = "n1"))
        assertEquals("", noteMomentSubject(true, true, listOf("m2"), noteId = "n1"))
    }

    @Test fun `a note with no recording plans on the note itself`() {
        assertEquals("n1", noteMomentSubject(true, false, emptyList(), noteId = "n1"))
    }
}
