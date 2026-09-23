package com.example.core.timeline

import com.example.core.identity.AppIdentity
import com.example.core.model.RecordingType
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/** Phases of the day the Home sky moves through. */
enum class SkyPhase { NIGHT, DAWN, MORNING, DAY, GOLDEN, DUSK }

/**
 * The Home hero's sky at a moment (PLAN_V2 F1): which body is up (sun or moon), where on its arc
 * it sits (0 = rising on the left, 1 = setting on the right; height from [elevation]), and the sky
 * colours. Colours interpolate between phases, so the card drifts through the day rather than
 * jumping.
 */
data class TimeOfDaySky(
    val phase: SkyPhase,
    val isMoon: Boolean,
    /** 0..1 across the card, left to right. */
    val arc: Float,
    /** 0..1: 0 on the horizon, 1 at the top of the arc. */
    val elevation: Float,
    val skyTop: Long,
    val skyBottom: Long,
    val orbLight: Long,
    val orbBody: Long,
    val orbDeep: Long,
    val glow: Long,
    /** 0..1: how visible the stars are. */
    val stars: Float
) {
    companion object {
        private const val SUNRISE = 6.0
        private const val SUNSET = 18.5

        fun at(hour: Int, minute: Int, warm: Boolean = false): TimeOfDaySky {
            val h = hour + minute / 60.0
            val day = h in SUNRISE..SUNSET
            val arc = if (day) ((h - SUNRISE) / (SUNSET - SUNRISE)).toFloat()
            else { val span = 24 - (SUNSET - SUNRISE); (((h - SUNSET + 24) % 24) / span).toFloat() }
            val elevation = sin(arc * PI).toFloat().coerceIn(0f, 1f)
            val phase = when {
                h < 5.0 || h >= 20.5 -> SkyPhase.NIGHT
                h < 7.0 -> SkyPhase.DAWN
                h < 10.5 -> SkyPhase.MORNING
                h < 16.5 -> SkyPhase.DAY
                h < 18.3 -> SkyPhase.GOLDEN
                else -> SkyPhase.DUSK
            }
            // Keyframes by hour: sky top, sky bottom.
            val frames = listOf(
                0.0 to (0xFF0B1026L to 0xFF1B2448L),
                5.0 to (0xFF1B2448L to 0xFF3A2E5CL),
                6.5 to (0xFFF4A38CL to 0xFF8A7FD0L),
                8.5 to (0xFF78B8F0L to 0xFF3E6FD8L),
                13.0 to (0xFF4FA3F7L to 0xFF2155C9L),
                17.0 to (0xFFF6B25EL to 0xFFB5566AL),
                18.8 to (0xFF8C5BA8L to 0xFF2A2352L),
                20.5 to (0xFF141A3AL to 0xFF0B1026L),
                24.0 to (0xFF0B1026L to 0xFF1B2448L)
            )
            val (a, b) = frames.zipWithNext().first { (x, y) -> h >= x.first && h < y.first }
            val t = ((h - a.first) / (b.first - a.first)).toFloat()
            var top = lerp(a.second.first, b.second.first, t)
            var bottom = lerp(a.second.second, b.second.second, t)
            if (warm) { top = lerp(top, 0xFFB7791FL, 0.12f); bottom = lerp(bottom, 0xFF3B1F2BL, 0.12f) }

            val moon = !day
            val (light, body, deep, glow) = when {
                moon -> listOf(0xFFFFFFFFL, 0xFFE8EAF2L, 0xFF9AA3BDL, 0xFFCBD5F5L)
                phase == SkyPhase.DAWN -> listOf(0xFFFFF1E6L, 0xFFF7A58BL, 0xFFB5566AL, 0xFFF7A58BL)
                phase == SkyPhase.GOLDEN || phase == SkyPhase.DUSK -> listOf(0xFFFFF3D6L, 0xFFF5A623L, 0xFFB45309L, 0xFFF5A623L)
                else -> listOf(0xFFFFFDF2L, 0xFFF9D56EL, 0xFFD97706L, 0xFFFDE68AL)
            }
            val starAlpha = when {
                day -> 0f
                phase == SkyPhase.NIGHT -> 1f
                else -> 0.45f
            }
            return TimeOfDaySky(phase, moon, arc, elevation, top, bottom, light, body, deep, glow, starAlpha)
        }

        fun lerp(a: Long, b: Long, t: Float): Long {
            fun ch(c: Long, s: Int) = ((c shr s) and 0xFF).toInt()
            fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t.coerceIn(0f, 1f)).toInt().coerceIn(0, 255).toLong()
            return (0xFFL shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
}

/**
 * A warm, slightly cheesy greeting for the moment — the way Claude says hello. Picked
 * deterministically for the day so it doesn't change every time Home is opened.
 */
object Greetings {
    private val general = mapOf(
        SkyPhase.NIGHT to listOf("Burning the midnight oil, %s?", "Still up, %s? Let's make it count", "Night owl mode, %s 🦉", "Quiet hours, big thoughts, %s"),
        SkyPhase.DAWN to listOf("Early bird, %s? The worm's all yours", "Up with the sun, %s ☀️", "Fresh start, %s"),
        SkyPhase.MORNING to listOf("Rise and shine, %s ☀️", "Morning, %s — coffee first?", "Let's make today a good one, %s", "Good morning, %s. What's on the agenda?"),
        SkyPhase.DAY to listOf("Coffee, then conquer, %s?", "Afternoon, %s — how's it going?", "Keep it rolling, %s", "Hey %s, what are we capturing today?"),
        SkyPhase.GOLDEN to listOf("Golden hour, %s ✨", "Winding down, %s?", "Nearly there, %s"),
        SkyPhase.DUSK to listOf("Evening, %s — time to catch your breath", "Good evening, %s", "Wrapping up the day, %s?")
    )
    private val faith = mapOf(
        SkyPhase.NIGHT to listOf("Rest well, %s — He never sleeps", "Peace for the night, %s", "Be still and know, %s"),
        SkyPhase.DAWN to listOf("His mercies are new this morning, %s", "Up with the sun, %s ☀️"),
        SkyPhase.MORNING to listOf("This is the day the Lord has made, %s", "Grace and peace, %s", "Good morning, %s — rejoice today"),
        SkyPhase.DAY to listOf("Keep going, %s — you're not alone", "Grace for the afternoon, %s", "Hey %s, how's your heart today?"),
        SkyPhase.GOLDEN to listOf("Golden hour, %s — count the blessings", "Nearly home, %s"),
        SkyPhase.DUSK to listOf("Evening, %s — time to reflect", "Good evening, %s. What did today hold?")
    )

    fun pick(identity: AppIdentity, now: Calendar = Calendar.getInstance()): String {
        val name = identity.firstName ?: "friend"
        val phase = TimeOfDaySky.at(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)).phase
        val sunday = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        if (identity.faithFirst && sunday && phase in setOf(SkyPhase.DAWN, SkyPhase.MORNING)) return "Ready for church, $name?"
        val pool = (if (identity.faithFirst) faith else general).getValue(phase)
        val seed = now.get(Calendar.YEAR) * 400 + now.get(Calendar.DAY_OF_YEAR) + phase.ordinal * 7
        return pool[Math.floorMod(seed, pool.size)].format(name)
    }

    /** "3 meetings today · 1 recording processing" — only the parts that are true. */
    fun contextLine(eventsToday: Int, processing: Int, answeredThisWeek: Int): String? = listOfNotNull(
        eventsToday.takeIf { it > 0 }?.let { "$it ${if (it == 1) "event" else "events"} today" },
        processing.takeIf { it > 0 }?.let { "$it ${if (it == 1) "recording" else "recordings"} processing" },
        answeredThisWeek.takeIf { it > 0 }?.let { "$it answered ${if (it == 1) "prayer" else "prayers"} this week 🙌" }
    ).joinToString(" · ").ifBlank { null }
}

/**
 * Learned rhythms: "You usually record a sermon on Sundays around 10:30". A pattern is at least
 * three recordings of one type on the same weekday within the same hour in the last eight weeks.
 */
object Rhythms {
    data class Rhythm(val type: RecordingType, val dayOfWeek: Int, val minuteOfDay: Int, val times: Int)

    fun learn(recordings: List<Pair<RecordingType, Long>>, minTimes: Int = 3): List<Rhythm> =
        recordings.groupBy { (type, at) ->
            val c = Calendar.getInstance().apply { timeInMillis = at }
            Triple(type, c.get(Calendar.DAY_OF_WEEK), c.get(Calendar.HOUR_OF_DAY))
        }.filter { it.value.size >= minTimes }.map { (key, list) ->
            val avgMinute = list.map { (_, at) -> Calendar.getInstance().apply { timeInMillis = at }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) } }.average().toInt()
            Rhythm(key.first, key.second, avgMinute, list.size)
        }.sortedByDescending { it.times }

    /** The rhythm due now: same weekday, from an hour before until half an hour after. */
    fun dueNow(rhythms: List<Rhythm>, now: Calendar = Calendar.getInstance()): Rhythm? {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return rhythms.firstOrNull { it.dayOfWeek == now.get(Calendar.DAY_OF_WEEK) && minute in (it.minuteOfDay - 60)..(it.minuteOfDay + 30) }
    }

    fun describe(r: Rhythm): String {
        val day = java.text.DateFormatSymbols.getInstance().weekdays[r.dayOfWeek]
        val time = "%d:%02d".format(r.minuteOfDay / 60, r.minuteOfDay % 60)
        return "You usually record ${article(r.type)} ${r.type.displayName.lowercase()} on ${day}s around $time"
    }

    private fun article(t: RecordingType) = if (t.displayName.first().lowercaseChar() in "aeiou") "an" else "a"
}

