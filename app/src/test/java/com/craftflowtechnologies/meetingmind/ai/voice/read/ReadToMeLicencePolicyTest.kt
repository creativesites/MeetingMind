package com.craftflowtechnologies.meetingmind.ai.voice.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadToMeLicencePolicyTest {
    @Test fun `licensed text may go to the cloud under the current decision`() {
        assertTrue(ReadToMeLicencePolicy.SEND_LICENSED_TEXT_TO_CLOUD)
        assertTrue(ReadToMeLicencePolicy.cloudOk(openForCloud = false))
        assertTrue(ReadToMeLicencePolicy.cloudOk(openForCloud = true))
    }

    @Test fun `with the policy off only open translations may go to the cloud`() {
        assertFalse(ReadToMeLicencePolicy.cloudOk(openForCloud = false, sendLicensed = false))
        assertTrue(ReadToMeLicencePolicy.cloudOk(openForCloud = true, sendLicensed = false))
    }
}
