package com.example.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository
import com.example.feature.notes.NotesScope
import com.example.feature.notes.NotesScreen
import com.example.feature.notes.NotesViewModel
import com.example.feature.settings.DataBackupScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screens in both themes (docs/PRD_M0.md §5), so a colour that only works on white — or
 * only on black — shows up in review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ThemeTourScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: Application
    private lateinit var database: MeetMindDatabase

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(database)
        runBlocking {
            val notes = NoteRepository(app, database)
            listOf("Client kickoff" to "Decisions: ship Friday", "Sermon: rest for the weary" to "Matthew 11", "Architecture review" to "Move to Postgres").forEach { (t, body) ->
                val n = notes.createNote(title = t)
                notes.saveBlocks(n.id, listOf(NoteBlock(NoteRepository.newId("b"), n.id, 0, NoteBlockType.PARAGRAPH, RichText.plain(body))))
            }
        }
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        database.close()
    }

    private fun both(name: String, content: @Composable () -> Unit) {
        val dark = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { MeetMindTheme(darkTheme = dark.value) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        for (d in listOf(true, false)) {
            dark.value = d
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/theme_${name}_${if (d) "dark" else "light"}.png")
        }
    }

    @Test fun notes() = both("notes") {
        NotesScreen(NotesViewModel(app, NotesScope.All), {}, {}, {}, null, {})
    }

    @Test fun settings() = both("settings") { SettingsScreen(SettingsViewModel(app), onNavigateBack = {}) }

    @Test fun dataBackup() = both("data_backup") { DataBackupScreen(onNavigateBack = {}, onOpenTrash = {}) }
}
