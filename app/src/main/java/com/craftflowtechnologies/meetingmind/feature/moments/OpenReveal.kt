package com.craftflowtechnologies.meetingmind.feature.moments

import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome
import com.craftflowtechnologies.meetingmind.core.model.RecordingType

/**
 * The note-opening reveal: First Keep's card bloom, worded for the note's type ("Your lecture notes", "Your
 * meeting notes · 3 action items, kept"), played when a finished note is opened in the editor. It closes into the
 * note by itself after a short hold, and any tap skips it.
 *
 * It replaces that open's Unveil, once per note per app session, so reopening a note you were just in is
 * instant. Never for a sensitive note (grief, funeral), a partial or failed one, or when the planner chose
 * no motion (reduced motion, a recording running): those plans aren't an Unveil to begin with.
 */
object OpenReveal {

    /** The wording family for a note type. Each has its own headline and sub line in `strings_moments.xml`. */
    enum class Kind { MEETING, INTERVIEW, LECTURE, RESEARCH, SERMON, FAITH, IDEAS, PERSONAL }

    private val played = mutableSetOf<String>()

    /** Whether this open's [plan] may become the reveal, for a note with these facts. */
    fun eligible(plan: MomentPlan?, facts: NoteFacts): Boolean =
        plan is MomentPlan.Unveil && facts.outcome == NoteOutcome.COMPLETE && !facts.sensitive

    /** Claims the reveal for [noteId] in this app session: true the first time only. */
    fun claim(noteId: String): Boolean = synchronized(played) { played.add(noteId) }

    /** Forgets every claim (tests). */
    internal fun resetForTest() = synchronized(played) { played.clear() }

    /** The First Keep plan that draws the reveal: never the once-ever play, never a chime. */
    fun planFor(unveil: MomentPlan.Unveil): MomentPlan.FirstKeep =
        MomentPlan.FirstKeep(quiet = unveil.quiet, hello = false, onceEver = false, chime = false, space = unveil.space)

    fun kindOf(type: RecordingType): Kind = when (type) {
        RecordingType.MEETING, RecordingType.CLIENT_CALL, RecordingType.ONE_ON_ONE, RecordingType.STANDUP,
        RecordingType.CONSULTATION, RecordingType.PROJECT_BRIEF, RecordingType.DECISION_RECORD,
        RecordingType.WEEKLY_REVIEW -> Kind.MEETING
        RecordingType.INTERVIEW -> Kind.INTERVIEW
        RecordingType.LECTURE, RecordingType.MOTIVATION, RecordingType.LIFE_SKILLS -> Kind.LECTURE
        RecordingType.RESEARCH -> Kind.RESEARCH
        RecordingType.SERMON -> Kind.SERMON
        RecordingType.BIBLE_STUDY, RecordingType.DEVOTIONAL, RecordingType.PRAYER, RecordingType.PRAYER_REQUEST,
        RecordingType.TESTIMONY, RecordingType.GRATITUDE -> Kind.FAITH
        RecordingType.IDEA, RecordingType.BRAINSTORM -> Kind.IDEAS
        else -> Kind.PERSONAL
    }
}
