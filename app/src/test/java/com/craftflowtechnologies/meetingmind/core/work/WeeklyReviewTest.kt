package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/** The weekly review: what the week held, and the plan for the next one made in one pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyReviewTest {
    private lateinit var f: PulseFixture
    private lateinit var reviews: WeeklyReviews
    private lateinit var overdue: String
    private lateinit var thisWeek: String
    private lateinit var undated: String
    private lateinit var owed: String
    private lateinit var question: String
    private val monday get() = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 12) }.timeInMillis

    @Before fun setup() {
        f = PulseFixture()
        reviews = WeeklyReviews(f.db) { f.now }
        overdue = f.mine("Send the proposal", due = f.today - 2 * f.day).id
        thisWeek = f.mine("Book the venue", due = f.today + 2 * f.day).id
        undated = f.mine("Think about pricing").id
        owed = f.theirs("Ana sends the logo", due = f.today + f.day).id
        question = f.item(ItemKind.QUESTION, ItemStatus.OPEN, "Who signs off the copy?").id
        f.mine("Confirm the date", status = ItemStatus.COMPLETED)
    }
    @After fun tearDown() { f.db.close() }

    private fun build() = runBlocking { reviews.build(f.now) }
    private fun item(id: String) = runBlocking { f.items.get(id)!! }
    private fun plan(vararg d: PlanDecision) = runBlocking { reviews.applyPlan(d.toList(), f.now) }

    @Test fun theFactsAreTheWeeksRecord() {
        val r = build()
        assertEquals(listOf("Send the proposal"), r.slipped.map { it.text })
        assertEquals(listOf("Ana sends the logo"), r.waitingOnThem.map { it.text })
        assertEquals(setOf("Book the venue", "Think about pricing"), r.waitingOnMe.map { it.text }.toSet())
        assertEquals(listOf("Confirm the date"), r.finished.map { it.text })
        assertEquals(listOf("Who signs off the copy?"), r.questions.map { it.text })
        val w = r.week.work!!
        assertEquals(1, w.meetings)              // the Acme review five days ago
        assertEquals(5, w.commitmentsMade)       // all created yesterday
        assertEquals(1, w.commitmentsDone)
        assertEquals(1, w.slipped)
        assertEquals(1, w.openQuestions)
    }

    @Test fun theWeekCoversTheLastSevenDaysAndNothingOlder() {
        f.clockValue = f.now - 30 * f.day // it was completed a month ago
        val old = f.mine("Ancient promise", created = f.now - 30 * f.day, status = ItemStatus.COMPLETED)
        f.clockValue = f.now
        assertTrue(build().finished.none { it.id == old.id })
        assertEquals(1, build().week.work!!.meetings)
    }

    @Test fun projectsNeedingAttentionAreCountedFromWhatIsOpen() {
        val p = build().projects.single()
        assertEquals("Acme launch", p.name); assertEquals(1, p.overdue)
    }

    @Test fun carryKeepsAnItemAndMovesOnlyAPassedDateToMonday() {
        val n = plan(PlanDecision(overdue, PlanChoice.CARRY), PlanDecision(thisWeek, PlanChoice.CARRY), PlanDecision(undated, PlanChoice.CARRY))
        assertEquals(1, n)
        assertEquals(monday, item(overdue).dueAt)
        assertEquals(f.today + 2 * f.day, item(thisWeek).dueAt)  // not late: untouched
        assertNull(item(undated).dueAt)                          // stays undated
        assertEquals(ItemStatus.OPEN.name, item(overdue).status)
    }

    @Test fun rescheduleSetsTheDayGiven() {
        val thursday = monday + 3 * f.day
        assertEquals(1, plan(PlanDecision(thisWeek, PlanChoice.RESCHEDULE, thursday)))
        assertEquals(thursday, item(thisWeek).dueAt)
        assertEquals(0, plan(PlanDecision(undated, PlanChoice.RESCHEDULE, null))) // no day, no change
    }

    @Test fun dropEndsAnItemAccordingToItsKind() {
        plan(PlanDecision(overdue, PlanChoice.DROP), PlanDecision(question, PlanChoice.DROP), PlanDecision(owed, PlanChoice.DROP))
        assertEquals(ItemStatus.CANCELLED.name, item(overdue).status)
        assertEquals(ItemStatus.DROPPED.name, item(question).status)
        assertEquals(ItemStatus.CANCELLED.name, item(owed).status)
    }

    @Test fun oneOnePassCarriesMovesAndDropsAndTheReviewShowsWhatIsLeft() {
        plan(
            PlanDecision(overdue, PlanChoice.CARRY), PlanDecision(thisWeek, PlanChoice.RESCHEDULE, monday + f.day),
            PlanDecision(undated, PlanChoice.DROP), PlanDecision(owed, PlanChoice.CARRY), PlanDecision(question, PlanChoice.CARRY)
        )
        val r = build()
        assertTrue(r.slipped.isEmpty())                         // the late one is now due Monday
        assertEquals(monday, item(overdue).dueAt)
        assertEquals(monday + f.day, item(thisWeek).dueAt)
        assertTrue(r.planItems.none { it.id == undated })
        // Every change is in the item's own history.
        val types = runBlocking { f.items.events(overdue) }.map { it.type }
        assertTrue(types.contains(ItemEventType.DUE_CHANGED.name))
    }

    @Test fun anUnknownItemIsSkipped() {
        assertEquals(0, plan(PlanDecision("nope", PlanChoice.DROP)))
    }

    @Test fun nextMondayIsTheComingOne() {
        assertEquals(monday, reviews.nextMonday(f.now))
        val monday10am = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 12, 10, 0) }.timeInMillis
        assertEquals(monday + 7 * f.day, reviews.nextMonday(monday10am)) // on a Monday, the next one
        assertNotNull(build().week.work)
    }
}
