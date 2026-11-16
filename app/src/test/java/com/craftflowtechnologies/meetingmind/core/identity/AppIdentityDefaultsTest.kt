package com.craftflowtechnologies.meetingmind.core.identity

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One default for "which spaces does this person use". Before this, reads defaulted to all four
 * spaces and the preferences writer to PERSONAL. Both now use [AppIdentity.DEFAULT_SPACES].
 */
class AppIdentityDefaultsTest {

    @Test fun defaultIsAllFourSpaces() {
        assertEquals(NotebookSpace.entries.toSet(), AppIdentity.DEFAULT_SPACES)
        assertEquals(AppIdentity.DEFAULT_SPACES, AppIdentity().spaces)
    }

    @Test fun missingOrEmptyStoredChoiceReadsAsTheDefault() {
        assertEquals(AppIdentity.DEFAULT_SPACES, AppIdentity.parseSpaces(null))
        assertEquals(AppIdentity.DEFAULT_SPACES, AppIdentity.parseSpaces(emptySet()))
        assertEquals(AppIdentity.DEFAULT_SPACES, AppIdentity.parseSpaces(setOf("NOT_A_SPACE")))
    }

    @Test fun validStoredChoiceIsKept() {
        assertEquals(setOf(NotebookSpace.WORK), AppIdentity.parseSpaces(setOf("WORK", "NOPE")))
    }
}
