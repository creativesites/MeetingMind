package com.craftflowtechnologies.meetingmind.core.integrations

import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile

/**
 * Google / Gmail integration provider.
 * Uses public client ID from docs/INTEGRATION_CREDENTIALS.md.
 */
class GoogleEmailProvider(
    private val workProfileProvider: () -> WorkProfile = { WorkProfile.CLIENT },
    private val explicitOptInProvider: () -> Boolean = { false },
    private val enabledChecker: () -> Boolean = { false },
    private val messageFetcher: suspend (Set<String>, Set<String>, Long) -> List<EmailMessage> = { _, _, _ -> emptyList() }
) : EmailProvider {

    override val id: String = ID

    override val name: String = "Google Workspace / Gmail"

    override val capabilities: Set<Capability> = setOf(
        Capability.EMAIL_INDEX_READ,
        Capability.EMAIL_DRAFT_SEND,
        Capability.EMAIL_ATTACH_MINUTES
    )

    override val accountScope: String = "Messages with known contacts and domains only"

    /** Client ID from BuildConfig (chosen per build type: dev vs release) */
    val clientId: String = BuildConfig.GOOGLE_CLIENT_ID

    val isEmailFeatureFlagEnabled: Boolean = BuildConfig.FEATURE_INTEGRATIONS_EMAIL

    override val isEnabled: Boolean
        get() = isEmailFeatureFlagEnabled && enabledChecker() && isProfilePermitted()

    /**
     * Gmail read access is disabled by default for Clinical and Legal profiles
     * unless the person explicitly enables it with confirmation.
     */
    fun isProfilePermitted(): Boolean {
        val profile = workProfileProvider()
        val isConfidentialProfile = (profile == WorkProfile.CLINICAL || profile == WorkProfile.LEGAL)
        if (isConfidentialProfile && !explicitOptInProvider()) {
            return false
        }
        return true
    }

    override val privacyNotice: String?
        get() {
            val profile = workProfileProvider()
            return if (profile == WorkProfile.CLINICAL || profile == WorkProfile.LEGAL) {
                "Email read access is restricted in ${profile.name.lowercase().replaceFirstChar { it.uppercase() }} profile to protect confidentiality."
            } else {
                null
            }
        }

    override val status: ProviderStatus
        get() {
            if (!isEmailFeatureFlagEnabled) return ProviderStatus.AVAILABLE
            if (!isProfilePermitted()) return ProviderStatus.DISABLED_BY_POLICY
            return if (isEnabled) ProviderStatus.CONNECTED else ProviderStatus.AVAILABLE
        }

    override suspend fun getScopedMessages(
        knownEmails: Set<String>,
        knownDomains: Set<String>,
        sinceEpochMs: Long
    ): List<EmailMessage> {
        if (!isEnabled) return emptyList()
        val rawMessages = messageFetcher(knownEmails, knownDomains, sinceEpochMs)
        // Enforce strict scope filtering: unknown senders never return
        return EmailScopeFilter.filterInScope(rawMessages, knownEmails, knownDomains)
    }

    companion object {
        const val ID = "email.google"
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    }
}
