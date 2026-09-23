package com.example.feature.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    /** Navigate here to record; [RECORDING_PATTERN] is the registered route. */
    const val RECORDING = "recording"
    const val RECORDING_PATTERN = "recording?noteId={noteId}"
    const val NOTES = "notes"
    const val NOTEBOOK = "notebook/{notebookId}"
    const val NOTES_ARCHIVE = "notes_archive"
    const val NOTE = "note/{noteId}?media={media}"
    const val IMPORT = "import"
    const val PROCESSING = "processing/{meetingId}/{audioPath}/{durationMs}"
    const val MEETING_DETAIL = "meeting_detail/{meetingId}?startAtMs={startAtMs}"
    const val SEARCH = "search"
    const val MODELS = "models"
    const val SETTINGS = "settings"

    /** Sentinel used when [MEETING_DETAIL]'s optional startAtMs query arg is absent — NavType.LongType has no nullable variant. */
    const val NO_START_AT_MS = -1L


    /** Records into an existing note ("Record here"). */
    fun recordIntoNoteRoute(noteId: String) = "recording?noteId=$noteId"

    fun notebookRoute(notebookId: String) = "notebook/$notebookId"

    /** [openMediaPicker] opens the photo picker as the note appears (Create → Photo or video). */
    fun noteRoute(noteId: String, openMediaPicker: Boolean = false) = "note/$noteId?media=$openMediaPicker"

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
