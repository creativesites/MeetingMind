package com.craftflowtechnologies.meetingmind.feature.recording

import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.work.MarkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RecordingActionsTest {
    private fun labels(type: RecordingType) = RecordingActions.forType(type).map { it.label }

    @Test fun sermonHasHighlightScriptureNoteAndPrayerPoint() {
        assertEquals(listOf("Highlight", "Scripture", "Note", "Prayer point"), labels(RecordingType.SERMON))
        assertEquals(listOf(MarkKind.KEY, MarkKind.SCRIPTURE, MarkKind.NOTE, MarkKind.PRAYER), RecordingActions.forType(RecordingType.SERMON).map { it.kind })
    }

    @Test fun meetingsAndInterviewsMarkDecisionsActionsAndQuestions() {
        listOf(RecordingType.MEETING, RecordingType.INTERVIEW, RecordingType.CLIENT_CALL, RecordingType.ONE_ON_ONE).forEach {
            assertEquals(it.name, listOf("Decision", "Action item", "Question", "Note"), labels(it))
        }
    }

    @Test fun lecturesAndStudyMarkWhatIsImportant() {
        assertEquals(listOf("Important", "Question", "Note"), labels(RecordingType.LECTURE))
        assertEquals(labels(RecordingType.LECTURE), labels(RecordingType.BIBLE_STUDY))
    }

    @Test fun everythingElseGetsHighlightAndNote() {
        listOf(RecordingType.GENERAL, RecordingType.VOICE_MEMO, RecordingType.PRAYER, RecordingType.CUSTOM).forEach {
            assertEquals(it.name, listOf("Highlight", "Note"), labels(it))
        }
        assertSame(RecordingActions.default, RecordingActions.forType(RecordingType.JOURNAL))
    }

    @Test fun everyTypeHasButtonsWithUniqueIds() {
        RecordingType.entries.forEach { type ->
            val ids = RecordingActions.forType(type).map { it.id }
            assertEquals(type.name, ids.distinct(), ids)
            assert(ids.isNotEmpty())
        }
    }

    @Test fun typedLinesMustNotBeBlank() {
        val note = RecordingActions.sermon.first { it.id == "note" }
        assertNull(RecordingActions.accept(note, "   "))
        assertEquals("Grace is not earned", RecordingActions.accept(note, "  Grace is not earned "))
    }

    @Test fun aReferenceIsKeptOnlyWhenTheScriptureParserUnderstandsIt() {
        val scripture = RecordingActions.sermon.first { it.id == "scripture" }
        assertEquals("John 3:16", RecordingActions.accept(scripture, "john 3:16"))
        assertEquals("Psalm 23", RecordingActions.accept(scripture, "Ps 23"))
        assertNull(RecordingActions.accept(scripture, "the good book"))
        assertNull(RecordingActions.accept(scripture, ""))
    }

    @Test fun aOneTapButtonKeepsNoText() {
        assertEquals("", RecordingActions.accept(RecordingActions.sermon.first { it.id == "highlight" }, "ignored"))
    }
}
