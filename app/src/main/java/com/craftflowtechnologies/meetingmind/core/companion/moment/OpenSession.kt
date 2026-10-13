package com.craftflowtechnologies.meetingmind.core.companion.moment

/**
 * One navigation entry into a note screen (signature moments §3.2): the unit "a fresh open" is counted in.
 *
 * A moment is planned at most once per entry. [claim] returns true exactly once, on the entry's first
 * composition, and false ever after for the same entry, so none of these replay a moment:
 *
 * - a configuration change or a process restore (the state is saved with the entry),
 * - coming back from a sub-screen (editor, share sheet, audio player, Back from a link): the entry stays
 *   on the back stack with its saved state,
 * - the app returning to the foreground (the composition is still there),
 * - the open that just played a First Keep ("Open note" only dismisses the overlay).
 *
 * A new navigation into the note (from the Notes list, Home, search, a notification or a snackbar) is a
 * new entry with a fresh session. In Compose, `rememberOpenSession()` (in `core.ui.mm.companion.moment`)
 * keeps one per entry with `rememberSaveable`; a ViewModel host can keep [claimed] in its `SavedStateHandle`.
 */
class OpenSession(claimed: Boolean = false) {

    /** Whether this entry already planned (and maybe played) its moment. Saved with the entry. */
    var claimed: Boolean = claimed
        private set

    /** True the first time only: this open is fresh and may plan a moment. */
    fun claim(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }
}
