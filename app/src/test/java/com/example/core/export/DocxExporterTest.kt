package com.example.core.export

import com.example.core.model.ActionItem
import com.example.core.model.Meeting
import com.example.core.model.MeetingSource
import com.example.core.model.MeetingStatus
import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class DocxExporterTest {

    private fun meeting() = Meeting(
        id = "m1",
        title = "Client Call",
        createdAt = System.currentTimeMillis(),
        durationMs = 300_000L,
        source = MeetingSource.LOCAL_RECORDING,
        audioFilePath = "/data/m1.wav",
        status = MeetingStatus.READY
    )

    private fun entryNames(bytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                entry = zip.nextEntry
            }
        }
        return names
    }

    @Test
    fun `transcript docx is a valid zip with the three required OOXML parts`() {
        val out = ByteArrayOutputStream()
        DocxExporter.transcript(
            meeting(),
            listOf(TranscriptSegment(id = "s1", meetingId = "m1", speakerName = "Winston", startMs = 0L, endMs = 1000L, text = "Hello there.")),
            out
        )

        val names = entryNames(out.toByteArray())
        assertTrue(names.contains("[Content_Types].xml"))
        assertTrue(names.contains("_rels/.rels"))
        assertTrue(names.contains("word/document.xml"))
    }

    @Test
    fun `action items docx escapes XML-unsafe characters in task text`() {
        val out = ByteArrayOutputStream()
        DocxExporter.actionItems(meeting(), listOf(ActionItem(id = "a1", meetingId = "m1", task = "Fix <critical> bug & \"deploy\"")), out)

        val documentXml = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            var xml = ""
            while (entry != null) {
                if (entry.name == "word/document.xml") xml = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
            xml
        }

        assertTrue(documentXml.contains("Fix &lt;critical&gt; bug &amp; &quot;deploy&quot;"))
        assertTrue("Must not contain a raw, unescaped angle bracket from user content", !documentXml.contains("<critical>"))
    }
}
