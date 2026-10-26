package com.craftflowtechnologies.meetingmind.core.billing

/**
 * The one place the app asks whether a plan capability is available, so a dead enum value cannot
 * pretend to gate anything.
 *
 * It reads the store's last confirmed entitlement through [Entitlements.effective], so a lapsed or
 * stale value stops granting access on its own.
 *
 * 1.0 deliberately has only two gates:
 * - [Capability.BYOK_KEY]: only a paid BYOK, Pro or Church plan may use a personal Gemini key.
 * - [Capability.MANAGED_CLOUD_AI]: paid plans carry a larger managed-cloud allowance.
 *
 * Managed cloud AI is still routed for free users: the proxy meters the free allowance. Callers must
 * not use [MANAGED_CLOUD_AI] to hide the managed route, only to describe what a paid plan adds.
 */
object FeatureGate {
    fun can(capability: Capability, nowMs: Long = System.currentTimeMillis()): Boolean =
        can(Billing.gateway.entitlements.value, capability, nowMs)

    fun can(entitlements: Entitlements, capability: Capability, nowMs: Long = System.currentTimeMillis()): Boolean =
        entitlements.effective(nowMs).has(capability)
}
