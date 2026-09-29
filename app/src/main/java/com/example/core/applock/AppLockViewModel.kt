package com.example.core.applock

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped holder of the [AppLockController]. Being a ViewModel is what lets the lock state
 * survive rotation and activity re-creation without re-authenticating; being tied to the process
 * is what makes a fresh launch start locked.
 */
class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferencesManager(application)
    val controller = AppLockController(now = SystemClock::elapsedRealtime)

    val state: StateFlow<AppLockState> = controller.state
    val ready: StateFlow<Boolean> = controller.ready

    init {
        // Follows the saved preference for the whole session, including changes from Settings.
        viewModelScope.launch {
            prefs.preferencesFlow.collect { controller.onPreferenceLoaded(it.appLockEnabled) }
        }
    }

    /** Settings logic bound to [authenticator] (which belongs to the current activity). */
    fun settings(authenticator: AppLockAuthenticator): AppLockSettings {
        val app = getApplication<Application>()
        return AppLockSettings(
            authenticator = authenticator,
            store = { prefs.setAppLockEnabled(it) },
            controller = controller,
            enablePrompt = AuthPrompt(app.getString(R.string.app_lock_prompt_enable_title), app.getString(R.string.app_lock_prompt_enable_subtitle)),
            disablePrompt = AuthPrompt(app.getString(R.string.app_lock_prompt_disable_title), app.getString(R.string.app_lock_prompt_disable_subtitle))
        )
    }

    fun unlockPrompt(): AuthPrompt {
        val app = getApplication<Application>()
        return AuthPrompt(app.getString(R.string.app_lock_prompt_unlock_title), app.getString(R.string.app_lock_prompt_unlock_subtitle))
    }

    /** Recovery when the phone can no longer authenticate its owner: switch the lock off. */
    fun turnOffWithoutAuthentication(availability: AppLockAvailability) {
        if (!availability.hasNoOwnerCredential) return
        viewModelScope.launch { prefs.setAppLockEnabled(false) }
    }
}
