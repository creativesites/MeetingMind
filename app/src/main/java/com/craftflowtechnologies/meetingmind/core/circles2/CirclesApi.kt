package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** What the composer sends. There is deliberately no uid or name field: the Worker knows the caller from the token. */
data class CreatePostRequest(
    val circleId: String,
    val type: PostType,
    val body: String,
    val verseRef: String? = null,
    val anonymous: Boolean = false
)

data class CreatedPost(val postId: String, val pending: Boolean)
data class CreateCircleRequest(val name: String, val template: String, val vocab: String, val displayName: String, val settings: CircleSettings)

/** The Worker's endpoints (server/circles-api). Every call returns a result: nothing here throws to the UI. */
interface CirclesApi {
    /** False when no base URL is configured: the UI shows "Circles isn't connected yet". */
    val isConfigured: Boolean

    suspend fun createCircle(req: CreateCircleRequest): CirclesResult<String>
    suspend fun updateCircle(circleId: String, name: String?, vocab: String?, settings: CircleSettings?): CirclesResult<Unit>
    suspend fun myCircles(): CirclesResult<List<String>>
    suspend fun createInvite(circleId: String, expiresInDays: Int = 7, maxUses: Int = 50): CirclesResult<Invite>
    suspend fun revokeInvite(code: String): CirclesResult<Unit>
    /** [pasted] is whatever the person pasted; the Worker extracts the code. Returns the circle id. */
    suspend fun join(pasted: String, displayName: String): CirclesResult<String>
    suspend fun leave(circleId: String): CirclesResult<Unit>
    suspend fun removeMember(circleId: String, targetUid: String): CirclesResult<Unit>
    suspend fun setRole(circleId: String, targetUid: String, role: Role): CirclesResult<Unit>
    suspend fun setMute(circleId: String, muted: Boolean): CirclesResult<Unit>

    suspend fun createPost(req: CreatePostRequest): CirclesResult<CreatedPost>
    suspend fun approvePost(circleId: String, postId: String): CirclesResult<Unit>
    suspend fun rejectPost(circleId: String, postId: String): CirclesResult<Unit>
    suspend fun editPost(circleId: String, postId: String, body: String): CirclesResult<Unit>
    suspend fun addUpdate(circleId: String, postId: String, body: String): CirclesResult<Unit>
    suspend fun markAnswered(circleId: String, postId: String): CirclesResult<Unit>
    suspend fun deletePost(circleId: String, postId: String): CirclesResult<Unit>
    /** Returns true if this person had already prayed. */
    suspend fun prayed(circleId: String, postId: String): CirclesResult<Boolean>
    suspend fun react(circleId: String, postId: String, kind: ReactionKind?): CirclesResult<Unit>
    suspend fun syncCounts(circleId: String, postId: String): CirclesResult<Unit>
    suspend fun report(circleId: String, postId: String, reason: String): CirclesResult<Unit>
    suspend fun reportMessage(circleId: String, messageId: String, reason: String): CirclesResult<Unit>

    suspend fun createPoll(circleId: String, question: String, options: List<String>, multi: Boolean): CirclesResult<Unit>
    suspend fun closePoll(circleId: String, pollId: String): CirclesResult<Unit>
    suspend fun startChain(circleId: String, title: String, postId: String?): CirclesResult<Unit>
    suspend fun celebrate(circleId: String, kind: CelebrationKind, text: String, companion: String?, postId: String? = null): CirclesResult<Unit>

    suspend fun registerToken(token: String): CirclesResult<Unit>
}

/** Used when CIRCLES_API_URL is empty. Every call says so plainly. */
object NotConfiguredCirclesApi : CirclesApi {
    override val isConfigured = false
    private fun <T> no(): CirclesResult<T> = CirclesFailure.notConnected.asResult()
    override suspend fun createCircle(req: CreateCircleRequest) = no<String>()
    override suspend fun updateCircle(circleId: String, name: String?, vocab: String?, settings: CircleSettings?) = no<Unit>()
    override suspend fun myCircles() = no<List<String>>()
    override suspend fun createInvite(circleId: String, expiresInDays: Int, maxUses: Int) = no<Invite>()
    override suspend fun revokeInvite(code: String) = no<Unit>()
    override suspend fun join(pasted: String, displayName: String) = no<String>()
    override suspend fun leave(circleId: String) = no<Unit>()
    override suspend fun removeMember(circleId: String, targetUid: String) = no<Unit>()
    override suspend fun setRole(circleId: String, targetUid: String, role: Role) = no<Unit>()
    override suspend fun setMute(circleId: String, muted: Boolean) = no<Unit>()
    override suspend fun createPost(req: CreatePostRequest) = no<CreatedPost>()
    override suspend fun approvePost(circleId: String, postId: String) = no<Unit>()
    override suspend fun rejectPost(circleId: String, postId: String) = no<Unit>()
    override suspend fun editPost(circleId: String, postId: String, body: String) = no<Unit>()
    override suspend fun addUpdate(circleId: String, postId: String, body: String) = no<Unit>()
    override suspend fun markAnswered(circleId: String, postId: String) = no<Unit>()
    override suspend fun deletePost(circleId: String, postId: String) = no<Unit>()
    override suspend fun prayed(circleId: String, postId: String) = no<Boolean>()
    override suspend fun react(circleId: String, postId: String, kind: ReactionKind?) = no<Unit>()
    override suspend fun syncCounts(circleId: String, postId: String) = no<Unit>()
    override suspend fun report(circleId: String, postId: String, reason: String) = no<Unit>()
    override suspend fun reportMessage(circleId: String, messageId: String, reason: String) = no<Unit>()
    override suspend fun createPoll(circleId: String, question: String, options: List<String>, multi: Boolean) = no<Unit>()
    override suspend fun closePoll(circleId: String, pollId: String) = no<Unit>()
    override suspend fun startChain(circleId: String, title: String, postId: String?) = no<Unit>()
    override suspend fun celebrate(circleId: String, kind: CelebrationKind, text: String, companion: String?, postId: String?) = no<Unit>()
    override suspend fun registerToken(token: String) = no<Unit>()
}

