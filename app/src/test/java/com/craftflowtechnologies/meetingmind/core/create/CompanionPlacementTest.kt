package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Z-22: the companion's place on a card and its pose (signature moments §5.2, §5.3, §5.6). */
class CompanionPlacementTest {

    @Test fun `every format, alignment and anchor keeps the invariants`() {
        for (f in CreateFormat.entries) for (centered in listOf(true, false)) for (anchor in CompanionAnchor.entries) for (scale in listOf(1f, 0.4f)) {
            val w = f.width * scale
            val h = f.height * scale
            val u = minOf(w, h) / 1080f
            val r = CompanionPlacement.place(f, w, h, centered, anchor)
            val safe = CompanionPlacement.safeRect(f, w, h, anchor)
            val case = "$f centered=$centered $anchor scale=$scale"
            assertTrue("inside the safe rect: $case", safe.contains(r))
            // The words end at the limit; the companion stays at least 24 units below it, short or long text alike.
            val textBottom = CompanionPlacement.textBottomLimit(f, w, h, anchor)
            assertTrue("24u clear of the words: $case", r.top - textBottom >= 24f * u - 0.5f)
            val longText = CardRect(safe.left, safe.top, safe.right, textBottom)
            val shortText = CardRect(safe.left + w * 0.3f, textBottom - 40f * u, safe.right - w * 0.3f, textBottom)
            assertFalse(case, r.intersects(longText)); assertFalse(case, r.intersects(shortText))
            // Never over the attribution or the watermark: the foot text narrows by the inset on the corner.
            val footTop = safe.bottom - CompanionPlacement.FootUnits * u
            val footText = CardRect(CompanionPlacement.safeRect(f, w, h).left, footTop, CompanionPlacement.safeRect(f, w, h).right - CompanionPlacement.footInset(f, w, h, anchor), safe.bottom)
            assertFalse("clear of the foot: $case", r.intersects(footText))
            // At least 160 px at full size, and the story's slot is 20% of the width.
            assertTrue(r.width >= CompanionPlacement.MinPx * w / 1080f - 0.5f)
            if (centered && anchor == CompanionAnchor.BELOW) assertEquals(w / 2f, (r.left + r.right) / 2f, 0.5f)
        }
        assertEquals(216f, CompanionPlacement.size(CreateFormat.STORY, 1080f), 0.5f)
    }

    @Test fun `the square corner keeps a 13 percent margin, and the top corners are never used`() {
        val f = CreateFormat.SQUARE
        val r = CompanionPlacement.place(f, 1080f, 1080f, centered = true, anchor = CompanionAnchor.CORNER_END)
        assertTrue(1080f - r.right >= 0.13f * 1080f - 0.5f)
        CreateFormat.entries.forEach { fmt ->
            CompanionAnchor.entries.forEach { a -> assertTrue(CompanionPlacement.place(fmt, fmt.width.toFloat(), fmt.height.toFloat(), true, a).top > fmt.height / 2f) }
        }
    }

    @Test fun `the export bitmap is twice the placed size`() {
        assertEquals(432, CompanionPlacement.exportBitmapPx(CreateFormat.STORY))
        assertEquals(432, CompanionPlacement.maxExportBitmapPx)
    }

    @Test fun `a prayer request offers only Prayerful and Peaceful`() {
        assertEquals(listOf(CreateMode.PRAYERFUL, CreateMode.PEACEFUL), CompanionPose.allowed(CreateSourceKind.PRAYER, CreateVibe.PEACEFUL))
        assertEquals(CreateMode.PRAYERFUL, CompanionPose.suggested(CreateSourceKind.PRAYER, CreateVibe.PRAYERFUL))
    }

    @Test fun `an override that becomes disallowed falls back to the suggestion`() {
        val chosen = CreateMode.JOYFUL
        assertEquals(chosen, CompanionPose.effective(CreateSourceKind.ACHIEVEMENT, CreateVibe.PEACEFUL, chosen))
        assertNull(CompanionPose.fellBack(CreateSourceKind.ACHIEVEMENT, CreateVibe.PEACEFUL, chosen))
        // The source changes to a prayer request: Joyful is no longer offered.
        val after = CompanionPose.effective(CreateSourceKind.PRAYER, CreateVibe.PEACEFUL, chosen)
        assertTrue(after == CreateMode.PRAYERFUL || after == CreateMode.PEACEFUL)
        assertEquals(chosen, CompanionPose.fellBack(CreateSourceKind.PRAYER, CreateVibe.PEACEFUL, chosen))
        // Grief and Good Friday step down too.
        assertEquals(listOf(CreateMode.PEACEFUL), CompanionPose.allowed(CreateSourceKind.TESTIMONY, CreateVibe.GRATEFUL, grief = true))
        assertFalse(CreateMode.CELEBRATORY in CompanionPose.allowed(CreateSourceKind.ACHIEVEMENT, CreateVibe.PEACEFUL, goodFriday = true))
    }
}
