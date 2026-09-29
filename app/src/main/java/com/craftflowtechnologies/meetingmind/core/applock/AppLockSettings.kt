package com.craftflowtechnologies.meetingmind.core.applock

/** Where the on/off preference is saved (backed by DataStore in the app). */
fun interface AppLockStore {
    suspend fun setEnabled(enabled: Boolean)
}

sealed interface AppLockChange {
    /** The preference now has the requested value. */
    data object Changed : AppLockChange
    /** Turning on was refused because the phone cannot authenticate its owner. */
    data class Unavailable(val reason: AppLockAvailability) : AppLockChange
    /** The owner did not authenticate; nothing changed. */
    data class NotVerified(val result: AuthResult) : AppLockChange
}

/**
 * Turning App Lock on or off. Both directions need the owner to authenticate first, so someone
 * holding an unlocked phone can neither switch the protection off nor claim it is on when it
 * cannot work. The preference is written only after a successful authentication.
 */
class AppLockSettings(
    private val authenticator: AppLockAuthenticator,
    private val store: AppLockStore,
    private val controller: AppLockController,
    private val enablePrompt: AuthPrompt,
    private val disablePrompt: AuthPrompt
) {
    suspend fun setEnabled(enable: Boolean): AppLockChange {
        val availability = authenticator.availability()
        if (enable && availability != AppLockAvailability.Available) return AppLockChange.Unavailable(availability)
        // Turning off with nothing to authenticate against (screen lock removed since) must not
        // trap the owner behind a lock they can no longer pass.
        if (!enable && availability.hasNoOwnerCredential) {
            store.setEnabled(false)
            return AppLockChange.Changed
        }
        val result = authenticator.authenticate(if (enable) enablePrompt else disablePrompt)
        if (result != AuthResult.Success) return AppLockChange.NotVerified(result)
        if (enable) {
            // Unlock this session first so the preference change never flashes the lock screen.
            controller.onEnabledBySession()
            try {
                store.setEnabled(true)
            } catch (e: Exception) {
                controller.onPreferenceLoaded(false)
                throw e
            }
        } else {
            store.setEnabled(false)
        }
        return AppLockChange.Changed
    }
}
