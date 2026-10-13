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
     * The queue parts: small enough that the first one is ready within seconds and a failure costs little.
     * The very first part holds about [firstMaxChars] characters, later ones about [maxChars]; a part
     * ends early after a reflection question when [splitAfterQuestions] is on, so playback can pause
     * there ("until I resume"). A unit is never cut unless it alone overflows the first part, and then
     * only at sentence ends; the words and their order are untouched. Part ids are stable: the first
     * keeps the section id, later ones add `#2`, `#3`.
     */
    fun parts(source: SpeechSource, splitAfterQuestions: Boolean, firstMaxChars: Int = FIRST_PART_CHARS, maxChars: Int = PART_CHARS): List<SpeechSection> {
        var emitted = 0
        return source.sections.flatMap { section ->
            val groups = mutableListOf<MutableList<SpeechUnit>>(mutableListOf())
            var size = 0
            fun budget() = if (emitted + groups.size - 1 == 0) firstMaxChars else maxChars
            section.units.forEachIndexed { i, unit ->
                val pieces = if (emitted + groups.size - 1 == 0 && unit.spoken.length > firstMaxChars) splitUnit(unit, firstMaxChars) else listOf(unit)
                pieces.forEach { piece ->
                    val len = piece.spoken.length
                    if (groups.last().any { it.role != SpeechRole.HEADING } && size + len > budget()) { groups.add(mutableListOf()); size = 0 }
                    groups.last() += piece
                    size += len
                }
                val moreToRead = section.units.drop(i + 1).any { it.role != SpeechRole.HEADING }
                if (splitAfterQuestions && unit.role == SpeechRole.QUESTION && moreToRead) { groups.add(mutableListOf()); size = 0 }
            }
            emitted += groups.size
            groups.mapIndexed { k, units ->
                section.copy(
                    id = if (k == 0) section.id else "${section.id}#${k + 1}",
                    units = units,
                    references = if (k == 0) section.references else emptyList()
                )
            }
        }
    }

    /** The unit as pieces of about [max] characters; the label stays on the first, every word stays. */
    private fun splitUnit(unit: SpeechUnit, max: Int): List<SpeechUnit> {
        val room = (max - (unit.lead?.length ?: 0)).coerceAtLeast(max / 2)
        val first = TextChunker.split(unit.text, room)
        if (first.size <= 1) return listOf(unit)
        val pieces = mutableListOf<String>()
        var rest = unit.text
        // Re-split the remainder at the plain limit so later pieces aren't shortened by the label.
        val head = first.first()
        pieces += head
        rest = unit.text.trim().replace(Regex("\\s+"), " ").removePrefix(head).trimStart()
        pieces += TextChunker.split(rest, max)
        return pieces.mapIndexed { n, t -> unit.copy(text = t, lead = if (n == 0) unit.lead else null) }
    }

    const val FIRST_PART_CHARS = 450
    const val PART_CHARS = 1500

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
