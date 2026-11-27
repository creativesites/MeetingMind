package com.craftflowtechnologies.meetingmind.feature.work.home

/** What the floating "Up next" tile offers. [NONE] shows no tile. */
enum class UpNextChoice { MEETING, REVIEW, FOLLOW_UP, NONE }

/**
 * Which item the floating "Up next" tile leads with. Before evening it is the next meeting (Prepare);
 * from evening it is what is waiting to be reviewed (Review). Plain counts and an hour, so no Android types.
 */
object WorkUpNext {
    /** The hour (0-23) from which the tile leads with what to review. */
    const val EVENING_HOUR = 17

    fun choose(hour: Int, hasNextMeeting: Boolean, reviews: Int, followUps: Int): UpNextChoice {
        val evening = hour >= EVENING_HOUR
        return when {
            evening && reviews > 0 -> UpNextChoice.REVIEW
            hasNextMeeting -> UpNextChoice.MEETING
            reviews > 0 -> UpNextChoice.REVIEW
            followUps > 0 -> UpNextChoice.FOLLOW_UP
            else -> UpNextChoice.NONE
        }
    }

    /** "To review · 2 recordings", for the tile's overline. */
    fun reviewLine(count: Int): String = "To review · " + WorkHomeCopy.plural(count, "recording")
}
