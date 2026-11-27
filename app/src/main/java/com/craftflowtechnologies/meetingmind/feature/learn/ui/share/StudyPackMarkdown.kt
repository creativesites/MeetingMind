package com.craftflowtechnologies.meetingmind.feature.learn.ui.share

import com.craftflowtechnologies.meetingmind.feature.learn.LessonState
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime

/** One key idea in a study pack. */
data class StudyPackIdea(val name: String, val definition: String)

/** One moment the lecturer stressed, by its time in the recording. */
data class StudyPackMoment(val atMs: Long, val quote: String)

/**
 * The study pack as markdown: the lecture's title and course, the 30-second summary, the key ideas and the stressed
 * moments. It is what a Circle receives, so it carries only the lecture's own words and the summary, nothing private.
 */
fun studyPackMarkdown(
    title: String,
    courseName: String?,
    summary: String,
    ideas: List<StudyPackIdea>,
    stressed: List<StudyPackMoment>
): String = buildString {
    append("# ").append(title.trim().ifBlank { "Study pack" }).append('\n')
    courseName?.trim()?.takeIf { it.isNotEmpty() }?.let { append('*').append(it).append("*\n") }
    if (summary.isNotBlank()) {
        append("\n## In 30 seconds\n").append(summary.trim()).append('\n')
    }
    if (ideas.isNotEmpty()) {
        append("\n## Key ideas\n")
        ideas.forEach { idea ->
            append("- **").append(idea.name.trim()).append("**")
            if (idea.definition.isNotBlank()) append(": ").append(idea.definition.trim())
            append('\n')
        }
    }
    if (stressed.isNotEmpty()) {
        append("\n## What the lecturer stressed\n")
        stressed.forEach { moment ->
            append("- ").append(formatLectureTime(moment.atMs)).append(" \"").append(moment.quote.trim()).append("\"\n")
        }
    }
}.trimEnd() + "\n"

/** The lesson's study pack, or null while the lesson has no kit to share yet. */
fun studyPackMarkdown(state: LessonState): String? {
    val kit = state.kit ?: return null
    return studyPackMarkdown(
        title = state.title,
        courseName = state.courseName,
        summary = kit.summary,
        ideas = state.concepts.map { StudyPackIdea(it.name, it.definition) },
        stressed = kit.stressed.map { StudyPackMoment(it.startMs, it.quote) }
    )
}
