package com.craftflowtechnologies.meetingmind.feature.settings

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: Application
    private lateinit var db: MeetMindDatabase

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
    }

    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    private fun shot(name: String, startGroup: String? = null, act: () -> Unit = {}) {
        compose.setContent { MeetMindTheme(darkTheme = false) { SettingsScreen(SettingsViewModel(app), onNavigateBack = {}, startGroup = startGroup) } }
        compose.waitForIdle()
        act()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun `top level is a search field and eight groups`() = shot("settings_top_light")

    @Test fun `faith group shows its few rows then Advanced`() = shot("settings_faith_light", startGroup = "faith")

    @Test fun `search jumps across groups`() = shot("settings_search_light") {
        compose.onNodeWithTag("settings_search").performTextInput("lock")
    }
}
