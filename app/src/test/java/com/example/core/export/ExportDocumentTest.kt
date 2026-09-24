package com.example.core.export

import com.example.core.model.Attachment
import com.example.core.model.AttachmentKind
import com.example.core.model.BlockSource
import com.example.core.model.Note
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NoteDocument
import com.example.core.model.NoteStatus
import com.example.core.model.RecordingType
import com.example.core.model.ScriptureOrigin
import com.example.core.model.ScriptureRef
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

class ExportDocumentTest {

    @get:Rule val temp = TemporaryFolder()

    private fun rich(text: String) = RichText.plain(text)

    private val sample = ExportDocument(
        title = "Faith that holds",
        subtitle = "Sunday 14 September 2025  ·  Pastor James",
        blocks = listOf(
            ExportBlock.Heading(1, rich("Key points")),
            ExportBlock.ListItem(ListKind.NUMBERED, rich("Faith is trust")),
            ExportBlock.ListItem(ListKind.NUMBERED, rich("Trust grows")),
            ExportBlock.Paragraph(RichText.plain("Read the docs & <notes>").applyStyle(InlineStyle.LINK, 9, 13, "https://example.test/a?b=1&c=2")),
            ExportBlock.ListItem(ListKind.NUMBERED, rich("Starts again")),
            ExportBlock.ListItem(ListKind.CHECKLIST, rich("Pray daily"), checked = true),
            ExportBlock.Scripture("Hebrews 11:1", "Now faith is confidence in what we hope for.", "NIV"),
            ExportBlock.Scripture("Romans 8:28", null, null),
            ExportBlock.Quote(rich("Grace is not earned.")),
            ExportBlock.Divider
        ),
        closingNotes = listOf("Scripture quotations taken from The Holy Bible, New International Version® NIV®.")
    )

    // ------------------------------------------------------------------ markdown

    @Test
    fun `markdown numbers lists and restarts after other content`() {
        val md = MarkdownDocumentRenderer.render(sample)
        assertTrue(md.startsWith("# Faith that holds\n\n_Sunday"))
        assertTrue(md.contains("1. Faith is trust\n2. Trust grows\n"))
        assertTrue(md.contains("1. Starts again\n- [x] Pray daily\n"))
        assertTrue(md.contains("> **Hebrews 11:1 (NIV)**  \n> Now faith"))
        assertTrue(md.contains("> _Verse text unavailable offline._"))
        assertTrue(md.contains("[docs](https://example.test/a?b=1&c=2)"))
        assertTrue(md.contains("<sub>Scripture quotations"))
    }

    // ------------------------------------------------------------------ docx

