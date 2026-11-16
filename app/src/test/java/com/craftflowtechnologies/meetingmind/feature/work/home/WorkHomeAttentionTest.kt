package com.craftflowtechnologies.meetingmind.feature.work.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkHomeAttentionTest {

    private data class Row(val kind: HomeAttentionKind, val key: String)

    private fun rank(vararg rows: Row, cap: Int = WorkHomeAttention.CAP) =
        WorkHomeAttention.rank(rows.toList(), { it.kind }, { it.key }, cap)

    @Test fun failedRecordingsRankAboveWhatIOwe() {
        val ranked = rank(
            Row(HomeAttentionKind.YOU_OWE, "a"),
            Row(HomeAttentionKind.FAILED, "f")
        )
        assertEquals(listOf("f", "a"), ranked.map { it.key })
    }

    @Test fun theOrderIsFailedThenWhatIOweThenReviewThenFollowUpThenDecisionThenTheyOweThenInboxThenQuiet() {
        val shuffled = listOf(
            HomeAttentionKind.QUIET, HomeAttentionKind.INBOX, HomeAttentionKind.THEY_OWE, HomeAttentionKind.DECISION,
            HomeAttentionKind.FOLLOW_UP, HomeAttentionKind.REVIEW, HomeAttentionKind.YOU_OWE, HomeAttentionKind.FAILED
        ).mapIndexed { i, k -> Row(k, "k$i") }
        val all = WorkHomeAttention.rank(shuffled, { it.kind }, { it.key }, cap = 99)
        assertEquals(
            listOf(
                HomeAttentionKind.FAILED, HomeAttentionKind.YOU_OWE, HomeAttentionKind.REVIEW, HomeAttentionKind.FOLLOW_UP,
                HomeAttentionKind.DECISION, HomeAttentionKind.THEY_OWE, HomeAttentionKind.INBOX, HomeAttentionKind.QUIET
            ),
            all.map { it.kind }
        )
    }

    @Test fun neverShowsMoreThanTheCap() {
        val ranked = rank(
            Row(HomeAttentionKind.FAILED, "1"), Row(HomeAttentionKind.YOU_OWE, "2"), Row(HomeAttentionKind.REVIEW, "3"),
            Row(HomeAttentionKind.FOLLOW_UP, "4"), Row(HomeAttentionKind.DECISION, "5"), Row(HomeAttentionKind.QUIET, "6")
        )
        assertEquals(WorkHomeAttention.CAP, ranked.size)
        assertEquals(4, WorkHomeAttention.CAP)
        assertEquals(listOf("1", "2", "3", "4"), ranked.map { it.key })
    }

    @Test fun aRowShownTwiceIsShownOnce() {
        val ranked = rank(Row(HomeAttentionKind.YOU_OWE, "same"), Row(HomeAttentionKind.REVIEW, "same"))
        assertEquals(listOf(HomeAttentionKind.YOU_OWE), ranked.map { it.kind })
    }

    @Test fun rowsOfTheSameKindKeepTheirOrder() {
        val ranked = rank(Row(HomeAttentionKind.THEY_OWE, "first"), Row(HomeAttentionKind.THEY_OWE, "second"))
        assertEquals(listOf("first", "second"), ranked.map { it.key })
    }

    @Test fun aZeroCapShowsNothing() {
        assertEquals(emptyList<Row>(), rank(Row(HomeAttentionKind.FAILED, "f"), cap = 0))
    }

    @Test fun eachKindHasOneVerb() {
        assertEquals(HomeVerb.TRY_AGAIN, WorkHomeAttention.verbFor(HomeAttentionKind.FAILED))
        assertEquals(HomeVerb.DONE, WorkHomeAttention.verbFor(HomeAttentionKind.YOU_OWE))
        assertEquals(HomeVerb.REVIEW, WorkHomeAttention.verbFor(HomeAttentionKind.REVIEW))
        assertEquals(HomeVerb.REVIEW, WorkHomeAttention.verbFor(HomeAttentionKind.FOLLOW_UP))
        assertEquals(HomeVerb.REVIEW, WorkHomeAttention.verbFor(HomeAttentionKind.DECISION))
        assertEquals(HomeVerb.NUDGE, WorkHomeAttention.verbFor(HomeAttentionKind.THEY_OWE))
        assertEquals(HomeVerb.OPEN, WorkHomeAttention.verbFor(HomeAttentionKind.INBOX))
        assertEquals(HomeVerb.PREPARE, WorkHomeAttention.verbFor(HomeAttentionKind.QUIET))
    }
}
