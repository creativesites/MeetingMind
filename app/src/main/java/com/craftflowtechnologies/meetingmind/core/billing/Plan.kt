package com.craftflowtechnologies.meetingmind.core.billing

/**
 * The plans on sale. There is one paid plan, unlocked by the RevenueCat entitlement [entitlementId]
 * ("meetingmind_pro"). Prices, periods and trial terms are never defined here: they come from the store
 * through the offering, so the paywall can only show what the store will charge.
 */
enum class Plan(val entitlementId: String, val rank: Int, val label: String) {
    FREE("", 0, "Free"),
    BYOK("meetingmind_byok", 1, "MeetingMind BYOK"),
    PRO("meetingmind_pro", 2, "MeetingMind Pro"),
    CHURCH("meetingmind_church", 3, "MeetingMind Church");

    companion object {
        fun fromEntitlementId(id: String): Plan? = entries.firstOrNull { it != FREE && it.entitlementId == id }
    }
}

/**
 * The products in the RevenueCat offering, by their package identifiers.
 */
enum class OfferKind(val packageId: String, val period: String, val defaultPlan: Plan = Plan.PRO) {
    MONTHLY("monthly", "month", Plan.PRO),
    YEARLY("yearly", "year", Plan.PRO),
    LIFETIME("lifetime", "one-time payment", Plan.PRO),
    BYOK_MONTHLY("byok_monthly", "month", Plan.BYOK),
    BYOK_YEARLY("byok_yearly", "year", Plan.BYOK),
    CHURCH_MONTHLY("church_monthly", "month", Plan.CHURCH),
    CHURCH_YEARLY("church_yearly", "year", Plan.CHURCH);

    companion object {
        fun fromPackageId(id: String): OfferKind? = entries.firstOrNull { it.packageId == id }
    }
}

/** What the user can do because of a plan. Local recording, notes, search and export are never gated. */
enum class Capability {
    /** Cloud transcription and AI notes that MeetingMind pays for, within the plan's allowance. */
    MANAGED_CLOUD_AI,
    /** Using a personal Gemini API key in Settings with all Pro features unlocked. */
    BYOK_KEY,
    /** Full Pro feature set (Ask across meetings, Speaker ID, Read-to-Me TTS, unlimited Circles). */
    PRO_FEATURES
}

object PlanCapabilities {
    fun has(plan: Plan, capability: Capability): Boolean = when (capability) {
        Capability.MANAGED_CLOUD_AI -> plan == Plan.PRO || plan == Plan.CHURCH
        Capability.BYOK_KEY -> plan == Plan.BYOK || plan == Plan.PRO || plan == Plan.CHURCH
        Capability.PRO_FEATURES -> plan != Plan.FREE
    }
}

enum class EntitlementState {
    /** No paid entitlement. */
    NONE,
    ACTIVE,
    /** An introductory period; access is full until it ends. */
    TRIAL,
    /** A payment failed but the store still gives access while it retries. */
    GRACE,
    EXPIRED
}

/**
 * What the store says the user owns. Subscription status is kept apart from onboarding progress, and a
 * purchase callback is never the only source of truth: [effective] drops a stale or lapsed value.
 */
data class Entitlements(
    val plan: Plan = Plan.FREE,
    val state: EntitlementState = EntitlementState.NONE,
    val expiresAtMs: Long? = null,
    val willRenew: Boolean = false,
    /** When the store last confirmed this. Zero means never. */
    val verifiedAtMs: Long = 0
) {
    val isPaid: Boolean get() = plan != Plan.FREE && state in PAID_STATES
    fun has(capability: Capability): Boolean = isPaid && PlanCapabilities.has(plan, capability)

    /**
     * The value to act on at [nowMs]. A paid value that has lapsed (its end has passed) or has gone
     * too long without being confirmed (the phone was offline for longer than [MAX_UNVERIFIED_MS])
     * is treated as free. The person's content is never touched, only paid access.
     */
    fun effective(nowMs: Long): Entitlements {
        if (!isPaid) return this
        val lapsed = expiresAtMs != null && nowMs > expiresAtMs
        val stale = nowMs - verifiedAtMs > MAX_UNVERIFIED_MS
        return if (lapsed || stale) FREE_VALUE.copy(state = EntitlementState.EXPIRED, verifiedAtMs = verifiedAtMs) else this
    }

    companion object {
        val FREE_VALUE = Entitlements()
        private val PAID_STATES = setOf(EntitlementState.ACTIVE, EntitlementState.TRIAL, EntitlementState.GRACE)
        /** How long a confirmed entitlement is trusted without hearing from the store again. */
        const val MAX_UNVERIFIED_MS = 7L * 24 * 60 * 60 * 1000
    }
}
