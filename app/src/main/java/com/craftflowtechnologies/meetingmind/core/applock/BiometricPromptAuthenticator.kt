package com.craftflowtechnologies.meetingmind.core.applock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [AppLockAuthenticator] on top of AndroidX BiometricPrompt: fingerprint, face or iris as the
 * phone exposes them, with the screen lock (PIN/pattern/password) as the fallback. Nothing is
 * stored; the result is only "the platform says it's the owner".
 *
 * Bound to one [activity]; create it per screen, never keep it in a ViewModel.
 */
class BiometricPromptAuthenticator(
    private val activity: FragmentActivity,
    private val sdk: Int = Build.VERSION.SDK_INT
) : AppLockAuthenticator {
    private val authenticators get() = AppLockAuthenticators.forSdk(sdk)

    override fun availability(): AppLockAvailability =
        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> AppLockAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> AppLockAvailability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> AppLockAvailability.HardwareUnavailable
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AppLockAvailability.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> AppLockAvailability.SecurityUpdateRequired
            else -> AppLockAvailability.Unsupported
        }

    override suspend fun authenticate(prompt: AuthPrompt): AuthResult {
        val available = availability()
        if (available != AppLockAvailability.Available) return AuthResult.Unavailable(available)
        return suspendCancellableCoroutine { cont ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(AuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(mapError(errorCode, errString.toString()))
                }
                // onAuthenticationFailed (a wrong finger) leaves the prompt open; nothing to do.
            }
            val biometricPrompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(prompt.title)
                .setSubtitle(prompt.subtitle)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(false)
                .build()
            cont.invokeOnCancellation { runCatching { biometricPrompt.cancelAuthentication() } }
            runCatching { biometricPrompt.authenticate(info) }
                .onFailure { if (cont.isActive) cont.resume(AuthResult.Error(-1, it.message.orEmpty())) }
        }
    }

    internal companion object {
        fun mapError(code: Int, message: String): AuthResult = when (code) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED -> AuthResult.Cancelled
            BiometricPrompt.ERROR_LOCKOUT -> AuthResult.LockedOut(permanent = false)
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthResult.LockedOut(permanent = true)
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> AuthResult.Unavailable(AppLockAvailability.NoneEnrolled)
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> AuthResult.Unavailable(AppLockAvailability.NoHardware)
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> AuthResult.Unavailable(AppLockAvailability.HardwareUnavailable)
            else -> AuthResult.Error(code, message)
        }
    }
}
