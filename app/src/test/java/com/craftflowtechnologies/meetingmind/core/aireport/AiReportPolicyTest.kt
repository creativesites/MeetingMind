package com.craftflowtechnologies.meetingmind.core.aireport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A report leaves the phone only when nothing about the content says it must stay. */
class AiReportPolicyTest {

    @Test
    fun `internet mode with nothing private uploads`() {
        assertEquals(ReportDelivery.Upload, AiReportPolicy.delivery(ReportPrivacy()))
    }

    @Test
    fun `any privacy flag, alone or combined, keeps the report on the phone`() {
        for (onDevice in listOf(false, true)) for (priv in listOf(false, true)) for (keep in listOf(false, true)) {
            if (!onDevice && !priv && !keep) continue
            val d = AiReportPolicy.delivery(ReportPrivacy(onDeviceMode = onDevice, privateNote = priv, keepOnDevice = keep))
            assertTrue("onDevice=$onDevice private=$priv keep=$keep should be copy only, was $d", d is ReportDelivery.CopyOnly)
        }
    }

    @Test
    fun `the reason names the strongest rule`() {
        assertEquals("you use private, on-device mode", (AiReportPolicy.delivery(ReportPrivacy(onDeviceMode = true)) as ReportDelivery.CopyOnly).why)
        assertEquals("this note is private", (AiReportPolicy.delivery(ReportPrivacy(onDeviceMode = true, privateNote = true)) as ReportDelivery.CopyOnly).why)
        assertEquals("this work is kept on your phone", (AiReportPolicy.delivery(ReportPrivacy(privateNote = true, keepOnDevice = true)) as ReportDelivery.CopyOnly).why)
    }
}