/** JSON bodies, kept pure so tests can assert exactly what leaves the phone. */
object CirclesJson {
    fun settings(s: CircleSettings): JSONObject = JSONObject()
        .put("allowedTypes", JSONArray(s.allowedTypes.map { it.wire }))
        .put("whoCanInvite", s.whoCanInvite.wire)
        .put("prayerApproval", s.prayerApproval)
        .put("reactions", JSONArray(s.reactions.map { it.wire }))
        .put("guidelines", s.guidelines)

    fun createPost(r: CreatePostRequest): JSONObject = JSONObject()
        .put("circleId", r.circleId).put("type", r.type.wire).put("body", r.body)
        .put("anonymous", r.anonymous)
        .apply { if (!r.verseRef.isNullOrBlank()) put("verseRef", r.verseRef) }

    fun createCircle(r: CreateCircleRequest): JSONObject = JSONObject()
        .put("name", r.name).put("template", r.template).put("vocab", r.vocab)
        .put("displayName", r.displayName).put("settings", settings(r.settings))
}

/**
 * HTTP client for the Worker. [tokenProvider] returns a fresh Firebase ID token; the Worker verifies it.
 * Errors from the Worker (`{ok:false,error:{code,message}}`) become [CirclesFailure] with the Worker's
 * own plain-English message.
 */
