package com.craftflowtechnologies.meetingmind.core.applock

import kotlinx.coroutines.CancellationException

internal val PROMPT = AuthPrompt("title", "subtitle")

internal class FakeAuthenticator(
    var available: AppLockAvailability = AppLockAvailability.Available,
    var result: AuthResult = AuthResult.Success,
    private val throwCancellation: Boolean = false
) : AppLockAuthenticator {
    var prompts = 0
    val shownPrompts = mutableListOf<AuthPrompt>()

    override fun availability() = available

    override suspend fun authenticate(prompt: AuthPrompt): AuthResult {
        prompts++
        shownPrompts += prompt
        if (throwCancellation) throw CancellationException("dismissed")
        return result
    }
}
