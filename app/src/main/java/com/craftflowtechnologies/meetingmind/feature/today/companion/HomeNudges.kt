package com.craftflowtechnologies.meetingmind.feature.today.companion

import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/**
 * What the companion says on a home: one short line about what is actually happening, never a
 * generic slogan. Pure, so the choice is tested; [HomeCompanionLine] draws it.
 *
 * In order: a note that has just arrived and hasn't been opened, a recording still being written up,
 * a first hello for someone new, a welcome back after a few quiet days, then one tip a day for the
 * first two weeks. Anything already done or waved away stays away. No streaks and no "you missed"
 * lines (companion spec §5.7): the weekly rhythm line covers what happened, kindly.
 */
object HomeNudges {

    /** Quiet days before the companion says "welcome back". */
    const val AWAY_DAYS = 4
    /** How long a just-arrived note is news. */
    const val READY_FOR_MS = 6 * 60 * 60 * 1000L
    /** Tips run for this many days after the first note. */
    const val TIP_DAYS = 14

    fun pick(facts: HomeNudgeFacts, seen: (String) -> Boolean): HomeNudge? {
        facts.readyMeeting?.let { m ->
            // A home that can't open a recording opens its note instead; with neither, there's nothing to offer.
            val open = if (facts.canOpenRecording) NudgeAction.OpenRecording(m.id) else m.noteId?.let { NudgeAction.OpenNote(it) }
            if (open != null) {
                val n = HomeNudge(
                    key = "ready:${m.id}", kind = NudgeKind.NOTE_READY, state = CompanionState.PROUD,
                    text = "Your notes from “${m.title.short()}” are ready.",
                    action = open, actionLabel = "Open"
                )
                if (!seen(n.key)) return n
            }
        }
        facts.processing?.let { m ->
            // Not dismissable for good: it goes away by itself when the notes arrive.
            return HomeNudge(
                key = "processing:${m.id}", kind = NudgeKind.PROCESSING, state = CompanionState.THINKING,
                text = if (facts.processingCount > 1) "I'm writing up ${facts.processingCount} recordings. I'll let you know when they're ready."
                else "I'm writing up “${m.title.short()}”. I'll let you know when it's ready.",
                action = NudgeAction.OpenRecording(m.id).takeIf { facts.canOpenRecording },
                actionLabel = "See progress".takeIf { facts.canOpenRecording }, dismissable = false
            )
        }
        if (facts.isNew && facts.firstRun) {
            val n = HomeNudge(
                key = "first", kind = NudgeKind.FIRST_RUN, state = CompanionState.CURIOUS,
                text = "Hi, I'm ${facts.companionName}. Tap Record during ${example(facts.space)} and I'll turn it into notes you can keep.",
                action = NudgeAction.Record, actionLabel = "Record"
            )
            if (!seen(n.key)) return n
        }
        val last = facts.lastNote
        if (last != null && facts.daysSinceLastNote >= AWAY_DAYS) {
            val n = HomeNudge(
                key = "back:${facts.today}", kind = NudgeKind.WELCOME_BACK, state = CompanionState.CURIOUS,
                text = "Good to see you. Want to pick up with “${last.title.short()}”?",
                action = NudgeAction.OpenNote(last.id), actionLabel = "Open note"
            )
            if (!seen(n.key)) return n
        }
        val firstDay = facts.firstNoteDay
        if (firstDay != null && !facts.isNew && facts.today - firstDay in 0 until TIP_DAYS) {
            // One tip a day at most: today's (until it's waved away), or else the first one not yet seen.
            facts.tipShownToday?.let { t -> return tip(t).takeIf { !seen(it.key) } }
            return Tip.entries.firstOrNull { !seen("tip:${it.name}") && it.available(facts) }?.let(::tip)
        }
        return null
    }

    private fun tip(t: Tip) = HomeNudge(
        key = "tip:${t.name}", kind = NudgeKind.TIP, state = CompanionState.CURIOUS,
        text = t.text, action = t.action, actionLabel = t.actionLabel, dismissLabel = "Got it", tip = t
    )

    private fun example(space: NotebookSpace?) = when (space) {
        NotebookSpace.WORK -> "a meeting"
        NotebookSpace.LEARNING -> "a lecture"
        NotebookSpace.FAITH -> "a sermon"
        else -> "a meeting, a class or a sermon"
    }

    private fun String.short(max: Int = 40) = trim().let { if (it.length <= max) it else it.take(max - 1).trimEnd() + "…" }
}

/** The first-two-weeks tips: things people miss, one a day. */
enum class Tip(val text: String, val action: NudgeAction?, val actionLabel: String?) {
    YOUTUBE("Paste a YouTube link and I'll write notes from it, with timestamps you can tap.", NudgeAction.YouTube, "Try it"),
    SEARCH("I keep every word. Search finds what anyone said, in any recording.", NudgeAction.Search, "Search"),
    ASK("Ask me about a note, like “What did we decide?”. Open its ⋯ menu and choose Assistant.", null, null),
    SHARE("Share any note as a card, a PDF or audio, from the share button at the top.", null, null),
    QUIET("Long-press me any time to choose how often I show up.", null, null);

    fun available(facts: HomeNudgeFacts) = action !is NudgeAction.Search || facts.canSearch
}

enum class NudgeKind { NOTE_READY, PROCESSING, FIRST_RUN, WELCOME_BACK, TIP }

sealed interface NudgeAction {
    data object Record : NudgeAction
    data object YouTube : NudgeAction
    data object Search : NudgeAction
    data class OpenNote(val noteId: String) : NudgeAction
    data class OpenRecording(val meetingId: String) : NudgeAction
}

data class HomeNudge(
    /** What "seen" is stored under; once seen (opened or waved away) it doesn't come back. */
    val key: String,
    val kind: NudgeKind,
    val state: CompanionState,
    val text: String,
    val action: NudgeAction?,
    val actionLabel: String?,
    val dismissable: Boolean = true,
    val dismissLabel: String = "Not now",
    val tip: Tip? = null
)

/** One recording or note the line can name. */
data class NudgeItem(val id: String, val title: String, val noteId: String? = null)

/** Everything [HomeNudges.pick] looks at. Days are local day numbers (see LocalClock.localDay). */
data class HomeNudgeFacts(
    val today: Long,
    val companionName: String = "your companion",
    val space: NotebookSpace? = null,
    /** Nothing recorded or written yet. */
    val isNew: Boolean = false,
    /** False where Home already greets someone new another way (Today's getting-started card). */
    val firstRun: Boolean = true,
    /** The newest finished recording whose note hasn't been opened yet. */
    val readyMeeting: NudgeItem? = null,
    val processing: NudgeItem? = null,
    val processingCount: Int = 0,
    val lastNote: NudgeItem? = null,
    val daysSinceLastNote: Long = 0,
    val firstNoteDay: Long? = null,
    /** The tip already shown today, so it stays put while today lasts. */
    val tipShownToday: Tip? = null,
    /** Whether this home has a Search to open. */
    val canSearch: Boolean = true,
    /** Whether this home can open a recording (its processing or detail page). */
    val canOpenRecording: Boolean = true,
    /** When recent notes were kept, for the weekly rhythm line (times only, never content). */
    val noteTimes: List<Long> = emptyList(),
    /** When daily devotionals were opened, for the Faith rhythm line and milestone. */
    val devotionalReadTimes: List<Long> = emptyList(),
    /** The local clock the facts were read with, for the rhythm line's week. */
    val nowMs: Long = 0,
    val utcOffsetMs: Long = 0
)
