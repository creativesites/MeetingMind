package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.FeatureGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ExternalTask(
    val title: String,
    val description: String? = null,
    val dueText: String? = null,
    val assignee: String? = null,
    val sourceId: String? = null
)

sealed class SyncResult {
    data class Success(val externalId: String, val webUrl: String? = null) : SyncResult()
    data class Failure(val error: String, val isAuthError: Boolean = false) : SyncResult()
    data object RequiresPro : SyncResult()
}

/**
 * Interface for task and issue syncing integrations (Todoist, GitHub, Linear, Notion, Webhooks).
 */
interface TaskSyncProvider : IntegrationProvider {
    override val category: IntegrationCategory get() = IntegrationCategory.TASK_MANAGER
    override val requiredPlanCapability: Capability? get() = Capability.CONNECTED_INTEGRATIONS

    suspend fun pushTask(task: ExternalTask): SyncResult
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

/**
 * Todoist task integration via REST API v2.
 */
class TodoistTaskProvider(
    private val credentialStore: IntegrationCredentialStore
) : TaskSyncProvider {
    override val id: String = ID
    override val name: String = "Todoist"
    override val capabilities: Set<com.craftflowtechnologies.meetingmind.core.integrations.Capability> =
        setOf(com.craftflowtechnologies.meetingmind.core.integrations.Capability.TASK_MIRROR_OUT)
    override var status: ProviderStatus = ProviderStatus.AVAILABLE
    override var isEnabled: Boolean = false
    override val accountScope: String = "Create tasks in personal inbox or projects"

    override suspend fun pushTask(task: ExternalTask): SyncResult = withContext(Dispatchers.IO) {
        if (!FeatureGate.can(Capability.CONNECTED_INTEGRATIONS)) return@withContext SyncResult.RequiresPro
        val token = credentialStore.getCredential(ID)
            ?: return@withContext SyncResult.Failure("Todoist API token not configured", isAuthError = true)

        val json = JSONObject().apply {
            put("content", task.title)
            task.description?.let { put("description", it) }
            task.dueText?.let { put("due_string", it) }
        }

        val request = Request.Builder()
            .url("https://api.todoist.com/rest/v2/tasks")
            .addHeader("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string().orEmpty())
                    SyncResult.Success(respJson.optString("id"), respJson.optString("url"))
                } else {
                    SyncResult.Failure("Todoist error: HTTP ${response.code}", isAuthError = response.code in 401..403)
                }
            }
        } catch (e: Exception) {
            SyncResult.Failure("Network error: ${e.localizedMessage}")
        }
    }

    companion object {
        const val ID = "tasks.todoist"
    }
}

/**
 * GitHub Issues integration via GitHub REST API.
 */
class GitHubIssueProvider(
    private val credentialStore: IntegrationCredentialStore,
    private val repoProvider: () -> String? = { null }
) : TaskSyncProvider {
    override val id: String = ID
    override val name: String = "GitHub Issues"
    override val capabilities: Set<com.craftflowtechnologies.meetingmind.core.integrations.Capability> =
        setOf(com.craftflowtechnologies.meetingmind.core.integrations.Capability.TASK_MIRROR_OUT)
    override var status: ProviderStatus = ProviderStatus.AVAILABLE
    override var isEnabled: Boolean = false
    override val accountScope: String = "Create issues in target repository"

    override suspend fun pushTask(task: ExternalTask): SyncResult = withContext(Dispatchers.IO) {
        if (!FeatureGate.can(Capability.CONNECTED_INTEGRATIONS)) return@withContext SyncResult.RequiresPro
        val token = credentialStore.getCredential(ID)
            ?: return@withContext SyncResult.Failure("GitHub token not configured", isAuthError = true)
        val targetRepo = repoProvider()
            ?: return@withContext SyncResult.Failure("Target repository (owner/repo) not configured")

        val json = JSONObject().apply {
            put("title", task.title)
            val bodyContent = buildString {
                task.description?.let { appendLine(it) }
                task.dueText?.let { appendLine("\n**Due date:** $it") }
                task.assignee?.let { appendLine("**Suggested assignee:** $it") }
            }
            put("body", bodyContent)
        }

        val request = Request.Builder()
            .url("https://api.github.com/repos/$targetRepo/issues")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "MeetingMind-App")
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string().orEmpty())
                    SyncResult.Success(respJson.optString("number"), respJson.optString("html_url"))
                } else {
                    SyncResult.Failure("GitHub error: HTTP ${response.code}", isAuthError = response.code in 401..403)
                }
            }
        } catch (e: Exception) {
            SyncResult.Failure("Network error: ${e.localizedMessage}")
        }
    }

    companion object {
        const val ID = "issues.github"
    }
}

/**
 * Linear issues integration via GraphQL API.
 */
