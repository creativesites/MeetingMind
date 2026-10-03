package com.craftflowtechnologies.meetingmind.feature.fellowship

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType

/** What the person can send to their group. Each one starts from a note they already wrote. */
enum class FellowshipKind(
    val route: String,
    val title: String,
    /** The hub card's sentence. */
    val line: String,
    val pickerTitle: String,
    val pickerLine: String,
    val icon: ImageVector,
    val tint: Color,
    val emptyTitle: String,
    val emptyBody: String,
    val emptyAction: String
) {
    STUDY(
        "study", "Group study guide",
        "Turn a sermon or Bible study into questions your group can talk through.",
        "Choose a sermon", "Pick the note to build a discussion guide from.",
        Icons.Filled.Church, FellowshipTints.study,
        "No sermon notes yet",
        "Record a Sunday sermon or start a Bible study, then turn it into a discussion guide for your group.",
        "Record a sermon"
    ),
    PRAYER(
        "prayer", "Prayer card",
        "Share a request as a clean card for WhatsApp, Instagram or a message.",
        "Choose a prayer request", "Only requests you are still praying for are listed.",
        Icons.Filled.Favorite, FellowshipTints.prayer,
        "No open prayer requests",
        "Write down what you're asking God for. When you're ready, you can share it with the people praying with you.",
        "New prayer request"
    ),
    TESTIMONY(
        "testimony", "Testimony card",
        "Tell what God has done, as a card your friends can see.",
        "Choose a testimony", "Testimonies and answered prayers are listed.",
        Icons.Filled.Star, FellowshipTints.testimony,
        "No testimonies yet",
        "When God answers a prayer, or you want to tell what He's done, write it here first — then share it.",
        "Write a testimony"
    );

    /** Whether [note] belongs in this picker. */
    fun accepts(note: Note): Boolean = when (this) {
        STUDY -> note.workflow == RecordingType.SERMON || note.workflow == RecordingType.BIBLE_STUDY
        PRAYER -> note.workflow == RecordingType.PRAYER_REQUEST && note.status == NoteStatus.OPEN
        TESTIMONY -> note.workflow == RecordingType.TESTIMONY ||
            (note.workflow == RecordingType.PRAYER_REQUEST && note.status == NoteStatus.ANSWERED)
    }

    /** The note type to create when the list is empty and the person taps the empty-state action. */
    val createType: RecordingType
        get() = when (this) {
            STUDY -> RecordingType.SERMON
            PRAYER -> RecordingType.PRAYER_REQUEST
            TESTIMONY -> RecordingType.TESTIMONY
        }

    /** The eyebrow labels a card can carry, the first being the default. */
    val eyebrows: List<String>
        get() = when (this) {
            STUDY -> emptyList()
            PRAYER -> listOf("Prayer request", "Please pray", "Prayer update")
            TESTIMONY -> listOf("Testimony", "God answered", "Praise report")
        }

    /**
     * What a generated card background is about. Deliberately general: the person's own words are
     * never sent to an image model, because a prayer can name someone.
     */
    val backgroundTheme: String
        get() = when (this) {
            STUDY -> "an open Bible in warm morning light"
            PRAYER -> "quiet, hopeful light and calm — a card for a prayer request"
            TESTIMONY -> "light breaking through clouds — thankfulness and joy"
        }

    companion object {
        fun fromRoute(route: String?): FellowshipKind? = entries.firstOrNull { it.route == route }

        /** The notes this kind can use, newest first. Archived or trashed notes never appear. */
        fun candidates(kind: FellowshipKind, notes: List<Note>): List<Note> =
            notes.filter { it.deletedAt == null && it.archivedAt == null && kind.accepts(it) }
                .sortedByDescending { it.eventDate ?: it.createdAt }
    }
}
