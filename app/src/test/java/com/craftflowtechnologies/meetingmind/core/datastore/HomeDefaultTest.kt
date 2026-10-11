package com.craftflowtechnologies.meetingmind.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ui.theme.Appearance
import com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** H-1: a new install opens the Everyday home; someone who already had the app keeps Today. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeDefaultTest {
    // One test, in order: the DataStore file is shared by the tests of a run, so a second test would see the first's data.
    @Test
    fun `a new install gets Everyday and an existing install keeps its choice`() = runBlocking {
        val manager = UserPreferencesManager(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(HomeStyle.EVERYDAY, manager.preferencesFlow.first().appearance.homeStyle)

        manager.setOnboardingCompleted(true)
        assertEquals(HomeStyle.EVERYDAY, manager.preferencesFlow.first().appearance.homeStyle)

        manager.setAppearance(Appearance(homeStyle = HomeStyle.TODAY))
        manager.setOnboardingCompleted(true)
        assertEquals(HomeStyle.TODAY, manager.preferencesFlow.first().appearance.homeStyle)
    }
}
