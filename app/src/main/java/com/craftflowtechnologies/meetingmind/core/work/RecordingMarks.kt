package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The marks tapped during the recording in progress (§4.2), wherever they were tapped: on the
 * recorder, on the notification, or on the lock screen. One list, so the screen and the
 * notification always agree, and it survives the recorder screen being closed. When the
 * recording is saved the list is taken and kept on the note ([Marks.save]).
 */
object RecordingMarks {
    private val _marks = MutableStateFlow<List<Mark>>(emptyList())
    val marks: StateFlow<List<Mark>> = _marks.asStateFlow()

    fun add(kind: MarkKind, atMs: Long): Mark = Mark(kind, atMs).also { m -> _marks.value = _marks.value + m }

    /** Everything marked so far, leaving the list empty for the next recording. */
    fun take(): List<Mark> = _marks.value.also { _marks.value = emptyList() }

    fun clear() { _marks.value = emptyList() }
}

/**
 * What a tap on a mark action means. The recording service turns the notification's intents into
 * marks here, so the mapping (and that a tap writes a mark at the recording's own clock) can be
 * tested without a service.
 */
object MarkActions {
    private const val PREFIX = "com.craftflowtechnologies.meetingmind.meetmind.ACTION_MARK_"
    val KEY_ACTION = PREFIX + MarkKind.KEY.name
    val ACTION_ACTION = PREFIX + MarkKind.ACTION.name
    val QUESTION_ACTION = PREFIX + MarkKind.QUESTION.name

    fun actionOf(kind: MarkKind) = PREFIX + kind.name

    fun kindOf(action: String?): MarkKind? =
        if (action != null && action.startsWith(PREFIX)) MarkKind.entries.firstOrNull { it.name == action.removePrefix(PREFIX) } else null

    /** A mark action arrived [elapsedMs] into the recording: write the mark. Anything else is not ours. */
    fun handle(action: String?, elapsedMs: Long): Mark? = kindOf(action)?.let { RecordingMarks.add(it, elapsedMs) }
}
