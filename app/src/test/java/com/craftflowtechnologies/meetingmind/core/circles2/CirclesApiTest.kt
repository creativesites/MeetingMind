package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** The HTTP client is exercised through an in-process interceptor: no socket is ever opened. */
@RunWith(RobolectricTestRunner::class)
class CirclesApiTest {
    private class Canned(val status: Int = 200, val body: String = """{"ok":true}""", val fail: IOException? = null, val requestId: String? = null) : Interceptor {
        var request: Request? = null
        var sent: String = ""
        override fun intercept(chain: Interceptor.Chain): Response {
            val r = chain.request()
            request = r
            sent = Buffer().also { b -> r.body?.writeTo(b) }.readUtf8()
            if (fail != null) throw fail
            return Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(status).message("x")
                .apply { if (requestId != null) header("X-Request-Id", requestId) }
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun api(canned: Canned, url: String = "https://circles.example.workers.dev/", token: CirclesResult<String> = CirclesResult.Ok("tok")) =
        HttpCirclesApi(url, { token }, OkHttpClient.Builder().addInterceptor(canned).build())

    @Test fun `an anonymous post never carries a uid or a name`() = runBlocking {
        val c = Canned(body = """{"ok":true,"postId":"p9","status":"pending"}""")
        val r = api(c).createPost(CreatePostRequest("c1", PostType.Prayer, "Please pray", "Psalm 23:1", anonymous = true))
        assertEquals(CreatedPost("p9", true), r.okOrNull)
        val json = JSONObject(c.sent)
        assertEquals(setOf("circleId", "type", "body", "anonymous", "verseRef"), json.keys().asSequence().toSet())
        assertTrue(json.getBoolean("anonymous"))
        assertFalse(c.sent.contains("uid", ignoreCase = true))
        assertFalse(c.sent.contains("displayName"))
        assertFalse(c.sent.contains("author", ignoreCase = true))
        assertEquals("Bearer tok", c.request?.header("Authorization"))
        assertEquals("https://circles.example.workers.dev/v1/createPost", c.request?.url.toString())
    }

    @Test fun `commentAsAuthor sends no uid or name, and token calls hit their endpoints`() = runBlocking {
        val c = Canned(body = """{"ok":true,"commentId":"x"}""")
        assertTrue(api(c).commentAsAuthor("c1", "p1", "Thanks", "cm1") is CirclesResult.Ok)
        val json = JSONObject(c.sent)
        assertEquals(setOf("circleId", "postId", "body", "parentId"), json.keys().asSequence().toSet())
        assertFalse(c.sent.contains("uid", ignoreCase = true))
        assertFalse(c.sent.contains("displayName"))
        assertEquals("https://circles.example.workers.dev/v1/commentAsAuthor", c.request?.url.toString())
        api(c).commentAsAuthor("c1", "p1", "Thanks", null)
        assertFalse(JSONObject(c.sent).has("parentId"))
        api(c).registerToken("tok-1-aaaaaaaaaaaaaaaaaaaa")
        assertEquals("https://circles.example.workers.dev/v1/registerToken", c.request?.url.toString())
        api(c).unregisterToken("tok-1-aaaaaaaaaaaaaaaaaaaa")
        assertEquals("https://circles.example.workers.dev/v1/unregisterToken", c.request?.url.toString())
        assertEquals("tok-1-aaaaaaaaaaaaaaaaaaaa", JSONObject(c.sent).getString("token"))
    }

    @Test fun `no base url means not connected and no request`() = runBlocking {
        val c = Canned()
        val r = api(c, url = "").join("GRACE-7K2Q", "Ann")
        assertEquals(FailureKind.NotConnected, r.failureOrNull?.kind)
        assertEquals(null, c.request)
        assertFalse(NotConfiguredCirclesApi.isConfigured)
        assertEquals(FailureKind.NotConnected, NotConfiguredCirclesApi.myCircles().failureOrNull?.kind)
    }

    @Test fun `worker errors keep their plain message`() = runBlocking {
        val c = Canned(410, """{"ok":false,"error":{"code":"expired","message":"That code has expired. Ask for a new one."}}""")
        val f = api(c).join("GRACE-7K2Q", "Ann").failureOrNull
        assertEquals("That code has expired. Ask for a new one.", f?.message)
        assertEquals("expired", f?.code)
        assertEquals(FailureKind.Rejected, f?.kind)
    }

    @Test fun `create request carries stable idempotency key and errors preserve request correlation`() = runBlocking {
        val key = "8d1333a4-6d8c-4d4e-92db-09f708916ccd"
        val request = CreateCircleRequest("Cell", "custom", "Circle", "Ann", CircleSettings(listOf(PostType.Prayer)), key)
        val c = Canned(503, """{"ok":false,"error":{"code":"service_misconfigured","message":"Circles service is temporarily unavailable."},"requestId":"ray-123"}""", requestId = "ray-123")
        val failure = api(c).createCircle(request).failureOrNull
        assertEquals(key, JSONObject(c.sent).getString("requestId"))
        assertEquals("ray-123", failure?.requestId)
        assertEquals(503, failure?.httpStatus)
    }

    @Test fun `status codes map to kinds`() = runBlocking {
        suspend fun kind(status: Int) = api(Canned(status, """{"ok":false}""")).leave("c").failureOrNull?.kind
        assertEquals(FailureKind.SignedOut, kind(401))
        assertEquals(FailureKind.NotFound, kind(404))
        assertEquals(FailureKind.RateLimited, kind(429))
        assertEquals(FailureKind.Server, kind(500))
    }

    @Test fun `garbage from the server is a failure not a crash`() = runBlocking {
        assertEquals(FailureKind.Server, api(Canned(200, "<html>oops</html>")).leave("c").failureOrNull?.kind)
        assertEquals(FailureKind.Server, api(Canned(200, """{"ok":true}""")).join("GRACE-7K2Q", "A").failureOrNull?.kind) // no circleId
        assertEquals(FailureKind.Server, api(Canned(200, """{"ok":true}""")).createPost(CreatePostRequest("c", PostType.Testimony, "x")).failureOrNull?.kind)
    }

    @Test fun `offline is a friendly failure`() = runBlocking {
        val f = api(Canned(fail = IOException("no route"))).myCircles().failureOrNull
        assertEquals(FailureKind.Offline, f?.kind)
    }

    @Test fun `a sign-in failure stops before any request`() = runBlocking {
        val c = Canned()
        val r = api(c, token = CirclesFailure.signedOut.asResult()).leave("c1")
        assertEquals(FailureKind.SignedOut, r.failureOrNull?.kind)
        assertEquals(null, c.request)
    }

    @Test fun `join and celebrate bodies`() = runBlocking {
        val c = Canned(body = """{"ok":true,"circleId":"c7"}""")
        assertEquals("c7", api(c).join("GRACE-7K2Q", "Ann").okOrNull?.circleId)
        assertEquals("GRACE-7K2Q", JSONObject(c.sent).getString("code"))
        val c2 = Canned()
        api(c2).celebrate("c1", CelebrationKind.Birthday, "30!", "zuri")
        val j = JSONObject(c2.sent)
        assertEquals("birthday", j.getString("kind")); assertEquals("zuri", j.getString("companion"))
    }

    @Test fun `createPost with meta serializes meta object and omits when empty or null`() {
        val meta = PostMeta(
            sourceKind = "action_item",
            sourceId = "act-1",
            assigneeUid = "u-99",
            assigneeName = "Bob",
            dueText = "Friday",
            dueAt = 1731600000000L,
            priority = WorkPriority.High,
            blockedOn = "backend",
            decisionOwner = "Alice",
            meetingDate = "2026-11-14"
        )
        val reqWithMeta = CreatePostRequest("c1", PostType.Task, "Task body", meta = meta)
        val json = CirclesJson.createPost(reqWithMeta)
        assertTrue(json.has("meta"))
        val metaObj = json.getJSONObject("meta")
        assertEquals("action_item", metaObj.getString("sourceKind"))
        assertEquals("act-1", metaObj.getString("sourceId"))
        assertEquals("u-99", metaObj.getString("assigneeUid"))
        assertEquals("Bob", metaObj.getString("assigneeName"))
        assertEquals("Friday", metaObj.getString("dueText"))
        assertEquals(1731600000000L, metaObj.getLong("dueAt"))
        assertEquals("high", metaObj.getString("priority"))
        assertEquals("backend", metaObj.getString("blockedOn"))
        assertEquals("Alice", metaObj.getString("decisionOwner"))
        assertEquals("2026-11-14", metaObj.getString("meetingDate"))

        val reqEmptyMeta = CreatePostRequest("c1", PostType.Task, "Task body", meta = PostMeta())
        assertFalse(CirclesJson.createPost(reqEmptyMeta).has("meta"))

        val reqNullMeta = CreatePostRequest("c1", PostType.Task, "Task body", meta = null)
        assertFalse(CirclesJson.createPost(reqNullMeta).has("meta"))
    }

    @Test fun `setWorkStatus posts to endpoint with proper status wire mapping`() = runBlocking {
        val c = Canned()
        val r = api(c).setWorkStatus("c1", "p1", WorkStatus.InProgress)
        assertTrue(r is CirclesResult.Ok)
        assertEquals("https://circles.example.workers.dev/v1/setWorkStatus", c.request?.url.toString())
        val j = JSONObject(c.sent)
        assertEquals("c1", j.getString("circleId"))
        assertEquals("p1", j.getString("postId"))
        assertEquals("in_progress", j.getString("status"))

        api(c).setWorkStatus("c1", "p1", WorkStatus.Done)
        assertEquals("answered", JSONObject(c.sent).getString("status"))

        api(c).setWorkStatus("c1", "p1", WorkStatus.Open)
        assertEquals("open", JSONObject(c.sent).getString("status"))

        val notConnected = NotConfiguredCirclesApi.setWorkStatus("c1", "p1", WorkStatus.Open)
        assertEquals(FailureKind.NotConnected, notConnected.failureOrNull?.kind)
    }

    @Test fun `createPoll includes closesAt only when specified`() = runBlocking {
        val c = Canned()
        api(c).createPoll("c1", "Deadline?", listOf("Yes", "No"), multi = false, closesAt = 1731600000000L)
        assertEquals("https://circles.example.workers.dev/v1/createPoll", c.request?.url.toString())
        val jWithDeadline = JSONObject(c.sent)
        assertEquals(1731600000000L, jWithDeadline.getLong("closesAt"))

        api(c).createPoll("c1", "No deadline?", listOf("Yes", "No"), multi = false)
        val jNoDeadline = JSONObject(c.sent)
        assertFalse(jNoDeadline.has("closesAt"))
    }
}
