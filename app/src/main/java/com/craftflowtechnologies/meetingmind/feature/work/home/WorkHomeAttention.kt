package com.craftflowtechnologies.meetingmind.feature.work.home

/**
 * What can need the person on the Work home, most urgent first. The declaration order is the
 * ranking, so [WorkHomeAttention.rank] sorts by ordinal.
 */
enum class HomeAttentionKind { FAILED, YOU_OWE, REVIEW, FOLLOW_UP, DECISION, THEY_OWE, INBOX, QUIET }

/** The one verb a "Your day" row offers. Each row has exactly one. */
enum class HomeVerb(val label: String) {
    PREPARE("Prepare"),
    DONE("Done"),
    NUDGE("Nudge"),
    REVIEW("Review"),
    TRY_AGAIN("Try again"),
    OPEN("Open")
}

/** The ranking and verb rules for "Your day", as plain functions so they are unit-tested on the JVM. */
object WorkHomeAttention {
    /** Most rows "Your day" shows. */
    const val CAP = 4

    /** The verb for each kind: the one thing the row lets the person do. */
    fun verbFor(kind: HomeAttentionKind): HomeVerb = when (kind) {
        HomeAttentionKind.FAILED -> HomeVerb.TRY_AGAIN
        HomeAttentionKind.YOU_OWE -> HomeVerb.DONE
        HomeAttentionKind.REVIEW, HomeAttentionKind.FOLLOW_UP, HomeAttentionKind.DECISION -> HomeVerb.REVIEW
        HomeAttentionKind.THEY_OWE -> HomeVerb.NUDGE
        HomeAttentionKind.INBOX -> HomeVerb.OPEN
        HomeAttentionKind.QUIET -> HomeVerb.PREPARE
    }

    /**
     * Most urgent first, one row per [keyOf] key (the first occurrence wins), at most [cap]. The sort is
     * stable, so rows of the same kind keep the order the caller gave them.
     */
    fun <T> rank(items: List<T>, kindOf: (T) -> HomeAttentionKind, keyOf: (T) -> String, cap: Int = CAP): List<T> {
        val seen = HashSet<String>()
        return items
            .filter { seen.add(keyOf(it)) }
            .sortedBy { kindOf(it).ordinal }
            .take(cap.coerceAtLeast(0))
    }
}
