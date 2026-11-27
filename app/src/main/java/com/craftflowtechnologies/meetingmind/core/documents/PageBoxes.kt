package com.craftflowtechnologies.meetingmind.core.documents

import org.json.JSONArray
import org.json.JSONObject

/**
 * A highlight on a page. All four numbers are fractions (0..1) of the page width and height, so a box
 * lands in the same place at any render size. [text] is the words inside it, when known.
 */
data class PageBox(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val text: String? = null
) {
    companion object {
        /** A box clamped onto the page. Returns null when nothing is left of it. */
        fun clamped(left: Double, top: Double, width: Double, height: Double, text: String? = null): PageBox? {
            val l = left.coerceIn(0.0, 1.0)
            val t = top.coerceIn(0.0, 1.0)
            val w = minOf(width, 1.0 - l).coerceAtLeast(0.0)
            val h = minOf(height, 1.0 - t).coerceAtLeast(0.0)
            return if (w > 0.0 && h > 0.0) PageBox(l, t, w, h, text) else null
        }
    }
}

/**
 * JSON for `document_pages.boxesJson` and `item_evidence.boxJson`: an array of `{x, y, w, h, t}`.
 * Tolerant on read: a single object, a bare array, or junk all parse to a list (junk to empty).
 */
object PageBoxes {
    fun encode(boxes: List<PageBox>): String? {
        if (boxes.isEmpty()) return null
        val array = JSONArray()
        boxes.forEach { box ->
            array.put(
                JSONObject()
                    .put("x", box.left)
                    .put("y", box.top)
                    .put("w", box.width)
                    .put("h", box.height)
                    .apply { box.text?.let { put("t", it) } }
            )
        }
        return array.toString()
    }

    fun parse(json: String?): List<PageBox> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val trimmed = json.trim()
            val items = if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                (0 until array.length()).map { array.getJSONObject(it) }
            } else {
                listOf(JSONObject(trimmed))
            }
            items.mapNotNull { o ->
                PageBox.clamped(
                    left = o.optDouble("x", Double.NaN).takeIf { !it.isNaN() } ?: return@mapNotNull null,
                    top = o.optDouble("y", Double.NaN).takeIf { !it.isNaN() } ?: return@mapNotNull null,
                    width = o.optDouble("w", Double.NaN).takeIf { !it.isNaN() } ?: return@mapNotNull null,
                    height = o.optDouble("h", Double.NaN).takeIf { !it.isNaN() } ?: return@mapNotNull null,
                    text = o.optString("t").takeIf { it.isNotEmpty() }
                )
            }
        }.getOrDefault(emptyList())
    }
}
