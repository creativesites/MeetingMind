package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.companion.CreateModePolicy
import kotlin.math.max
import kotlin.math.min

/** Where the companion sits on a Create card (signature moments §5.2). The top corners are never used. */
enum class CompanionAnchor {
    /** Under the words, above the foot; centred or start-aligned with the text (the default everywhere). */
    BELOW,
    /** In the foot row at the end corner; the attribution and watermark move to the start side. */
    CORNER_END
}

/** A rectangle in card pixels. Pure, so the placement rules are JVM tests. */
data class CardRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun intersects(o: CardRect): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    fun contains(o: CardRect, slack: Float = 0.5f): Boolean =
        o.left >= left - slack && o.top >= top - slack && o.right <= right + slack && o.bottom <= bottom + slack
}

/**
 * The companion's place on a card (§5.2), one rule shared by the renderer, the editor's reveal and the tests.
 *
 * Invariants: it never leaves the safe rect, never comes within 24 units of the text block (the renderer
 * reserves [reservedHeight] above the foot, so the words end above it) and never covers the attribution or
 * the watermark (the foot is reserved below it, and with [CompanionAnchor.CORNER_END] the foot text narrows
 * by [footInset]). On Square the corner uses a 13% side margin, because the profile grid crops 12.5% off
 * each side.
 */
object CompanionPlacement {
    /** Below this (at 1080 px) it reads as a sticker. */
    const val MinPx = 160f
    /** The foot's height, in units of width/1080 (attribution and watermark). */
    const val FootUnits = 100f
    const val TextGapUnits = 24f
    const val FootGapUnits = 12f
    const val FootInsetGapUnits = 16f
    /** On Square, the side margin a corner needs to survive the grid's 3:4 crop. */
    const val SquareCornerSide = 0.13f

    fun sizeFraction(format: CreateFormat): Float = when (format) {
        CreateFormat.STORY -> 0.20f
        CreateFormat.PORTRAIT -> 0.19f
        CreateFormat.SQUARE -> 0.18f
    }

    /** The anchors a format offers. Square's corner moves in to the 13% margin. */
    fun anchors(format: CreateFormat): List<CompanionAnchor> = CompanionAnchor.entries

    fun side(format: CreateFormat, anchor: CompanionAnchor): Float {
        val safe = CreateCardRenderer.safe(format).side
        return if (format == CreateFormat.SQUARE && anchor == CompanionAnchor.CORNER_END) max(safe, SquareCornerSide) else safe
    }

    /** The companion's size in pixels on a card [w] wide. */
    fun size(format: CreateFormat, w: Float): Float = max(w * sizeFraction(format), MinPx * w / 1080f)

    /** The size of the bitmap to draw for the export: twice the placed size, downsampled when drawn. */
    fun exportBitmapPx(format: CreateFormat): Int = (size(format, format.width.toFloat()) * 2).toInt()

    /** The largest export bitmap any format needs (the story's 216 px slot, at 2×). */
    val maxExportBitmapPx: Int get() = CreateFormat.entries.maxOf { exportBitmapPx(it) }

    private fun unit(w: Float, h: Float) = min(w, h) / 1080f

    /** The height the text block gives up for the companion (the companion plus its gaps). */
    fun reservedHeight(format: CreateFormat, w: Float, h: Float, anchor: CompanionAnchor): Float {
        val u = unit(w, h)
        val s = size(format, w)
        return when (anchor) {
            CompanionAnchor.BELOW -> s + (TextGapUnits + FootGapUnits) * u
            // The corner shares the foot row; whatever rises above the foot is kept free of words.
            CompanionAnchor.CORNER_END -> max(0f, s - FootUnits * u) + TextGapUnits * u
        }
    }

