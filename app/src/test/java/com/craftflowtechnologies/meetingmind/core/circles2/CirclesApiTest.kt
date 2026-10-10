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
    private class Canned(val status: Int = 200, val body: String = """{"ok":true}""", val fail: IOException? = null) : Interceptor {
        var request: Request? = null
        var sent: String = ""
        override fun intercept(chain: Interceptor.Chain): Response {
            val r = chain.request()
            request = r
            sent = Buffer().also { b -> r.body?.writeTo(b) }.readUtf8()
            if (fail != null) throw fail
            return Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(status).message("x")
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
        assertEquals("c7", api(c).join("GRACE-7K2Q", "Ann").okOrNull)
        assertEquals("GRACE-7K2Q", JSONObject(c.sent).getString("code"))
        val c2 = Canned()
        api(c2).celebrate("c1", CelebrationKind.Birthday, "30!", "zuri")
        val j = JSONObject(c2.sent)
        assertEquals("birthday", j.getString("kind")); assertEquals("zuri", j.getString("companion"))
    }
}
