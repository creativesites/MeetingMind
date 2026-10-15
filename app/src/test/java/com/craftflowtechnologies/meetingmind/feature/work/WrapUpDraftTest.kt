package com.craftflowtechnologies.meetingmind.feature.work

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.work.Direction
import com.craftflowtechnologies.meetingmind.core.work.ItemsFixture
import com.craftflowtechnologies.meetingmind.core.work.Settlement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Wrap-up's unsaved choices survive the view-model being recreated from the same SavedStateHandle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WrapUpDraftTest {
    private lateinit var db: MeetMindDatabase

    @Before
    fun setup() {
        db = ItemsFixture.database()
        ItemsFixture.seed(db)
        MeetMindDatabase.setInstanceForTest(db)
    }

    @After
    fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    @Test
    fun settled_codec_round_trips() {
        val m = mapOf("a" to Settlement(Direction.THEIRS, "ana", "Friday"), "b" to Settlement(Direction.MINE))
        assertEquals(m, WrapUpViewModel.decodeSettled(WrapUpViewModel.encodeSettled(m)))
    }

    @Test
    fun draft_is_restored_from_the_saved_state() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val handle = SavedStateHandle()
        val first = WrapUpViewModel(app, "m1", handle)
        first.addSignal("sig1")
        first.settle("sig2", Settlement(Direction.THEIRS, "ana", "Friday"))
        // The state is what a Bundle would carry through process death.
        val restored = SavedStateHandle(mapOf(
            WrapUpViewModel.KEY_ADDED to handle.get<ArrayList<String>>(WrapUpViewModel.KEY_ADDED),
            WrapUpViewModel.KEY_SETTLED to handle.get<String>(WrapUpViewModel.KEY_SETTLED)
        ))
        val second = WrapUpViewModel(app, "m1", restored)
        assertEquals(setOf("sig1"), second.added.value)
        assertEquals(Settlement(Direction.THEIRS, "ana", "Friday"), second.settled.value["sig2"])
    }
}
