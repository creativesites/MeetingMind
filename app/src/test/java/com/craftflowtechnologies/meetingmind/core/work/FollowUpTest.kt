package com.craftflowtechnologies.meetingmind.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric for org.json, which settings are stored as. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowUpTest {

    private val sarah = WorkPerson("p1", PersonKind.PERSON, "Sarah Chen", emails = listOf("sarah@acme.com"))
    private val tino = WorkPerson("p2", PersonKind.PERSON, "Tino Moyo", phones = listOf("+263771234567"))
    private val settings = WorkSettings(signOff = "Thanks", footer = true)

    private fun item(text: String, owner: String? = null, due: String? = null) = FollowUpLine(text, owner, null, due)

    private fun input(recipients: List<WorkPerson>, s: WorkSettings = settings, tone: Tone = Tone.FRIENDLY) = FollowUpWriter.Input(
        meetingTitle = "Acme review", meetingAt = 0, recipients = recipients,
        decisions = listOf(item("Use OAuth2")),
        myTasks = listOf(item("Send API docs", due = "Friday")),
        theirTasks = listOf(item("Share production credentials", owner = "Sarah Chen")),
        questions = listOf(item("When is the launch")),
        senderName = "Ana Banda", settings = s, tone = tone
    )

    @Test fun `email reads as an email and says only what was confirmed`() {
        val m = FollowUpWriter.compose(input(listOf(sarah)), Channel.EMAIL, now = 0)
        assertEquals("Follow-up: Acme review", m.subject)
        assertTrue(m.body.startsWith("Hi Sarah,"))
        assertTrue(m.body.contains("- Use OAuth2"))
        assertTrue(m.body.contains("- Send API docs (Friday)"))
        assertTrue(m.body.contains("What you'll do"))
        assertTrue(m.body.contains("- When is the launch?"))
        assertTrue(m.body.contains("Thanks,\nAna Banda"))
        assertTrue(m.body.endsWith("Notes by MeetingMind"))
    }

    @Test fun `whatsapp uses its own formatting`() {
        val m = FollowUpWriter.compose(input(listOf(tino)), Channel.WHATSAPP, now = 0)
        assertTrue(m.body.contains("*Decisions*"))
        assertTrue(m.body.contains("• Use OAuth2"))
        assertTrue(m.body.contains("Thanks, Ana"))
        assertTrue(m.body.contains("_Notes by MeetingMind_"))
    }

    @Test fun `clinical and legal work never carries the footer`() {
        val clinical = WorkSettings.forProfile(WorkProfile.CLINICAL)
        val m = FollowUpWriter.compose(input(listOf(sarah), clinical, Tone.FORMAL), Channel.EMAIL, now = 0)
        assertFalse(m.body.contains("MeetingMind"))
        assertTrue(m.body.startsWith("Dear Sarah,"))
    }

    @Test fun `the channel adapts to people, then reach, then region`() {
        val adaptive = WorkSettings()
        assertEquals(Channel.EMAIL, ChannelChooser.pick(listOf(sarah), adaptive, whatsappFirstRegion = true))
        assertEquals(Channel.WHATSAPP, ChannelChooser.pick(listOf(tino), adaptive, whatsappFirstRegion = false))
        assertEquals(Channel.WHATSAPP, ChannelChooser.pick(emptyList(), adaptive, whatsappFirstRegion = true))
        assertEquals(Channel.EMAIL, ChannelChooser.pick(emptyList(), adaptive, whatsappFirstRegion = false))
        // What worked last time wins.
        assertEquals(Channel.WHATSAPP, ChannelChooser.pick(listOf(sarah.copy(preferredChannel = Channel.WHATSAPP)), adaptive, whatsappFirstRegion = false))
        // A fixed choice wins over everything.
        assertEquals(Channel.SMS, ChannelChooser.pick(listOf(sarah), adaptive.copy(defaultChannel = Channel.SMS)))
    }

    @Test fun `a nudge names the task and the person`() {
        val m = FollowUpWriter.nudge(item("Share production credentials", owner = "Sarah Chen", due = "Friday"), "Ana Banda", Tone.FRIENDLY, Channel.WHATSAPP)
        assertTrue(m.body.startsWith("Hi Sarah,"))
        assertTrue(m.body.contains("share production credentials (Friday)"))
    }

    @Test fun `settings survive a round trip and new sections appear`() {
        val s = WorkSettings.forProfile(WorkProfile.LEGAL).copy(termsOverride = Terms("Client", "Matter", "Client"), hiddenSections = setOf(WorkSection.PEOPLE), tabSlot = TabSlot.WORK)
        val back = WorkSettings.fromJson(s.toJson())
        assertEquals(s, back)
        assertTrue(back.keepOnDevice)
        assertEquals("Matters", back.terms.projects)
        assertEquals("Companies", Terms("Company", "Project", "Contact").organisations)
        assertEquals(WorkSettings(), WorkSettings.fromJson("not json"))
    }
}
