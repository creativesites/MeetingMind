package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XRayTest {
    private lateinit var f: PulseFixture
    private lateinit var builder: XRayBuilder

    @Before
    fun setup() {
        f = PulseFixture()
        builder = XRayBuilder(f.db) { f.now }
    }

    @After
    fun tearDown() {
        f.db.close()
    }

    @Test
    fun xrayBuildsCompleteEntityGraphFromRoom() = runBlocking {
        // 1. Add Decision with evidence quote
        f.item(
            kind = ItemKind.DECISION,
            status = ItemStatus.ACTIVE,
            text = "Adopt Room Database v21",
            quote = "We agree to standardize on Room v21",
            startMs = 120_000
        )

        // 2. Add Risk
        f.item(
            kind = ItemKind.RISK,
            status = ItemStatus.OPEN,
            text = "Tight release deadline"
        )

        // 3. Add Task assigned to Ana
        f.db.taskDao().upsert(
            TaskEntity(
                id = "task_xray_1",
                title = "Prepare launch checklist",
                notes = "",
                kind = "TASK",
                dueAt = f.today + 3 * f.day,
                remindAt = null,
                repeat = "NEVER",
                doneAt = null,
                personId = "ana",
                noteId = "n1",
                blockId = null,
                meetingId = null,
                startMs = null,
                scripture = null,
                createdAt = f.now,
                updatedAt = f.now
            )
        )

        val graph = builder.build("nb")

        // Check project node
        assertEquals("nb", graph.projectId)
        val projectNode = graph.nodes.find { it.id == "nb" }
        assertNotNull(projectNode)
        assertEquals(XRayNodeType.PROJECT, projectNode!!.type)

        // Check people nodes
        val anaNode = graph.nodes.find { it.id == "ana" }
        assertNotNull(anaNode)
        assertEquals(XRayNodeType.PERSON, anaNode!!.type)
        assertEquals("Ana", anaNode.title)

        // Check decision nodes
        val decisionNode = graph.nodes.find { it.type == XRayNodeType.DECISION }
        assertNotNull(decisionNode)
        assertEquals("Adopt Room Database v21", decisionNode!!.title)
        assertEquals("We agree to standardize on Room v21", decisionNode.citationQuote)

        // Check risk nodes
        val riskNode = graph.nodes.find { it.type == XRayNodeType.RISK }
        assertNotNull(riskNode)
        assertEquals("Tight release deadline", riskNode!!.title)

        // Check meeting nodes
        val meetingNode = graph.nodes.find { it.type == XRayNodeType.MEETING }
        assertNotNull(meetingNode)
        assertEquals("m1", meetingNode!!.id)

        // Check task nodes
        val taskNode = graph.nodes.find { it.type == XRayNodeType.TASK }
        assertNotNull(taskNode)
        assertEquals("Prepare launch checklist", taskNode!!.title)

        // Verify edges
        val edgeTypes = graph.edges.map { it.type }.toSet()
        assertTrue(edgeTypes.contains(XRayEdgeType.INVOLVED_IN))
        assertTrue(edgeTypes.contains(XRayEdgeType.DECIDED_IN))
        assertTrue(edgeTypes.contains(XRayEdgeType.IDENTIFIED_IN))
        assertTrue(edgeTypes.contains(XRayEdgeType.BELONGS_TO))
        assertTrue(edgeTypes.contains(XRayEdgeType.ASSIGNED_TO))

        // Verify clusters
        val clusterTypes = graph.clusters.map { it.type }.toSet()
        assertTrue(clusterTypes.contains(XRayNodeType.PERSON))
        assertTrue(clusterTypes.contains(XRayNodeType.DECISION))
        assertTrue(clusterTypes.contains(XRayNodeType.RISK))
        assertTrue(clusterTypes.contains(XRayNodeType.MEETING))
        assertTrue(clusterTypes.contains(XRayNodeType.TASK))

        // Verify summary text
        assertTrue(graph.summary.contains("meetings"))
        assertTrue(graph.summary.contains("decisions in effect"))
        assertTrue(graph.summary.contains("active risks identified"))
    }
}
