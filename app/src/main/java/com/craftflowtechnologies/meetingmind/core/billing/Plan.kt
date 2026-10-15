package com.craftflowtechnologies.meetingmind.core.billing

/**
 * The plans on sale. [entitlementId] is the RevenueCat entitlement identifier and [packagePrefix] the
 * start of its package identifiers in the RevenueCat offering ("pro_monthly", "pro_yearly", ...); the
 * dashboard has to use these names. Prices, periods and trial terms are never defined here: they come
 * from the store through the offering, so the paywall can only show what the store will charge.
 */
enum class Plan(val entitlementId: String, val packagePrefix: String, val rank: Int, val label: String) {
    FREE("", "", 0, "Free"),
    BYOK("byok", "byok", 1, "Bring your own key"),
    PRO("pro", "pro", 2, "Pro"),
    PRO_PLUS("pro_plus", "pro_plus", 3, "Pro+"),
    CHURCH("church", "church", 3, "Church");

    companion object {
        fun fromEntitlementId(id: String): Plan? = entries.firstOrNull { it != FREE && it.entitlementId == id }

        /** "pro_plus_yearly" is PRO_PLUS, not PRO: the longest matching prefix wins. */
        fun fromPackageId(id: String): Plan? =
            entries.filter { it != FREE && (id == it.packagePrefix || id.startsWith(it.packagePrefix + "_")) }
                .maxByOrNull { it.packagePrefix.length }
    }
}

/** What the user can do because of a plan. Local recording, notes, search and export are never gated. */
enum class Capability {
    /** Cloud transcription and AI notes that MeetingMind pays for, within the plan's allowance. */
    MANAGED_CLOUD_AI
}

object PlanCapabilities {
    fun has(plan: Plan, capability: Capability): Boolean = when (capability) {
        Capability.MANAGED_CLOUD_AI -> plan == Plan.PRO || plan == Plan.PRO_PLUS || plan == Plan.CHURCH
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
