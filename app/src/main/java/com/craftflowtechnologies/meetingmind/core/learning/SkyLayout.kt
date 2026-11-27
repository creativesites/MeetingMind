package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.MasteryCalculator
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.feature.learn.SkyLink
import com.craftflowtechnologies.meetingmind.feature.learn.SkyState
import com.craftflowtechnologies.meetingmind.feature.learn.SkyStar
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds the Study sky from concepts and their attempts. Pure: no Android, no database.
 *
 * - Star state comes from [MasteryCalculator] (strong → Solid, developing/learning → Getting there,
 *   new → New, needs review → Worth a look). A concept with no attempts is New.
 * - A star's position is a hash of its concept id, so it stays where it was when the sky changes.
 *   Stars that would land on top of one another are nudged along a golden-angle spiral. Stars are
 *   placed in hash order, so the result does not depend on the order the caller passes them in.
 * - Links come from concept relationships, matched by name within the same set of concepts.
 */
object SkyLayout {

    /** Minimum distance between two star centres, in the unit square of the sky. */
    const val MIN_SEPARATION = 0.12f

    private const val MARGIN = 0.08f
    private const val SPAN = 0.84f
    private const val MAX_PROBES = 400

    /** The star a concept shows: New with no attempts, otherwise the state its attempts earn. */
    fun conceptStar(attempts: List<ActivityAttempt>, schedule: ReviewSchedule?, now: Long): StarState =
        if (attempts.isEmpty()) StarState.NEW
        else starStateFor(MasteryCalculator.evaluate(attempts, schedule, now).state)

    fun starStateFor(state: LearningMasteryState): StarState = when (state) {
        LearningMasteryState.STRONG -> StarState.SHINING
        LearningMasteryState.DEVELOPING, LearningMasteryState.LEARNING -> StarState.GROWING
        LearningMasteryState.NEW -> StarState.NEW
        LearningMasteryState.NEEDS_REVIEW -> StarState.WORTH_A_LOOK
    }

    /**
     * [attemptsByConcept] and [schedulesByConcept] are keyed by concept id. A concept missing from
     * [schedulesByConcept] is judged without a schedule, as [MasteryCalculator] allows.
     */
    fun build(
        concepts: List<LearningConcept>,
        attemptsByConcept: Map<String, List<ActivityAttempt>>,
        schedulesByConcept: Map<String, ReviewSchedule>,
        now: Long
    ): SkyState {
        val distinct = concepts.distinctBy { it.id }
        val ordered = distinct.sortedWith(compareBy({ hash(it.id) }, { it.id }))
        val positions = placeAll(ordered.map { it.id })

        val stars = ordered.map { concept ->
            val (x, y) = positions.getValue(concept.id)
            SkyStar(
                conceptId = concept.id,
                label = concept.name,
                star = conceptStar(attemptsByConcept[concept.id].orEmpty(), schedulesByConcept[concept.id], now),
                x = x,
                y = y
            )
        }

        return SkyState(stars = stars, links = linksFor(distinct), totalIdeas = distinct.size)
    }

    /** Deterministic position for an id before any collision handling. Each axis is in 0..1. */
    fun basePosition(conceptId: String): Pair<Float, Float> {
        val h = hash(conceptId)
        val x = ((h and 0xFFFF) / 65535f) * SPAN + MARGIN
        val y = (((h ushr 16) and 0xFFFF) / 65535f) * SPAN + MARGIN
        return x to y
    }

    /** FNV-1a, so the value is the same on every JVM and every run. */
    internal fun hash(text: String): Int {
        var h = 0x811c9dc5.toInt()
        for (c in text) {
            h = h xor c.code
            h *= 0x01000193
        }
        return h
    }

    private fun placeAll(idsInOrder: List<String>): Map<String, Pair<Float, Float>> {
        val placed = mutableListOf<Pair<Float, Float>>()
        val result = LinkedHashMap<String, Pair<Float, Float>>()
        for (id in idsInOrder) {
            val base = basePosition(id)
            val pos = probe(base, placed)
            placed += pos
            result[id] = pos
        }
        return result
    }

    private fun probe(base: Pair<Float, Float>, placed: List<Pair<Float, Float>>): Pair<Float, Float> {
        if (placed.none { distance(it, base) < MIN_SEPARATION }) return base
        for (k in 1..MAX_PROBES) {
            val radius = MIN_SEPARATION * sqrt(k.toFloat()) * 0.6f
            val theta = k * GOLDEN_ANGLE
            val x = (base.first + radius * cos(theta)).coerceIn(MARGIN, MARGIN + SPAN)
            val y = (base.second + radius * sin(theta)).coerceIn(MARGIN, MARGIN + SPAN)
            val candidate = x to y
            if (placed.none { distance(it, candidate) < MIN_SEPARATION }) return candidate
        }
        // Every probe collided (only possible with a very large sky): keep the base position.
        return base
    }

    private fun linksFor(concepts: List<LearningConcept>): List<SkyLink> {
        val byName = LinkedHashMap<String, String>()
        concepts.forEach { byName.putIfAbsent(it.name.trim().lowercase(), it.id) }
        val seen = mutableSetOf<Pair<String, String>>()
        val links = mutableListOf<SkyLink>()
        for (concept in concepts) {
            for (rel in concept.relationships) {
                val target = byName[rel.trim().lowercase()] ?: continue
                if (target == concept.id) continue
                val key = if (concept.id < target) concept.id to target else target to concept.id
                if (seen.add(key)) links += SkyLink(fromId = concept.id, toId = target)
            }
        }
        return links
    }

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    private const val GOLDEN_ANGLE = 2.3999632f
}
