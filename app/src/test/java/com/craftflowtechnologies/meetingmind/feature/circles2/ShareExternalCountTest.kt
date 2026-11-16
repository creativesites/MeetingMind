package com.craftflowtechnologies.meetingmind.feature.circles2

import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.feature.work.externalCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The share sheet's second confirmation: counts outside people, and never treats an unread list as none. */
class ShareExternalCountTest {
    @Test fun `counts the people outside the organisation`() {
        val members = listOf(
            Member("a", "A", Role.Member, external = true),
            Member("b", "B", Role.Member),
            Member("c", "C", Role.Admin, external = true),
        )
        assertEquals(2, externalCount(DataState.Ready(members)))
        assertEquals(0, externalCount(DataState.Ready(emptyList())))
    }

    @Test fun `a member list that could not be read is unknown, not zero`() {
        assertNull(externalCount(DataState.Loading))
        assertNull(externalCount(DataState.Failed(CirclesFailure.server())))
    }
}
