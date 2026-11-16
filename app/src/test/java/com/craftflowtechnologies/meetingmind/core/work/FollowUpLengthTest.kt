package com.craftflowtechnologies.meetingmind.core.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The composer's length filter and its WhatsApp formatting. Robolectric for the settings' org.json, as FollowUpTest does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowUpLengthTest {

    private val sarah = WorkPerson("p1", PersonKind.PERSON, "Sarah Chen", emails = listOf("sarah@acme.com"))
    private val settings = WorkSettings(signOff = "Thanks", footer = true)

    private fun input(length: FollowUpLength) = FollowUpWriter.Input(
        meetingTitle = "Acme review", meetingAt = 0, recipients = listOf(sarah),
        decisions = listOf(FollowUpLine("Use OAuth2")),
        myTasks = listOf(FollowUpLine("Send API docs", dueText = "Friday")),
        theirTasks = listOf(FollowUpLine("Share production credentials", owner = "Sarah Chen")),
        questions = listOf(FollowUpLine("When is the launch")),
        senderName = "Ana Banda", settings = settings, tone = Tone.FRIENDLY, length = length
    )

    @Test fun `short leaves out the open questions and keeps decisions and tasks`() {
        val short = FollowUpWriter.compose(input(FollowUpLength.SHORT), Channel.EMAIL, now = 0).body
        assertFalse(short.contains("Still open"))
        assertFalse(short.contains("When is the launch"))
        assertTrue(short.contains("- Use OAuth2"))
        assertTrue(short.contains("- Send API docs (Friday)"))
    }

    @Test fun `detailed keeps every section`() {
        val detailed = FollowUpWriter.compose(input(FollowUpLength.DETAILED), Channel.EMAIL, now = 0).body
        assertTrue(detailed.contains("Still open"))
        assertTrue(detailed.contains("- When is the launch?"))
    }

    @Test fun `short only removes lines, it never rewords them`() {
        val short = FollowUpWriter.compose(input(FollowUpLength.SHORT), Channel.EMAIL, now = 0).body
        val detailed = FollowUpWriter.compose(input(FollowUpLength.DETAILED), Channel.EMAIL, now = 0).body.lines().toSet()
        assertTrue(short.lines().all { it in detailed })
    }

    @Test fun `whatsapp bolds the section titles and uses bullets, with the same items`() {
        val wa = FollowUpWriter.compose(input(FollowUpLength.DETAILED), Channel.WHATSAPP, now = 0).body
        assertTrue(wa.contains("*Decisions*"))
        assertTrue(wa.contains("• Use OAuth2"))
        assertTrue(wa.contains("• Send API docs (Friday)"))
        assertFalse(wa.contains("- Use OAuth2"))
        assertTrue(wa.contains("_Notes by MeetingMind_"))
    }

    @Test fun `email has no WhatsApp asterisks and carries the same items`() {
        val email = FollowUpWriter.compose(input(FollowUpLength.DETAILED), Channel.EMAIL, now = 0).body
        assertFalse(email.contains("*"))
        assertTrue(email.contains("Use OAuth2"))
        assertTrue(email.contains("Send API docs (Friday)"))
        assertTrue(email.contains("Share production credentials"))
    }
}
