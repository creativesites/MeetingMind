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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** D5 Worker package on the phone: external flag, decision status, invite preview, The Table's bring list. */
@RunWith(RobolectricTestRunner::class)
class CirclesD5Test {
    private class Canned(val status: Int = 200, val body: String = """{"ok":true}""") : Interceptor {
        var request: Request? = null
        var sent: String = ""
        override fun intercept(chain: Interceptor.Chain): Response {
            val r = chain.request()
            request = r
            sent = Buffer().also { b -> r.body?.writeTo(b) }.readUtf8()
            return Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(status).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun api(c: Canned) = HttpCirclesApi("https://circles.example.workers.dev/", { CirclesResult.Ok("tok") }, OkHttpClient.Builder().addInterceptor(c).build())

    // ---- parsing ----

    @Test fun `member - external is read, and off when missing or not true`() {
        assertTrue(CirclesParsing.member("u1", mapOf("displayName" to "Ruth", "role" to "member", "external" to true)).external)
        assertFalse(CirclesParsing.member("u2", mapOf("displayName" to "Ann", "role" to "member")).external)
        assertFalse(CirclesParsing.member("u3", mapOf("displayName" to "Bo", "external" to "yes")).external)
    }

    @Test fun `decision status - from() reads the wire values and nothing else`() {
        assertEquals(DecisionStatus.Confirmed, DecisionStatus.from("confirmed"))
        assertEquals(DecisionStatus.Replaced, DecisionStatus.from("replaced"))
        assertNull(DecisionStatus.from("maybe"))
        assertNull(DecisionStatus.from(null))
    }

    @Test fun `postMeta - decision status, replaces and replaced-by are read, and a decision with only a status is kept`() {
        val m = CirclesParsing.postMeta(mapOf("decisionStatus" to "replaced", "replacedByPostId" to "p2", "decisionOwner" to "Ann"))
        assertEquals(DecisionStatus.Replaced, m?.decisionStatus)
        assertEquals("p2", m?.replacedByPostId)
        assertEquals("Ann", m?.decisionOwner)
        assertEquals("p1", CirclesParsing.postMeta(mapOf("replacesPostId" to "p1"))?.replacesPostId)
        assertNull("an unknown status is no status", CirclesParsing.postMeta(mapOf("decisionStatus" to "maybe")))
    }

    @Test fun `event - the bring list is read, claims included, and malformed items are dropped`() {
        val e = CirclesParsing.event("e1", mapOf(
            "title" to "Dinner",
            "bringList" to listOf(
                mapOf("id" to "a", "text" to "Cake", "claimedByUid" to "ann", "claimedByName" to "Ann"),
                mapOf("id" to "b", "text" to "Plates"),
                mapOf("text" to "no id"),
                mapOf("id" to "c", "text" to ""),
            ),
        ))
        val list = e!!.bringList
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertEquals("ann", list[0].claimedByUid)
        assertTrue(list[0].claimed)
        assertEquals("Ann", list[0].claimedByName)
        assertFalse(list[1].claimed)
        assertTrue(CirclesParsing.event("e2", mapOf("title" to "Empty"))!!.bringList.isEmpty())
    }

    @Test fun `invitePreview - parsed from the Worker's fields, blank means none`() {
        assertEquals(
            InvitePreview("c1", "Client", "work", "client_engagement", 4, null, "Ruth"),
            CirclesParsing.invitePreview(mapOf("circleId" to "c1", "name" to "Client", "space" to "work", "template" to "client_engagement", "memberCount" to 4L, "coverPath" to "", "inviterName" to "Ruth")),
        )
        assertNull(CirclesParsing.invitePreview(mapOf("circleId" to "c1", "name" to "Client", "inviterName" to null))?.inviterName)
        assertNull(CirclesParsing.invitePreview(mapOf("circleId" to "c1")))
    }

    // ---- request bodies ----

    @Test fun `createPost - decision status and replaces are sent, and replacedByPostId never is`() {
        val meta = PostMeta(decisionStatus = DecisionStatus.Proposed, replacesPostId = "old1", replacedByPostId = "forged")
        val json = CirclesJson.createPost(CreatePostRequest("c1", PostType.Decision, "Ship", meta = meta)).getJSONObject("meta")
        assertEquals("proposed", json.getString("decisionStatus"))
        assertEquals("old1", json.getString("replacesPostId"))
        assertFalse(json.has("replacedByPostId"))
    }

    @Test fun `createPost - a meta with only a decision status is not empty, so it is sent`() {
        assertTrue(CirclesJson.createPost(CreatePostRequest("c1", PostType.Decision, "Ship", meta = PostMeta(decisionStatus = DecisionStatus.Confirmed))).has("meta"))
        assertTrue(PostMeta().isEmpty)
    }

    @Test fun `event - the draft's bring list goes up as the whole list of texts`() {
        val draft = EventDraft("Dinner", "", "", null, 1L, 2L, "UTC", bringList = listOf("Cake", "Plates"))
        val arr = CirclesJson.event("c1", draft).getJSONArray("bringList")
        assertEquals(listOf("Cake", "Plates"), (0 until arr.length()).map { arr.getString(it) })
        assertEquals(0, CirclesJson.event("c1", EventDraft("Dinner", "", "", null, 1L, 2L, "UTC")).getJSONArray("bringList").length())
    }

    // ---- HTTP ----

    @Test fun `setExternal posts the member, the flag and the circle to the Worker`() = runBlocking {
        val c = Canned()
        assertTrue(api(c).setExternal("c1", "ann", true) is CirclesResult.Ok)
        assertEquals("https://circles.example.workers.dev/v1/setExternal", c.request?.url.toString())
        val j = JSONObject(c.sent)
        assertEquals("c1", j.getString("circleId"))
        assertEquals("ann", j.getString("uid"))
        assertTrue(j.getBoolean("external"))
    }

    @Test fun `setDecisionStatus sends the post and the wire status`() = runBlocking {
        val c = Canned()
        assertTrue(api(c).setDecisionStatus("c1", "p1", DecisionStatus.Confirmed) is CirclesResult.Ok)
        assertEquals("https://circles.example.workers.dev/v1/setDecisionStatus", c.request?.url.toString())
        val j = JSONObject(c.sent)
        assertEquals("p1", j.getString("postId"))
        assertEquals("confirmed", j.getString("status"))
    }

    @Test fun `previewInvite maps the answer, and JSON nulls are none rather than the word null`() = runBlocking {
        val c = Canned(body = """{"ok":true,"circleId":"c1","name":"Client","space":"work","template":null,"memberCount":4,"coverPath":null,"inviterName":"Ruth"}""")
        val preview = (api(c).previewInvite("GRACE-7K2Q") as CirclesResult.Ok).value
        assertEquals(InvitePreview("c1", "Client", "work", null, 4, null, "Ruth"), preview)
        assertEquals("https://circles.example.workers.dev/v1/previewInvite", c.request?.url.toString())
        assertEquals("GRACE-7K2Q", JSONObject(c.sent).getString("code"))
    }

    @Test fun `previewInvite - an expired code shows the Worker's plain message, and an answer with no name is a server failure`() = runBlocking {
        val expired = Canned(status = 410, body = """{"ok":false,"error":{"code":"expired","message":"That code has expired. Ask for a new one."}}""")
        val f = api(expired).previewInvite("GRACE-7K2Q").failureOrNull
        assertEquals("expired", f?.code)
        assertEquals("That code has expired. Ask for a new one.", f?.message)
        assertEquals(FailureKind.Rejected, f?.kind)
        assertEquals(FailureKind.Server, api(Canned(body = """{"ok":true}""")).previewInvite("GRACE-7K2Q").failureOrNull?.kind)
    }

    @Test fun `claimBringItem sends the event, the item and the claim flag`() = runBlocking {
        val c = Canned()
        assertTrue(api(c).claimBringItem("c1", "e1", "i1", false) is CirclesResult.Ok)
        assertEquals("https://circles.example.workers.dev/v1/claimBringItem", c.request?.url.toString())
        val j = JSONObject(c.sent)
        assertEquals("e1", j.getString("eventId"))
        assertEquals("i1", j.getString("itemId"))
        assertFalse(j.getBoolean("claim"))
    }

    // ---- repository ----

    /** Records the four new calls and leaves every other call to the shared fake. */
    private class RecordingApi : CirclesApi by FakeApi() {
        val previews = mutableListOf<String>()
        val statuses = mutableListOf<Pair<String, DecisionStatus>>()
        val claims = mutableListOf<Pair<String, Boolean>>()
        override suspend fun previewInvite(code: String): CirclesResult<InvitePreview> {
            previews += code
            return CirclesResult.Ok(InvitePreview("c1", "Client", "work", null, 4, null, "Ruth"))
        }
        override suspend fun setDecisionStatus(circleId: String, postId: String, status: DecisionStatus): CirclesResult<Unit> {
            statuses += postId to status
            return CirclesResult.Ok(Unit)
        }
        override suspend fun claimBringItem(circleId: String, eventId: String, itemId: String, claim: Boolean): CirclesResult<Unit> {
            claims += itemId to claim
            return CirclesResult.Ok(Unit)
        }
    }

    private fun repo(api: CirclesApi, uid: String? = "me") =
        CirclesRepository(api, FakeData(), FakeAuth(uid = uid), InMemoryLocalCircleStore(), clock = { 1L })

    @Test fun `previewInvite finds the code in a pasted message and sends only the code`() = runBlocking {
        val api = RecordingApi()
        val r = repo(api).previewInvite("Come on in! GRACE-7K2Q thanks")
        assertEquals(listOf("GRACE-7K2Q"), api.previews)
        assertEquals("Client", r.okOrNull?.name)
    }

    @Test fun `previewInvite refuses garbage inline and signed-out callers, without reaching the Worker`() = runBlocking {
        val api = RecordingApi()
        assertEquals("bad_code", repo(api).previewInvite("hello there").failureOrNull?.code)
        assertEquals(FailureKind.SignedOut, repo(api, uid = null).previewInvite("GRACE-7K2Q").failureOrNull?.kind)
        assertTrue(api.previews.isEmpty())
    }

    @Test fun `setDecisionStatus and claimBringItem reach the Worker, and an unconfigured build says so`() = runBlocking {
        val api = RecordingApi()
        val r = repo(api)
        assertTrue(r.setDecisionStatus("c1", "p1", DecisionStatus.Replaced) is CirclesResult.Ok)
        assertEquals(listOf("p1" to DecisionStatus.Replaced), api.statuses)
        assertTrue(r.claimBringItem("c1", "e1", "i1", true) is CirclesResult.Ok)
        assertEquals(listOf("i1" to true), api.claims)
        val offline = repo(FakeApi(isConfigured = false))
        assertEquals(FailureKind.NotConnected, offline.claimBringItem("c1", "e1", "i1", true).failureOrNull?.kind)
        assertEquals(FailureKind.NotConnected, offline.setExternal("c1", "ann", true).failureOrNull?.kind)
        assertEquals(FailureKind.NotConnected, offline.previewInvite("GRACE-7K2Q").failureOrNull?.kind)
    }
}
