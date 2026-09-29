package com.example.core.work

/*
 * The four nouns people see (docs/PLAN_PROFESSIONAL.md §5.2): My tasks, Waiting on, Decisions and
 * Open questions. They are views over one [WorkItem] list, not four kinds of object.
 */

enum class ItemKind { TASK, DECISION, QUESTION }

enum class ItemStatus { OPEN, DONE, DROPPED, ANSWERED }

/** Where an item came from. Anything but [USER] cites the transcript it was drawn from. */
enum class ItemSource { AI, USER, MARK }

/** How a follow-up leaves MeetingMind (PLAN_PROFESSIONAL.md §6.5). */
enum class Channel(val label: String) { WHATSAPP("WhatsApp"), EMAIL("Email"), SMS("Message"), SHARE("More apps") }

/** Which of the four views an item belongs to. */
enum class WorkView(val label: String) {
    MY_TASKS("My tasks"),
    WAITING_ON("Waiting on"),
    DECISIONS("Decisions"),
    OPEN_QUESTIONS("Open questions")
}

const val SUBTYPE_FOLLOW_UP = "FOLLOW_UP"

data class WorkItem(
    val id: String,
    val meetingId: String?,
    val noteId: String?,
    val kind: ItemKind,
    val subtype: String? = null,
    val text: String,
    val status: ItemStatus = ItemStatus.OPEN,
    val ownerSpeakerId: String? = null,
    val ownerPersonId: String? = null,
    /** The owner's name as it is now: a renamed speaker or person shows their new name. */
    val ownerName: String? = null,
    /** True when the owner is the app's own user. */
    val ownerIsSelf: Boolean = false,
    val dueAt: Long? = null,
    val dueText: String? = null,
    val answer: String? = null,
    val answeredAt: Long? = null,
    val projectId: String? = null,
    val source: ItemSource = ItemSource.USER,
    val confidence: Float? = null,
    val reviewed: Boolean = true,
    val sourceSegmentIds: List<String> = emptyList(),
    val sourceStartMs: Long? = null,
    val supersededById: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val completedAt: Long? = null
) {
    val isOpen: Boolean get() = status == ItemStatus.OPEN

    /** The view this item is listed under. An unassigned task counts as the person's own. */
    val view: WorkView get() = when (kind) {
        ItemKind.DECISION -> WorkView.DECISIONS
        ItemKind.QUESTION -> WorkView.OPEN_QUESTIONS
        ItemKind.TASK -> if (ownerIsSelf || (ownerSpeakerId == null && ownerPersonId == null && ownerName == null)) WorkView.MY_TASKS else WorkView.WAITING_ON
    }

    fun isOverdue(now: Long): Boolean = isOpen && dueAt != null && dueAt < DueDates.startOfDay(now)
}
