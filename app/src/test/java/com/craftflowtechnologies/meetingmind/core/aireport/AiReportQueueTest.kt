package com.craftflowtechnologies.meetingmind.core.aireport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class AiReportQueueTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_700_000_000_000L
    private fun queue(dir: File = tmp.root) = AiReportQueue(dir) { clock }

    private fun report(at: Long = clock, id: String = UUID.randomUUID().toString()) = AiReport.create(
        AiReportSource(AiFeature.ASK_LEARNING, "answer"), AiReportReason.MISLEADING, null, "m", "1.0", at, id
    )

    @Test
    fun `reports wait in order and leave when removed`() {
        val q = queue()
        val a = report(at = clock - 2); val b = report(at = clock - 1)
        q.enqueue(b); q.enqueue(a)
        assertEquals(listOf(a, b), q.pending())
        q.remove(a.reportId)
        assertEquals(listOf(b), q.pending())
    }

    @Test
    fun `the same report is stored once`() {
        val q = queue()
        val a = report()
        q.enqueue(a); q.enqueue(a)
        assertEquals(1, q.size())
    }

    @Test
    fun `queue survives a restart`() {
        val a = report()
        queue().enqueue(a)
        assertEquals(listOf(a), queue().pending())
    }

    @Test
    fun `queue keeps only the newest reports past the cap`() {
        val q = queue()
        val all = (0 until AiReportQueue.MAX_REPORTS + 5).map { report(at = clock - 1_000 + it) }
        all.forEach { q.enqueue(it) }
        assertEquals(all.takeLast(AiReportQueue.MAX_REPORTS), q.pending())
    }

    @Test
    fun `old reports are dropped unsent`() {
        val q = queue()
        q.enqueue(report(at = clock - AiReportQueue.MAX_AGE_MS - 1))
        val fresh = report()
        q.enqueue(fresh)
        assertEquals(listOf(fresh), q.pending())
        assertEquals(1, q.size())
    }

    @Test
    fun `a bad id is never used as a file name, and a broken file is cleared`() {
        val q = queue()
        q.enqueue(report(id = "../../escape"))
        assertEquals(0, q.size())
        File(tmp.root, "broken.json").writeText("{not json")
        assertTrue(q.pending().isEmpty())
        assertEquals(0, q.size())
    }

    @Test
    fun `stored file holds only the report keys`() {
        val q = queue()
        val a = report()
        q.enqueue(a)
        val stored = org.json.JSONObject(File(tmp.root, "${a.reportId}.json").readText())
        assertEquals(AiReport.KEYS.toSet() - "comment", stored.keys().asSequence().toSet())
    }
}
