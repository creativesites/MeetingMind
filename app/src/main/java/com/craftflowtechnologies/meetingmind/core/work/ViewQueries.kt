package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Columns in a project Kanban board.
 */
enum class KanbanColumnType(val label: String) {
    TO_DO("To Do"),
    IN_PROGRESS("In Progress"),
    DONE("Done")
}

data class KanbanCard(
    val id: String,
    val title: String,
    val isTask: Boolean,
    val itemKind: ItemKind?,
    val status: String,
    val counterpartyName: String?,
    val dueAt: Long?,
    val severity: String?,
    val sourceNoteTitle: String? = null
) {
    fun isOverdue(now: Long): Boolean = dueAt != null && status != ItemStatus.COMPLETED.name && status != ItemStatus.CLOSED.name && status != "DONE" && dueAt < DueDates.startOfDay(now)
}

data class KanbanBoardData(
    val toDo: List<KanbanCard>,
    val inProgress: List<KanbanCard>,
    val done: List<KanbanCard>
)

enum class TimelineItemType {
    MEETING,
    COMMITMENT,
    TASK,
    DECISION,
    RISK
}

data class TimelineItem(
    val id: String,
    val title: String,
    val type: TimelineItemType,
    val at: Long,
    val status: String?,
    val counterparty: String?,
    val isOverdue: Boolean = false,
    val noteId: String? = null
)

data class TimelineSection(
    val title: String,
    val items: List<TimelineItem>
)

data class RiskItem(
    val id: String,
    val title: String,
    val severity: String,
    val status: String,
    val meetingTitle: String?,
    val counterparty: String?,
    val createdAt: Long
)

