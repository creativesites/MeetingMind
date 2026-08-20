package com.example.core.export

/** What's being exported. Not every content type supports every format — see [ExportFormat.supportedFor]. */
enum class ExportContentType(val displayName: String) {
    TRANSCRIPT("Transcript"),
    SUMMARY("Summary"),
    ACTION_ITEMS("Action Items")
}

enum class ExportFormat(val displayName: String, val extension: String, val mimeType: String) {
    MARKDOWN("Markdown", "md", "text/markdown"),
    CSV("CSV", "csv", "text/csv"),
    PDF("PDF", "pdf", "application/pdf"),
    DOCX("Word Document", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /** Not every type is forced into every format — CSV is tabular and only makes sense for
     * row-shaped data (transcript lines, action items), never a prose summary. */
    fun supportedFor(contentType: ExportContentType): Boolean = when (this) {
        CSV -> contentType != ExportContentType.SUMMARY
        else -> true
    }
}
