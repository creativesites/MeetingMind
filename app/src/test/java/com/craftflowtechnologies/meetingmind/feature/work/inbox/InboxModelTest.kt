package com.craftflowtechnologies.meetingmind.feature.work.inbox

import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.feature.work.InboxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Inbox's rules as plain functions: states, filters, the one-tap label, and the grounded sentence. */
class InboxModelTest {
    private fun item(
        kind: String = "PDF",
        status: String = "NEW",
        readStatus: String = "NONE",
        pageCount: Int? = null,
        readPages: Int = 0,
        transcription: String = "NONE",
        text: String? = null
    ) = InboxItemEntity(
        id = "i1", kind = kind, status = status, createdAt = 0L, text = text, readStatus = readStatus,
        pageCount = pageCount, readPages = readPages, transcriptionStatus = transcription
    )

    @Test fun aReadingDocumentSaysWhichPageItIsOn() {
        assertEquals("Reading page 5 of 12", InboxRules.stateLine(item(readStatus = "READING", pageCount = 12, readPages = 4))!!.text)
        assertEquals("Reading on this phone", InboxRules.stateLine(item(readStatus = "READING"))!!.text)
        assertEquals("Waiting to read", InboxRules.stateLine(item(readStatus = "PENDING"))!!.text)
    }

    @Test fun aLockedOrFailedDocumentIsAProblemAndANoTextOneIsNot() {
        assertTrue(InboxRules.stateLine(item(readStatus = "LOCKED"))!!.problem)
        assertTrue(InboxRules.stateLine(item(readStatus = "FAILED"))!!.problem)
        val noText = InboxRules.stateLine(item(readStatus = "NO_TEXT"))!!
        assertFalse(noText.problem)
        assertEquals("No readable text · filed by name", noText.text)
    }

    @Test fun aFailedTranscriptionIsAProblemAndADoneOneHasNoStateLine() {
        assertTrue(InboxRules.stateLine(item(kind = "AUDIO", transcription = "FAILED"))!!.problem)
        assertEquals("Transcribing on this phone", InboxRules.stateLine(item(kind = "AUDIO", transcription = "TRANSCRIBING"))!!.text)
        assertNull(InboxRules.stateLine(item(kind = "AUDIO", transcription = "COMPLETED")))
        assertNull(InboxRules.stateLine(item(kind = "TEXT")))
    }

    @Test fun needsYouIsASuggestionToConfirmOrAFailure() {
        assertTrue(InboxRules.matches(InboxFilter.NEEDS_ACTION, item(status = "PROPOSED")))
        assertTrue(InboxRules.matches(InboxFilter.NEEDS_ACTION, item(readStatus = "LOCKED")))
        assertFalse(InboxRules.matches(InboxFilter.NEEDS_ACTION, item(status = "NEW", readStatus = "READING")))
        assertFalse(InboxRules.matches(InboxFilter.FILED, item(status = "FILED")))
    }

    @Test fun theFilterTabsSplitMediaFromDocuments() {
        assertTrue(InboxRules.matches(InboxFilter.MEDIA, item(kind = "AUDIO")))
        assertFalse(InboxRules.matches(InboxFilter.MEDIA, item(kind = "PDF")))
        assertTrue(InboxRules.matches(InboxFilter.DOCS, item(kind = "PDF")))
        assertTrue(InboxRules.matches(InboxFilter.DOCS, item(kind = "URL")))
        assertFalse(InboxRules.matches(InboxFilter.DOCS, item(kind = "AUDIO")))
    }

    @Test fun theOneTapLabelNamesTheProjectOrItsFirstWordWhenLong() {
        assertEquals("File to Kalahari", InboxRules.fileToLabel("Kalahari"))
        assertEquals("File to Acme", InboxRules.fileToLabel("Acme Integration Programme"))
    }

    @Test fun theGroundedSentenceIsTheOneThatSharesTheClaimsWords() {
        val transcript = "Hi Ana, quick one. IT security wants the API documentation before they release the production credentials. Thanks."
        val range = InboxRules.groundedSpan(transcript, listOf("Send the API documentation to IT security"))!!
        assertTrue(transcript.substring(range).trim().startsWith("IT security"))
        assertNull(InboxRules.groundedSpan(transcript, listOf("Buy a spaceship")))
        assertNull(InboxRules.groundedSpan("", listOf("anything")))
    }

    @Test fun theSummaryComesFromTheTranscriptBeforeTheSuggestion() {
        assertEquals("From the recording", InboxRules.summaryOf(item(), com.craftflowtechnologies.meetingmind.feature.work.InboxLinkedMeeting(
            meetingId = "m", noteId = null, title = "t", summaryText = "From the recording", transcriptPreview = null,
            status = "READY", audioFilePath = null, durationMs = 0L, participantCount = 1
        )))
        assertNull(InboxRules.summaryOf(item(), null))
    }
}
