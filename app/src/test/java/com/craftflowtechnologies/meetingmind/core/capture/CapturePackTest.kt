package com.craftflowtechnologies.meetingmind.core.capture

import com.craftflowtechnologies.meetingmind.core.work.Mark
import com.craftflowtechnologies.meetingmind.core.work.MarkKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePackTest {
    private val snapshot = CaptureContextSnapshot(
        workflow = "SERMON",
        fields = mapOf("preacher" to "Pastor Ruth"),
        customFocus = "Track the passages",
        sourceTitle = "Part 2",
        capturedAt = 1L
    )

    private fun pack(
        marks: List<PackMark> = emptyList(),
        segments: List<PackSegment> = listOf(PackSegment("s1", "p1", 0, "Grace is a gift."))
    ) = CapturePack.build(
        outputSchema = "Return key points.",
        focus = "Pay attention to application",
        sharedNote = mapOf("series" to "Faith in Practice"),
        snapshots = listOf("p2" to snapshot),
        marks = marks,
        segments = segments
    )

    @Test
    fun sectionsAppearInTheSpecOrder() {
        val text = pack()
        val order = listOf("## Rules", "## Output", "## Custom focus", "## Note context", "## Recording p2 context", "## Transcript (evidence)")
            .map { text.indexOf(it) }
        assertTrue("sections out of order: $order", order.zipWithNext().all { (a, b) -> a in 0 until b })
    }

    @Test
    fun contextIsLabelledAsMetadataNotEvidence() {
        val text = pack()
        assertTrue(text.contains("Note context (user-provided metadata, not transcript evidence)"))
        assertTrue(text.contains("Recording p2 context (user-provided metadata, not transcript evidence)"))
        // Context values never appear inside the transcript section.
        assertFalse(text.substringAfter("## Transcript (evidence)").contains("Pastor Ruth"))
    }

    @Test
    fun everyMarkIsIncludedInTimeOrderWithItsId() {
        val marks = listOf(
            PackMark("m2", "p1", Mark(MarkKind.KEY, 9_000)),
            PackMark("m1", "p1", Mark(MarkKind.ACTION, 2_000, "Send notes")),
            PackMark("m3", "p2", Mark(MarkKind.NOTE, 1_000, "Ask Sam"))
        )
        val marksSection = pack(marks = marks).substringAfter("## Marks from the person").substringBefore("## Transcript")
        val ids = Regex("\\[(m\\d)\\]").findAll(marksSection).map { it.groupValues[1] }.toList()
        assertEquals(listOf("m1", "m2", "m3"), ids)
    }

    @Test
    fun aMarksWordsNeverJoinTheTranscript() {
        val text = pack(marks = listOf(PackMark("m1", "p1", Mark(MarkKind.NOTE, 1_000, "ask pastor about this"))))
        assertFalse(text.substringAfter("## Transcript (evidence)").contains("ask pastor"))
        assertTrue(text.contains("text=\"ask pastor about this\""))
    }

    @Test
    fun segmentsKeepTheirIdsAndLocalTimes() {
        val text = pack(segments = listOf(PackSegment("s2", "p2", 5_000, "Second part.")))
        assertTrue(text.contains("[s2] recording=p2 at=5000ms: Second part."))
    }

    @Test
    fun theSameInputGivesTheSameText() {
        val marks = listOf(PackMark("m1", "p1", Mark(MarkKind.KEY, 1)), PackMark("m2", "p1", Mark(MarkKind.KEY, 2)))
        assertEquals(pack(marks), pack(marks.reversed()))
    }

    @Test
    fun aSnapshotRoundTripsAndKeepsFieldsItDoesNotKnow() {
        val json = JSONObject(snapshot.toJson()).put("futureField", JSONObject().put("x", 1)).toString()
        val read = CaptureContextSnapshot.fromJson(json)!!
        assertEquals("SERMON", read.workflow)
        assertEquals(mapOf("preacher" to "Pastor Ruth"), read.fields)
        assertEquals("Track the passages", read.customFocus)
        assertEquals(1, JSONObject(read.toJson()).getJSONObject("futureField").getInt("x"))
    }

    @Test
    fun aBrokenSnapshotReadsAsNullInsteadOfFailing() {
        assertNull(CaptureContextSnapshot.fromJson("{not json"))
        assertNull(CaptureContextSnapshot.fromJson(null))
    }
}
