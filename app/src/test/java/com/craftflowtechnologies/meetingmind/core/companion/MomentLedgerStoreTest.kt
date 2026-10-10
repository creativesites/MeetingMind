package com.craftflowtechnologies.meetingmind.core.companion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MomentLedgerStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `recorded moments survive a reload`() = runBlocking {
        val file = tmp.newFolder().resolve("moments.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = MomentLedgerStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        val clock = LocalClock(5_000)
        store.record(Moment.FirstRecordingPeak, clock)
        store.record(Moment.Proud(MilestoneKind.FIRST_NOTE), clock)
        val ledger = store.ledger.first()
        assertFalse(ledger.allows(Moment.FirstRecordingPeak, clock))
        assertFalse(ledger.allows(Moment.Proud(MilestoneKind.FIRST_NOTE), clock))
        assertTrue(ledger.allows(Moment.Proud(MilestoneKind.TENTH_NOTE), clock))
        scope.cancel()
    }
}
