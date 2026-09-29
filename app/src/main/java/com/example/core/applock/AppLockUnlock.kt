package com.example.core.applock

/**
 * Runs one unlock attempt against [authenticator] and reports it to the controller. Returns null
 * when there was nothing to unlock (App Lock off, or already unlocked). If the caller is cancelled
 * (rotation, screen closed) the controller falls back to Locked so the next screen can ask again.
 */
suspend fun AppLockController.unlockWith(authenticator: AppLockAuthenticator, prompt: AuthPrompt): AuthResult? {
    if (!beginUnlock()) return null
    var result: AuthResult = AuthResult.Cancelled
    try {
        result = authenticator.authenticate(prompt)
    } finally {
        if (result == AuthResult.Success) onUnlockSucceeded() else onUnlockFailed()
    }
    return result
}
