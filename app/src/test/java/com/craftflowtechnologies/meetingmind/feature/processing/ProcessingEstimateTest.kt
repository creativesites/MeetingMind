package com.craftflowtechnologies.meetingmind.feature.processing

import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.feature.processing.ProcessingEstimate.TimeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The processing wait's "About 4 min left": cautious before progress, honest after, never jittery. */
class ProcessingEstimateTest {
    private val tenMinutes = 600_000L

    @Test fun priorIsCautiousOnThePhoneAndQuickerInTheCloud() {
        assertEquals(510_000L, ProcessingEstimate.priorTotalMs(tenMinutes, ProcessingProfile.OFFLINE))
        assertEquals(117_000L, ProcessingEstimate.priorTotalMs(tenMinutes, ProcessingProfile.INTERNET))
        assertNull(ProcessingEstimate.priorTotalMs(0L, ProcessingProfile.INTERNET))
    }

    @Test fun beforeRealProgressThePriorCountsDown() {
        assertEquals(505_000L, ProcessingEstimate.remainingMs(tenMinutes, ProcessingProfile.OFFLINE, elapsedMs = 5_000, percent = 0))
    }

    @Test fun noAudioLengthAndNoProgressSaysNothing() {
        assertNull(ProcessingEstimate.remainingMs(0L, ProcessingProfile.OFFLINE, elapsedMs = 5_000, percent = 3))
    }

    @Test fun runningPastTheCautiousPriorSaysNothingRatherThanZero() {
        // One minute of audio in the cloud: the prior is 52 s, and it is past that with no real progress.
        assertNull(ProcessingEstimate.remainingMs(60_000L, ProcessingProfile.INTERNET, elapsedMs = 60_000, percent = 5))
    }

    @Test fun realProgressAloneExtrapolatesTheObservedRate() {
        assertEquals(240_000L, ProcessingEstimate.remainingMs(0L, ProcessingProfile.OFFLINE, elapsedMs = 60_000, percent = 20))
    }

    @Test fun fromSixtyPercentWhatWasObservedFullyWins() {
        assertEquals(40_000L, ProcessingEstimate.remainingMs(tenMinutes, ProcessingProfile.OFFLINE, elapsedMs = 60_000, percent = 60))
    }

    @Test fun nearTheEndIsAlmostDone() {
        assertEquals(0L, ProcessingEstimate.remainingMs(tenMinutes, ProcessingProfile.OFFLINE, elapsedMs = 60_000, percent = 96))
        assertEquals(TimeLeft.AlmostDone, ProcessingEstimate.bucket(120_000L, percent = 96))
    }

    @Test fun bucketsAreCalmSteps() {
        assertEquals(TimeLeft.Unknown, ProcessingEstimate.bucket(null, 10))
        assertEquals(TimeLeft.UnderAMinute, ProcessingEstimate.bucket(30_000L, 10))
        assertEquals(TimeLeft.Minutes(2), ProcessingEstimate.bucket(61_000L, 10))
        assertEquals(TimeLeft.Minutes(10), ProcessingEstimate.bucket(600_000L, 10))
        assertEquals(TimeLeft.Minutes(15), ProcessingEstimate.bucket(660_000L, 10))
    }

    @Test fun shownTimeCountsDownAndOnlyJumpsUpWhenClearlyWorse() {
        // Shown 5:00 a minute ago, so 4:00 now. A slightly worse estimate keeps the countdown.
        assertEquals(240_000L, ProcessingEstimate.smooth(300_000L, 60_000L, 250_000L))
        // A better one is taken.
        assertEquals(230_000L, ProcessingEstimate.smooth(300_000L, 60_000L, 230_000L))
        // A much worse one is shown honestly rather than hidden.
        assertEquals(500_000L, ProcessingEstimate.smooth(300_000L, 60_000L, 500_000L))
        assertNull(ProcessingEstimate.smooth(300_000L, 60_000L, null))
    }

    @Test fun smootherGivesTheSameAnswerForARepeatedFrame() {
        val smoother = ProcessingEstimate.Smoother()
        smoother.next(0L, 300_000L)
        val first = smoother.next(60_000L, 250_000L)
        val again = smoother.next(60_000L, 250_000L)
        assertEquals(240_000L, first)
        assertEquals(first, again)
    }
}
