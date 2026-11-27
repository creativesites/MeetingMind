package com.craftflowtechnologies.meetingmind.feature.learn.lesson

import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.feature.learn.LessonSection
import com.craftflowtechnologies.meetingmind.feature.learn.QuickCheck
import com.craftflowtechnologies.meetingmind.feature.learn.StressReason
import com.craftflowtechnologies.meetingmind.feature.learn.StressedMoment
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit

/**
 * A lesson built from the concepts and questions the old Learning studio already saved for a lecture.
 *
 * No AI runs here: the lesson is the learner's own Learning data, laid out as sections. It is not stored, so
 * "make the lesson again" later (when AI is set up) replaces nothing silently.
 */
object LegacyLesson {
    /** The `modelId` written on a derived kit, so nobody mistakes it for a generated one. */
    const val MODEL_ID = "learning-concepts"

    /** Null when there are no live concepts to lay out. */
    fun kitFrom(
        sessionId: String,
        concepts: List<LearningConcept>,
        activities: List<LearningActivity>,
        createdAt: Long
    ): StudyKit? {
        val live = concepts
            .filter { !it.isDismissed && it.name.isNotBlank() }
            .sortedBy { c -> c.evidence.mapNotNull { it.startMs }.minOrNull() ?: Long.MAX_VALUE }
        if (live.isEmpty()) return null
        val summary = live.take(3).map { it.definition.trim() }.filter { it.isNotBlank() }.joinToString(" ")
        val stressed = live.filter { !it.emphasis.isNullOrBlank() }.mapNotNull { c ->
            val ev = c.evidence.firstOrNull { it.startMs != null } ?: return@mapNotNull null
            val start = ev.startMs ?: return@mapNotNull null
            StressedMoment(
                startMs = start,
                endMs = ev.endMs ?: start,
                quote = ev.quote.ifBlank { c.name },
                reason = if (c.emphasis.orEmpty().contains("exam", ignoreCase = true)) StressReason.NAMED_EXAM else StressReason.CALLED_IMPORTANT,
                conceptId = c.id
            )
        }
        val sections = live.mapIndexed { i, c ->
            val check = activities.firstOrNull {
                it.conceptId == c.id && it.type == LearningActivityType.RECALL && !it.isDismissed && !it.isStale
            }
            LessonSection(
                index = i + 1,
                title = c.name,
                body = c.definition,
                quickCheck = check?.let { QuickCheck(prompt = it.prompt, answer = it.expectedAnswer, conceptId = c.id) },
                startMs = c.evidence.firstNotNullOfOrNull { it.startMs }
            )
        }
        return StudyKit(
            sessionId = sessionId,
            summary = summary,
            stressed = stressed,
            sections = sections,
            modelId = MODEL_ID,
            createdAt = createdAt
        )
    }
}
