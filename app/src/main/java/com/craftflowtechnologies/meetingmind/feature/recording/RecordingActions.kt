package com.craftflowtechnologies.meetingmind.feature.recording

import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.work.MarkKind

/** What a button asks for before it writes its marker. */
enum class ActionInput {
    /** Nothing: one tap marks the moment. */
    NONE,
    /** A short typed line. */
    TEXT,
    /** A Bible reference, checked by the scripture parser before it is kept. */
    REFERENCE
}

/** One button on the recording screen. [id] is stable (tests, analytics); [label] is what people read. */
data class RecordingAction(val id: String, val label: String, val kind: MarkKind, val input: ActionInput = ActionInput.NONE)

/**
 * The buttons under the timer, by what is being recorded (R-1). Data, not layout: adding a type or
 * a button is one line here, and the screen renders whatever it gets. Each button writes a
 * [MarkKind] marker at the recording's own clock.
 */
object RecordingActions {
    private val highlight = RecordingAction("highlight", "Highlight", MarkKind.KEY)
    private val important = RecordingAction("important", "Important", MarkKind.KEY)
    private val note = RecordingAction("note", "Note", MarkKind.NOTE, ActionInput.TEXT)
    private val question = RecordingAction("question", "Question", MarkKind.QUESTION)

    val sermon = listOf(
        highlight,
        RecordingAction("scripture", "Scripture", MarkKind.SCRIPTURE, ActionInput.REFERENCE),
        note,
        RecordingAction("prayer", "Prayer point", MarkKind.PRAYER, ActionInput.TEXT)
    )
    val meeting = listOf(
        RecordingAction("decision", "Decision", MarkKind.DECISION),
        RecordingAction("action", "Action item", MarkKind.ACTION),
        question,
        note
    )
    val study = listOf(important, question, note)
    val default = listOf(highlight, note)

    fun forType(type: RecordingType): List<RecordingAction> = when (type) {
        RecordingType.SERMON -> sermon
        RecordingType.MEETING, RecordingType.INTERVIEW, RecordingType.CLIENT_CALL, RecordingType.ONE_ON_ONE,
        RecordingType.STANDUP, RecordingType.CONSULTATION -> meeting
        RecordingType.LECTURE, RecordingType.RESEARCH, RecordingType.BIBLE_STUDY -> study
        else -> default
    }

    /**
     * What to keep for a button's input, or null when it is not good enough yet: a typed line must
     * not be blank; a reference must be a verse the scripture parser understands, and is kept in its
     * canonical form ("jn 3:16" becomes "John 3:16"). Buttons that ask for nothing keep nothing.
     */
    fun accept(action: RecordingAction, raw: String): String? = when (action.input) {
        ActionInput.NONE -> ""
        ActionInput.TEXT -> raw.trim().ifBlank { null }
        ActionInput.REFERENCE -> ScriptureReferenceParser.parse(raw.trim())?.display()
    }
}