    private fun docxParts(document: ExportDocument): Map<String, ByteArray> {
        val out = ByteArrayOutputStream()
        DocxDocumentRenderer.render(document, out)
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { parts[it.name] = zip.readBytes() }
        }
        return parts
    }

    @Test
    fun `docx has every required part and all xml is well formed`() {
        val parts = docxParts(sample)
        listOf(
            "[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels",
            "word/styles.xml", "word/numbering.xml", "word/footer1.xml", "docProps/core.xml"
        ).forEach { assertTrue("missing $it", it in parts) }

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (name, bytes) ->
            runCatching { factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)) }
                .onFailure { throw AssertionError("$name is not well-formed XML", it) }
        }
    }

    @Test
    fun `docx uses real heading styles, lists that restart, and escaped external links`() {
        val parts = docxParts(sample)
        val body = String(parts.getValue("word/document.xml"))
        val rels = String(parts.getValue("word/_rels/document.xml.rels"))
        val numbering = String(parts.getValue("word/numbering.xml"))

        assertTrue(body.contains("<w:pStyle w:val=\"Heading1\"/>"))
        // Two separate numbered runs → two list instances, each restarting at 1.
        assertTrue(body.contains("<w:numId w:val=\"2\"/>"))
        assertTrue(body.contains("<w:numId w:val=\"3\"/>"))
        assertTrue(numbering.contains("<w:num w:numId=\"3\">"))
        assertTrue(body.contains("<w:hyperlink r:id=\"rIdLink1\""))
        assertTrue(rels.contains("Target=\"https://example.test/a?b=1&amp;c=2\" TargetMode=\"External\""))
        assertTrue(body.contains("Read the "))
        assertTrue(body.contains("&amp; &lt;notes&gt;"))
        assertTrue(body.contains("☑"))
        assertTrue(body.contains("Verse text unavailable offline."))
        assertTrue(body.contains("New International Version"))
    }

    @Test
    fun `docx embeds images at their aspect ratio and declares their type`() {
        val png = temp.newFile("photo.png")
        ImageIO.write(BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB), "png", png)
        val parts = docxParts(ExportDocument("Photos", blocks = listOf(ExportBlock.Image(png.path, "Baptism Sunday"))))

        assertTrue("word/media/image1.png" in parts)
        assertEquals(png.length().toInt(), parts.getValue("word/media/image1.png").size)
        assertTrue(String(parts.getValue("[Content_Types].xml")).contains("Extension=\"png\" ContentType=\"image/png\""))
        val body = String(parts.getValue("word/document.xml"))
        // Filled to the text column (9026 twips = 5,731,510 EMU); half as tall.
        assertTrue(body.contains("<wp:extent cx=\"5731510\" cy=\"2865755\"/>"))
        assertTrue(body.contains("Baptism Sunday"))
    }

    @Test
    fun `a missing image becomes a note in the text rather than a broken file`() {
        val parts = docxParts(ExportDocument("Photos", blocks = listOf(ExportBlock.Image("/nope/gone.jpg", null))))
        assertFalse(parts.keys.any { it.startsWith("word/media/") })
        assertTrue(String(parts.getValue("word/document.xml")).contains("Image not available: gone.jpg"))
    }

    @Test
    fun `characters xml forbids are dropped instead of corrupting the file`() {
        val parts = docxParts(ExportDocument("Bad \u0001 chars", blocks = listOf(ExportBlock.Paragraph(rich("a\u0000b")))))
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(parts.getValue("word/document.xml")))
    }

    @Test
    fun `image header reader measures png and jpeg without decoding`() {
        val png = temp.newFile("a.png").also { ImageIO.write(BufferedImage(31, 17, BufferedImage.TYPE_INT_RGB), "png", it) }
        val jpg = temp.newFile("a.jpg").also { ImageIO.write(BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB), "jpg", it) }
        assertEquals(ImageInfo(31, 17, "png", "image/png"), ImageInfo.read(png))
        assertEquals(ImageInfo(640, 480, "jpeg", "image/jpeg"), ImageInfo.read(jpg))
        assertEquals(null, ImageInfo.read(temp.newFile("x.txt").also { it.writeText("hello") }))
    }

    // ------------------------------------------------------------------ note mapping

    private fun note(blocks: List<NoteBlock>, refs: List<ScriptureRef> = emptyList(), attachments: List<Attachment> = emptyList()) =
        NoteDocument(
            note = Note(
                id = "n1", title = "Sermon", workflow = RecordingType.LECTURE, notebookId = null,
                createdAt = 0, updatedAt = 0, eventDate = 1_757_851_200_000, pinned = false, isPrivate = false,
                status = NoteStatus.OPEN, answeredAt = null, metadata = mapOf("speaker" to "Pastor James")
            ),
            blocks = blocks, attachments = attachments, tags = emptyList(), scriptureRefs = refs
        )

    private fun block(type: NoteBlockType, text: String = "", position: Int = 0, payload: Map<String, String> = emptyMap(), section: String? = null) =
        NoteBlock(id = "b$position", noteId = "n1", position = position, type = type, content = RichText.plain(text), payload = payload, source = BlockSource.USER, sectionKey = section)

    @Test
    fun `note mapping keeps order, drops private sections and edge padding`() {
        val doc = note(
            listOf(
                block(NoteBlockType.PARAGRAPH, "", 0),
                block(NoteBlockType.HEADING_2, "My prayer", 2, section = "prayer"),
                block(NoteBlockType.PARAGRAPH, "Lord, help me", 3, section = "prayer"),
                block(NoteBlockType.HEADING_1, "Notes", 1),
                block(NoteBlockType.PARAGRAPH, "", 4)
            )
        )
        val options = NoteExportOptions(privateSectionKeys = setOf("prayer"))

        val shared = NoteExportMapper.map(doc, options = options, locale = Locale.UK)
        assertEquals(listOf(ExportBlock.Heading(1, RichText.plain("Notes"))), shared.blocks)
        assertEquals("Sunday 14 September 2025  ·  Pastor James", shared.subtitle)

        val all = NoteExportMapper.map(doc, options = options.copy(includePrivateSections = true), locale = Locale.UK)
        assertEquals(3, all.blocks.size)
    }

    @Test
    fun `scripture uses fetched text and attribution, or falls back to the reference`() {
        val ref = ScriptureRef("r1", "n1", null, "HEB", 11, 1, null, 111, ScriptureOrigin.DETECTED, createdAt = 0)
        val doc = note(listOf(block(NoteBlockType.SCRIPTURE, position = 0, payload = mapOf(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID to "r1"))), refs = listOf(ref))

        val offline = NoteExportMapper.map(doc)
        assertEquals(ExportBlock.Scripture("Hebrews 11:1", null, null), offline.blocks.single())

        val online = NoteExportMapper.map(doc, passages = mapOf("r1" to ExportPassage("Hebrews 11:1", "Now faith…", "NIV", "© Biblica")))
        assertEquals(ExportBlock.Scripture("Hebrews 11:1", "Now faith…", "NIV"), online.blocks.single())
        assertEquals(listOf("© Biblica"), online.closingNotes)
    }

    @Test
    fun `recordings, images and excerpts map to their export blocks`() {
        val image = Attachment("a1", "n1", AttachmentKind.IMAGE, "/x/p.jpg", "image/jpeg", 10, caption = "Choir", createdAt = 0)
        val doc = note(
            listOf(
                block(NoteBlockType.RECORDING, position = 0, payload = mapOf(NoteBlock.PAYLOAD_MEETING_ID to "m1")),
                block(NoteBlockType.IMAGE, position = 1, payload = mapOf(NoteBlock.PAYLOAD_ATTACHMENT_ID to "a1")),
                block(NoteBlockType.TRANSCRIPT_EXCERPT, "Grace is enough", 2, mapOf(NoteBlock.PAYLOAD_SPEAKER to "Pastor", NoteBlock.PAYLOAD_START_MS to "65000"))
            ),
            attachments = listOf(image)
        )
        val out = NoteExportMapper.map(doc, recordings = mapOf("m1" to RecordingExportInfo("Sunday service", 3_600_000, "About grace")))
        assertEquals(ExportBlock.Recording("Sunday service", "01:00:00", "About grace"), out.blocks[0])
        assertEquals(ExportBlock.Image("/x/p.jpg", "Choir"), out.blocks[1])
        assertEquals(ExportBlock.Excerpt("Pastor · 01:05", "Grace is enough"), out.blocks[2])
    }

    @Test
    fun `a chosen cover leads, typed verse text is kept, screenshots are cropped`() {
        val cover = Attachment("c1", "n1", AttachmentKind.IMAGE, "/x/cover.jpg", "image/jpeg", 10, createdAt = 0)
        val shot = Attachment("s1", "n1", AttachmentKind.IMAGE, "/x/shot.png", "image/png", 10, createdAt = 0)
        val base = note(
            listOf(
                block(NoteBlockType.SCRIPTURE, position = 0, payload = mapOf("reference" to "Psalm 23:1", NoteBlock.PAYLOAD_USER_TEXT to "The Lord is my shepherd", NoteBlock.PAYLOAD_USER_LABEL to "NIV")),
                block(NoteBlockType.IMAGE, position = 1, payload = mapOf(NoteBlock.PAYLOAD_ATTACHMENT_ID to "s1"))
            ),
            attachments = listOf(cover, shot)
        )
        val doc = base.copy(note = base.note.copy(metadata = base.note.metadata + (com.example.core.repository.NoteRepository.COVER_KEY to "c1")))
        val out = NoteExportMapper.map(doc, crops = mapOf("s1" to (80 to 120)))
        assertEquals(ExportBlock.Image("/x/cover.jpg", null), out.blocks[0])
        assertEquals(ExportBlock.Scripture("Psalm 23:1", "The Lord is my shepherd", "NIV"), out.blocks[1])
        assertEquals(ExportBlock.Image("/x/shot.png", null, 80, 120), out.blocks[2])
    }

    @Test
    fun `only pictures the exact size of the screen count as screenshots`() {
        assertEquals(80 to 120, ScreenshotBars.crop(1080, 2400, 1080, 2400, 80, 120))
        assertEquals(80 to 120, ScreenshotBars.crop(1080, 2400, 2400, 1080, 80, 120))
        assertEquals(null, ScreenshotBars.crop(1080, 1920, 1080, 2400, 80, 120))
        assertEquals(null, ScreenshotBars.crop(0, 0, 1080, 2400, 80, 120))
    }
}
