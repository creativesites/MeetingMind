package com.craftflowtechnologies.meetingmind.core.aireport

import org.json.JSONObject
import java.io.File

/**
 * Reports waiting to be sent, one small JSON file each in [dir] (no database migration). The
 * person is never blocked: a report waits here until the network and the server are there.
 * Holds at most [MAX_REPORTS]; a report older than [MAX_AGE_MS] is dropped unsent.
 */
class AiReportQueue(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {

    @Synchronized
    fun enqueue(report: AiReport) {
        dir.mkdirs()
        val file = fileFor(report.reportId) ?: return
        if (file.exists()) return
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(report.toJson().toString())
        tmp.renameTo(file)
        trim()
    }

    /** Waiting reports, oldest first. Expired and unreadable files are removed on the way. */
    @Synchronized
    fun pending(): List<AiReport> {
        val cutoff = now() - MAX_AGE_MS
        return files().mapNotNull { f ->
            val report = runCatching { AiReport.fromJson(JSONObject(f.readText())) }.getOrNull()
            if (report == null || report.createdAt < cutoff) { f.delete(); null } else report
        }.sortedBy { it.createdAt }
    }

    @Synchronized
    fun remove(reportId: String) {
        fileFor(reportId)?.delete()
    }

    fun size(): Int = files().size

    private fun trim() {
        val all = pending()
        if (all.size > MAX_REPORTS) all.take(all.size - MAX_REPORTS).forEach { remove(it.reportId) }
    }

    private fun files(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty()

    /** Ids are UUIDs; anything else is refused rather than used as a file name. */
    private fun fileFor(reportId: String): File? =
        if (ID.matches(reportId)) File(dir, "$reportId.json") else null

    companion object {
        const val MAX_REPORTS = 50
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        private val ID = Regex("[A-Za-z0-9-]{8,64}")
    }
}
