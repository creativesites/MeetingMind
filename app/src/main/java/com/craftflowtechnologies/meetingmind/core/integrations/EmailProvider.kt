package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * A retrieved email message, scoped strictly to known contacts.
 */
data class EmailMessage(
    val id: String,
    val threadId: String?,
    val sender: String,
    val senderEmail: String,
    val recipients: List<String>,
    val subject: String,
    val snippet: String,
    val timestampMs: Long,
    val providerId: String
) {
    /** Formats as cited evidence for Memory/Ask. */
    fun asCitedEvidence(): String {
        return "Email from $sender <$senderEmail>: \"$subject\" — $snippet"
    }
}

/**
 * Filter that enforces MeetingMind's trust rule: only messages involving
 * known people and domains can be indexed or stored.
 */
object EmailScopeFilter {
    fun isMessageInScope(
        message: EmailMessage,
        knownEmails: Set<String>,
        knownDomains: Set<String>
    ): Boolean {
        val normalizedKnownEmails = knownEmails.map { it.trim().lowercase() }.toSet()
        val normalizedKnownDomains = knownDomains.map { it.trim().lowercase().removePrefix("@") }.toSet()

        val allEmailsInvolved = (listOf(message.senderEmail) + message.recipients)
            .map { extractEmail(it).lowercase() }
            .filter { it.isNotBlank() }

        // Check if any involved email matches known emails
        if (allEmailsInvolved.any { it in normalizedKnownEmails }) {
            return true
        }

        // Check if any domain matches known domains
        val allDomainsInvolved = allEmailsInvolved.mapNotNull { email ->
            val at = email.indexOf('@')
            if (at >= 0 && at < email.length - 1) email.substring(at + 1) else null
        }
        if (allDomainsInvolved.any { it in normalizedKnownDomains }) {
            return true
        }

        return false
    }

    fun filterInScope(
        messages: List<EmailMessage>,
        knownEmails: Set<String>,
        knownDomains: Set<String>
    ): List<EmailMessage> {
        return messages.filter { isMessageInScope(it, knownEmails, knownDomains) }
    }

    private fun extractEmail(raw: String): String {
        val trimmed = raw.trim()
        val match = EMAIL_REGEX.find(trimmed)
        return match?.value ?: trimmed
    }

    private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
}

/**
 * Provider interface for email integration (Gmail, Outlook).
 */
interface EmailProvider : IntegrationProvider {
    override val category: IntegrationCategory
        get() = IntegrationCategory.EMAIL

    /**
     * Retrieve messages involving [knownEmails] and [knownDomains] since [sinceEpochMs].
     * Unscoped messages are discarded and never returned.
     */
    suspend fun getScopedMessages(
        knownEmails: Set<String>,
        knownDomains: Set<String>,
        sinceEpochMs: Long
    ): List<EmailMessage>

    /**
     * Prepares an email draft and hands off to the user's email client via Intent.
     * Per brief: Send stays a draft handoff; MeetingMind never sends mail directly.
     */
    fun prepareDraft(
        context: Context,
        toEmail: String,
        subject: String,
        body: String
    ) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val chooser = Intent.createChooser(intent, "Compose Email").apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}
