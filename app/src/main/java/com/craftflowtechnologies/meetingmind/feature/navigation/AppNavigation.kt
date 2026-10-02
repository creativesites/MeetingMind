package com.craftflowtechnologies.meetingmind.feature.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    /** Navigate here to record; [RECORDING_PATTERN] is the registered route. */
    const val RECORDING = "recording"
    const val RECORDING_PATTERN = "recording?noteId={noteId}&type={type}&title={title}&speakers={speakers}"
    const val NOTES = "notes"
    const val NOTEBOOK = "notebook/{notebookId}"
    const val NOTES_ARCHIVE = "notes_archive"
    const val NOTES_TRASH = "notes_trash"
    const val DATA_BACKUP = "data_backup"
    const val NOTE = "note/{noteId}?media={media}"
    const val IMPORT = "import"
    const val PROCESSING = "processing/{meetingId}/{audioPath}/{durationMs}"
    const val MEETING_DETAIL = "meeting_detail/{meetingId}?startAtMs={startAtMs}"
    const val SEARCH = "search"
    const val MODELS = "models"
    const val SETTINGS = "settings"
    const val INTEGRATIONS = "integrations"
    const val RECIPES = "recipes"
    const val FAITH = "faith"
    const val FAITH_JOURNEY = "faith_journey"
    const val FAITH_SCRIPTURE = "faith_scripture"
    const val BIBLE = "bible?ref={ref}&search={search}"
    const val DEVOTIONAL = "devotional?play={play}&note={note}"
    fun devotionalRoute(play: String? = null) = "devotional" + (play?.let { "?play=$it" } ?: "")
    /** A saved devotional, opened as it was — never rewritten. */
    fun pastDevotionalRoute(noteId: String) = "devotional?note=$noteId"
    const val DEVOTIONAL_ARCHIVE = "devotional_archive"
    /** Study beside a note: a sermon's timeline and transcript, or a Bible passage. */
    const val STUDY = "study/{noteId}?meeting={meeting}&ref={ref}"
    fun studyRoute(noteId: String, meetingId: String? = null, passageId: String? = null) =
        "study/$noteId" + listOfNotNull(meetingId?.let { "meeting=$it" }, passageId?.let { "ref=$it" }).joinToString("&", prefix = "?").takeIf { meetingId != null || passageId != null }.orEmpty()
    const val STORIES = "stories?start={start}"
    const val SHARE = "share"
    const val PLANS = "reading_plans"
    const val PRAYER_LIST = "prayer_list"
    const val TASKS = "tasks"
    const val APPEARANCE = "appearance"
    const val PRAY = "pray?mode={mode}"
    const val SETUP = "setup"
    // Work (docs/PLAN_PROFESSIONAL.md §6–7).
    const val WORK = "work"
    const val WORK_ALL = "work_all?tab={tab}"
    const val WORK_PERSON = "work_person/{personId}"
    const val PROJECT = "project/{projectId}"
    const val WRAP_UP = "wrapup/{meetingId}?compose={compose}"
    const val WORK_SETTINGS = "work_settings"
    /** The Work Inbox: things shared into the app, waiting to be filed. */
    const val WORK_INBOX = "work_inbox"
    /** Imports a file already in the app (an inbox audio item) as a recording. */
    const val IMPORT_FILE = "import_file/{path}"
    fun importFileRoute(path: String) = "import_file/" + java.net.URLEncoder.encode(path, "UTF-8")
    /** One context page for a person, an organisation or a project (D5.2). */
    const val CONTEXT = "context/{type}/{id}"
    /** An Intelligence Brief: [kind] is a BriefKind name, and the id is the meeting, organisation, project or person ("-" for the weekly one). */
    const val WEEKLY_REVIEW = "work/review"
    const val BRIEF = "brief/{kind}/{id}"
    fun brief(kind: com.craftflowtechnologies.meetingmind.core.work.BriefKind, id: String = "-") = "brief/${kind.name}/${android.net.Uri.encode(id)}"
    fun context(type: com.craftflowtechnologies.meetingmind.core.work.ContextType, id: String) = "context/${type.name}/$id"
    fun workAllRoute(tab: String) = "work_all?tab=$tab"
    fun workPersonRoute(personId: String) = "work_person/$personId"
    fun projectRoute(projectId: String) = "project/$projectId"
    /** [compose] opens the follow-up composer straight away (Follow-ups to send). */
    fun wrapUpRoute(meetingId: String, compose: Boolean = false) = "wrapup/$meetingId" + if (compose) "?compose=true" else ""
    fun prayRoute(mode: String? = null) = "pray" + (mode?.let { "?mode=$it" } ?: "")

    fun storiesRoute(start: String? = null) = "stories" + (start?.let { "?start=$it" } ?: "")

    // Learning (docs/PLAN_LEARNING.md §1.2)
    const val LEARN = "learn"
    const val LEARNING_SESSION = "learning_session/{sessionId}"
    const val LEARNING_DIAGNOSTIC = "learning_diagnostic/{sessionId}"
    const val LEARNING_PRACTICE = "learning_practice?sessionId={sessionId}&activityId={activityId}"
    fun learningSessionRoute(sessionId: String) = "learning_session/$sessionId"
    fun learningDiagnosticRoute(sessionId: String) = "learning_diagnostic/$sessionId"
    fun learningPracticeRoute(sessionId: String? = null, activityId: String? = null): String {
        val params = listOfNotNull(sessionId?.let { "sessionId=$it" }, activityId?.let { "activityId=$it" })
        return if (params.isNotEmpty()) "learning_practice?" + params.joinToString("&") else "learning_practice"
    }

    /** Sentinel used when [MEETING_DETAIL]'s optional startAtMs query arg is absent — NavType.LongType has no nullable variant. */
    const val NO_START_AT_MS = -1L


    /** Records into an existing note ("Record here"). */
    fun recordIntoNoteRoute(noteId: String) = "recording?noteId=$noteId"

    /** Opens the recorder with [type] already picked. */
    fun recordTypeRoute(type: com.craftflowtechnologies.meetingmind.core.model.RecordingType) = "recording?type=${type.name}"

    /** The recorder with [type] and [title] filled in, for an event with no note yet ("Starting now — record?"). */
    fun recordTitledRoute(type: com.craftflowtechnologies.meetingmind.core.model.RecordingType, title: String) =
        "recording?type=${type.name}&title=${android.net.Uri.encode(title)}"

    /** Records into [noteId] with its type, title and speaker count filled in (a calendar event). */
    fun recordEventRoute(noteId: String, type: com.craftflowtechnologies.meetingmind.core.model.RecordingType, title: String, speakers: Int?) =
        "recording?noteId=$noteId&type=${type.name}&title=${android.net.Uri.encode(title)}" +
            (speakers?.let { "&speakers=$it" } ?: "")

    /** The Bible reader, at [reference] (a USFM passage id such as "JHN.3.16") or where it was left. */
    fun bibleRoute(reference: String? = null, search: Boolean = false) =
        "bible?search=$search" + (reference?.let { "&ref=$it" } ?: "")

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
