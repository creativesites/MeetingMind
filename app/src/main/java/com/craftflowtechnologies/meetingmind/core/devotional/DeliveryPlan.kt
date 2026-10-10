package com.craftflowtechnologies.meetingmind.core.devotional

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Why today's devotional isn't there at delivery time, in words a person can act on. */
enum class NotReadyReason(val line: String) {
    OFFLINE("You're offline, so it couldn't be written."),
    AI_UNAVAILABLE("The AI writer isn't available right now."),
    STILL_WRITING("It's still being written. This can take a minute.")
}

/** What went wrong with a write, coarsely. */
enum class FailureKind { OFFLINE, TIMEOUT, AI_UNAVAILABLE, OTHER }

/** What the delivery moment should do. */
sealed interface DeliveryAction {
    /** Nothing to say: turned off, already announced, or already opened. */
    data object Nothing : DeliveryAction
    /** Announce the devotional that exists. */
    data object AnnounceReady : DeliveryAction
    /** Not there: make one last try now, then decide again with [DeliveryState.attempted]. */
    data object TryWriteNow : DeliveryAction
    /** Not there after the last try: say so honestly. */
    data class AnnounceNotReady(val reason: NotReadyReason) : DeliveryAction
}

data class DeliveryState(
    val enabled: Boolean,
    val ready: Boolean,
    val alreadyAnnounced: Boolean = false,
    val opened: Boolean = false,
    val online: Boolean = true,
    /** The last-chance write at delivery time has already been tried. */
    val attempted: Boolean = false,
    val lastFailure: FailureKind? = null,
    /** Another write is running right now. */
    val writeInFlight: Boolean = false
)

/** What the write-ahead moment should do. */
enum class WriteStep { SKIP, WRITE }

/**
 * The devotional's delivery decisions, kept free of Android so they can be tested: whether to
 * write ahead, what to do at the delivery time, why a missing devotional is missing, and how long
 * to keep retrying. The rule above all: never announce something that doesn't exist.
 */
object DevotionalDelivery {
    fun atWriteTime(enabled: Boolean, ready: Boolean): WriteStep =
        if (!enabled || ready) WriteStep.SKIP else WriteStep.WRITE

    fun atDelivery(s: DeliveryState): DeliveryAction = when {
        !s.enabled -> DeliveryAction.Nothing
        s.ready -> if (s.alreadyAnnounced || s.opened) DeliveryAction.Nothing else DeliveryAction.AnnounceReady
        // Opened the app and read something else, or already told them it isn't ready: don't nag.
        s.opened -> DeliveryAction.Nothing
        !s.attempted -> DeliveryAction.TryWriteNow
        else -> DeliveryAction.AnnounceNotReady(reasonFor(s.lastFailure, s.online, s.writeInFlight))
    }

    fun reasonFor(failure: FailureKind?, online: Boolean, writeInFlight: Boolean): NotReadyReason = when {
        writeInFlight -> NotReadyReason.STILL_WRITING
        !online || failure == FailureKind.OFFLINE -> NotReadyReason.OFFLINE
        else -> NotReadyReason.AI_UNAVAILABLE
    }

    fun classify(message: String?, online: Boolean, timedOut: Boolean = false): FailureKind {
        val m = message.orEmpty().lowercase()
        return when {
            !online -> FailureKind.OFFLINE
            timedOut || "too long" in m || "timed out" in m || "timeout" in m -> FailureKind.TIMEOUT
            listOf("unable to resolve", "unknownhost", "network", "offline", "no internet", "failed to connect").any { it in m } -> FailureKind.OFFLINE
            m.isBlank() -> FailureKind.OTHER
            else -> FailureKind.AI_UNAVAILABLE
        }
    }

    // Retry policy ------------------------------------------------------------------------------

    /** First wait before a retry; WorkManager doubles it each time (30s, 1m, 2m, 4m ... well inside the 90-minute lead). */
    const val BACKOFF_SECONDS = 30L
    /** After delivery time the write-ahead keeps trying this long; the delivery attempt takes over before that. */
    const val GRACE_MINUTES = 30

    /** The moment the write-ahead stops retrying: delivery time plus the grace, but never into tomorrow. */
    fun retryDeadline(day: LocalDate, deliveryMinutes: Int): LocalDateTime {
        val deliver = day.atStartOfDay().plusMinutes(Math.floorMod(deliveryMinutes, 24 * 60).toLong())
        val limit = deliver.plusMinutes(GRACE_MINUTES.toLong())
        val midnight = day.plusDays(1).atStartOfDay().minusMinutes(1)
        return if (limit.isAfter(midnight)) midnight else limit
    }

    /** Keep retrying a failed write-ahead until the deadline; after it, the delivery try (or the person) owns it. */
    fun shouldRetry(now: LocalDateTime, deliveryMinutes: Int): Boolean =
        now.isBefore(retryDeadline(now.toLocalDate(), deliveryMinutes))

    // Timetable ---------------------------------------------------------------------------------

    const val LEAD_MINUTES = 90

    /** When, in minutes after midnight, the write-ahead alarm rings: [LEAD_MINUTES] before delivery, but not the evening before. */
    fun writeMinute(deliveryMinutes: Int): Int =
        (Math.floorMod(deliveryMinutes, 24 * 60) - LEAD_MINUTES).coerceAtLeast(0)

    /** The next instant [minuteOfDay] comes round in [zone], strictly after [nowMillis]. Handles midnight and DST. */
    fun nextAlarmMillis(nowMillis: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val m = Math.floorMod(minuteOfDay, 24 * 60)
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        fun at(date: LocalDate) = date.atStartOfDay().plusMinutes(m.toLong()).atZone(zone)
        val today = at(now.toLocalDate())
        val target = if (today.toInstant().toEpochMilli() > nowMillis) today else at(now.toLocalDate().plusDays(1))
        return target.toInstant().toEpochMilli()
    }
}

/** The one quiet line under a devotional saying when it was written and by what. */
object DevotionalStatus {
    fun writtenLine(origin: DevotionalOrigin, engine: String?, createdAtMs: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        val time = java.time.Instant.ofEpochMilli(createdAtMs).atZone(zone).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
        return when (origin) {
            DevotionalOrigin.CLOUD_AI -> "Written at $time by ${if (engine?.contains("deepseek", ignoreCase = true) == true) "DeepSeek" else "Gemini"}"
            DevotionalOrigin.DEVICE_AI -> "Written at $time on this phone"
            DevotionalOrigin.CLASSIC -> "A classic reading, put out at $time"
            DevotionalOrigin.MINE -> null
            DevotionalOrigin.CARE -> null
        }
    }
}
