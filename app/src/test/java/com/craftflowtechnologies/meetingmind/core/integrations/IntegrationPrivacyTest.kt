package com.craftflowtechnologies.meetingmind.core.integrations

import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for integration privacy gates.
 * Verifies that Clinical and Legal profiles protect client confidentiality by default.
 */
class IntegrationPrivacyTest {

    @Test
    fun clinicalProfile_disablesEmailByDefault() {
        var optIn = false
        val googleProvider = GoogleEmailProvider(
            workProfileProvider = { WorkProfile.CLINICAL },
            explicitOptInProvider = { optIn },
            enabledChecker = { true }
        )

        assertFalse(googleProvider.isProfilePermitted())
        assertFalse(googleProvider.isEnabled)
        assertNotNull(googleProvider.privacyNotice)
        assertTrue(googleProvider.privacyNotice!!.contains("Clinical", ignoreCase = true))
    }

    @Test
    fun legalProfile_disablesEmailByDefault() {
        var optIn = false
        val msProvider = MicrosoftEmailProvider(
            workProfileProvider = { WorkProfile.LEGAL },
            explicitOptInProvider = { optIn },
            enabledChecker = { true }
        )

        assertFalse(msProvider.isProfilePermitted())
        assertFalse(msProvider.isEnabled)
        assertNotNull(msProvider.privacyNotice)
        assertTrue(msProvider.privacyNotice!!.contains("Legal", ignoreCase = true))
    }

    @Test
    fun clinicalProfile_enablesEmailWhenExplicitlyOptedIn() {
        var optIn = true
        val googleProvider = GoogleEmailProvider(
            workProfileProvider = { WorkProfile.CLINICAL },
            explicitOptInProvider = { optIn },
            enabledChecker = { true }
        )

        assertTrue(googleProvider.isProfilePermitted())
    }

    @Test
    fun clientProfile_permitsEmailWithoutSpecialOptIn() {
        val googleProvider = GoogleEmailProvider(
            workProfileProvider = { WorkProfile.CLIENT },
            explicitOptInProvider = { false },
            enabledChecker = { true }
        )

        assertTrue(googleProvider.isProfilePermitted())
        assertNull(googleProvider.privacyNotice)
    }

    @Test
    fun statusReflectsPolicyConstraint() {
        val googleProvider = GoogleEmailProvider(
            workProfileProvider = { WorkProfile.CLINICAL },
            explicitOptInProvider = { false },
            enabledChecker = { true }
        )

        if (googleProvider.isEmailFeatureFlagEnabled) {
            assertEquals(ProviderStatus.DISABLED_BY_POLICY, googleProvider.status)
        } else {
            // When feature flag is off, status is AVAILABLE (inactive)
            assertEquals(ProviderStatus.AVAILABLE, googleProvider.status)
        }
    }
}
