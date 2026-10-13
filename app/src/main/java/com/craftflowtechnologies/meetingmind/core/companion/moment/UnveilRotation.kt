package com.craftflowtechnologies.meetingmind.core.companion.moment

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/*
 * The Unveil rotation (docs/mvp/ZURI_SIGNATURE_MOMENTS.md §3.6, §3.7). Pure Kotlin: no Android, no
 * randomness, so every pick is reproducible in a JVM test.
 */

/** How a variant feels. */
enum class Energy { GENTLE, CRISP, PLAYFUL }

/**
 * The five Unveils. Append-only: [id] is also the Rive `momentVariant` input value, and the declaration
 * order is `VARIANT_ORDER` (the rotation's tie-break).
 */
enum class UnveilVariant(val id: Int, val energy: Energy, val hops: Boolean, val baseMs: Int) {
    LIFT(1, Energy.PLAYFUL, hops = true, baseMs = 1500),
    INKLINE(2, Energy.CRISP, hops = false, baseMs = 1400),
    CURTAIN(3, Energy.CRISP, hops = false, baseMs = 1500),
    DAWN(4, Energy.GENTLE, hops = false, baseMs = 1500),
    PLANT(5, Energy.PLAYFUL, hops = true, baseMs = 1600);

    companion object {
        fun ofId(id: Int): UnveilVariant? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The four moment spaces of the spec. The app's [NotebookSpace] maps onto them: Personal (and no space)
 * is Everyday, Learning is Study.
 */
enum class MomentSpace(
    /** Timing scale (§3.6): Faith ×1.15, Work ×0.85. */
    val timeScale: Float,
    /** Amplitude scale: Faith ×0.7 (gentle, no hops). */
    val amplitude: Float
) {
    EVERYDAY(1f, 1f),
    FAITH(1.15f, 0.7f),
    WORK(0.85f, 1f),
    STUDY(1f, 1f);

    companion object {
        fun of(space: NotebookSpace?): MomentSpace = when (space) {
            NotebookSpace.FAITH -> FAITH
            NotebookSpace.WORK -> WORK
            NotebookSpace.LEARNING -> STUDY
            NotebookSpace.PERSONAL, null -> EVERYDAY
        }
    }
}

/**
 * The persisted rotation (§3.6, about 40 bytes): smooth weighted round-robin credits per space and
 * variant, the last variant (the no-repeat guard is global, across spaces) and how often each variant
 * has played (familiarity compression). A cleared store restarts the rotation, which is harmless.
 */
data class RotationState(
    /** Credits per space, indexed by [UnveilVariant.ordinal]. A missing space starts at zero. */
    val credits: Map<MomentSpace, List<Int>> = emptyMap(),
    val last: UnveilVariant? = null,
    /** Plays per variant, indexed by [UnveilVariant.ordinal]. */
    val seen: List<Int> = List(UnveilVariant.entries.size) { 0 }
) {
    fun seen(v: UnveilVariant): Int = seen.getOrElse(v.ordinal) { 0 }

    /** A compact, versioned text form for DataStore: `1;last;seen,…;SPACE=c,…;…`. */
    fun encode(): String = buildString {
        append(Version).append(';').append(last?.id ?: 0).append(';').append(seen.joinToString(","))
        for (s in MomentSpace.entries) credits[s]?.let { append(';').append(s.name).append('=').append(it.joinToString(",")) }
    }

    companion object {
        private const val Version = 1
        private val Size = UnveilVariant.entries.size

        /** The state [encode] wrote; anything unreadable is a fresh rotation. */
        fun decode(raw: String?): RotationState {
            if (raw.isNullOrBlank()) return RotationState()
            return runCatching {
                val parts = raw.split(';')
                require(parts[0].toInt() == Version)
                val last = UnveilVariant.ofId(parts[1].toInt())
                val seen = parts[2].split(',').map { it.toInt() }.also { require(it.size == Size) }
                val credits = parts.drop(3).associate { kv ->
                    val (k, v) = kv.split('=')
                    MomentSpace.valueOf(k) to v.split(',').map { it.toInt() }.also { require(it.size == Size) }
                }
                RotationState(credits, last, seen)
            }.getOrDefault(RotationState())
        }
    }
}

/** One pick and the state after it. */
data class RotationPick(val variant: UnveilVariant, val state: RotationState)

/**
 * Smooth weighted round-robin with a no-repeat guard (§3.6): deterministic, proportional over every
 * cycle, and never the same variant twice in a row (unless a space offers only one).
 */
object UnveilRotation {

    /** Weights per space, indexed by [UnveilVariant.ordinal]. 0 = never offered there (no hops in Faith). */
    val weights: Map<MomentSpace, IntArray> = mapOf(
        //                        Lift Ink Cur Dawn Plant
        MomentSpace.EVERYDAY to intArrayOf(3, 2, 2, 2, 2),
        MomentSpace.FAITH to intArrayOf(0, 2, 1, 4, 0),
        MomentSpace.WORK to intArrayOf(1, 4, 3, 1, 0),
        MomentSpace.STUDY to intArrayOf(3, 2, 1, 1, 4)
    )

    fun weight(space: MomentSpace, v: UnveilVariant): Int = weights.getValue(space)[v.ordinal]

    /** The next variant for [space]. Pure: state in, state out. */
    fun next(state: RotationState, space: MomentSpace): RotationPick {
        val w = weights.getValue(space)
        val total = w.sum()
        val cw = (state.credits[space] ?: List(w.size) { 0 }).toIntArray()
        for (i in w.indices) if (w[i] > 0) cw[i] += w[i]
        val candidates = UnveilVariant.entries.filter { w[it.ordinal] > 0 }
            .sortedWith(compareByDescending<UnveilVariant> { cw[it.ordinal] }.thenBy { it.ordinal })
        val pick = candidates.firstOrNull { it != state.last } ?: candidates.first()
        cw[pick.ordinal] -= total
        val seen = state.seen.toMutableList().also { it[pick.ordinal] = it[pick.ordinal] + 1 }
        return RotationPick(pick, RotationState(state.credits + (space to cw.toList()), pick, seen))
    }

    /** The next [n] picks from [state] without keeping them (Companion Lab, docs). */
    fun preview(state: RotationState, space: MomentSpace, n: Int): List<UnveilVariant> {
        var s = state
        return List(n) { next(s, space).also { s = it.state }.variant }
    }

    /** Familiarity compression (§3.6): ×0.9 once a variant has played 8 times, ×0.8 at 25. [plays] includes this one. */
    fun familiarity(plays: Int): Float = when {
        plays >= 25 -> 0.8f
        plays >= 8 -> 0.9f
        else -> 1f
    }

    const val MinMs = 1200
    const val MaxMs = 1800

    /**
     * The played length of [variant] (§3.6): base × space × familiarity, clamped to [MinMs]..[MaxMs].
     * The system animator duration scale is applied later, by the moment clock.
     */
    fun durationMs(variant: UnveilVariant, space: MomentSpace, plays: Int): Int =
        scaledMs(variant.baseMs, space.timeScale * familiarity(plays))

    /** [baseMs] × [scale], clamped to the Unveil window. The one place the clamp lives. */
    fun scaledMs(baseMs: Int, scale: Float): Int = Math.round(baseMs * scale).coerceIn(MinMs, MaxMs)
}
