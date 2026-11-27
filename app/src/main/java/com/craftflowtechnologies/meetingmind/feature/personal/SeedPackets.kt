package com.craftflowtechnologies.meetingmind.feature.personal

import com.craftflowtechnologies.meetingmind.core.model.RecordingType

/**
 * Seed packets: the lines in a talk or how-to note's "Try this week" sections, each one a thing
 * the person could plant as a practice. Pure, so it is unit-tested on the JVM.
 */
object SeedPackets {

    /** The notes that come with seed packets. */
    val WORKFLOWS: List<RecordingType> = listOf(RecordingType.MOTIVATION, RecordingType.LIFE_SKILLS)

    /** The sections whose lines are things to practise. */
    val SECTION_KEYS: Set<String> = setOf("daily_habits", "try_this")

    /** How many recent notes the garden offers packets from. */
    const val MAX_PACKETS = 6

    /** Lines longer than this are notes, not practices. */
    private const val MAX_LINE = 140

    /** One block of a note, as much of it as packets need. */
    data class Line(val sectionKey: String?, val type: String, val text: String)

    /** The practisable lines, in note order, without headings, blanks or repeats. */
    fun lines(blocks: List<Line>): List<String> = blocks
        .asSequence()
        .filter { it.sectionKey in SECTION_KEYS && !it.type.startsWith("HEADING") }
        .map { it.text.trim().trimEnd('.', ';').trim() }
        .filter { it.length in 3..MAX_LINE }
        .distinctBy { key(it) }
        .toList()

    /** How two titles are compared: case, spaces and a trailing full stop don't matter. */
    fun key(text: String): String = text.trim().trimEnd('.').replace(Regex("\\s+"), " ").lowercase()
}
