package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference

/**
 * Reading a passage verse by verse. Pure.
 *
 * A chapter becomes one part per verse when it is short, and one part per [GROUP_SIZE] verses when it is
 * long, so each part is one queue item. The queue index is then the part's place in the chapter, which is
 * how the reader follows the verse being read (no timestamps are invented). Part ids are `v<first verse>`,
 * so "read from this verse" can find the part that contains a verse.
 */
object PassageSpeech {
    /** Up to this many verses, each verse is its own part. */
    const val PER_VERSE_LIMIT = 12
    const val GROUP_SIZE = 3

    /** Whether a reading id is a passage (see [passageId]) rather than a note. */
    fun isPassage(readingId: String): Boolean = readingId.startsWith("passage:")

    /** The reading id for a passage: the whole chapter in [versionId] (the default when null). */
    fun passageId(reference: ScriptureReference, versionId: Int?): String =
        "passage:${versionId ?: 0}:${reference.passageId()}"

    /** Verse numbers grouped into parts, in order. Every verse appears once. */
    fun groups(verses: List<Int>): List<List<Int>> =
        if (verses.size <= PER_VERSE_LIMIT) verses.map { listOf(it) } else verses.chunked(GROUP_SIZE)

    /**
     * One section per part. [verses] are (number, text) in order. [spokenReference] is the label spoken before the
     * first verse ("John chapter 3, verses 16 to 18"). Each verse is a separate scripture unit so it can be followed.
     */
    fun sections(verses: List<Pair<Int, String>>, spokenReference: String, blockId: String, bookLabel: String): List<SpeechSection> {
        val byNumber = verses.toMap()
        var spokenOnce = false
        return groups(verses.map { it.first }).map { group ->
            val first = group.first()
            val last = group.last()
            val units = group.map { n ->
                val lead = if (!spokenOnce) spokenReference.also { spokenOnce = true } else null
                SpeechUnit(
                    blockIds = listOf(blockId), role = SpeechRole.SCRIPTURE, lead = lead, text = byNumber.getValue(n).trim(),
                    locked = true, isPrivate = false, aiGenerated = false
                )
            }
            val title = if (first == last) "$bookLabel $first" else "$bookLabel $first–$last"
            SpeechSection(id = "v$first", title = title, units = units)
        }
    }

    /** The first verse of a part, from its section id ("v16" → 16); null for any other id. */
    fun firstVerse(sectionId: String?): Int? = sectionId?.takeIf { it.startsWith("v") }?.substring(1)?.substringBefore('-')?.toIntOrNull()

    /**
     * The parts that start reading at [verse]: from the part that contains it to the end of the chapter.
     * A verse before the first part, or no verse, gives every part.
     */
    fun fromVerse(sections: List<SpeechSection>, verse: Int?): List<SpeechSection> {
        if (verse == null) return sections
        val start = sections.indexOfLast { (firstVerse(it.id) ?: Int.MAX_VALUE) <= verse }
        return if (start <= 0) sections else sections.drop(start)
    }
}
