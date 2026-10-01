package com.craftflowtechnologies.meetingmind.core.integrations

import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile

/**
 * Microsoft 365 / Outlook integration provider.
 * Uses public client ID and redirect URI from docs/INTEGRATION_CREDENTIALS.md.
 */
class MicrosoftEmailProvider(
    private val workProfileProvider: () -> WorkProfile = { WorkProfile.CLIENT },
    private val explicitOptInProvider: () -> Boolean = { false },
    private val enabledChecker: () -> Boolean = { false },
    private val messageFetcher: suspend (Set<String>, Set<String>, Long) -> List<EmailMessage> = { _, _, _ -> emptyList() }
) : EmailProvider {

    override val id: String = ID

    override val name: String = "Microsoft 365 / Outlook"

    override val capabilities: Set<Capability> = setOf(
        Capability.EMAIL_INDEX_READ,
        Capability.EMAIL_DRAFT_SEND,
        Capability.EMAIL_ATTACH_MINUTES
    )

    override val accountScope: String = "Messages with known contacts and domains only"

    /** Client ID from BuildConfig (public client, no secret) */
    val clientId: String = BuildConfig.MICROSOFT_CLIENT_ID

    /** Redirect URI from BuildConfig (chosen per build type) */
    val redirectUri: String = BuildConfig.MICROSOFT_REDIRECT_URI

    val isEmailFeatureFlagEnabled: Boolean = BuildConfig.FEATURE_INTEGRATIONS_EMAIL

    override val isEnabled: Boolean
        get() = isEmailFeatureFlagEnabled && enabledChecker() && isProfilePermitted()

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
        return EmailScopeFilter.filterInScope(rawMessages, knownEmails, knownDomains)
    }

    companion object {
        const val ID = "email.microsoft"
        val REQUIRED_SCOPES = listOf("Mail.Read", "offline_access", "User.Read")
    }
}
