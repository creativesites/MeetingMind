package com.craftflowtechnologies.meetingmind.core.share

/**
 * Canonical outbound links for MeetingMind.
 *
 * Uses the Google Play listing for the release package (`com.craftflowtechnologies.meetingmind`)
 * so any devotional, Circle post, testimony, or share card shared outside the app brings
 * recipients straight to the store listing.
 */
object AppLinks {
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.craftflowtechnologies.meetingmind"
    const val DOWNLOAD_URL = PLAY_STORE_URL

    /** True when [text] already carries a MeetingMind store/download URL. */
    fun containsDownloadLink(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.contains("play.google.com/store/apps/details?id=com.craftflowtechnologies.meetingmind", ignoreCase = true) ||
            text.contains("meetingmind.app", ignoreCase = true)
    }

    /** Appends the MeetingMind download link to a shared text body without duplicating it. */
    fun withAppLink(
        body: String,
        callToAction: String = "Download MeetingMind"
    ): String {
        val trimmed = body.trimEnd()
        if (containsDownloadLink(trimmed)) return trimmed
        if (trimmed.isBlank()) return "$callToAction: $DOWNLOAD_URL"
        return "$trimmed\n\n$callToAction:\n$DOWNLOAD_URL"
    }

    /** Formats a caption for image/audio card shares (WhatsApp, Share sheet) with the app link. */
    fun shortCaptionWithLink(caption: String?): String {
        val clean = caption?.trim()?.takeIf { it.isNotEmpty() }
        if (clean != null && containsDownloadLink(clean)) return clean
        return if (clean == null) {
            "Shared via MeetingMind · $DOWNLOAD_URL"
        } else {
            "$clean\n\nShared via MeetingMind · $DOWNLOAD_URL"
        }
    }
}
