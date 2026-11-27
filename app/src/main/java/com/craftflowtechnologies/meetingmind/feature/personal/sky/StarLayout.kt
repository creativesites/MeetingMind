package com.craftflowtechnologies.meetingmind.feature.personal.sky

import kotlin.math.sqrt

/**
 * Where each reflection's star sits in the sky. Positions come from a stable hash of the entry id,
 * so the same reflections always draw the same sky. Stars keep off the top 15 % (the header) and
 * the bottom 30 % (the pattern card), and where there is room they stay at least 24 dp apart.
 *
 * Coordinates are in dp, measured from the top left of the sky area.
 */
object StarLayout {
    const val MIN_GAP_DP = 24f
    const val TOP_FRACTION = 0.15f
    const val BOTTOM_FRACTION = 0.30f
    private const val SIDE_DP = 20f
    private const val CANDIDATES = 40

    data class Point(val x: Float, val y: Float)

    /** One position per distinct id, in the order the ids were given. */
    fun place(entryIds: List<String>, width: Float, height: Float): Map<String, Point> {
        val ids = entryIds.distinct()
        val minX = SIDE_DP
        val maxX = (width - SIDE_DP).coerceAtLeast(minX)
        val minY = height * TOP_FRACTION
        val maxY = (height * (1f - BOTTOM_FRACTION)).coerceAtLeast(minY)
        // Placed in id order, so the sky does not depend on how the list happened to be sorted.
        val placed = HashMap<String, Point>()
        val taken = ArrayList<Point>()
        for (id in ids.sorted()) {
            val p = pick(id, minX, maxX, minY, maxY, taken)
            placed[id] = p
            taken.add(p)
        }
        return ids.associateWith { placed.getValue(it) }
    }

    /** The first candidate spot at least [MIN_GAP_DP] from every taken spot, or the roomiest one tried. */
    private fun pick(id: String, minX: Float, maxX: Float, minY: Float, maxY: Float, taken: List<Point>): Point {
        val units = unitsFor(id, CANDIDATES * 2)
        var best = Point(minX, minY)
        var bestGap = -1f
        for (k in 0 until CANDIDATES) {
            val p = Point(
                x = minX + units[2 * k] * (maxX - minX),
                y = minY + units[2 * k + 1] * (maxY - minY)
            )
            val gap = taken.minOfOrNull { distance(it, p) } ?: Float.MAX_VALUE
            if (gap >= MIN_GAP_DP) return p
            if (gap > bestGap) {
                bestGap = gap
                best = p
            }
        }
        return best
    }

    /** [count] values in [0, 1), from a stable hash of [key]. The same key always gives the same values. */
    fun unitsFor(key: String, count: Int): FloatArray {
        var seed = fnv1a(key)
        return FloatArray(count) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            (seed ushr 40).toFloat() / 16_777_216f
        }
    }

    private fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /** 64-bit FNV-1a, so the hash does not change between runs or devices. */
    private fun fnv1a(s: String): Long {
        var h = -3750763034362895579L // 0xcbf29ce484222325
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 1099511628211L // 0x100000001b3
        }
        return h
    }

    /**
     * The order to join a constellation's stars so it reads as a shape, not a zigzag: start at the
     * left-most star and always step to the nearest one not yet joined.
     */
    fun chain(points: List<Point>): List<Point> {
        if (points.size < 3) return points
        val left = points.toMutableList()
        val out = mutableListOf(left.minBy { it.x }.also { left.remove(it) })
        while (left.isNotEmpty()) {
            val last = out.last()
            val next = left.minBy { (it.x - last.x) * (it.x - last.x) + (it.y - last.y) * (it.y - last.y) }
            out += next
            left.remove(next)
        }
        return out
    }
}
