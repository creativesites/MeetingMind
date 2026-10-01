package com.craftflowtechnologies.meetingmind.core.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [EmailScopeFilter].
 * Verifies the trust boundary: mail from unknown senders is never accepted or stored.
 */
class ScopeFilterTest {

    private val knownEmails = setOf("alice@acme.com", "bob@partner.org")
    private val knownDomains = setOf("acme.com", "clientcorp.io")

    private fun createMessage(
        id: String,
        senderEmail: String,
        recipients: List<String> = emptyList(),
        subject: String = "Test Subject",
        snippet: String = "Test snippet"
    ): EmailMessage = EmailMessage(
        id = id,
        threadId = "t-$id",
        sender = senderEmail.substringBefore("@"),
        senderEmail = senderEmail,
        recipients = recipients,
        subject = subject,
        snippet = snippet,
        timestampMs = 1000L,
        providerId = "test"
    )

    @Test
    fun messageFromKnownEmail_isInScope() {
        val msg = createMessage("1", "alice@acme.com")
        assertTrue(EmailScopeFilter.isMessageInScope(msg, knownEmails, emptySet()))
    }

    @Test
    fun messageToKnownEmail_isInScope() {
        val msg = createMessage("2", "unknown@other.com", recipients = listOf("bob@partner.org"))
        assertTrue(EmailScopeFilter.isMessageInScope(msg, knownEmails, emptySet()))
    }

    @Test
    fun messageFromKnownDomain_isInScope() {
        val msg = createMessage("3", "charlie@clientcorp.io")
        assertTrue(EmailScopeFilter.isMessageInScope(msg, emptySet(), knownDomains))
    }

    @Test
    fun messageFromUnknownSenderAndDomain_isRejected() {
        val msg = createMessage("4", "stranger@unknown-random.com", recipients = listOf("nobody@other.com"))
        assertFalse(EmailScopeFilter.isMessageInScope(msg, knownEmails, knownDomains))
    }

    @Test
    fun scopeFilter_isCaseInsensitive() {
        val msg = createMessage("5", "ALICE@ACME.COM")
        assertTrue(EmailScopeFilter.isMessageInScope(msg, setOf("alice@acme.com"), emptySet()))

        val domainMsg = createMessage("6", "someone@CLIENTCORP.IO")
        assertTrue(EmailScopeFilter.isMessageInScope(domainMsg, emptySet(), setOf("clientcorp.io")))
    }

    @Test
    fun filterInScope_discardsAllUnknownMessages() {
        val inScope1 = createMessage("1", "alice@acme.com")
        val outOfScope1 = createMessage("2", "spam@promo.com")
        val inScope2 = createMessage("3", "dan@clientcorp.io")
        val outOfScope2 = createMessage("4", "newsletter@weekly.net")

        val result = EmailScopeFilter.filterInScope(
            listOf(inScope1, outOfScope1, inScope2, outOfScope2),
            knownEmails,
            knownDomains
        )

        assertEquals(2, result.size)
        assertEquals("1", result[0].id)
        assertEquals("3", result[1].id)
    }

    @Test
    fun formattedEmailWithDisplayName_parsesAndFiltersCorrectly() {
        val msg = EmailMessage(
            id = "7",
            threadId = null,
            sender = "Alice Smith",
            senderEmail = "Alice Smith <alice@acme.com>",
            recipients = emptyList(),
            subject = "Project Update",
            snippet = "Everything on schedule",
            timestampMs = 2000L,
            providerId = "test"
        )
        assertTrue(EmailScopeFilter.isMessageInScope(msg, knownEmails, knownDomains))
    }
}
