package com.craftflowtechnologies.meetingmind.core.applock

/**
 * The one seam between MeetingMind and Android's owner check. Everything App Lock decides is
 * written against this interface so it can be tested with a fake; the real implementation
 * ([BiometricPromptAuthenticator]) only ever asks the platform BiometricPrompt "is this the owner?"
 * and never sees, stores or compares any biometric data.
 */
interface AppLockAuthenticator {
    /** Whether the phone can authenticate its owner right now. Cheap; safe to call any time. */
    fun availability(): AppLockAvailability

    /** Shows the system prompt and suspends until it resolves. Cancelling the caller dismisses it. */
    suspend fun authenticate(prompt: AuthPrompt): AuthResult
}

/** Text for the system prompt. Supplied by the caller so it comes from string resources. */
data class AuthPrompt(val title: String, val subtitle: String)

enum class AppLockAvailability {
    Available,
    /** No biometric hardware and no screen lock to fall back on. */
    NoHardware,
    /** Hardware exists but is busy or off right now — worth retrying. */
    HardwareUnavailable,
    /** Nothing enrolled: no fingerprint/face and no PIN, pattern or password. */
    NoneEnrolled,
    SecurityUpdateRequired,
    Unsupported;

    /** The phone has no way to authenticate its owner at all, so App Lock cannot protect anything. */
    val hasNoOwnerCredential: Boolean get() = this == NoHardware || this == NoneEnrolled
}

sealed interface AuthResult {
    data object Success : AuthResult
    /** The person dismissed the prompt, or the system did (activity left, screen off). */
    data object Cancelled : AuthResult
    /** Too many wrong attempts; [permanent] until the phone's own credential is used. */
    data class LockedOut(val permanent: Boolean) : AuthResult
    data class Unavailable(val reason: AppLockAvailability) : AuthResult
    data class Error(val code: Int, val message: String) : AuthResult
}

/**
 * Strongest combination Android supports on this API level: strong biometrics with the screen
 * lock as fallback. `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is rejected by the platform on API 28–29,
 * so those two versions accept weak biometrics (still the phone's own enrolled biometrics).
 */
internal object AppLockAuthenticators {
    const val BIOMETRIC_STRONG = 0x000F
    const val BIOMETRIC_WEAK = 0x00FF
    const val DEVICE_CREDENTIAL = 0x8000

    fun forSdk(sdk: Int): Int =
        if (sdk in 28..29) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_STRONG or DEVICE_CREDENTIAL
}
