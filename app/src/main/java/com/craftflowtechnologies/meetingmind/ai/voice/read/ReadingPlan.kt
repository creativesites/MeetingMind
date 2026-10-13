package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk

/**
 * Turns the note's sections into the parts that are generated, cached and queued, and decides the
 * silence between spoken units. Pure.
 */
object ReadingPlan {

    /** From the section [sectionId] to the end of the note; the whole note when it isn't found or is null. */
    fun fromSection(source: SpeechSource, sectionId: String?): SpeechSource {
        if (sectionId == null) return source
        val index = source.sections.indexOfFirst { it.id == sectionId }
        return if (index <= 0) source else source.copy(sections = source.sections.drop(index))
    }

    /**
     * The queue parts. With [splitAfterQuestions] a part ends right after each reflection question
     * that has more reading after it, so playback can pause there ("until I resume") and carry on
     * from the next part without losing the place. Part ids are stable: the first keeps the section id,
     * later ones add `#2`, `#3`.
     */
    fun parts(source: SpeechSource, splitAfterQuestions: Boolean): List<SpeechSection> = source.sections.flatMap { section ->
        if (!splitAfterQuestions) return@flatMap listOf(section)
        val groups = mutableListOf<MutableList<SpeechUnit>>(mutableListOf())
        section.units.forEachIndexed { i, unit ->
            groups.last() += unit
            val moreToRead = section.units.drop(i + 1).any { it.role != SpeechRole.HEADING }
            if (unit.role == SpeechRole.QUESTION && moreToRead) groups += mutableListOf<SpeechUnit>()
        }
        groups.mapIndexed { k, units ->
            section.copy(
                id = if (k == 0) section.id else "${section.id}#${k + 1}",
                units = units,
                references = if (k == 0) section.references else emptyList()
            )
        }
    }

    fun endsWithQuestion(part: SpeechSection): Boolean = part.units.lastOrNull { it.role != SpeechRole.HEADING }?.role == SpeechRole.QUESTION

    /** Playback itself pauses at the end of this part. */
    fun pausesAfter(part: SpeechSection, settings: ReadToMeSettings): Boolean =
        settings.pauseAfterQuestion == PauseAfterQuestion.UNTIL_RESUME && endsWithQuestion(part)

    /** One spoken chunk per unit, each followed by its silence. The words are exactly [SpeechUnit.spoken]. */
    fun chunks(part: SpeechSection, settings: ReadToMeSettings): List<SpeechChunk> = part.units.mapNotNull { unit ->
        val text = unit.spoken.trim()
        if (text.isEmpty()) null else SpeechChunk(text, pauseAfter(unit, settings))
    }

    fun pauseAfter(unit: SpeechUnit, settings: ReadToMeSettings): Long = when (unit.role) {
        SpeechRole.HEADING -> 700
        SpeechRole.SCRIPTURE -> if (unit.unavailable) 600 else 900
        SpeechRole.QUESTION -> settings.pauseAfterQuestion.silenceMs
        SpeechRole.PRAYER -> 700
        else -> 500
    }
}
