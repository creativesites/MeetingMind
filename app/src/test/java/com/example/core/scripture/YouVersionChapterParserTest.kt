package com.example.core.scripture

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Chapters as the live YouVersion API returns them (public-domain text, saved as fixtures). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouVersionChapterParserTest {

    private fun chapter(name: String) =
        YouVersionChapterParser.parse(JSONObject(javaClass.classLoader!!.getResource("youversion/$name")!!.readText()).getString("content"))

    @Test
    fun `every verse of Matthew 5 comes out, in order`() {
        val verses = chapter("MAT.5_3034.json")
        assertEquals((1..48).toList(), verses.map { it.number })
        assertTrue(verses[0].text.startsWith("When Jesus saw the crowds"))
        assertTrue(verses[0].paragraph)
        assertFalse(verses.any { it.text.contains('<') || it.text.contains("yv-") })
    }

    @Test
    fun `the beatitudes keep their poetry lines`() {
        val v3 = chapter("MAT.5_3034.json").first { it.number == 3 }
        assertTrue(v3.poetry)
        assertEquals("“Blessed are the poor in spirit,\nfor theirs is the kingdom of heaven.", v3.text)
    }

    @Test
    fun `a psalm title joins verse one`() {
        val verses = chapter("PSA.23_3034.json")
        assertEquals(6, verses.size)
        assertTrue(verses[0].text.startsWith("A Psalm of David."))
        assertTrue(verses[0].text.contains("The Lord is my shepherd"))
    }

    @Test
    fun `prose chapters read as running text`() {
        val verses = chapter("ROM.8_206.json")
        assertEquals(39, verses.size)
        assertEquals(28, verses[27].number)
        assertTrue(verses[27].text.contains("all things work together for good"))
    }

    @Test
    fun `entities are decoded and empty markup yields nothing`() {
        val html = """<div><div class="p"><span class="yv-v" v="1"></span><span class="yv-vlbl">1</span>Faith &amp; hope</div></div>"""
        assertEquals("Faith & hope", YouVersionChapterParser.parse(html).single().text)
        assertTrue(YouVersionChapterParser.parse("<div></div>").isEmpty())
    }

    @Test
    fun `licensing keeps copyrighted text off the phone`() {
        assertTrue(BibleLicensing.offlineAllowed("BSB", "Public Domain"))
        assertTrue(BibleLicensing.offlineAllowed("ASV", null))
        assertTrue(BibleLicensing.offlineAllowed("XYZ", "Licensed under Creative Commons Attribution-ShareAlike"))
        assertFalse(BibleLicensing.offlineAllowed("NIV", "Holy Bible, New International Version® Copyright © 1973 by Biblica"))
        assertFalse(BibleLicensing.offlineAllowed("TOJB2011", null))
    }
}
