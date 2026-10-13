package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.scripture.BibleBooks
import com.craftflowtechnologies.meetingmind.core.scripture.BibleLicensing
import com.craftflowtechnologies.meetingmind.core.scripture.Passage
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleLicensingTest {
    private val open = listOf("BSB", "ASV", "WEB", "WEBUS", "ENGWEBUS", "WEBBE", "WMB", "WMBBE", "CPDV", "GNV", "ENGGNV", "LSV", "FBV", "TCENT", "BBE", "YLT", "DRA", "OEB")

    @Test fun `every open translation may go to a cloud voice`() {
        open.forEach { assertTrue(it, BibleLicensing.openForCloud(3034, it, "")) }
        assertTrue(BibleLicensing.openForCloud(3034, "bsb", null)) // case-insensitive
    }

    @Test fun `licensed translations may not`() {
        listOf("NIV", "ESV", "NLT", "NKJV", "AMP", "MSG").forEach { assertFalse(it, BibleLicensing.openForCloud(111, it, "© Publisher. All rights reserved.")) }
    }

    @Test fun `the KJV waits for the owner's decision`() {
        assertFalse(BibleLicensing.KJV_TREATED_AS_OPEN)
        assertFalse(BibleLicensing.openForCloud(1, "KJV", ""))
    }

    @Test fun `open wording in the attribution counts`() {
        assertTrue(BibleLicensing.openForCloud(5, "XYZ", "Public Domain"))
        assertTrue(BibleLicensing.openForCloud(5, "XYZ", "Licensed under Creative Commons CC BY 4.0"))
        assertFalse(BibleLicensing.openForCloud(5, "XYZ", "All rights reserved"))
    }

    @Test fun `Free Use Bible API translations are open`() {
        assertTrue(BibleLicensing.openForCloud(-123456, "FOO", "via the Free Use Bible API"))
    }

    @Test fun `a passage knows whether its text may go to the cloud`() {
        val ref = ScriptureReference(BibleBooks.byUsfm("JHN")!!, 3, 16, 16)
        assertTrue(Passage(ref, "t", 3034, "BSB", "Public domain").openForCloud)
        assertFalse(Passage(ref, "t", 111, "NIV", "© Biblica").openForCloud)
        assertEquals(false, Passage(ref, "t", 59, "ESV", "© Crossway").openForCloud)
    }
}