class LinearIssueProvider(
    private val credentialStore: IntegrationCredentialStore,
    private val teamIdProvider: () -> String? = { null }
) : TaskSyncProvider {
    override val id: String = ID
    override val name: String = "Linear"
    override val capabilities: Set<com.craftflowtechnologies.meetingmind.core.integrations.Capability> =
        setOf(com.craftflowtechnologies.meetingmind.core.integrations.Capability.TASK_MIRROR_OUT)
    override var status: ProviderStatus = ProviderStatus.AVAILABLE
    override var isEnabled: Boolean = false
    override val accountScope: String = "Create issues in Linear team"

    override suspend fun pushTask(task: ExternalTask): SyncResult = withContext(Dispatchers.IO) {
        if (!FeatureGate.can(Capability.CONNECTED_INTEGRATIONS)) return@withContext SyncResult.RequiresPro
        val apiKey = credentialStore.getCredential(ID)
            ?: return@withContext SyncResult.Failure("Linear API key not configured", isAuthError = true)
        val teamId = teamIdProvider()
            ?: return@withContext SyncResult.Failure("Linear Team ID not configured")

        val query = """
            mutation IssueCreate(${"$"}input: IssueCreateInput!) {
                issueCreate(input: ${"$"}input) {
                    success
                    issue {
                        id
                        identifier
                        url
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().apply {
            put("input", JSONObject().apply {
                put("title", task.title)
                put("teamId", teamId)
                task.description?.let { put("description", it) }
            })
        }

        val payload = JSONObject().apply {
            put("query", query)
            put("variables", variables)
        }

        val request = Request.Builder()
            .url("https://api.linear.app/graphql")
            .addHeader("Authorization", apiKey)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string().orEmpty())
                    val data = respJson.optJSONObject("data")?.optJSONObject("issueCreate")
                    if (data?.optBoolean("success") == true) {
                        val issue = data.optJSONObject("issue")
                        SyncResult.Success(issue?.optString("identifier") ?: "linear_issue", issue?.optString("url"))
                    } else {
                        SyncResult.Failure("Linear returned error response")
                    }
                } else {
                    SyncResult.Failure("Linear error: HTTP ${response.code}", isAuthError = response.code in 401..403)
                }
            }
        } catch (e: Exception) {
            SyncResult.Failure("Network error: ${e.localizedMessage}")
        }
    }

    companion object {
        const val ID = "issues.linear"
    }
}

/**
 * Webhook output channel for Slack, Microsoft Teams, or custom HTTP endpoints.
 */
class WebhookChannel(
    override val id: String,
    override val name: String,
    private val credentialStore: IntegrationCredentialStore,
    private val channelKind: WebhookKind = WebhookKind.GENERIC
) : TaskSyncProvider {
    enum class WebhookKind { SLACK, TEAMS, GENERIC }

    override val category: IntegrationCategory get() = IntegrationCategory.OUTPUT_CHANNEL
    override val capabilities: Set<com.craftflowtechnologies.meetingmind.core.integrations.Capability> =
        setOf(com.craftflowtechnologies.meetingmind.core.integrations.Capability.OUTPUT_DIRECT_MESSAGE)
    override var status: ProviderStatus = ProviderStatus.AVAILABLE
    override var isEnabled: Boolean = false
    override val accountScope: String = "Post formatted notifications and summaries to webhook"

    override suspend fun pushTask(task: ExternalTask): SyncResult = withContext(Dispatchers.IO) {
        if (!FeatureGate.can(Capability.CONNECTED_INTEGRATIONS)) return@withContext SyncResult.RequiresPro
        val webhookUrl = credentialStore.getCredential(id)
            ?: return@withContext SyncResult.Failure("$name webhook URL not configured", isAuthError = true)

        val payload = when (channelKind) {
            WebhookKind.SLACK -> JSONObject().apply {
                put("text", "📋 *${task.title}*${task.description?.let { "\n$it" }.orEmpty()}")
            }
            WebhookKind.TEAMS -> JSONObject().apply {
                put("title", task.title)
                put("text", task.description ?: task.title)
            }
            WebhookKind.GENERIC -> JSONObject().apply {
                put("event", "task.created")
                put("title", task.title)
                task.description?.let { put("description", it) }
                task.dueText?.let { put("dueText", it) }
                task.sourceId?.let { put("sourceId", it) }
            }
        }

        val request = Request.Builder()
            .url(webhookUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    SyncResult.Success("webhook_sent")
                } else {
                    SyncResult.Failure("$name webhook error: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            SyncResult.Failure("Network error: ${e.localizedMessage}")
        }
    }

    companion object {
        const val SLACK_ID = "webhook.slack"
        const val TEAMS_ID = "webhook.teams"
        const val GENERIC_ID = "webhook.generic"
    }
}
