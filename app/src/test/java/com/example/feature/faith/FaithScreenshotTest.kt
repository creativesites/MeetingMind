package com.example.feature.faith

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
@Config(qualifiers = "w411dp-h1600dp-xxhdpi", sdk = [34])
class FaithScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MeetMindDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    @Test fun `faith home begins beautifully`() {
        val vm = FaithViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { MeetMindTheme { FaithScreen(vm, {}, {}, {}, {}, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/faith_home.png")
    }
}