class ViewQueries(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val itemDao = database.itemDao()
    private val workDao = database.workDao()

    /**
     * Builds Kanban board data for a project.
     * Partitions project items and tasks into To Do, In Progress, and Done columns.
     */
    suspend fun projectKanban(projectId: String): KanbanBoardData = withContext(Dispatchers.IO) {
        val now = clock()
        val allProjectItems = itemDao.allLive().filter {
            it.projectId == projectId || itemDao.linksFor(it.id).any { link -> link.targetType == LinkType.PROJECT && link.targetId == projectId }
        }
        val projectNotes = workDao.observeNotesIn(projectId).first()
        val noteIds = projectNotes.map { it.id }.toSet()
        val projectTasks = workDao.observeTasksIn(projectId).first()
        val peopleMap = workDao.allPeople().associate { it.id to it.name }
        val noteTitleMap = projectNotes.associate { it.id to it.title }

        val cards = mutableListOf<KanbanCard>()

        // Add project items (commitments, questions, risks, etc.)
        for (item in allProjectItems) {
            val counterparty = listOfNotNull(item.counterpartyPersonId, item.ownerPersonId)
                .firstOrNull()?.let { peopleMap[it] }
            cards.add(
                KanbanCard(
                    id = item.id,
                    title = item.text,
                    isTask = false,
                    itemKind = item.itemKind,
                    status = item.status,
                    counterpartyName = counterparty,
                    dueAt = item.dueAt,
                    severity = item.severity
                )
            )
        }

        // Add tasks that are not already mirrored as commitments
        val linkedTaskIds = allProjectItems.mapNotNull { it.taskId }.toSet()
        for (task in projectTasks) {
            if (task.id !in linkedTaskIds) {
                val counterparty = task.personId?.let { peopleMap[it] }
                val statusStr = if (task.doneAt != null) "DONE" else "OPEN"
                cards.add(
                    KanbanCard(
                        id = task.id,
                        title = task.title,
                        isTask = true,
                        itemKind = null,
                        status = statusStr,
                        counterpartyName = counterparty,
                        dueAt = task.dueAt,
                        severity = null,
                        sourceNoteTitle = task.noteId?.let { noteTitleMap[it] }
                    )
                )
            }
        }

        val toDo = mutableListOf<KanbanCard>()
        val inProgress = mutableListOf<KanbanCard>()
        val done = mutableListOf<KanbanCard>()

        for (card in cards) {
            when {
                card.status == ItemStatus.COMPLETED.name || card.status == ItemStatus.CLOSED.name || card.status == "DONE" || card.status == ItemStatus.ANSWERED.name -> {
                    done.add(card)
                }
                card.status == ItemStatus.ACTIVE.name || (card.isOverdue(now) && card.status != ItemStatus.CANCELLED.name) -> {
                    inProgress.add(card)
                }
                else -> {
                    toDo.add(card)
                }
            }
        }

        KanbanBoardData(
            toDo = toDo.sortedBy { it.dueAt ?: Long.MAX_VALUE },
            inProgress = inProgress.sortedBy { it.dueAt ?: Long.MAX_VALUE },
            done = done.sortedByDescending { it.dueAt ?: 0L }
        )
    }

    /**
     * Builds a chronological timeline for a project.
     */
    suspend fun projectTimeline(projectId: String): List<TimelineSection> = withContext(Dispatchers.IO) {
        val now = clock()
        val startOfToday = DueDates.startOfDay(now)
        val endOfWeek = startOfToday + 7 * 86_400_000L

        val projectNotes = workDao.observeNotesIn(projectId).first()
        val projectTasks = workDao.observeTasksIn(projectId).first()
        val projectItems = itemDao.allLive().filter {
            it.projectId == projectId || itemDao.linksFor(it.id).any { link -> link.targetType == LinkType.PROJECT && link.targetId == projectId }
        }
        val meetings = workDao.meetingsInProject(projectId)
        val peopleMap = workDao.allPeople().associate { it.id to it.name }

        val timelineItems = mutableListOf<TimelineItem>()

        // Add meetings
        for (m in meetings) {
            timelineItems.add(
                TimelineItem(
                    id = m.id,
                    title = m.title,
                    type = TimelineItemType.MEETING,
                    at = m.createdAt,
                    status = "RECORDED",
                    counterparty = null
                )
            )
        }

        // Add dated items
        for (item in projectItems) {
            val date = item.dueAt ?: item.createdAt
            val counterparty = listOfNotNull(item.counterpartyPersonId, item.ownerPersonId)
                .firstOrNull()?.let { peopleMap[it] }
            val type = when (item.itemKind) {
                ItemKind.COMMITMENT -> TimelineItemType.COMMITMENT
                ItemKind.DECISION -> TimelineItemType.DECISION
                ItemKind.RISK -> TimelineItemType.RISK
                else -> TimelineItemType.TASK
            }
            timelineItems.add(
                TimelineItem(
                    id = item.id,
                    title = item.text,
                    type = type,
                    at = date,
                    status = item.status,
                    counterparty = counterparty,
                    isOverdue = item.isOverdue(now)
                )
            )
        }

        // Add dated tasks
        val linkedTaskIds = projectItems.mapNotNull { it.taskId }.toSet()
        for (task in projectTasks) {
            if (task.id !in linkedTaskIds && task.dueAt != null) {
                val counterparty = task.personId?.let { peopleMap[it] }
                timelineItems.add(
                    TimelineItem(
                        id = task.id,
                        title = task.title,
                        type = TimelineItemType.TASK,
                        at = task.dueAt,
                        status = if (task.doneAt != null) "DONE" else "OPEN",
                        counterparty = counterparty,
                        isOverdue = task.doneAt == null && task.dueAt < startOfToday,
                        noteId = task.noteId
                    )
                )
            }
        }

        val overdue = mutableListOf<TimelineItem>()
        val today = mutableListOf<TimelineItem>()
        val thisWeek = mutableListOf<TimelineItem>()
        val later = mutableListOf<TimelineItem>()
        val past = mutableListOf<TimelineItem>()

        for (item in timelineItems.sortedByDescending { it.at }) {
            when {
                item.isOverdue -> overdue.add(item)
                item.at in startOfToday until (startOfToday + 86_400_000L) -> today.add(item)
                item.at in (startOfToday + 86_400_000L)..endOfWeek -> thisWeek.add(item)
                item.at > endOfWeek -> later.add(item)
                else -> past.add(item)
            }
        }

        listOfNotNull(
            TimelineSection("Overdue", overdue).takeIf { overdue.isNotEmpty() },
            TimelineSection("Today", today).takeIf { today.isNotEmpty() },
            TimelineSection("This Week", thisWeek).takeIf { thisWeek.isNotEmpty() },
            TimelineSection("Upcoming", later).takeIf { later.isNotEmpty() },
            TimelineSection("Past Activity", past).takeIf { past.isNotEmpty() }
        )
    }

    /**
     * Queries all recorded risks, optionally filtered to a single project.
     */
    suspend fun risks(projectId: String? = null): List<RiskItem> = withContext(Dispatchers.IO) {
        val items = itemDao.allLive().filter {
            it.itemKind == ItemKind.RISK && (projectId == null || it.projectId == projectId)
        }
        val peopleMap = workDao.allPeople().associate { it.id to it.name }
        val meetings = workDao.riskMeetingTitles().associate { it.id to it.title }

        items.map { item ->
            val counterparty = listOfNotNull(item.counterpartyPersonId, item.ownerPersonId)
                .firstOrNull()?.let { peopleMap[it] }
            RiskItem(
                id = item.id,
                title = item.text,
                severity = item.severity ?: "MEDIUM",
                status = item.status,
                meetingTitle = item.meetingId?.let { meetings[it] },
                counterparty = counterparty,
                createdAt = item.createdAt
            )
        }.sortedWith(
            compareByDescending<RiskItem> { it.severity == "HIGH" }
                .thenByDescending { it.createdAt }
        )
    }
}
