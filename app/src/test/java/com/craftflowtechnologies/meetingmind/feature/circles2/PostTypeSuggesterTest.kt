package com.craftflowtechnologies.meetingmind.feature.circles2

import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostTypeSuggesterTest {

    private val faithAll = listOf(
        PostType.Note, PostType.Prayer, PostType.Testimony, PostType.Devotional,
        PostType.Reading, PostType.Encouragement, PostType.Achievement, PostType.Study
    )
    private val workAll = listOf(
        PostType.Note, PostType.Task, PostType.Decision, PostType.Brief,
        PostType.Standup, PostType.Blocker
    )

    private fun faith(body: String, current: PostType = PostType.Note, allowed: List<PostType> = faithAll) =
        PostTypeSuggester.suggest(body, current, allowed, isWork = false)

    private fun work(body: String, current: PostType = PostType.Note, allowed: List<PostType> = workAll) =
        PostTypeSuggester.suggest(body, current, allowed, isWork = true)

    // ---- length gate ----

    @Test
    fun bodyUnder12CharsGivesNoSuggestion() {
        // 11 characters, a clear prayer keyword, still nothing.
        assertNull(faith("pray for me!".dropLast(1)))
        assertNull(faith(""))
    }

    @Test
    fun bodyOf12CharsIsJudged() {
        // "pray for me!" is exactly 12 characters.
        assertEquals(12, "pray for me!".length)
        assertEquals(PostType.Prayer, faith("pray for me!"))
    }

    @Test
    fun leadingAndTrailingWhitespaceDoesNotCountTowardLength() {
        assertNull(faith("     pray     "))
    }

    // ---- Faith: prayer, testimony, devotional, reading, encouragement ----

    @Test
    fun prayForSuggestsPrayer() {
        assertEquals(PostType.Prayer, faith("Please pray for my mom's surgery on Tuesday"))
    }

    @Test
    fun prayerWordIsCaseInsensitive() {
        assertEquals(PostType.Prayer, faith("PRAYER for the team as we travel this week"))
    }

    @Test
    fun prayerWordNeedsWordBoundary() {
        // "prayers" is a plural of the keyword, but "prayerful" is not matched by \bprayers?\b.
        assertNull(faith("She is a prayerful woman who cooks well"))
    }

    @Test
    fun thankGodSuggestsTestimony() {
        assertEquals(PostType.Testimony, faith("Thank God, the surgery went really well today"))
    }

    @Test
    fun godAnsweredSuggestsTestimonyOnTie() {
        // "God answered" (testimony) and "prayer" (prayer) each score one; the earlier rule wins the tie.
        assertEquals(PostType.Testimony, faith("God answered our prayer last night"))
    }

    @Test
    fun praiseReportSuggestsTestimony() {
        assertEquals(PostType.Testimony, faith("Praise report: my brother finally got the job"))
    }

    @Test
    fun devotionalWordSuggestsDevotional() {
        assertEquals(PostType.Devotional, faith("Today's devotional spoke to me about patience"))
    }

    @Test
    fun verseReferenceSuggestsDevotional() {
        assertEquals(PostType.Devotional, faith("Psalm 23:1 has been on my mind all week long"))
    }

    @Test
    fun readingPlanSuggestsReading() {
        assertEquals(PostType.Reading, faith("Our reading plan covers chapter 4 this week"))
    }

    @Test
    fun encourageSuggestsEncouragement() {
        assertEquals(PostType.Encouragement, faith("Let's encourage Mia this week with a card"))
    }

    @Test
    fun youveGotThisWithStraightApostropheSuggestsEncouragement() {
        assertEquals(PostType.Encouragement, faith("You've got this, keep going friend"))
    }

    @Test
    fun youveGotThisWithCurlyApostropheSuggestsEncouragement() {
        assertEquals(PostType.Encouragement, faith("You’ve got this, keep going friend"))
    }

    @Test
    fun multiplePrayerPatternsBeatASingleTestimonyPattern() {
        // "pray for" + "please pray" = 2 prayer hits; "thank God" = 1 testimony hit.
        assertEquals(PostType.Prayer, faith("Please pray for us, thank God for the week"))
    }

    // ---- Work: decision, blocker, task, brief, standup ----

    @Test
    fun decidedSuggestsDecision() {
        assertEquals(PostType.Decision, work("We decided to ship the beta on Monday"))
    }

    @Test
    fun weAgreedSuggestsDecision() {
        assertEquals(PostType.Decision, work("We agreed the API will freeze at the end of sprint"))
    }

    @Test
    fun blockedOrWaitingSuggestsBlocker() {
        assertEquals(PostType.Blocker, work("Blocked on the vendor contract, waiting on legal"))
    }

    @Test
    fun cantProceedSuggestsBlocker() {
        assertEquals(PostType.Blocker, work("I can't proceed until the staging keys arrive"))
    }

    @Test
    fun actionColonSuggestsTask() {
        assertEquals(PostType.Task, work("Action: Jane to update the API client"))
    }

    @Test
    fun mentionWillSuggestsTask() {
        assertEquals(PostType.Task, work("@Sam will send the deck by Friday"))
    }

    @Test
    fun summaryOfTheMeetingSuggestsBrief() {
        assertEquals(PostType.Brief, work("Summary of the meeting: we covered Q3 goals"))
    }

    @Test
    fun recapSuggestsBrief() {
        assertEquals(PostType.Brief, work("Quick recap of the planning call for everyone"))
    }

    @Test
    fun yesterdayISuggestsStandup() {
        assertEquals(PostType.Standup, work("Yesterday I finished the login screen"))
    }

    // ---- Context rules ----

    @Test
    fun faithRulesDoNotApplyInWorkCircles() {
        assertNull(work("Please pray for the team, we are tired"))
    }

    @Test
    fun workRulesDoNotApplyInFaithCircles() {
        assertNull(faith("Action: Jane to update the API client"))
    }

    @Test
    fun currentTypeThatAlreadyFitsGetsNoSuggestion() {
        assertNull(faith("Please pray for my mom's surgery on Tuesday", current = PostType.Prayer))
        assertNull(work("We decided to ship the beta on Monday", current = PostType.Decision))
    }

    @Test
    fun neverSuggestsTheCurrentType() {
        val result = faith("Please pray for my mom's surgery", current = PostType.Prayer)
        assertFalse(result == PostType.Prayer)
    }

    @Test
    fun suggestsBetterTypeWhenCurrentScoresLower() {
        // Two prayer hits beat one testimony hit, so a testimony draft that reads like a request is redirected.
        assertEquals(PostType.Prayer, faith("Please pray for us, thank God for the week", current = PostType.Testimony))
    }

    @Test
    fun onlyAllowedTypesAreSuggested() {
        // The body is a prayer, but the circle does not allow prayer requests.
        assertNull(faith("Please pray for my mom's surgery", allowed = listOf(PostType.Testimony, PostType.Note)))
    }

    @Test
    fun plainChatterGivesNoSuggestion() {
        assertNull(faith("Good morning everyone, hope you are all well today"))
        assertNull(work("Good morning everyone, hope you are all well today"))
    }

    @Test
    fun suggestionNeverIsCurrentOrOutsideAllowedOrAdminOnly() {
        val bodies = listOf(
            "Please pray for my mom's surgery",
            "Thank God the surgery went well",
            "Devotional on Psalm 23:1 this morning",
            "We decided to ship, action: Jane to update",
            "Blocked on legal, waiting on the signed copy",
            "Yesterday I finished the recap of the brief"
        )
        val allowed = listOf(PostType.Prayer, PostType.Testimony, PostType.Decision, PostType.Blocker)
        for (current in PostType.entries) {
            for (body in bodies) {
                for (isWork in listOf(true, false)) {
                    val result = PostTypeSuggester.suggest(body, current, allowed, isWork = isWork, isAdmin = false)
                    if (result != null) {
                        assertTrue("never the current type", result != current)
                        assertTrue("only allowed types", result in allowed)
                        assertFalse("no admin-only type without admin", result.adminOnly)
                    }
                }
            }
        }
    }

    @Test
    fun allowedAdminOnlyTypeDoesNotChangeOtherSuggestions() {
        // No keyword maps to Announcement today, so the admin gate is covered by the property test above.
        // This checks that an admin-only type in the allowed list does not disturb a normal suggestion.
        val allowed = listOf(PostType.Announcement, PostType.Prayer)
        val asMember = PostTypeSuggester.suggest("Please pray for my mom's surgery", PostType.Note, allowed, isWork = false, isAdmin = false)
        val asAdmin = PostTypeSuggester.suggest("Please pray for my mom's surgery", PostType.Note, allowed, isWork = false, isAdmin = true)
        assertEquals(PostType.Prayer, asMember)
        assertEquals(PostType.Prayer, asAdmin)
    }

    @Test
    fun describeReadsNaturallyInTheChip() {
        assertEquals("a prayer request", PostTypeSuggester.describe(PostType.Prayer))
        assertEquals("an encouragement", PostTypeSuggester.describe(PostType.Encouragement))
        assertEquals("an action item", PostTypeSuggester.describe(PostType.Task))
        assertEquals("a status update", PostTypeSuggester.describe(PostType.Standup))
    }
}