class HttpCirclesApi(
    private val baseUrl: String,
    private val tokenProvider: suspend () -> CirclesResult<String>,
    private val client: OkHttpClient
) : CirclesApi {
    override val isConfigured: Boolean = baseUrl.isNotBlank()
    private val root = baseUrl.trim().trimEnd('/')
    private val json = "application/json; charset=utf-8".toMediaType()

    /** One POST. Never throws. */
    suspend fun call(endpoint: String, body: JSONObject = JSONObject()): CirclesResult<JSONObject> {
        if (!isConfigured) return CirclesFailure.notConnected.asResult()
        val token = when (val t = tokenProvider()) {
            is CirclesResult.Ok -> t.value
            is CirclesResult.Err -> return t
        }
        return withContext(Dispatchers.IO) { post(endpoint, body, token) }
    }

    private fun post(endpoint: String, body: JSONObject, token: String): CirclesResult<JSONObject> {
        val request = try {
            Request.Builder().url("$root/v1/$endpoint").header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(json)).build()
        } catch (e: IllegalArgumentException) {
            return CirclesFailure.notConnected.asResult()
        }
        return try {
            client.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val obj = try { JSONObject(text) } catch (e: JSONException) { null }
                if (res.isSuccessful && obj != null && obj.optBoolean("ok", false)) CirclesResult.Ok(obj)
                else CirclesResult.Err(failureFrom(res.code, obj))
            }
        } catch (e: IOException) {
            CirclesFailure.offline.asResult()
        } catch (e: RuntimeException) {
            CirclesFailure.server().asResult()
        }
    }

    private fun failureFrom(status: Int, obj: JSONObject?): CirclesFailure {
        val err = obj?.optJSONObject("error")
        val code = err?.optString("code")?.takeIf { it.isNotBlank() }
        val message = err?.optString("message")?.takeIf { it.isNotBlank() }
        val kind = when {
            status == 401 -> FailureKind.SignedOut
            status == 404 -> FailureKind.NotFound
            status == 429 -> FailureKind.RateLimited
            status in 400..499 -> FailureKind.Rejected
            else -> FailureKind.Server
        }
        val fallback = when (kind) {
            FailureKind.SignedOut -> CirclesFailure.signedOut.message
            FailureKind.RateLimited -> "You're doing that too fast. Please wait a bit and try again."
            else -> CirclesFailure.server().message
        }
        return CirclesFailure(kind, message ?: fallback, code)
    }

    private suspend fun unit(endpoint: String, body: JSONObject): CirclesResult<Unit> = call(endpoint, body).map { }

    override suspend fun createCircle(req: CreateCircleRequest): CirclesResult<String> =
        call("createCircle", CirclesJson.createCircle(req)).flatMap { str(it, "circleId") }

    override suspend fun updateCircle(circleId: String, name: String?, vocab: String?, settings: CircleSettings?) =
        unit("updateCircle", JSONObject().put("circleId", circleId).apply {
            if (name != null) put("name", name)
            if (vocab != null) put("vocab", vocab)
            if (settings != null) put("settings", CirclesJson.settings(settings))
        })

    override suspend fun myCircles(): CirclesResult<List<String>> = call("myCircles").map { o ->
        val a = o.optJSONArray("circleIds") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    override suspend fun createInvite(circleId: String, expiresInDays: Int, maxUses: Int) =
        call("createInvite", JSONObject().put("circleId", circleId).put("expiresInDays", expiresInDays).put("maxUses", maxUses))
            .flatMap { o ->
                val code = o.optString("code")
                if (code.isBlank()) CirclesFailure.server().asResult() else CirclesResult.Ok(Invite(code, o.optString("expiresAt").ifBlank { null }, if (o.has("maxUses")) o.optInt("maxUses") else null))
            }

    override suspend fun revokeInvite(code: String) = unit("revokeInvite", JSONObject().put("code", code))
    override suspend fun join(pasted: String, displayName: String): CirclesResult<String> =
        call("join", JSONObject().put("code", pasted.take(2000)).put("displayName", displayName)).flatMap { str(it, "circleId") }
    override suspend fun leave(circleId: String) = unit("leave", JSONObject().put("circleId", circleId))
    override suspend fun removeMember(circleId: String, targetUid: String) = unit("removeMember", JSONObject().put("circleId", circleId).put("targetUid", targetUid))
    override suspend fun setRole(circleId: String, targetUid: String, role: Role) = unit("setRole", JSONObject().put("circleId", circleId).put("targetUid", targetUid).put("role", role.wire))
    override suspend fun setMute(circleId: String, muted: Boolean) = unit("setMute", JSONObject().put("circleId", circleId).put("muted", muted))

    override suspend fun createPost(req: CreatePostRequest): CirclesResult<CreatedPost> =
        call("createPost", CirclesJson.createPost(req)).flatMap { o ->
            val id = o.optString("postId")
            if (id.isBlank()) CirclesFailure.server().asResult() else CirclesResult.Ok(CreatedPost(id, o.optString("status") == "pending"))
        }

    override suspend fun approvePost(circleId: String, postId: String) = unit("approvePost", post(circleId, postId))
    override suspend fun rejectPost(circleId: String, postId: String) = unit("rejectPost", post(circleId, postId))
    override suspend fun editPost(circleId: String, postId: String, body: String) = unit("editPost", post(circleId, postId).put("body", body))
    override suspend fun addUpdate(circleId: String, postId: String, body: String) = unit("addUpdate", post(circleId, postId).put("body", body))
    override suspend fun markAnswered(circleId: String, postId: String) = unit("markAnswered", post(circleId, postId))
    override suspend fun deletePost(circleId: String, postId: String) = unit("deletePost", post(circleId, postId))
    override suspend fun prayed(circleId: String, postId: String): CirclesResult<Boolean> = call("prayed", post(circleId, postId)).map { it.optBoolean("alreadyPrayed", false) }
    override suspend fun react(circleId: String, postId: String, kind: ReactionKind?) =
        unit("react", post(circleId, postId).put("kind", kind?.wire ?: JSONObject.NULL))
    override suspend fun syncCounts(circleId: String, postId: String) = unit("syncCounts", post(circleId, postId))
    override suspend fun report(circleId: String, postId: String, reason: String) = unit("report", post(circleId, postId).put("reason", reason))
    override suspend fun reportMessage(circleId: String, messageId: String, reason: String) = unit("reportMessage", JSONObject().put("circleId", circleId).put("messageId", messageId).put("reason", reason))

    override suspend fun createPoll(circleId: String, question: String, options: List<String>, multi: Boolean) =
        unit("createPoll", JSONObject().put("circleId", circleId).put("question", question).put("options", JSONArray(options)).put("multi", multi))
    override suspend fun closePoll(circleId: String, pollId: String) = unit("closePoll", JSONObject().put("circleId", circleId).put("pollId", pollId))
    override suspend fun startChain(circleId: String, title: String, postId: String?) =
        unit("startChain", JSONObject().put("circleId", circleId).put("title", title).apply { if (postId != null) put("postId", postId) })
    override suspend fun celebrate(circleId: String, kind: CelebrationKind, text: String, companion: String?, postId: String?) =
        unit("celebrate", JSONObject().put("circleId", circleId).put("kind", kind.wire).put("text", text)
            .apply { if (companion != null) put("companion", companion); if (postId != null) put("postId", postId) })
    override suspend fun registerToken(token: String) = unit("registerToken", JSONObject().put("token", token))

    private fun post(circleId: String, postId: String) = JSONObject().put("circleId", circleId).put("postId", postId)
    private fun str(o: JSONObject, key: String): CirclesResult<String> =
        o.optString(key).takeIf { it.isNotBlank() }?.let { CirclesResult.Ok(it) } ?: CirclesFailure.server().asResult()
}
