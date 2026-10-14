package com.craftflowtechnologies.meetingmind.feature.circles2

import com.craftflowtechnologies.meetingmind.core.circles2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** View-model logic against fakes. No network, no Firebase. */
@OptIn(ExperimentalCoroutinesApi::class)
class CircleViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val me = Member("me", "Me", Role.Member)
    private val admin = Member("me", "Me", Role.Admin)

    private class Rig(val api: FakeApi, val data: FakeData, val repo: CirclesRepository, val vm: CircleHomeViewModel)

    private fun TestScope.rig(role: Member = me, approval: Boolean = true, api: FakeApi = FakeApi(), data: FakeData = FakeData(), store: LocalCircleStore = InMemoryLocalCircleStore()): Rig {
        data.circle.value = DataState.Ready(testCircle(approval))
        data.members.value = DataState.Ready(listOf(role, Member("owner", "Olive", Role.Owner)))
        val repo = repoWith(api, data, store = store)
        val vm = CircleHomeViewModel("c1", repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return Rig(api, data, repo, vm)
    }

    // ---------- posting and anonymity ----------

    @Test fun `an anonymous prayer sends only the flag, never a uid or a name`() = runTest {
        val r = rig()
        r.vm.openComposer(PostType.Prayer)
        r.vm.setBody("Please pray for my exam"); r.vm.setAnonymous(true)
        r.vm.submitPost()
        val sent = r.api.lastPost!!
        assertTrue(sent.anonymous)
        assertEquals(PostType.Prayer, sent.type)
        // The payload type has no field that could carry identity.
        val fields = CreatePostRequest::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.none { "uid" in it || "author" in it || "name" in it || "user" in it })
        assertFalse(sent.toString().contains("me"))
    }

    @Test fun `only prayer requests can be anonymous`() = runTest {
        val r = rig()
        r.vm.openComposer(PostType.Testimony)
        r.vm.setAnonymous(true)
        assertFalse(r.vm.composer.value.anonymous)
        r.vm.setBody("God healed me"); r.vm.submitPost()
        assertFalse(r.api.lastPost!!.anonymous)
    }

    @Test fun `with approval on a prayer is sent for approval and the person is told`() = runTest {
        val r = rig(approval = true)
        val notes = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { r.vm.messages.collect { notes += it } }
        r.api.createdPending = true
        r.vm.openComposer(PostType.Prayer); r.vm.setBody("Pray"); r.vm.submitPost()
        assertFalse(r.vm.composer.value.open)
        assertEquals(1, r.vm.state.value.myPendingCount)
        assertTrue(notes.single().contains("approve"))
        assertTrue(r.repo.store.myPostIds("c1").isNotEmpty())
    }

    @Test fun `with approval off a prayer is simply posted`() = runTest {
        val r = rig(approval = false)
        val notes = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { r.vm.messages.collect { notes += it } }
        r.api.createdPending = false
        r.vm.openComposer(PostType.Prayer); r.vm.setBody("Pray"); r.vm.submitPost()
        assertEquals(0, r.vm.state.value.myPendingCount)
        assertEquals(listOf("Posted."), notes)
    }

    @Test fun `empty text is refused locally and a server error keeps the draft`() = runTest {
        val r = rig()
        r.vm.openComposer(PostType.Encouragement)
        r.vm.setBody("   "); r.vm.submitPost()
        assertNotNull(r.vm.composer.value.error); assertTrue(r.api.calls.isEmpty())
        r.vm.setBody("Hang in there")
        r.api.nextFailure = CirclesFailure(FailureKind.RateLimited, "Slow down.")
        r.vm.submitPost()
        val c = r.vm.composer.value
        assertTrue(c.open); assertEquals("Hang in there", c.body); assertEquals("Slow down.", c.error); assertFalse(c.sending)
    }

    @Test fun `types the circle does not use cannot be opened`() = runTest {
        val r = rig()
        r.data.circle.value = DataState.Ready(testCircle(types = listOf(PostType.Prayer)))
        r.vm.openComposer(PostType.Study)
        assertFalse(r.vm.composer.value.open)
    }

    @Test fun `my own anonymous post is recognised as mine only on this phone`() = runTest {
        val r = rig(store = InMemoryLocalCircleStore().also { it.addMyPost("c1", "p1") })
        r.data.posts.value = DataState.Ready(listOf(Post("p1", PostType.Prayer, "x", anonymous = true), Post("p2", PostType.Prayer, "y", anonymous = true)))
        val feed = r.vm.state.value.posts
        assertTrue(feed.first { it.post.id == "p1" }.mine); assertFalse(feed.first { it.post.id == "p2" }.mine)
        assertNull(feed[0].post.authorUid)
    }

    @Test fun `testimony from an anonymous answered request starts blank`() = runTest {
        val r = rig()
        r.vm.startTestimonyFrom(Post("p", PostType.Prayer, "secret words", anonymous = true, answered = true))
        assertEquals("", r.vm.composer.value.body); assertEquals(PostType.Testimony, r.vm.composer.value.type)
        r.vm.startTestimonyFrom(Post("p", PostType.Prayer, "open words", anonymous = false, answered = true))
        assertTrue(r.vm.composer.value.body.contains("open words"))
    }

    @Test fun `celebrating an anonymous answered request is never sent`() = runTest {
        val r = rig()
        r.vm.celebrateAnswered(Post("p", PostType.Prayer, "x", anonymous = true, answered = true), "zuri")
        assertTrue(r.api.calls.none { it == "celebrate" })
        r.vm.celebrateAnswered(Post("p2", PostType.Prayer, "x", answered = true), "zuri")
        assertEquals(Triple(CelebrationKind.Answered, "zuri", "p2"), r.api.lastCelebrate)
    }

    // ---------- admin pending queue ----------

    @Test fun `only admins listen to the pending queue`() = runTest {
        val member = rig(role = me)
        assertEquals(0, member.data.pendingObserved)
        assertTrue(member.vm.state.value.pending.isEmpty())
        val data = FakeData().also { it.pending.value = DataState.Ready(listOf(PendingPost("pp", PostType.Prayer, "wait", null, true, 1L))) }
        val adm = rig(role = admin, data = data)
        assertEquals(1, adm.vm.state.value.pending.size)
        assertTrue(adm.vm.state.value.isAdmin)
        adm.vm.approve(adm.vm.state.value.pending.single())
        adm.vm.reject(adm.vm.state.value.pending.single())
        assertEquals(listOf("approvePost", "rejectPost"), adm.api.calls)
    }

    // ---------- praying ----------

    @Test fun `I prayed is optimistic and reverts on error`() = runTest {
        val r = rig()
        val post = Post("p1", PostType.Prayer, "x")
        r.data.posts.value = DataState.Ready(listOf(post))
        r.vm.pray(post)
        assertTrue(r.vm.state.value.posts.single().prayedByMe)
        assertTrue(r.repo.store.prayedIds("c1").contains("p1"))
        val post2 = Post("p2", PostType.Prayer, "y")
        r.data.posts.value = DataState.Ready(listOf(post, post2))
        r.api.nextFailure = CirclesFailure.offline
        r.vm.pray(post2)
        assertFalse(r.vm.state.value.posts.first { it.post.id == "p2" }.prayedByMe)
    }

    @Test fun `removed from the circle shows the gone state`() = runTest {
        val r = rig()
        r.data.circle.value = DataState.Ready(null)
        assertTrue(r.vm.state.value.gone)
        r.data.circle.value = DataState.Failed(CirclesFailure(FailureKind.Rejected, "no", "permission_denied"))
        assertTrue(r.vm.state.value.gone)
    }

    // ---------- list and join ----------

    @Test fun `joining with garbage shows an inline message and calls nothing`() = runTest {
        val api = FakeApi()
        val vm = CirclesListViewModel(repoWith(api))
        vm.onPaste("🙏 lol no code here"); vm.onJoinName("Ann")
        var opened: String? = null
        vm.join { opened = it }
        assertNotNull(vm.join.value.error); assertNull(opened); assertTrue(api.calls.none { it == "join" })
    }

    @Test fun `joining with a whole pasted message sends the clean code`() = runTest {
        val api = FakeApi().also { it.joinedCircleId = "c9" }
        val repo = repoWith(api)
        val vm = CirclesListViewModel(repo)
        vm.onPaste("Hi! Join our Cell group.\nCode: grace-7k2q\nsee you Tuesday 🙏"); vm.onJoinName("Ann")
        assertEquals("GRACE-7K2Q", vm.join.value.code)
        var opened: String? = null
        vm.join { opened = it }
        assertEquals("c9", opened); assertEquals("GRACE-7K2Q", api.lastJoinText)
        assertEquals(listOf("c9"), repo.store.circleIds())
    }

    @Test fun `join failures become messages`() = runTest {
        val api = FakeApi()
        val vm = CirclesListViewModel(repoWith(api))
        api.nextFailure = CirclesFailure(FailureKind.Rejected, "That code has expired. Ask for a new one.", "expired")
        vm.onPaste("GRACE-7K2Q"); vm.join { fail("should not open") }
        assertEquals("That code has expired. Ask for a new one.", vm.join.value.error); assertFalse(vm.join.value.busy)
    }

    @Test fun `circles isn't connected when the api has no url`() = runTest {
        val repo = repoWith(FakeApi(isConfigured = false))
        val vm = CirclesListViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.list.collect { } }
        assertEquals(CirclesListState.NotConnected, vm.list.value)
        vm.onPaste("GRACE-7K2Q"); var opened = false
        vm.join { opened = true }
        assertFalse(opened)
        assertEquals("Circles isn't connected yet.", vm.join.value.error)
    }

    @Test fun `the list shows my circles and forgets ones I was removed from`() = runTest {
        val api = FakeApi(); val data = FakeData()
        val store = InMemoryLocalCircleStore().also { it.addCircle("c1") }
        val repo = repoWith(api, data, store = store)
        data.circle.value = DataState.Ready(testCircle())
        val vm = CirclesListViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.list.collect { } }
        assertEquals(listOf("Tuesday Night"), (vm.list.value as CirclesListState.Ready).circles.map { it.name })
        data.circle.value = DataState.Ready(null)
        assertTrue(store.circleIds().isEmpty())
    }

    @Test fun `create validates then calls the api with the chosen settings`() = runTest {
        val api = FakeApi()
        val vm = CreateCircleViewModel(repoWith(api))
        var created: String? = null
        vm.create { created = it }
        assertNotNull(vm.state.value.error); assertNull(created)
        vm.setName("Tuesday Night"); vm.setDisplayName("Ann")
        vm.pickTemplate(CircleTemplate.byId("prayer_partners"))
        assertEquals(listOf(PostType.Prayer, PostType.Encouragement), vm.state.value.types)
        assertEquals("Prayer partners", vm.state.value.vocab)
        vm.setApproval(false)
        vm.create { created = it }
        assertEquals("new-circle", created); assertEquals(listOf("createCircle"), api.calls)
    }

    // ---------- chat ----------

    private fun TestScope.chat(api: FakeApi = FakeApi(), data: FakeData = FakeData(), store: LocalCircleStore = InMemoryLocalCircleStore()): Triple<ChatViewModel, FakeData, FakeApi> {
        val vm = ChatViewModel("c1", repoWith(api, data, store = store))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return Triple(vm, data, api)
    }

    private fun msg(id: String, uid: String = "other", at: Long = 10, text: String = "hi", kind: MessageKind = MessageKind.Text) =
        ChatMessage(id, uid, uid.replaceFirstChar { it.uppercase() }, kind, text, createdAt = at)

    @Test fun `sending a message writes it and clears the draft, and a failure puts the words back`() = runTest {
        val (vm, data, _) = chat()
        vm.setDraft("Hello circle"); vm.send()
        assertEquals(listOf<MessageDraft>(MessageDraft.Text("Hello circle")), data.sentDrafts); assertEquals("", vm.draft.value)
        data.failNextWrite = CirclesFailure.offline
        vm.setDraft("Second"); vm.send()
        assertEquals("Second", vm.draft.value)
    }

    @Test fun `replying sends a reply with the original id`() = runTest {
        val (vm, data, _) = chat()
        data.messages.value = DataState.Ready(listOf(msg("m1")))
        vm.startReply(msg("m1")); vm.setDraft("Amen"); vm.send()
        assertEquals(MessageDraft.Reply("Amen", "m1"), data.sentDrafts.single())
        assertNull(vm.state.value.replyTo)
    }

    @Test fun `editing updates text and only text messages can be edited`() = runTest {
        val (vm, data, _) = chat()
        vm.startEdit(msg("m1", "me", text = "typo")); assertEquals("typo", vm.draft.value)
        vm.setDraft("fixed"); vm.send()
        assertEquals(listOf("editMessage:fixed"), data.writes)
        vm.startEdit(msg("m2", "me", kind = MessageKind.Poll)); assertNull(vm.state.value.editing)
    }

    @Test fun `unread counts messages from others after the last read`() = runTest {
        val store = InMemoryLocalCircleStore().also { it.setLastRead("c1", 10) }
        val (vm, data, _) = chat(store = store)
        data.messages.value = DataState.Ready(listOf(msg("a", at = 5), msg("b", at = 20), msg("c", "me", at = 30), msg("d", at = 40)))
        assertEquals(2, vm.state.value.unread)
        vm.markRead()
        assertEquals(0, vm.state.value.unread); assertEquals(40L, store.lastRead("c1"))
    }

    private fun pollState(multi: Boolean, mine: List<String> = emptyList(), closed: Boolean = false) = PollState(
        Poll("pl", "Night?", listOf(PollOption("o0", "Tue"), PollOption("o1", "Thu")), multi, closed, "x"), if (mine.isEmpty()) emptyMap() else mapOf("me" to mine), "me"
    )

    @Test fun `single choice polls replace, toggle off, and closed polls ignore taps`() = runTest {
        val (vm, data, _) = chat()
        vm.vote(pollState(false), "o0"); assertEquals(listOf("o0"), data.lastVote)
        vm.vote(pollState(false, listOf("o0")), "o1"); assertEquals(listOf("o1"), data.lastVote)
        vm.vote(pollState(false, listOf("o0")), "o0"); assertEquals(emptyList<String>(), data.lastVote)
        data.lastVote = null
        vm.vote(pollState(false, closed = true), "o0"); assertNull(data.lastVote)
    }

    @Test fun `multi choice polls toggle options`() = runTest {
        val (vm, data, _) = chat()
        vm.vote(pollState(true, listOf("o0")), "o1"); assertEquals(listOf("o0", "o1"), data.lastVote)
        vm.vote(pollState(true, listOf("o0", "o1")), "o0"); assertEquals(listOf("o1"), data.lastVote)
    }

    @Test fun `a poll needs a question and two different options`() = runTest {
        val (vm, _, api) = chat()
        vm.openPollDraft(); vm.setPollQuestion("Night?"); vm.setPollOption(0, "Tue")
        vm.createPoll(); assertNotNull(vm.pollDraft.value?.error); assertTrue(api.calls.isEmpty())
        vm.setPollOption(1, "Thu"); vm.addPollOption(); vm.setPollMulti(true)
        assertEquals(listOf("Tue", "Thu"), vm.pollDraft.value?.cleanOptions)
        vm.createPoll()
        assertEquals(listOf("createPoll"), api.calls); assertNull(vm.pollDraft.value)
    }

    @Test fun `reactions toggle off when already mine`() = runTest {
        val (vm, data, _) = chat()
        vm.toggleReaction("m1", "🙏", listOf(MessageReaction("🙏", 2, mine = true)))
        vm.toggleReaction("m1", "❤️", listOf(MessageReaction("🙏", 2, mine = true)))
        assertEquals(listOf("react:m1:null", "react:m1:❤️"), data.writes)
    }

    @Test fun `celebrations only send known companions`() = runTest {
        val (vm, _, api) = chat()
        vm.celebrate(CelebrationKind.Birthday, " 30! ", "zuri"); assertEquals(Triple(CelebrationKind.Birthday, "zuri", null), api.lastCelebrate)
        vm.celebrate(CelebrationKind.Streak, "", "dragon"); assertEquals(Triple(CelebrationKind.Streak, null, null), api.lastCelebrate)
    }

    @Test fun `shared cards go out as template id, text and mood`() = runTest {
        val (vm, data, _) = chat()
        vm.shareCard(CardPayload("gold", "Be still", "calm"))
        assertEquals(MessageDraft.Card(CardPayload("gold", "Be still", "calm")), data.sentDrafts.single())
    }

    @Test fun `signed out writes fail with a message instead of crashing`() = runTest {
        val auth = FakeAuth(uid = null)
        val vm = ChatViewModel("c1", CirclesRepository(FakeApi(), FakeData(), auth, InMemoryLocalCircleStore()))
        val notes = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.collect { notes += it } }
        vm.setDraft("hello"); vm.send()
        assertEquals("hello", vm.draft.value); assertTrue(notes.isNotEmpty())
    }
}
