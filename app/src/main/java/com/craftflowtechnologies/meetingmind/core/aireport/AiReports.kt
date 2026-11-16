package com.craftflowtechnologies.meetingmind.core.aireport

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.net.Net
import com.craftflowtechnologies.meetingmind.core.work.WorkPrivacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the report sheet needs before it opens: may the report leave the phone, and which model to name. */
data class PreparedReport(val delivery: ReportDelivery, val fallbackModel: String?)

/**
 * The one way into AI-content reporting. Screens call [prepare] when the sheet opens and [submit]
 * on Send; sending happens later in [AiReportWorker], so the person is never kept waiting.
 */
object AiReports {
    /** Shown in a copied report. Null until the founder gives a support address. */
    val SUPPORT_EMAIL: String? = null

    private const val WORK_NAME = "ai-report-send"

    fun queue(context: Context) = AiReportQueue(File(context.applicationContext.filesDir, "ai_reports"))

    /** Reads the privacy settings for [source]. Never throws: if anything can't be read, the report stays on the phone. */
    suspend fun prepare(context: Context, source: AiReportSource): PreparedReport = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runCatching {
            val prefs = UserPreferencesManager(app).preferencesFlow.first()
            val onDevice = !prefs.processingProfile.requiresNetwork
            val db = MeetMindDatabase.getInstance(app)
            val meetingIds = listOfNotNull(source.meetingId) + source.moreMeetingIds
            val noteIds = (listOfNotNull(source.noteId) + source.moreNoteIds +
                meetingIds.mapNotNull { db.meetingDao().getMeetingById(it)?.noteId }).distinct()
            val privateNote = noteIds.any { db.noteDao().getById(it)?.isPrivate == true }
            val keep = source.mustStayOnDevice ||
                noteIds.any { WorkPrivacy.mustStayOnDevice(app, noteId = it) } ||
                meetingIds.any { WorkPrivacy.mustStayOnDevice(app, meetingId = it) }
            val model = if (onDevice) prefs.selectedLlmModelId else DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL
            PreparedReport(AiReportPolicy.delivery(ReportPrivacy(onDevice, privateNote, keep)), model)
        }.getOrElse { PreparedReport(ReportDelivery.CopyOnly("its privacy settings couldn't be read"), null) }
    }

    fun build(source: AiReportSource, reason: AiReportReason, comment: String?, prepared: PreparedReport): AiReport =
        AiReport.create(source, reason, comment, prepared.fallbackModel, BuildConfig.VERSION_NAME, System.currentTimeMillis())

    /** Queues [report] and schedules sending. Only call when [PreparedReport.delivery] is [ReportDelivery.Upload]. */
    suspend fun submit(context: Context, report: AiReport) {
        withContext(Dispatchers.IO) { queue(context).enqueue(report) }
        schedule(context)
    }

    /** [submit] on an app-wide scope, so closing the sheet doesn't cancel it. */
    fun submitInBackground(context: Context, report: AiReport) {
        val app = context.applicationContext
        scope.launch { runCatching { submit(app, report) } }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<AiReportWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/** What to do with a report after one try. */
enum class SendOutcome { SENT, RETRY, DROP }

/** Posts one report to the Circles Worker's `POST /v1/reportAiContent`. No account, no token. */
class AiReportSender(
    baseUrl: String = BuildConfig.CIRCLES_API_URL,
    private val client: OkHttpClient = Net.client(connectS = 10, readS = 20, writeS = 20),
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val json = "application/json; charset=utf-8".toMediaType()

    fun send(report: AiReport): SendOutcome {
        if (root.isBlank()) return SendOutcome.RETRY
        val request = try {
            Request.Builder().url("$root/v1/reportAiContent").post(report.toJson().toString().toRequestBody(json)).build()
        } catch (e: IllegalArgumentException) {
            return SendOutcome.RETRY
        }
        return try {
            client.newCall(request).execute().use { outcome(it.code) }
        } catch (e: IOException) {
            SendOutcome.RETRY
        }
    }

    companion object {
        /**
         * 2xx: stored. 400, 413 and 422: the server will never take this report, so drop it.
         * Everything else, including 404 (route not deployed yet), 429 and 5xx: keep it and try later.
         */
        fun outcome(code: Int): SendOutcome = when (code) {
            in 200..299 -> SendOutcome.SENT
            400, 413, 422 -> SendOutcome.DROP
            else -> SendOutcome.RETRY
        }
    }
}

class AiReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val queue = AiReports.queue(applicationContext)
        val sender = AiReportSender()
        var retry = false
        for (report in queue.pending()) {
            when (sender.send(report)) {
                SendOutcome.SENT, SendOutcome.DROP -> queue.remove(report.reportId)
                SendOutcome.RETRY -> { retry = true; break }
            }
        }
        if (retry) Result.retry() else Result.success()
    }
}
