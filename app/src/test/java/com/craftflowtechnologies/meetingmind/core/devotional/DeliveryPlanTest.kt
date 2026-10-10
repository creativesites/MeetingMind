package com.craftflowtechnologies.meetingmind.core.devotional

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DeliveryPlanTest {
    private val ready = DeliveryState(enabled = true, ready = true)
    private val missing = DeliveryState(enabled = true, ready = false)

    // Write time
    @Test fun `write time writes only when on and missing`() {
        assertEquals(WriteStep.WRITE, DevotionalDelivery.atWriteTime(enabled = true, ready = false))
        assertEquals(WriteStep.SKIP, DevotionalDelivery.atWriteTime(enabled = true, ready = true))
        assertEquals(WriteStep.SKIP, DevotionalDelivery.atWriteTime(enabled = false, ready = false))
    }

    // Delivery time
    @Test fun `ready devotional is announced once`() {
        assertEquals(DeliveryAction.AnnounceReady, DevotionalDelivery.atDelivery(ready))
        assertEquals(DeliveryAction.Nothing, DevotionalDelivery.atDelivery(ready.copy(alreadyAnnounced = true)))
        assertEquals(DeliveryAction.Nothing, DevotionalDelivery.atDelivery(ready.copy(opened = true)))
        assertEquals(DeliveryAction.Nothing, DevotionalDelivery.atDelivery(ready.copy(enabled = false)))
    }

    @Test fun `missing devotional gets one last try before anything is said`() {
        assertEquals(DeliveryAction.TryWriteNow, DevotionalDelivery.atDelivery(missing))
    }

    @Test fun `if the last try wrote it, it is announced as ready`() {
        assertEquals(DeliveryAction.AnnounceReady, DevotionalDelivery.atDelivery(missing.copy(ready = true, attempted = true)))
    }

    @Test fun `never announces ready when nothing exists`() {
        val states = listOf(true, false).flatMap { online -> FailureKind.entries.map { f -> missing.copy(attempted = true, online = online, lastFailure = f) } }
        states.forEach { assertTrue(DevotionalDelivery.atDelivery(it) is DeliveryAction.AnnounceNotReady) }
    }

    @Test fun `not ready says why in plain words`() {
        fun reason(s: DeliveryState) = (DevotionalDelivery.atDelivery(s) as DeliveryAction.AnnounceNotReady).reason
        val tried = missing.copy(attempted = true)
        assertEquals(NotReadyReason.OFFLINE, reason(tried.copy(online = false)))
        assertEquals(NotReadyReason.OFFLINE, reason(tried.copy(lastFailure = FailureKind.OFFLINE)))
        assertEquals(NotReadyReason.AI_UNAVAILABLE, reason(tried.copy(lastFailure = FailureKind.AI_UNAVAILABLE)))
        assertEquals(NotReadyReason.AI_UNAVAILABLE, reason(tried.copy(lastFailure = FailureKind.TIMEOUT)))
        assertEquals(NotReadyReason.STILL_WRITING, reason(tried.copy(writeInFlight = true, online = false)))
    }

    @Test fun `failures are classified`() {
        assertEquals(FailureKind.OFFLINE, DevotionalDelivery.classify("anything", online = false))
        assertEquals(FailureKind.OFFLINE, DevotionalDelivery.classify("Unable to resolve host", online = true))
        assertEquals(FailureKind.TIMEOUT, DevotionalDelivery.classify("Gemini took too long to answer.", online = true))
        assertEquals(FailureKind.TIMEOUT, DevotionalDelivery.classify(null, online = true, timedOut = true))
        assertEquals(FailureKind.AI_UNAVAILABLE, DevotionalDelivery.classify("429 quota exceeded", online = true))
        assertEquals(FailureKind.OTHER, DevotionalDelivery.classify("", online = true))
    }

    // Retry policy
    @Test fun `retries until thirty minutes after delivery and no longer`() {
        val delivery = 6 * 60 + 30
        assertTrue(DevotionalDelivery.shouldRetry(LocalDateTime.of(2026, 10, 9, 5, 0), delivery))
        assertTrue(DevotionalDelivery.shouldRetry(LocalDateTime.of(2026, 10, 9, 6, 59), delivery))
        assertFalse(DevotionalDelivery.shouldRetry(LocalDateTime.of(2026, 10, 9, 7, 0), delivery))
        assertFalse(DevotionalDelivery.shouldRetry(LocalDateTime.of(2026, 10, 9, 15, 0), delivery))
    }

    @Test fun `retry deadline never runs into tomorrow`() {
        val day = LocalDate.of(2026, 10, 9)
        assertEquals(LocalDateTime.of(2026, 10, 9, 23, 59), DevotionalDelivery.retryDeadline(day, 23 * 60 + 45))
        assertEquals(LocalDateTime.of(2026, 10, 9, 0, 40), DevotionalDelivery.retryDeadline(day, 10))
    }

    // Timetable
    @Test fun `write alarm is ninety minutes before delivery and not the evening before`() {
        assertEquals(5 * 60, DevotionalDelivery.writeMinute(6 * 60 + 30))
        assertEquals(0, DevotionalDelivery.writeMinute(30))
        assertEquals(0, DevotionalDelivery.writeMinute(0))
    }

    private fun ms(z: ZonedDateTime) = z.toInstant().toEpochMilli()

    @Test fun `next alarm wraps past midnight`() {
        val zone = ZoneId.of("UTC")
        val now = ms(ZonedDateTime.of(2026, 10, 9, 23, 50, 0, 0, zone))
        assertEquals(ms(ZonedDateTime.of(2026, 10, 10, 0, 30, 0, 0, zone)), DevotionalDelivery.nextAlarmMillis(now, 30, zone))
        assertEquals(ms(ZonedDateTime.of(2026, 10, 10, 23, 50, 0, 0, zone)), DevotionalDelivery.nextAlarmMillis(now, 23 * 60 + 50, zone))
    }

    @Test fun `next alarm keeps wall-clock time across spring-forward`() {
        val ny = ZoneId.of("America/New_York")
        val now = ms(ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, ny))
        // 06:30 on 8 March is after the clocks jump: 18.5 real hours away, not 19.5.
        val next = DevotionalDelivery.nextAlarmMillis(now, 6 * 60 + 30, ny)
        assertEquals(ZonedDateTime.of(2026, 3, 8, 6, 30, 0, 0, ny).toInstant(), Instant.ofEpochMilli(next))
        // A time inside the skipped hour lands just after it rather than being lost.
        val gap = Instant.ofEpochMilli(DevotionalDelivery.nextAlarmMillis(now, 2 * 60 + 30, ny)).atZone(ny)
        assertEquals(LocalDate.of(2026, 3, 8), gap.toLocalDate()); assertEquals(3, gap.hour)
    }

    @Test fun `next alarm keeps wall-clock time across fall-back`() {
        val ny = ZoneId.of("America/New_York")
        val now = ms(ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, ny))
        val next = Instant.ofEpochMilli(DevotionalDelivery.nextAlarmMillis(now, 6 * 60 + 30, ny)).atZone(ny)
        assertEquals(LocalDateTime.of(2026, 11, 1, 6, 30), next.toLocalDateTime())
    }

    @Test fun `next alarm is strictly after now`() {
        val zone = ZoneId.of("UTC")
        val now = ms(ZonedDateTime.of(2026, 10, 9, 6, 30, 0, 0, zone))
        assertEquals(ms(ZonedDateTime.of(2026, 10, 10, 6, 30, 0, 0, zone)), DevotionalDelivery.nextAlarmMillis(now, 6 * 60 + 30, zone))
    }

    // Status line
    @Test fun `status line says when and by what`() {
        val zone = ZoneId.of("UTC")
        val at = ZonedDateTime.of(2026, 10, 9, 5, 12, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Written at 05:12 by Gemini", DevotionalStatus.writtenLine(DevotionalOrigin.CLOUD_AI, "gemini-x", at, zone))
        assertEquals("Written at 05:12 by DeepSeek", DevotionalStatus.writtenLine(DevotionalOrigin.CLOUD_AI, "deepseek-chat", at, zone))
        assertEquals("Written at 05:12 on this phone", DevotionalStatus.writtenLine(DevotionalOrigin.DEVICE_AI, null, at, zone))
        assertTrue(DevotionalStatus.writtenLine(DevotionalOrigin.CLASSIC, null, at, zone)!!.startsWith("A classic"))
        assertNull(DevotionalStatus.writtenLine(DevotionalOrigin.MINE, null, at, zone))
    }

    @Test fun `notification copy is honest`() {
        assertEquals("You're offline, so it couldn't be written.", NotReadyReason.OFFLINE.line)
        assertEquals("The AI writer isn't available right now.", NotReadyReason.AI_UNAVAILABLE.line)
    }
}
