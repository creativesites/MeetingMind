package com.craftflowtechnologies.meetingmind.core.devotional

/** The one-time tip on the devotional page that says an AI devotional can be written any time. */
object DevotionalTip {
    /**
     * Shown on a devotional that came from a plan, on the first visits, until the person dismisses it (or uses it).
     * Once dismissed it never comes back. It isn't shown on a past devotional opened from the archive.
     */
    fun visible(dismissed: Boolean, past: Boolean, shown: Devotional?): Boolean =
        !dismissed && !past && shown?.origin == DevotionalOrigin.CLASSIC

    /** What the person is reading, for the quiet strip on the page: the plan's name, "Written with AI", or null when no strip fits. */
    fun readingName(d: Devotional?): String? = when (d?.origin) {
        DevotionalOrigin.CLASSIC -> d.engine?.substringAfter(", ")?.removeSuffix(" (evening)")?.takeIf { it.isNotBlank() } ?: "A reading plan"
        DevotionalOrigin.CLOUD_AI, DevotionalOrigin.DEVICE_AI -> "Written with AI"
        else -> null
    }
}
