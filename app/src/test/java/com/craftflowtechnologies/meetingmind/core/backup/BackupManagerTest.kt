package com.craftflowtechnologies.meetingmind.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.DatabaseGuard
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.Attachment
import com.craftflowtechnologies.meetingmind.core.model.AttachmentKind
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.BibleStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        MeetMindDatabase.forget()
        context.deleteDatabase(DatabaseGuard.NAME)
        File(context.filesDir, "notes").deleteRecursively()
    }

    @After
    fun tearDown() {
        MeetMindDatabase.forget()
        context.deleteDatabase(DatabaseGuard.NAME)
    }

    private fun repo() = NoteRepository(context, MeetMindDatabase.getInstance(context))

    private suspend fun seed(): String {
        val notes = repo()
        val note = notes.createNote(title = "Client kickoff")
        notes.saveBlocks(note.id, listOf(
            NoteBlock(NoteRepository.newId("block"), note.id, 0, NoteBlockType.HEADING_1, RichText.plain("Decisions")),
            NoteBlock(NoteRepository.newId("block"), note.id, 1, NoteBlockType.CHECKLIST, RichText.plain("Send proposal"), checked = true)
        ))
        notes.addTag(note.id, "client")
        val picture = File(context.filesDir, "notes/${note.id}/photo.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }
        notes.addAttachment(Attachment("att1", note.id, AttachmentKind.IMAGE, picture.absolutePath, "image/jpeg", 4, null, null, null, "Whiteboard", 1L))
        UserPreferencesManager(context).setUserName("Winston")
        runCatching { BibleStore.get(context).importHighlights("""[{"book":"ROM","chapter":8,"verse":28,"color":"yellow","at":5}]""") }
        return note.id
    }

    private fun backup(options: BackupOptions = BackupOptions()): ByteArray = runBlocking {
        ByteArrayOutputStream().also { BackupManager(context).write(it, options) }.toByteArray()
    }

    @Test
    fun `a backup restores every note, picture, tag and setting`() = runBlocking {
        val noteId = seed()
        val bytes = backup()

        // Lose everything that matters.
        repo().deleteNote(noteId)
        File(context.filesDir, "notes").deleteRecursively()
        UserPreferencesManager(context).setUserName("Someone else")

        val manifest = BackupManager(context).restore(ByteArrayInputStream(bytes))
        assertEquals(1, manifest.counts["notes"])

        val doc = repo().getDocument(noteId)!!
        assertEquals("Client kickoff", doc.note.title)
        assertEquals(listOf("Decisions", "Send proposal"), doc.blocks.map { it.content.text })
        assertTrue(doc.blocks[1].checked)
        assertEquals(listOf("client"), doc.tags.map { it.name })
        val picture = File(doc.attachments.single().path)
        assertTrue(picture.exists())
        assertEquals(listOf<Byte>(1, 2, 3, 4), picture.readBytes().toList())
        assertEquals("Winston", UserPreferencesManager(context).preferencesFlow.first().userName)
    }

    @Test
    fun `the manifest describes the backup and the API key is left out by default`() = runBlocking {
        seed()
        val bytes = backup()
        val names = ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        assertEquals(BackupFormat.MANIFEST, names.first())
        assertTrue(BackupFormat.MAIN_DB in names)
        assertTrue(names.any { it.startsWith("files/notes/") })
        assertFalse(BackupFormat.CREDENTIALS in names)
        val manifest = BackupManager(context).inspect(ByteArrayInputStream(bytes))
        assertEquals(MeetMindDatabase.VERSION, manifest.schemaVersion)
        assertEquals(names.drop(1).toSet(), manifest.checksums.keys)
    }

    @Test
    fun `a damaged backup is refused and nothing changes`() = runBlocking {
        val noteId = seed()
        val bytes = backup()
        val tampered = rewrite(bytes) { name, data -> if (name.startsWith("files/notes/")) byteArrayOf(9, 9) else data }
        repo().renameNote(noteId, "Changed after backup")
        try {
            BackupManager(context).restore(ByteArrayInputStream(tampered))
            fail("A damaged backup was restored")
        } catch (e: BackupRejected) {
            assertTrue(e.message!!.contains("damaged"))
        }
        assertEquals("Changed after backup", repo().getNote(noteId)!!.title)
    }

    @Test
    fun `a backup from a newer app is refused`() = runBlocking {
        seed()
        val newer = rewrite(backup()) { name, data ->
            if (name == BackupFormat.MANIFEST) String(data).replace("\"schema\": ${MeetMindDatabase.VERSION}", "\"schema\": ${MeetMindDatabase.VERSION + 1}").toByteArray() else data
        }
        try {
            BackupManager(context).inspect(ByteArrayInputStream(newer))
            fail("A newer backup was accepted")
        } catch (e: BackupRejected) {
            assertTrue(e.message!!.contains("newer"))
        }
    }

    @Test
    fun `unsafe entry names are refused`() {
        assertFalse(BackupFormat.isSafeEntryName("../evil"))
        assertFalse(BackupFormat.isSafeEntryName("/data/evil"))
        assertFalse(BackupFormat.isSafeEntryName("files/../../evil"))
        assertTrue(BackupFormat.isSafeEntryName("files/notes/n1/photo.jpg"))
    }

    @Test
    fun `paths move to the new install's folder, including inside JSON`() {
        val file = File(context.cacheDir, "paths.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE attachments (path TEXT)")
            db.execSQL("CREATE TABLE meetings (audioFilePath TEXT)")
            db.execSQL("CREATE TABLE notes (metadataJson TEXT)")
            db.execSQL("CREATE TABLE note_blocks (payloadJson TEXT)")
            db.execSQL("INSERT INTO attachments VALUES ('/data/user/0/old.app/files/notes/a.jpg')")
            db.execSQL("INSERT INTO meetings VALUES ('/data/user/0/old.app/files/meetings/m.wav')")
            db.execSQL("INSERT INTO notes VALUES ('{\"image\":\"\\/data\\/user\\/0\\/old.app\\/files\\/devotional_images\\/x.jpg\"}')")
            db.execSQL("INSERT INTO note_blocks VALUES ('{}')")
            BackupManager.movePaths(db, "/data/user/0/old.app/files", "/data/user/0/new.app/files")
            fun one(sql: String) = db.rawQuery(sql, null).use { it.moveToFirst(); it.getString(0) }
            assertEquals("/data/user/0/new.app/files/notes/a.jpg", one("SELECT path FROM attachments"))
            assertEquals("/data/user/0/new.app/files/meetings/m.wav", one("SELECT audioFilePath FROM meetings"))
            assertEquals("{\"image\":\"\\/data\\/user\\/0\\/new.app\\/files\\/devotional_images\\/x.jpg\"}", one("SELECT metadataJson FROM notes"))
        }
    }

    @Test
    fun `settings survive the typed round trip, with paths moved`() {
        val encoded = PrefsCodec.encode(mapOf("b" to true, "i" to 3, "l" to 4L, "f" to 1.5f, "s" to "/old/files/profile/a.jpg", "set" to setOf("WORK", "FAITH")))
        val decoded = PrefsCodec.decode(encoded) { it.replace("/old/files", "/new/files") }
        assertEquals(mapOf("b" to true, "i" to 3, "l" to 4L, "f" to 1.5f, "s" to "/new/files/profile/a.jpg", "set" to setOf("WORK", "FAITH")), decoded)
    }

    @Test
    fun `the Markdown export names files safely and writes front matter`() {
        assertEquals("Q3 plan v2", MarkdownVaultExporter.safeName("Q3 plan: v2"))
        assertEquals("a b", MarkdownVaultExporter.safeName("a/b."))
        val used = mutableSetOf<String>()
        assertEquals("Work/Plan", MarkdownVaultExporter.unique("Work/Plan", used))
        assertEquals("Work/Plan (2)", MarkdownVaultExporter.unique("Work/Plan", used))
    }

    private fun rewrite(bytes: ByteArray, change: (String, ByteArray) -> ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            ZipOutputStream(out).use { zout ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    zout.putNextEntry(ZipEntry(e.name)); zout.write(change(e.name, zin.readBytes())); zout.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
