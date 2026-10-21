package com.craftflowtechnologies.meetingmind.ai.voice.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadToMeLicencePolicyTest {
    @Test fun `licensed text does not go to the cloud under owner decision D2`() {
        assertFalse(ReadToMeLicencePolicy.SEND_LICENSED_TEXT_TO_CLOUD)
        assertFalse(ReadToMeLicencePolicy.cloudOk(openForCloud = false))
        assertTrue(ReadToMeLicencePolicy.cloudOk(openForCloud = true))
    }

    @Test fun `open translations may go to the cloud`() {
        assertFalse(ReadToMeLicencePolicy.cloudOk(openForCloud = false, sendLicensed = false))
        assertTrue(ReadToMeLicencePolicy.cloudOk(openForCloud = true, sendLicensed = false))
    }

    @Test fun `kjv is treated as open`() {
        assertTrue(com.craftflowtechnologies.meetingmind.core.scripture.BibleLicensing.KJV_TREATED_AS_OPEN)
        assertTrue(com.craftflowtechnologies.meetingmind.core.scripture.BibleLicensing.offlineAllowed("KJV", null))
        assertTrue(com.craftflowtechnologies.meetingmind.core.scripture.BibleLicensing.openForCloud(1, "KJV", null))
        assertTrue(com.craftflowtechnologies.meetingmind.core.scripture.BibleLicensing.openForCloud(2, "WEB", null))
        assertFalse(com.craftflowtechnologies.meetingmind.core.scripture.BibleLicensing.openForCloud(3, "NIV", "Licensed"))
    }
}