    /** How much narrower the foot text gets, from the end side, with the corner anchor. */
    fun footInset(format: CreateFormat, w: Float, h: Float, anchor: CompanionAnchor): Float {
        if (anchor != CompanionAnchor.CORNER_END) return 0f
        // From the text's end edge back past the companion's start edge (Square's corner sits further in).
        val textRight = safeRect(format, w, h).right
        val companionLeft = place(format, w, h, centered = true, anchor = anchor).left
        return textRight - companionLeft + FootInsetGapUnits * unit(w, h)
    }

    /** The safe rect for [anchor] (the renderer's safe fractions; Square's corner uses 13% sides). */
    fun safeRect(format: CreateFormat, w: Float, h: Float, anchor: CompanionAnchor = CompanionAnchor.BELOW): CardRect {
        val s = CreateCardRenderer.safe(format)
        val side = side(format, anchor)
        return CardRect(w * side, h * s.top, w * (1 - side), h * (1 - s.bottom))
    }

    /** The text block's lowest edge the renderer allows when the companion is on. */
    fun textBottomLimit(format: CreateFormat, w: Float, h: Float, anchor: CompanionAnchor): Float {
        val safe = safeRect(format, w, h)
        return safe.bottom - FootUnits * unit(w, h) - reservedHeight(format, w, h, anchor)
    }

    /**
     * The companion's rect on a card [w] × [h]. BELOW: its bottom sits 12 units above the foot, x follows the
     * text alignment. CORNER_END: in the foot row at the end corner ([rtl] flips the end).
     */
    fun place(format: CreateFormat, w: Float, h: Float, centered: Boolean, anchor: CompanionAnchor, rtl: Boolean = false): CardRect {
        val u = unit(w, h)
        val s = size(format, w)
        val safe = safeRect(format, w, h, anchor)
        return when (anchor) {
            CompanionAnchor.BELOW -> {
                val bottom = safe.bottom - FootUnits * u - FootGapUnits * u
                val left = when {
                    centered -> (w - s) / 2f
                    rtl -> safe.right - s
                    else -> safe.left
                }
                CardRect(left, bottom - s, left + s, bottom)
            }
            CompanionAnchor.CORNER_END -> {
                val left = if (rtl) safe.left else safe.right - s
                CardRect(left, safe.bottom - s, left + s, safe.bottom)
            }
        }
    }
}

/**
 * The pose a card's companion takes (§5.3): the person's override when the policy allows it, otherwise the
 * suggestion (which follows the vibe). Nothing from a model reaches the pose without passing
 * [CreateModePolicy]. The suggestion only picks a pose; it never writes or changes text.
 */
object CompanionPose {
    fun allowed(source: CreateSourceKind, vibe: CreateVibe, grief: Boolean = false, goodFriday: Boolean = false): List<CreateMode> {
        val set = CreateModePolicy.allowed(source.policy, vibe.policyVibe, grief, goodFriday)
        return CreateMode.entries.filter { it in set }
    }

    fun suggested(source: CreateSourceKind, vibe: CreateVibe, grief: Boolean = false, goodFriday: Boolean = false): CreateMode {
        val mode = CreateVibePolicy.companionMode(source, vibe, grief)
        val allowed = allowed(source, vibe, grief, goodFriday)
        return if (mode in allowed) mode else CreateModePolicy.suggest(source.policy, vibe.policyVibe, grief, goodFriday)
    }

    /** The pose to draw and export. */
    fun effective(source: CreateSourceKind, vibe: CreateVibe, override: CreateMode?, grief: Boolean = false, goodFriday: Boolean = false): CreateMode =
        override?.takeIf { it in allowed(source, vibe, grief, goodFriday) } ?: suggested(source, vibe, grief, goodFriday)

    /** The override the person chose that the card no longer allows (after a source or vibe change), or null. */
    fun fellBack(source: CreateSourceKind, vibe: CreateVibe, override: CreateMode?, grief: Boolean = false, goodFriday: Boolean = false): CreateMode? =
        override?.takeIf { it !in allowed(source, vibe, grief, goodFriday) }
}
