package com.craftflowtechnologies.meetingmind.core.integrations

/**
 * Capabilities enabled by an integration provider.
 *
 * Per docs/PLAN_PROFESSIONAL.md §6.6 and D8:
 * The question isn't "which apps", but where work enters and leaves.
 * The Integration Centre lists what each connection enables, not just a "Connected" badge.
 */
enum class Capability(val label: String, val description: String) {
    // Calendar capabilities
    CALENDAR_EVENTS("Meeting detection", "Detect upcoming and past meetings from calendar events"),
    CALENDAR_ATTENDEES("Attendees", "Match meeting attendees against known people in MeetingMind"),
    CALENDAR_PREP("Meeting prep", "Surface past decisions, commitments, and open questions before meetings"),
    CALENDAR_WORKFLOW_RULES("Workflow rules", "Auto-select meeting workflows and templates based on calendar details"),

    // Email capabilities
    EMAIL_INDEX_READ("Relevant correspondence", "Index messages from known contacts and domains into Memory"),
    EMAIL_DRAFT_SEND("Reply drafts", "Prepare draft replies to hand off to your email app"),
    EMAIL_ATTACH_MINUTES("Attach minutes", "Attach formatted meeting minutes and follow-ups to email threads"),

    // Storage capabilities
    STORAGE_IMPORT_DOCS("Import documents", "Import PDFs, audio, and reference documents into projects"),
    STORAGE_ATTACHMENTS("Project attachments", "Attach files and meeting artifacts to notebooks and context"),
    STORAGE_RETRIEVAL("Document retrieval", "Query project documents directly from Memory and Ask"),

    // Output channels
    OUTPUT_SHARE_SHEET("Share to apps", "Share summaries, action items, and transcripts via Android share sheet"),
    OUTPUT_DIRECT_MESSAGE("Direct message handoff", "Hand off formatted messages directly to WhatsApp, Slack, etc."),

    // External meeting recording platforms
    MEETING_RECORDING_IMPORT("Platform recordings", "Import recorded audio directly from Zoom, Meet, or Teams"),

    // External task providers
    TASK_MIRROR_OUT("Mirror tasks", "Mirror commitments and tasks to external task managers")
}
