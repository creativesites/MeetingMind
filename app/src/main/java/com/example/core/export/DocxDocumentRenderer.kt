package com.example.core.export

import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an [ExportDocument] as a Word (.docx) file, by hand, with no library.
 *
 * Unlike the older [DocxExporter], this writes real styles (so headings appear in Word's
 * navigation pane and restyle together), real lists (so Word renumbers them when edited),
 * working hyperlinks, embedded images and a page-number footer. The output opens in Word, Google
 * Docs, Pages and LibreOffice.
 */
object DocxDocumentRenderer {

    private const val ACCENT = "3E5BA9"
    private const val MUTED = "6B6B6B"
    // A4 with one-inch margins: the text column is 9026 twentieths of a point wide.
    private const val CONTENT_WIDTH_EMU = 9026L * 635L
    private const val MAX_IMAGE_HEIGHT_EMU = 8L * 914400L

    private const val REL_STYLES = "rIdStyles"
    private const val REL_NUMBERING = "rIdNumbering"
    private const val REL_FOOTER = "rIdFooter"

    fun render(document: ExportDocument, out: OutputStream) {
        val writer = BodyWriter(document)
        writer.writeAll()
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(writer.media))
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("docProps/core.xml", coreProps(document.title))
            zip.put("word/document.xml", writer.documentXml())
            zip.put("word/_rels/document.xml.rels", writer.relationshipsXml())
            zip.put("word/styles.xml", stylesXml(document.serifBody))
            zip.put("word/numbering.xml", writer.numberingXml())
            zip.put("word/footer1.xml", FOOTER)
            writer.media.forEach { m ->
                zip.putNextEntry(ZipEntry("word/media/${m.fileName}"))
                File(m.sourcePath).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private class Media(val relId: String, val fileName: String, val sourcePath: String, val info: ImageInfo)

    private class BodyWriter(private val document: ExportDocument) {
        private val body = StringBuilder()
        private val hyperlinks = linkedMapOf<String, String>() // url -> relId
        val media = mutableListOf<Media>()
        /** One Word list instance per run of numbered items, so each run restarts at 1. */
        private var numberedInstances = 0
        private var currentNumberedInstance = -1
        private var drawingId = 1

        fun writeAll() {
            paragraph(style = "Title", runs = plainRuns(document.title.ifBlank { "Untitled" }))
            document.subtitle?.let { paragraph(style = "Subtitle", runs = plainRuns(it)) }

            var previousNumbered = false
            for (block in document.blocks) {
                val isNumbered = block is ExportBlock.ListItem && block.kind == ListKind.NUMBERED
                if (isNumbered && !previousNumbered) currentNumberedInstance = ++numberedInstances
                // Other list items between numbered ones keep the numbering running, as in the editor.
                previousNumbered = isNumbered || (previousNumbered && block is ExportBlock.ListItem)
                write(block)
            }

            if (document.closingNotes.isNotEmpty()) {
                divider()
                document.closingNotes.forEach { paragraph(style = "Attribution", runs = plainRuns(it)) }
            }
        }

        private fun write(block: ExportBlock) {
            when (block) {
                is ExportBlock.Heading -> paragraph(style = "Heading${block.level.coerceIn(1, 3)}", runs = richRuns(block.text))
                is ExportBlock.Paragraph -> paragraph(runs = richRuns(block.text))
                is ExportBlock.ListItem -> listItem(block)
                is ExportBlock.Quote -> paragraph(style = "Quote", runs = richRuns(block.text))
                is ExportBlock.Scripture -> {
                    val version = block.versionAbbreviation?.let { " ($it)" } ?: ""
                    paragraph(style = "ScriptureReference", runs = plainRuns(block.reference + version), keepNext = true)
                    if (block.text != null) paragraph(style = "Scripture", runs = plainRuns(block.text))
                    else paragraph(style = "Scripture", runs = run("Verse text unavailable offline.", italic = true, color = MUTED))
                }
                is ExportBlock.Excerpt -> {
                    block.label?.let { paragraph(style = "Caption", runs = plainRuns(it), keepNext = true) }
                    paragraph(style = "Quote", runs = plainRuns(block.text))
                }
                is ExportBlock.Image -> image(block)
                is ExportBlock.Recording -> {
                    val detail = block.detail?.let { " · $it" } ?: ""
                    paragraph(runs = run("Recording: ", bold = true, color = ACCENT) + run(block.title + detail, bold = true), keepNext = block.summary != null)
                    block.summary?.takeIf { it.isNotBlank() }?.let { paragraph(runs = plainRuns(it)) }
                }
                is ExportBlock.Facts -> block.rows.forEach { (k, v) ->
                    paragraph(style = "Fact", runs = run("$k: ", bold = true) + run(v))
                }
                ExportBlock.Divider -> divider()
            }
        }

        private fun listItem(item: ExportBlock.ListItem) {
            val level = item.indent.coerceIn(0, 8)
            when (item.kind) {
                ListKind.BULLET -> paragraph(style = "ListParagraph", numbering = BULLET_NUM_ID to level, runs = richRuns(item.text))
                ListKind.NUMBERED -> paragraph(
                    style = "ListParagraph",
                    numbering = (FIRST_NUMBERED_NUM_ID + currentNumberedInstance - 1) to level,
                    runs = richRuns(item.text)
                )
                ListKind.CHECKLIST -> paragraph(
                    style = "ListParagraph",
                    indentTwips = 360 + level * 360,
                    runs = run(if (item.checked) "☑  " else "☐  ") + richRuns(item.text, struck = item.checked)
                )
            }
        }

        private fun divider() {
            body.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"D0D0D0\"/></w:pBdr>")
                .append("<w:spacing w:before=\"120\" w:after=\"240\"/></w:pPr></w:p>")
        }

        private fun image(block: ExportBlock.Image) {
            val file = File(block.path)
            val info = if (file.isFile) ImageInfo.read(file) else null
            if (info == null) {
                paragraph(runs = run("[Image not available: ${file.name}]", italic = true, color = MUTED))
                return
            }
            val index = media.size + 1
            val m = Media("rIdImage$index", "image$index.${info.extension}", file.path, info)
            media += m
            // Fit to the text column, never enlarge, and keep a tall photo on one page.
            var cx = minOf(CONTENT_WIDTH_EMU, info.width.toLong() * 9525L)
            var cy = cx * info.height / info.width
            if (cy > MAX_IMAGE_HEIGHT_EMU) {
                cy = MAX_IMAGE_HEIGHT_EMU
                cx = cy * info.width / info.height
            }
            val id = drawingId++
            val name = xml(block.caption ?: file.name)
            body.append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:keepNext/></w:pPr><w:r><w:drawing>")
                .append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/>")
                .append("<wp:docPr id=\"$id\" name=\"Picture $id\" descr=\"$name\"/>")
                .append("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
                .append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                .append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"${m.fileName}\"/><pic:cNvPicPr/></pic:nvPicPr>")
                .append("<pic:blipFill><a:blip r:embed=\"${m.relId}\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
                .append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>")
                .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
                .append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
            block.caption?.takeIf { it.isNotBlank() }?.let { paragraph(style = "Caption", runs = plainRuns(it), center = true) }
        }

        private fun paragraph(
            style: String? = null,
            runs: String,
            numbering: Pair<Int, Int>? = null,
            indentTwips: Int? = null,
            keepNext: Boolean = false,
            center: Boolean = false
        ) {
            body.append("<w:p><w:pPr>")
            style?.let { body.append("<w:pStyle w:val=\"$it\"/>") }
            if (keepNext) body.append("<w:keepNext/>")
            numbering?.let { (numId, level) -> body.append("<w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"$numId\"/></w:numPr>") }
            indentTwips?.let { body.append("<w:ind w:left=\"$it\"/>") }
            if (center) body.append("<w:jc w:val=\"center\"/>")
            body.append("</w:pPr>").append(runs).append("</w:p>")
        }

        private fun plainRuns(text: String): String = run(text)

        private fun richRuns(text: RichText, struck: Boolean = false): String = buildString {
            for (r in text.runs()) {
                val link = r.url?.takeIf { InlineStyle.LINK in r.styles }
                val runXml = run(
                    text = r.text,
                    bold = InlineStyle.BOLD in r.styles,
                    italic = InlineStyle.ITALIC in r.styles,
                    underline = InlineStyle.UNDERLINE in r.styles,
                    strike = struck || InlineStyle.STRIKETHROUGH in r.styles,
                    highlight = InlineStyle.HIGHLIGHT in r.styles,
                    code = InlineStyle.CODE in r.styles,
                    hyperlink = link != null
                )
                if (link != null) {
                    val relId = hyperlinks.getOrPut(link) { "rIdLink${hyperlinks.size + 1}" }
                    append("<w:hyperlink r:id=\"$relId\" w:history=\"1\">").append(runXml).append("</w:hyperlink>")
                } else {
                    append(runXml)
                }
            }
        }

        private fun run(
            text: String,
            bold: Boolean = false,
            italic: Boolean = false,
            underline: Boolean = false,
            strike: Boolean = false,
            highlight: Boolean = false,
            code: Boolean = false,
            hyperlink: Boolean = false,
            color: String? = null
        ): String = buildString {
            append("<w:r>")
            val props = buildString {
                if (hyperlink) append("<w:rStyle w:val=\"Hyperlink\"/>")
                if (code) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:cs=\"Consolas\"/>")
                if (bold) append("<w:b/>")
                if (italic) append("<w:i/>")
                if (strike) append("<w:strike/>")
                color?.let { append("<w:color w:val=\"$it\"/>") }
                if (highlight) append("<w:highlight w:val=\"yellow\"/>")
                if (underline) append("<w:u w:val=\"single\"/>")
                if (code) append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/>")
            }
            if (props.isNotEmpty()) append("<w:rPr>").append(props).append("</w:rPr>")
            // Line breaks inside a block become Word line breaks, not new paragraphs.
            text.split('\n').forEachIndexed { i, line ->
                if (i > 0) append("<w:br/>")
                append("<w:t xml:space=\"preserve\">").append(xml(line)).append("</w:t>")
            }
            append("</w:r>")
        }

        fun documentXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr><w:footerReference w:type="default" r:id="$REL_FOOTER"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>"""

        fun relationshipsXml(): String = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="$REL_STYLES" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            append("""<Relationship Id="$REL_NUMBERING" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""")
            append("""<Relationship Id="$REL_FOOTER" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""")
            hyperlinks.forEach { (url, id) ->
                append("""<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${xml(url)}" TargetMode="External"/>""")
            }
            media.forEach { m ->
                append("""<Relationship Id="${m.relId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${m.fileName}"/>""")
            }
            append("</Relationships>")
        }

        fun numberingXml(): String = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            append("""<w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/>""")
            val bullets = listOf("•", "◦", "▪")
            for (level in 0..8) {
                append("""<w:lvl w:ilvl="$level"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="${bullets[level % 3]}"/>""")
                append("""<w:lvlJc w:val="left"/><w:pPr><w:ind w:left="${720 + level * 360}" w:hanging="360"/></w:pPr></w:lvl>""")
            }
            append("</w:abstractNum>")
            append("""<w:abstractNum w:abstractNumId="1"><w:multiLevelType w:val="hybridMultilevel"/>""")
            val formats = listOf("decimal", "lowerLetter", "lowerRoman")
            for (level in 0..8) {
                append("""<w:lvl w:ilvl="$level"><w:start w:val="1"/><w:numFmt w:val="${formats[level % 3]}"/><w:lvlText w:val="%${level + 1}."/>""")
                append("""<w:lvlJc w:val="left"/><w:pPr><w:ind w:left="${720 + level * 360}" w:hanging="360"/></w:pPr></w:lvl>""")
            }
            append("</w:abstractNum>")
            append("""<w:num w:numId="$BULLET_NUM_ID"><w:abstractNumId w:val="0"/></w:num>""")
            for (i in 1..numberedInstances) {
                append("""<w:num w:numId="${FIRST_NUMBERED_NUM_ID + i - 1}"><w:abstractNumId w:val="1"/>""")
                for (level in 0..8) append("""<w:lvlOverride w:ilvl="$level"><w:startOverride w:val="1"/></w:lvlOverride>""")
                append("</w:num>")
            }
            append("</w:numbering>")
        }
    }

    private const val BULLET_NUM_ID = 1
    private const val FIRST_NUMBERED_NUM_ID = 2

    private fun contentTypes(media: List<Media>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        media.map { it.info }.distinctBy { it.extension }.forEach {
            append("""<Default Extension="${it.extension}" ContentType="${it.contentType}"/>""")
        }
        append("""<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""")
        append("""<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""")
        append("""<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>""")
        append("""<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>""")
        append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
        append("</Types>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>"""

    private const val FOOTER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:color w:val="$MUTED"/><w:sz w:val="16"/></w:rPr><w:t xml:space="preserve">MeetingMind  ·  </w:t></w:r><w:fldSimple w:instr=" PAGE "><w:r><w:rPr><w:color w:val="$MUTED"/><w:sz w:val="16"/></w:rPr><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>"""

    private fun coreProps(title: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${xml(title)}</dc:title><dc:creator>MeetingMind</dc:creator></cp:coreProperties>"""

    private fun stylesXml(serif: Boolean): String {
        val body = if (serif) "Georgia" else "Calibri"
        val headings = "Calibri"
        fun para(id: String, name: String, rPr: String, pPr: String = "", basedOn: String = "Normal", next: String? = "Normal") =
            """<w:style w:type="paragraph" w:styleId="$id"><w:name w:val="$name"/><w:basedOn w:val="$basedOn"/>""" +
                (next?.let { """<w:next w:val="$it"/>""" } ?: "") + """<w:qFormat/><w:pPr>$pPr</w:pPr><w:rPr>$rPr</w:rPr></w:style>"""
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            append("""<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="$body" w:hAnsi="$body" w:eastAsia="$body" w:cs="$body"/>""")
            append("""<w:sz w:val="22"/><w:szCs w:val="22"/><w:lang w:val="en-GB"/></w:rPr></w:rPrDefault>""")
            append("""<w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""")
            append("""<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>""")
            val headingFont = """<w:rFonts w:ascii="$headings" w:hAnsi="$headings"/>"""
            append(para("Title", "Title", "$headingFont<w:b/><w:sz w:val=\"44\"/><w:color w:val=\"1F1F1F\"/>", "<w:spacing w:after=\"80\"/>"))
            append(para("Subtitle", "Subtitle", "$headingFont<w:color w:val=\"$MUTED\"/><w:sz w:val=\"20\"/>", "<w:spacing w:after=\"320\"/>"))
            append(para("Heading1", "heading 1", "$headingFont<w:b/><w:sz w:val=\"32\"/><w:color w:val=\"1F1F1F\"/>", "<w:keepNext/><w:spacing w:before=\"360\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/>"))
            append(para("Heading2", "heading 2", "$headingFont<w:b/><w:sz w:val=\"26\"/><w:color w:val=\"1F1F1F\"/>", "<w:keepNext/><w:spacing w:before=\"280\" w:after=\"100\"/><w:outlineLvl w:val=\"1\"/>"))
            append(para("Heading3", "heading 3", "$headingFont<w:b/><w:sz w:val=\"23\"/><w:color w:val=\"$ACCENT\"/>", "<w:keepNext/><w:spacing w:before=\"220\" w:after=\"80\"/><w:outlineLvl w:val=\"2\"/>"))
            append(para("Quote", "Quote", "<w:i/><w:color w:val=\"404040\"/>", "<w:ind w:left=\"567\"/><w:pBdr><w:left w:val=\"single\" w:sz=\"18\" w:space=\"8\" w:color=\"$ACCENT\"/></w:pBdr>"))
            append(para("ScriptureReference", "Scripture Reference", "$headingFont<w:b/><w:color w:val=\"$ACCENT\"/><w:sz w:val=\"20\"/>", "<w:keepNext/><w:ind w:left=\"567\"/><w:spacing w:before=\"160\" w:after=\"40\"/>"))
            append(para("Scripture", "Scripture", "<w:color w:val=\"2B2B2B\"/>", "<w:ind w:left=\"567\"/><w:pBdr><w:left w:val=\"single\" w:sz=\"18\" w:space=\"8\" w:color=\"$ACCENT\"/></w:pBdr>"))
            append(para("Caption", "caption", "<w:color w:val=\"$MUTED\"/><w:sz w:val=\"18\"/>", "<w:spacing w:after=\"60\"/>"))
            append(para("Fact", "Fact", "<w:sz w:val=\"20\"/>", "<w:spacing w:after=\"40\"/>"))
            append(para("Attribution", "Attribution", "<w:color w:val=\"$MUTED\"/><w:sz w:val=\"16\"/>", "<w:spacing w:after=\"60\"/>"))
            append(para("ListParagraph", "List Paragraph", "", "<w:spacing w:after=\"60\"/><w:contextualSpacing/>"))
            append("""<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/><w:rPr><w:color w:val="$ACCENT"/><w:u w:val="single"/></w:rPr></w:style>""")
            append("</w:styles>")
        }
    }

    private fun xml(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                // Characters XML 1.0 forbids would make Word refuse the whole file.
                else -> if (c == '\t' || c == '\n' || c == '\r' || c >= ' ') append(c)
            }
        }
    }
}
