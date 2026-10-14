package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class XRayNodeType(val label: String) {
    PROJECT("Project"),
    PERSON("People"),
    DECISION("Decisions"),
    RISK("Risks"),
    MEETING("Meetings"),
    TASK("Tasks")
}

data class XRayNode(
    val id: String,
    val title: String,
    val subtitle: String?,
    val type: XRayNodeType,
    val status: String?,
    val citedId: String? = null,
    val citationQuote: String? = null
)

enum class XRayEdgeType {
    INVOLVED_IN,
    DECIDED_IN,
    IDENTIFIED_IN,
    BELONGS_TO,
    ASSIGNED_TO
}

data class XRayEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val label: String,
    val type: XRayEdgeType
)

data class XRayCluster(
    val type: XRayNodeType,
    val label: String,
    val nodes: List<XRayNode>
)

data class XRayGraph(
    val projectId: String,
    val projectName: String,
    val nodes: List<XRayNode>,
    val edges: List<XRayEdge>,
    val clusters: List<XRayCluster>,
    val summary: String,
    val citedQuotes: List<Pair<String, String>>
)

class XRayBuilder(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val itemDao = database.itemDao()
    private val workDao = database.workDao()
    private val packBuilder = ContextPackBuilder(database, clock = clock)

    /**
     * Builds a laid-out project entity graph and cited explanation directly from SQLite.
     * Operates completely offline with zero neural model dependency.
     */
    suspend fun build(projectId: String): XRayGraph = withContext(Dispatchers.IO) {
        val projectNotes = workDao.observeNotesIn(projectId).first()
        val projectTasks = workDao.observeTasksIn(projectId).first()
        val projectMeetings = workDao.meetingsInProject(projectId)
        val projectMembers = itemDao.membersOf(projectId)
        val allProjectItems = itemDao.allLive().filter {
            it.projectId == projectId || itemDao.linksFor(it.id).any { link -> link.targetType == LinkType.PROJECT && link.targetId == projectId }
        }
        val allPeople = workDao.allPeople().associate { it.id to it.name }

        val pack = packBuilder.build(PackScope.Entity(ContextType.PROJECT, projectId))

        val nodes = mutableListOf<XRayNode>()
        val edges = mutableListOf<XRayEdge>()

        // 1. Project node
        val projectName = projectNotes.firstOrNull()?.notebookId ?: "Project"
        val projectNode = XRayNode(
            id = projectId,
            title = projectName,
            subtitle = "${projectMeetings.size} meetings · ${projectTasks.size} tasks",
            type = XRayNodeType.PROJECT,
            status = "ACTIVE"
        )
        nodes.add(projectNode)

        // 2. People nodes
        val memberPersonIds = projectMembers.map { it.personId }.toSet()
        val peopleInvolvedIds = mutableSetOf<String>()
        peopleInvolvedIds.addAll(memberPersonIds)
        allProjectItems.forEach { item ->
            item.ownerPersonId?.let { peopleInvolvedIds.add(it) }
            item.counterpartyPersonId?.let { peopleInvolvedIds.add(it) }
        }
        projectTasks.forEach { task ->
            task.personId?.let { peopleInvolvedIds.add(it) }
        }

        val peopleNodes = mutableListOf<XRayNode>()
        for (pid in peopleInvolvedIds) {
            val name = allPeople[pid] ?: continue
            val role = projectMembers.firstOrNull { it.personId == pid }?.role ?: "Stakeholder"
            val node = XRayNode(
                id = pid,
                title = name,
                subtitle = role,
                type = XRayNodeType.PERSON,
                status = "ACTIVE"
            )
            peopleNodes.add(node)
            nodes.add(node)
            edges.add(XRayEdge(pid, projectId, "Member", XRayEdgeType.INVOLVED_IN))
        }

        // 3. Decision nodes
        val decisionItems = allProjectItems.filter { it.itemKind == ItemKind.DECISION }
        val decisionNodes = mutableListOf<XRayNode>()
        for (d in decisionItems) {
            val ev = pack.evidenceFor(d.id)
            val node = XRayNode(
                id = d.id,
                title = d.text,
                subtitle = "Decided ${Pulse.shortDate(d.createdAt)}",
                type = XRayNodeType.DECISION,
                status = d.status,
                citedId = ev?.id,
                citationQuote = ev?.quote
            )
            decisionNodes.add(node)
            nodes.add(node)
            edges.add(XRayEdge(d.id, projectId, "Decided in", XRayEdgeType.DECIDED_IN))
            d.meetingId?.let { mid ->
                edges.add(XRayEdge(d.id, mid, "Agreed in", XRayEdgeType.DECIDED_IN))
            }
        }

        // 4. Risk nodes
        val riskItems = allProjectItems.filter { it.itemKind == ItemKind.RISK }
        val riskNodes = mutableListOf<XRayNode>()
        for (r in riskItems) {
            val ev = pack.evidenceFor(r.id)
            val node = XRayNode(
                id = r.id,
                title = r.text,
                subtitle = "${r.severity ?: "Medium"} risk",
                type = XRayNodeType.RISK,
                status = r.status,
                citedId = ev?.id,
                citationQuote = ev?.quote
            )
            riskNodes.add(node)
            nodes.add(node)
            edges.add(XRayEdge(r.id, projectId, "Risk in", XRayEdgeType.IDENTIFIED_IN))
        }

        // 5. Meeting nodes
        val meetingNodes = mutableListOf<XRayNode>()
        for (m in projectMeetings) {
            val node = XRayNode(
                id = m.id,
                title = m.title,
                subtitle = Pulse.shortDate(m.createdAt),
                type = XRayNodeType.MEETING,
                status = "RECORDED"
            )
            meetingNodes.add(node)
            nodes.add(node)
            edges.add(XRayEdge(m.id, projectId, "Session in", XRayEdgeType.BELONGS_TO))
        }

        // 6. Task nodes
        val taskNodes = mutableListOf<XRayNode>()
        for (t in projectTasks) {
            val node = XRayNode(
                id = t.id,
                title = t.title,
                subtitle = if (t.doneAt != null) "Done" else t.dueAt?.let { "Due ${Pulse.shortDate(it)}" } ?: "Open",
                type = XRayNodeType.TASK,
                status = if (t.doneAt != null) "DONE" else "OPEN"
            )
            taskNodes.add(node)
            nodes.add(node)
            edges.add(XRayEdge(t.id, projectId, "Task for", XRayEdgeType.BELONGS_TO))
            t.personId?.let { pid ->
                edges.add(XRayEdge(t.id, pid, "Assigned to", XRayEdgeType.ASSIGNED_TO))
            }
        }

        val clusters = listOf(
            XRayCluster(XRayNodeType.PERSON, "People (${peopleNodes.size})", peopleNodes),
            XRayCluster(XRayNodeType.DECISION, "Decisions (${decisionNodes.size})", decisionNodes),
            XRayCluster(XRayNodeType.RISK, "Risks (${riskNodes.size})", riskNodes),
            XRayCluster(XRayNodeType.MEETING, "Meetings (${meetingNodes.size})", meetingNodes),
            XRayCluster(XRayNodeType.TASK, "Tasks (${taskNodes.size})", taskNodes)
        ).filter { it.nodes.isNotEmpty() }

        // Short cited explanation from the context pack (works offline)
        val summaryText = buildString {
            append("Project has ${projectMeetings.size} meetings and ${peopleNodes.size} key collaborators. ")
            if (decisionNodes.isNotEmpty()) {
                append("${decisionNodes.size} decisions in effect. ")
            }
            if (riskNodes.isNotEmpty()) {
                append("${riskNodes.size} active risks identified. ")
            }
            if (taskNodes.isNotEmpty()) {
                val openCount = taskNodes.count { it.status == "OPEN" }
                append("$openCount open tasks remaining.")
            }
        }

        val citedQuotes = pack.evidence.map { it.id to it.quote }

        XRayGraph(
            projectId = projectId,
            projectName = projectName,
            nodes = nodes,
            edges = edges,
            clusters = clusters,
            summary = summaryText,
            citedQuotes = citedQuotes
        )
    }
}
