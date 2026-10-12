package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadFingerprintTest {

    private val settings = FingerprintSettings("exact", SpeechProvider.GEMINI, "gemini-3.8-flash-tts", "Kore", "warm-3-steady")

    private fun section(text: String = "Grace is enough", lookup: ScriptureLookup = lookupOf("John 3:16-17" to john316), options: ExtractOptions = ExtractOptions()): SpeechSection {
        val b = Blocks().apply { add(HEADING_2, "Study", id = "h"); scripture("John 3:16-17", id = "s"); add(PARAGRAPH, text, "my_notes", edited = true, id = "p") }
        return extract(b, lookup, options).sections.single()
    }

    @Test fun `the same section and settings give the same fingerprint`() {
        assertEquals(ReadFingerprint.section(section(), settings), ReadFingerprint.section(section(), settings))
    }

    @Test fun `editing the note changes it`() {
        assertNotEquals(ReadFingerprint.section(section(), settings), ReadFingerprint.section(section("Grace is enough!"), settings))
    }

    @Test fun `a different translation or re-fetched scripture text changes it`() {
        val other = john316.copy(translationName = "World English Bible", verses = john316.verses!!.map { it.copy(text = it.text.replace("Son", "Child")) })
        assertNotEquals(
            ReadFingerprint.section(section(), settings),
            ReadFingerprint.section(section(lookup = lookupOf("John 3:16-17" to other)), settings)
        )
    }

    @Test fun `verse-number and label settings change it because they change the spoken words`() {
        val base = ReadFingerprint.section(section(), settings)
        assertNotEquals(base, ReadFingerprint.section(section(options = ExtractOptions(readVerseNumbers = true)), settings))
        assertNotEquals(base, ReadFingerprint.section(section(options = ExtractOptions(labelStyle = LabelStyle.OFF)), settings))
    }

    @Test fun `voice, provider, model, delivery, mode and versions change it`() {
        val base = ReadFingerprint.section(section(), settings)
        listOf(
            settings.copy(voiceId = "Puck"), settings.copy(provider = SpeechProvider.LOCAL), settings.copy(model = "gemini-3.8-flash-lite-tts"),
            settings.copy(delivery = "vibrant-5-building"), settings.copy(mode = "smart"), settings.copy(language = "en"),
            settings.copy(scriptVersion = 2), settings.copy(formatVersion = 2)
        ).forEach { assertNotEquals(it.toString(), base, ReadFingerprint.section(section(), it)) }
    }

    @Test fun `moving blocks around without changing the words keeps the audio`() {
        val a = section()
        val moved = a.copy(units = a.units.map { it.copy(blockIds = it.blockIds.map { id -> "moved-$id" }) })
        assertEquals(ReadFingerprint.section(a, settings), ReadFingerprint.section(moved, settings))
    }

    @Test fun `freshness compares against what is stored`() {
        val stored = ReadFingerprint.section(section(), settings)
        assertTrue(ReadFingerprint.isFresh(stored, section(), settings))
        assertFalse(ReadFingerprint.isFresh(stored, section("edited since"), settings))
        assertFalse(ReadFingerprint.isFresh(null, section(), settings))
    }
}
