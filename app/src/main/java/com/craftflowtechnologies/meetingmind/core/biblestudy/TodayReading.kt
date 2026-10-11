package com.craftflowtechnologies.meetingmind.core.biblestudy

import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.devotional.LiturgicalCalendar
import com.craftflowtechnologies.meetingmind.core.devotional.LiturgicalSeason
import com.craftflowtechnologies.meetingmind.core.devotional.PassageRotation
import com.craftflowtechnologies.meetingmind.core.devotional.Tradition
import com.craftflowtechnologies.meetingmind.core.devotional.TopicPassages
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import java.time.DayOfWeek
import java.time.LocalDate

/** Today's reading: a passage and two plain lines on what kind of reading it is. */
data class TodayReading(val reference: ScriptureReference, val lines: List<String>)

/**
 * Today's passage for the Bible home. The same choice the devotional makes when the Personal touch
 * is off: the church year's reading on feasts when the person's tradition follows it, otherwise the
 * next passage in [PassageRotation] (one step per day through 400+ passages, so nothing returns
 * within 30 days). It knows nothing about the reader beyond their style settings and "less of".
 */
object TodayPassage {

    private val followsChurchYear = setOf(Tradition.CATHOLIC, Tradition.ORTHODOX, Tradition.ANGLICAN, Tradition.METHODIST)

    fun forDate(date: LocalDate, profile: DevotionalProfile = DevotionalProfile(), variant: Int = 0): TodayReading? {
        val less = profile.lessOf
        if (variant == 0) {
            val day = LiturgicalCalendar.dayOf(date, profile.tradition)
            val follows = profile.tradition in followsChurchYear
            val seasonal = day.season != LiturgicalSeason.ORDINARY &&
                (day.feast != null || follows && (date.dayOfWeek == DayOfWeek.SUNDAY || day.season == LiturgicalSeason.HOLY_WEEK))
            if (seasonal) TopicPassages.pick(date, emptyList(), less, day)?.let { return TodayReading(it, listOf(WhyLines.kindLine(it.book), "A reading for ${day.feast ?: day.season.label}.")) }
        }
        val index = PassageRotation.indexFor(date, variant)
        for (step in 0 until PassageRotation.size) {
            val (ref, tags) = PassageRotation.at(index + step) ?: continue
            if (less.any { it in tags } || TopicPassages.topicsOf(ref).any { it in less }) continue
            return TodayReading(ref, WhyLines.lines(ref, tags))
        }
        return null
    }
}
