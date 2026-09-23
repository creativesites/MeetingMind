package com.example.feature.notes

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository
import com.example.core.ui.CreateSheet
import com.example.feature.notes.editor.NoteEditorScreen
import com.example.feature.notes.editor.NoteEditorViewModel
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class NotesUiTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var app: Application
    private lateinit var database: MeetMindDatabase
    private lateinit var notes: NoteRepository

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(database)
        notes = NoteRepository(app, database)
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        database.close()
    }

    private fun block(noteId: String, type: NoteBlockType, text: RichText, checked: Boolean = false, indent: Int = 0) =
        NoteBlock(NoteRepository.newId("block"), noteId, 0, type, text, checked = checked, indent = indent)

    private fun sampleNote(): String = runBlocking {
        val note = notes.createNote(title = "Faith that holds")
        val id = note.id
        notes.saveBlocks(
            id,
            listOf(
                block(id, NoteBlockType.HEADING_1, RichText.plain("Key points")),
                block(id, NoteBlockType.PARAGRAPH, RichText.plain("Faith is trust in what we cannot yet see.").applyStyle(InlineStyle.BOLD, 0, 5).applyStyle(InlineStyle.HIGHLIGHT, 18, 23)),
                block(id, NoteBlockType.NUMBERED, RichText.plain("Hear the word")),
                block(id, NoteBlockType.NUMBERED, RichText.plain("Hold on to it")),
                block(id, NoteBlockType.BULLET, RichText.plain("Even when it's hard"), indent = 1),
                block(id, NoteBlockType.CHECKLIST, RichText.plain("Read Hebrews 11"), checked = true),
                block(id, NoteBlockType.CHECKLIST, RichText.plain("Call Mum")),
                block(id, NoteBlockType.QUOTE, RichText.plain("Now faith is confidence in what we hope for.")),
                block(id, NoteBlockType.DIVIDER, RichText.EMPTY),
                block(id, NoteBlockType.PARAGRAPH, RichText.plain("Last line"))
            )
        )
        notes.addTag(id, "grace")
        id
    }

    @Test
    fun editor_renders_a_rich_note_and_saves_typing() {
        val id = sampleNote()
        val vm = NoteEditorViewModel(app, id)
        compose.setContent {
            MeetMindTheme {
                NoteEditorScreen(viewModel = vm, onNavigateBack = {}, onOpenRecording = { _, _ -> }, onOpenNote = {}, onRecordHere = {})
            }
        }
        compose.waitUntil(5_000) { vm.loaded.value }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/note_editor.png")

        // Type at the end of the last paragraph, then press Enter: a new paragraph appears.
        val last = compose.onNode(hasSetTextAction() and hasText("Last line", substring = true))
        last.performClick()
        last.performTextInput(" and more")
        compose.waitForIdle()
        assertTrue(vm.blocks.value.any { it.content.text.startsWith("Last line") && it.content.text.contains("and more") })
        compose.onRoot().captureRoboImage("src/test/screenshots/note_editor_typing.png")

        runBlocking { vm.flush() }
        val saved = runBlocking { notes.getDocument(id)!! }
        assertTrue(saved.note.plainText.contains("and more"))
        assertEquals(1, saved.tags.size)
    }

    @Test
    fun library_lists_notes_and_notebooks() {
        sampleNote()
        runBlocking {
            notes.createNotebook("Sermons", com.example.core.model.NotebookSpace.FAITH, "#10B981")
            notes.createNote(title = "Team sync").let { notes.setPinned(it.id, true) }
            notes.createNote(title = "Prayer for the week", isPrivate = true)
        }
        val vm = NotesViewModel(app)
        compose.setContent {
            MeetMindTheme {
                NotesScreen(viewModel = vm, onOpenNote = {}, onOpenNotebook = {}, onOpenArchive = {}, onNavigateBack = null, onNavigateBottomNav = {})
            }
        }
        compose.waitUntil(5_000) { vm.visibleNotes.value.size == 3 }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/notes_library.png")
        assertEquals("Team sync", vm.visibleNotes.value.first().title)
    }

    @Test
    fun create_sheet_offers_record_first() {
        compose.setContent { MeetMindTheme { CreateSheet(onPick = {}, onDismiss = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/create_sheet.png")
    }
}
