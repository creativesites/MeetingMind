package com.craftflowtechnologies.meetingmind.feature.work.inbox

import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.core.documents.DocumentReadState
import com.craftflowtechnologies.meetingmind.core.documents.ReadStatus
import com.craftflowtechnologies.meetingmind.core.work.ChangeDetector
import com.craftflowtechnologies.meetingmind.core.work.Filing
import com.craftflowtechnologies.meetingmind.core.work.InboxKind
import com.craftflowtechnologies.meetingmind.core.work.InboxStatus
import com.craftflowtechnologies.meetingmind.core.youtube.YouTubeUrlParser
import com.craftflowtechnologies.meetingmind.feature.work.InboxFilter
import com.craftflowtechnologies.meetingmind.feature.work.InboxLinkedMeeting

/** What a card or an item screen says about an item's state, in one plain line. */
data class InboxStateLine(val text: String, val problem: Boolean = false)

/** Something the person can undo right after it happened. */
enum class InboxUndoKind { DISMISS, FILE }

data class InboxUndo(val itemId: String, val kind: InboxUndoKind, val message: String)

/** Pure rules for the Work Inbox (spec "Inbox", #8a–#8g). No Android types, so they are unit tested. */
object InboxRules {
    private val documentKinds = setOf(InboxKind.PDF.name, InboxKind.IMAGE.name, InboxKind.URL.name, InboxKind.FILE.name, InboxKind.TEXT.name)

    fun isDocument(item: InboxItemEntity): Boolean = item.kind == InboxKind.PDF.name || item.kind == InboxKind.IMAGE.name

    fun isMedia(item: InboxItemEntity): Boolean = item.kind == InboxKind.AUDIO.name || item.kind == InboxKind.VIDEO.name

    fun isYouTube(item: InboxItemEntity): Boolean =
        item.kind == InboxKind.URL.name && YouTubeUrlParser.isYouTubeUrl(item.text ?: item.uri)

    /** The one suggestion stored on the item, if any. Nothing in it has happened yet. */
    fun suggestion(item: InboxItemEntity): Filing? = Filing.fromJson(item.proposedJson)

    /** Items that need the person: a suggestion to confirm, or something that could not be read. */
    fun needsYou(item: InboxItemEntity): Boolean =
        item.status == InboxStatus.PROPOSED.name ||
            item.transcriptionStatus == "FAILED" ||
            ReadStatus.parse(item.readStatus) in setOf(ReadStatus.LOCKED, ReadStatus.FAILED, ReadStatus.NO_TEXT)

    /** Whether an open item shows under [filter]. Filed items are a separate list and never match here. */
    fun matches(filter: InboxFilter, item: InboxItemEntity): Boolean = when (filter) {
        InboxFilter.ALL -> true
        InboxFilter.NEEDS_ACTION -> needsYou(item)
        InboxFilter.MEDIA -> isMedia(item)
        InboxFilter.DOCS -> item.kind in documentKinds
        InboxFilter.FILED -> false
    }

    /** The state line for a card, or null when there is nothing to say (a proposal stands on its own). */
    fun stateLine(item: InboxItemEntity): InboxStateLine? {
        if (item.transcriptionStatus == "FAILED") return InboxStateLine("Couldn't be transcribed", problem = true)
        if (isMedia(item) || isYouTube(item)) {
            return when (item.transcriptionStatus) {
                "TRANSCRIBING" -> InboxStateLine("Transcribing on this phone")
                "PENDING" -> InboxStateLine("Waiting to transcribe")
                else -> null
            }
        }
        if (!isDocument(item)) return null
        return when (val read = DocumentReadState.from(item.readStatus, item.pageCount, item.readPages)) {
            DocumentReadState.Pending -> InboxStateLine("Waiting to read")
            is DocumentReadState.Reading -> InboxStateLine(readingLine(read.pageCount, read.readPages))
            is DocumentReadState.Done -> InboxStateLine("Read · ${pagesLabel(read.pageCount)}")
            is DocumentReadState.NoText -> InboxStateLine("No readable text · filed by name")
            DocumentReadState.Locked -> InboxStateLine("Locked · couldn't be read", problem = true)
            DocumentReadState.Failed -> InboxStateLine("Couldn't be read", problem = true)
            DocumentReadState.NotStarted -> null
        }
    }

    /** "Reading page 5 of 12", or "Reading on this phone" until the page count is known. */
    fun readingLine(pageCount: Int?, readPages: Int): String =
        if (pageCount == null || pageCount <= 0) "Reading on this phone"
        else "Reading page ${(readPages + 1).coerceAtMost(pageCount)} of $pageCount"

    fun pagesLabel(pageCount: Int): String = if (pageCount == 1) "1 page" else "$pageCount pages"

    /** The summary to show: the transcript's summary for a recording, or the document's summary from its suggestion. */
    fun summaryOf(item: InboxItemEntity, linked: InboxLinkedMeeting?): String? =
        linked?.summaryText?.takeIf { it.isNotBlank() }
            ?: suggestion(item)?.summary?.takeIf { it.isNotBlank() }

    /** The one-tap label, short enough for a button: the project's name, or its first word when long. */
    fun fileToLabel(destination: String): String =
        "File to " + if (destination.length <= 16) destination else destination.substringBefore(' ')

    /** The sentence of [transcript] that shares the most words with [claims], as a range; null when none does. */
    fun groundedSpan(transcript: String, claims: List<String>): IntRange? {
        if (transcript.isBlank()) return null
        val wanted = claims.flatMap { ChangeDetector.words(it) }.toSet()
        if (wanted.isEmpty()) return null
        var best: IntRange? = null
        var bestScore = 0
        for (sentence in SENTENCE.findAll(transcript)) {
            val score = ChangeDetector.words(sentence.value).count { it in wanted }
            if (score > bestScore) {
                bestScore = score
                best = sentence.range
            }
        }
        return best
    }

    private val SENTENCE = Regex("[^.!?\\n]+[.!?]?")
}
