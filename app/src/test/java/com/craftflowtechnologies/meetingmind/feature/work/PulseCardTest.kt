package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.work.AttentionAction
import com.craftflowtechnologies.meetingmind.core.work.AttentionKind
import com.craftflowtechnologies.meetingmind.core.work.AttentionRow
import com.craftflowtechnologies.meetingmind.core.work.ChangeGroup
import com.craftflowtechnologies.meetingmind.core.work.ChangeLine
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.PulseDayLine
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.DateFormat

/** The Pulse renders from data alone: no model, no network. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class PulseCardTest {
    @get:Rule val compose = createComposeRule()

    private val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    private fun event(title: String) = PulseEvent("1@1", title, 1_000, 2_000)

    private fun show(ui: PulseUi, actions: PulseActions = PulseActions()) = compose.setContent { MeetMindTheme { PulseCard(ui, fmt, actions) } }

    @Test fun showsTheFourRowsSinceYesterdayAndToday() {
        val rows = listOf(
            AttentionRow(AttentionKind.YOU_OWE, "Send the contract", "Overdue since Oct 5", actions = listOf(AttentionAction.PLAY, AttentionAction.OPEN)),
            AttentionRow(AttentionKind.DECISION_NEEDED, "Pricing model", "Decision needed · Acme launch", actions = listOf(AttentionAction.PREPARE, AttentionAction.OPEN)),
            AttentionRow(AttentionKind.THEY_OWE, "API keys", "Ana · open 6 days", personId = "ana", actions = listOf(AttentionAction.NUDGE, AttentionAction.OPEN)),
            AttentionRow(AttentionKind.QUIET, "Acme", "No meeting for 40 days · 2 open", entityType = ContextType.ORG, entityId = "org1", actions = listOf(AttentionAction.PREPARE))
        )
        show(PulseUi(
            attention = rows, sinceLabel = "Since yesterday", coldStart = false, summary = "2 meetings today",
            changes = listOf(ChangeGroup("Acme launch", ContextType.PROJECT, "nb", listOf(ChangeLine("Launch moved Oct 14 → Oct 21", "i1", 1), ChangeLine("Ana committed to API docs by Fri", "i2", 1)))),
            today = listOf(PulseDayLine(event("Acme sync"), youOwe = 2, theyOwe = 1, questions = 0, decisionsNeeded = 1, withLabel = "Acme launch", firstMeeting = false))
        ))
        compose.onNodeWithTag("pulse_card").assertIsDisplayed()
        compose.onAllNodesWithTag("pulse_row").assertCountEquals(4)
        compose.onNodeWithText("Send the contract").assertIsDisplayed()
        compose.onNodeWithText("SINCE YESTERDAY").assertIsDisplayed()
        compose.onNodeWithText("Launch moved Oct 14 → Oct 21").assertIsDisplayed()
        compose.onNodeWithText("Acme launch — you owe 2 · they owe 1 · 1 to decide").assertIsDisplayed()
        compose.onNodeWithTag("pulse_ask").assertIsDisplayed()
    }

    @Test fun aFirstMeetingReadsAsOneNotAnEmptyState() {
        show(PulseUi(coldStart = true, today = listOf(PulseDayLine(event("NetOne intro"), 0, 0, 0, 0, null, firstMeeting = true))))
        compose.onNodeWithText("First meeting with NetOne intro").assertIsDisplayed()
    }

    @Test fun withNothingYetItOffersToBegin() {
        var recorded = 0; var templates = 0
        show(PulseUi(coldStart = true), PulseActions(onRecord = { recorded++ }, onTemplates = { templates++ }))
        compose.onNodeWithText("Start with one conversation").assertIsDisplayed()
        compose.onNodeWithText("● Record a conversation").performClick()
        compose.onNodeWithText("Use a template").performClick()
        assertEquals(1 to 1, recorded to templates)
    }

    @Test fun rowActionsAndAskAreWired() {
        val log = mutableListOf<String>()
        val row = AttentionRow(AttentionKind.THEY_OWE, "API keys", "Ana · open 6 days", personId = "ana", meetingId = "m1", actions = listOf(AttentionAction.PLAY, AttentionAction.NUDGE, AttentionAction.OPEN))
        show(PulseUi(attention = listOf(row), coldStart = false, changes = listOf(ChangeGroup("Other", null, null, listOf(ChangeLine("Decided: Use OAuth2", "i1", 1))))),
            PulseActions(onPlay = { log += "play" }, onNudge = { log += "nudge" }, onOpenRow = { log += "open" }, onPlayChange = { log += "change:${it.text}" }, onAsk = { log += "ask" }))
        compose.onNodeWithText("▶ Evidence").performClick()
        compose.onNodeWithText("Nudge").performClick()
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Decided: Use OAuth2").performClick()
        compose.onNodeWithText("Ask about my work").performClick()
        assertEquals(listOf("play", "nudge", "open", "change:Decided: Use OAuth2", "ask"), log)
    }

    @Test fun moreThanThreeChangesSummariseTheRestInWords() {
        val lines = (1..5).map { ChangeLine("Change $it", "i$it", it.toLong()) }
        show(PulseUi(coldStart = false, changes = listOf(ChangeGroup("Other", null, null, lines))))
        compose.onNodeWithText("Change 3").assertIsDisplayed()
        compose.onNodeWithText("and 2 more changes").assertIsDisplayed()
    }
}
