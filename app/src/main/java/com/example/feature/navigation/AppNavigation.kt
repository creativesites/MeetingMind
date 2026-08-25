package com.example.feature.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val RECORDING = "recording"
    const val IMPORT = "import"
    const val PROCESSING = "processing/{meetingId}/{audioPath}/{durationMs}"
    const val MEETING_DETAIL = "meeting_detail/{meetingId}?startAtMs={startAtMs}"
    const val SEARCH = "search"
    const val MODELS = "models"
    const val SETTINGS = "settings"

    /** Sentinel used when [MEETING_DETAIL]'s optional startAtMs query arg is absent — NavType.LongType has no nullable variant. */
    const val NO_START_AT_MS = -1L


    fun processingRoute(meetingId: String, audioPath: String, durationMs: Long): String {
        val encodedPath = java.net.URLEncoder.encode(audioPath, "UTF-8")
        return "processing/$meetingId/$encodedPath/$durationMs"
    }

    /** [startAtMs] deep-links straight to a specific transcript moment — e.g. from a search
     * result — instead of just opening the recording at its Overview tab. */
    fun meetingDetailRoute(meetingId: String, startAtMs: Long? = null): String {
        return if (startAtMs != null) "meeting_detail/$meetingId?startAtMs=$startAtMs" else "meeting_detail/$meetingId"
    }
}
