package com.example.feature.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val RECORDING = "recording"
    const val IMPORT = "import"
    const val PROCESSING = "processing/{meetingId}/{audioPath}/{durationMs}"
    const val MEETING_DETAIL = "meeting_detail/{meetingId}"
    const val SEARCH = "search"
    const val MODELS = "models"
    const val SETTINGS = "settings"

    fun processingRoute(meetingId: String, audioPath: String, durationMs: Long): String {
        val encodedPath = java.net.URLEncoder.encode(audioPath, "UTF-8")
        return "processing/$meetingId/$encodedPath/$durationMs"
    }

    fun meetingDetailRoute(meetingId: String): String {
        return "meeting_detail/$meetingId"
    }
}
