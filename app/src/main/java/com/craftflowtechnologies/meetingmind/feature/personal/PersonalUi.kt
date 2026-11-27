package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.runtime.Immutable
import com.craftflowtechnologies.meetingmind.feature.personal.garden.PlantLook
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPhase

/** One day under a plant: its letter and how it went. */
@Immutable
data class DayDotUi(val letter: String, val state: DayState)

enum class DayState { KEPT, MISSED, TODAY, UPCOMING, OFF }

/** One plant in the garden, ready to draw. */
@Immutable
data class GardenPlantUi(
    val id: String,
    val title: String,
    val look: PlantLook,
    val tendedToday: Boolean,
    val bloomed: Boolean,
    val resting: Boolean,
    val week: List<DayDotUi>,
    /** "2 days this week". Never a streak. */
    val rhythmLine: String,
    /** "Weekdays", "3 times a week"… */
    val rhythmLabel: String,
    /** "From “Deep work in a distracted week”", when it came from a note. */
    val sourceLine: String?,
    val sourceNoteId: String?,
    /** "7 of 10 days kept since you planted it". */
    val windowLine: String,
    val checkInDue: Boolean
) {
    /** The few words under the plant's name in the garden. */
    val tag: String get() = when {
        bloomed -> "in bloom"
        tendedToday -> "tended today"
        checkInDue -> "ready to check in"
        resting -> "resting"
        else -> rhythmLine
    }
}

/** Everything the Personal home shows. */
@Immutable
data class PersonalUiState(
    val phase: GardenPhase = GardenPhase.DAY,
    val whenLine: String = "",
    val questionLabel: String = "TODAY'S QUESTION",
    val question: String = "",
    /** Reflections kept in the last seven days: the fireflies. */
    val lights: Int = 0,
    val plants: List<GardenPlantUi> = emptyList(),
    val ideas: Int = 0,
    val loaded: Boolean = false
)

/** How often a new practice should come round, as the Plant sheet offers it. */
enum class PlantRhythm(val label: String, val perWeek: Int) {
    DAILY("Every day", 7), WEEKDAYS("Weekdays", 5), THREE_A_WEEK("3 times a week", 3), WEEKLY("Once a week", 1)
}

/** When to nudge, as minutes after midnight, or none. */
enum class PlantNudge(val label: String, val minutes: Int?) {
    NONE("No reminder", null), MORNING("Morning, 08:00", 8 * 60), EVENING("Evening, 17:30", 17 * 60 + 30)
}

/** What the Plant sheet hands back. */
data class PlantRequest(val title: String, val rhythm: PlantRhythm, val nudge: PlantNudge, val checkInWeeks: Int)

object PlantSummary {
    /** "About 10 chances before we look at how it grew." No streaks, nothing withers. */
    fun line(rhythm: PlantRhythm, checkInWeeks: Int): String =
        if (checkInWeeks <= 0) "It grows for as long as you tend it. Missed days rest the plant; nothing withers."
        else "About ${rhythm.perWeek * checkInWeeks} chances before we look at how it grew together. Missed days rest the plant; nothing withers."
}