/**
 * Meeting prep: before an event, the last time you met these people, what's still open from it,
 * and notes that mention them. Matching is by the attendee names kept on notes made from events.
 */
object MeetingPrep {
    data class Prep(val lastNoteId: String?, val lastTitle: String?, val lastWhen: Long?, val sharedPeople: List<String>)

    fun find(attendees: List<String>, notes: List<com.example.core.model.Note>, before: Long): Prep? {
        val wanted = attendees.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
        if (wanted.isEmpty()) return null
        val match = notes.asSequence()
            .filter { (it.eventDate ?: it.createdAt) < before }
            .mapNotNull { n ->
                val people = n.metadata["participants"]?.split(", ")?.map { it.lowercase().trim() }.orEmpty()
                val shared = people.filter { it in wanted }
                if (shared.isEmpty()) null else n to shared
            }
            .maxByOrNull { (n, shared) -> shared.size * 10_000_000_000_000L + (n.eventDate ?: n.createdAt) }
            ?: return null
        return Prep(match.first.id, match.first.title, match.first.eventDate ?: match.first.createdAt,
            attendees.filter { it.lowercase().trim() in match.second })
    }
}

/** The week in numbers, for the Sunday-evening card. Only what happened; nothing inferred. */
data class WeekReview(val recordings: Int, val notes: Int, val faith: Int, val answered: Int, val minutesRecorded: Int) {
    val isEmpty get() = recordings + notes + faith + answered == 0

    companion object {
        fun of(items: List<TimelineItem>): WeekReview = WeekReview(
            recordings = items.count { it.kind == ItemKind.RECORDING },
            notes = items.count { it.kind == ItemKind.NOTE && it.layer == TimelineLayer.NOTES },
            faith = items.count { it.layer == TimelineLayer.FAITH && it.kind != ItemKind.ANSWERED_PRAYER },
            answered = items.count { it.kind == ItemKind.ANSWERED_PRAYER },
            minutesRecorded = items.filter { it.kind == ItemKind.RECORDING }.sumOf { ((it.end ?: it.start) - it.start) / 60_000 }.toInt()
        )
    }
}
