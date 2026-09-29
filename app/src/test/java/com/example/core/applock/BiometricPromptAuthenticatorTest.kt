package com.example.core.applock

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPromptAuthenticatorTest {
    private val strong = 0x000F
    private val weak = 0x00FF
    private val credential = 0x8000

    @Test fun `authenticator constants match androidx`() {
        assertEquals(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG, AppLockAuthenticators.BIOMETRIC_STRONG)
        assertEquals(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK, AppLockAuthenticators.BIOMETRIC_WEAK)
        assertEquals(androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL, AppLockAuthenticators.DEVICE_CREDENTIAL)
    }

    @Test fun `strongest combination Android supports per API level`() {
        assertEquals(strong or credential, AppLockAuthenticators.forSdk(24))
        assertEquals(strong or credential, AppLockAuthenticators.forSdk(27))
        assertEquals("28-29 reject strong+credential", weak or credential, AppLockAuthenticators.forSdk(28))
        assertEquals(weak or credential, AppLockAuthenticators.forSdk(29))
        assertEquals(strong or credential, AppLockAuthenticators.forSdk(30))
        assertEquals(strong or credential, AppLockAuthenticators.forSdk(36))
    }

    @Test fun `prompt errors map to a recovery the UI understands`() {
        val m = BiometricPromptAuthenticator.Companion::mapError
        assertEquals(AuthResult.Cancelled, m(BiometricPrompt.ERROR_USER_CANCELED, ""))
        assertEquals(AuthResult.Cancelled, m(BiometricPrompt.ERROR_NEGATIVE_BUTTON, ""))
        assertEquals(AuthResult.Cancelled, m(BiometricPrompt.ERROR_CANCELED, ""))
        assertEquals(AuthResult.LockedOut(false), m(BiometricPrompt.ERROR_LOCKOUT, ""))
        assertEquals(AuthResult.LockedOut(true), m(BiometricPrompt.ERROR_LOCKOUT_PERMANENT, ""))
        assertEquals(AuthResult.Unavailable(AppLockAvailability.NoneEnrolled), m(BiometricPrompt.ERROR_NO_BIOMETRICS, ""))
        assertEquals(AuthResult.Unavailable(AppLockAvailability.NoneEnrolled), m(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, ""))
        assertEquals(AuthResult.Unavailable(AppLockAvailability.NoHardware), m(BiometricPrompt.ERROR_HW_NOT_PRESENT, ""))
        assertEquals(AuthResult.Unavailable(AppLockAvailability.HardwareUnavailable), m(BiometricPrompt.ERROR_HW_UNAVAILABLE, ""))
        assertTrue(m(BiometricPrompt.ERROR_TIMEOUT, "slow") is AuthResult.Error)
    }

    @Test fun `only a phone with nothing to verify the owner may drop the lock without authenticating`() {
        assertTrue(AppLockAvailability.NoneEnrolled.hasNoOwnerCredential)
        assertTrue(AppLockAvailability.NoHardware.hasNoOwnerCredential)
        assertEquals(false, AppLockAvailability.HardwareUnavailable.hasNoOwnerCredential)
        assertEquals(false, AppLockAvailability.Available.hasNoOwnerCredential)
    }
}
