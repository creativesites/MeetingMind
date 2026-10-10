package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Test doubles. Nothing here touches the network or Firebase. */
class FakeAuth(var uid: String? = "me", var anonymous: Boolean = true) : CirclesAuth {
    override suspend fun ensureSignedIn(): CirclesResult<String> = uid?.let { CirclesResult.Ok(it) } ?: CirclesFailure.signedOut.asResult()
    override suspend fun idToken(): CirclesResult<String> = uid?.let { CirclesResult.Ok("token-$it") } ?: CirclesFailure.signedOut.asResult()
    override val currentUid: String? get() = uid
    override val isAnonymous: Boolean get() = anonymous
    override var linkedEmail: String? = null
    /** What the next [linkGoogle] returns, and what [switchToLinkedAccount] returns. */
    var linkResult: LinkResult = LinkResult.Linked("me@example.com")
    var switchResult: LinkResult = LinkResult.Linked("me@example.com")
    val authCalls = mutableListOf<String>()
    override suspend fun linkGoogle(activityContext: android.content.Context): LinkResult {
        authCalls += "link"
        (linkResult as? LinkResult.Linked)?.let { anonymous = false; linkedEmail = it.email }
        return linkResult
    }
    override suspend fun switchToLinkedAccount(): LinkResult {
        authCalls += "switch"
        (switchResult as? LinkResult.Linked)?.let { anonymous = false; linkedEmail = it.email; uid = "other-phone-uid" }
        return switchResult
    }
}

/** Records every call and replies from [nextFailure] or the defaults. */
class FakeApi(override val isConfigured: Boolean = true) : CirclesApi {
    val calls = mutableListOf<String>()
    var nextFailure: CirclesFailure? = null
    var createdPending = true
    var joinedCircleId = "c1"
    var alreadyPrayed = false
    var lastPost: CreatePostRequest? = null
    var lastJoinText: String? = null
    var lastCelebrate: Triple<CelebrationKind, String?, String?>? = null

    private fun <T> reply(name: String, ok: T): CirclesResult<T> {
        calls += name
        return nextFailure?.let { f -> nextFailure = null; CirclesResult.Err(f) } ?: CirclesResult.Ok(ok)
    }

    override suspend fun createCircle(req: CreateCircleRequest) = reply("createCircle", "new-circle")
    override suspend fun updateCircle(circleId: String, name: String?, vocab: String?, settings: CircleSettings?) = reply("updateCircle", Unit)
    override suspend fun myCircles() = reply("myCircles", myCirclesResult)
    override suspend fun createInvite(circleId: String, expiresInDays: Int, maxUses: Int) = reply("createInvite", Invite("GRACE-7K2Q", null, 50))
    override suspend fun revokeInvite(code: String) = reply("revokeInvite", Unit)
    override suspend fun join(pasted: String, displayName: String): CirclesResult<String> { lastJoinText = pasted; return reply("join", joinedCircleId) }
    override suspend fun leave(circleId: String) = reply("leave", Unit)
    override suspend fun removeMember(circleId: String, targetUid: String) = reply("removeMember", Unit)
    override suspend fun setRole(circleId: String, targetUid: String, role: Role) = reply("setRole", Unit)
    override suspend fun setMute(circleId: String, muted: Boolean) = reply("setMute", Unit)
    override suspend fun createPost(req: CreatePostRequest): CirclesResult<CreatedPost> { lastPost = req; return reply("createPost", CreatedPost("post-${calls.size}", createdPending)) }
    override suspend fun approvePost(circleId: String, postId: String) = reply("approvePost", Unit)
    override suspend fun rejectPost(circleId: String, postId: String) = reply("rejectPost", Unit)
    override suspend fun editPost(circleId: String, postId: String, body: String) = reply("editPost", Unit)
    override suspend fun addUpdate(circleId: String, postId: String, body: String) = reply("addUpdate", Unit)
    override suspend fun markAnswered(circleId: String, postId: String) = reply("markAnswered", Unit)
    override suspend fun deletePost(circleId: String, postId: String) = reply("deletePost", Unit)
    override suspend fun prayed(circleId: String, postId: String) = reply("prayed", alreadyPrayed)
    override suspend fun react(circleId: String, postId: String, kind: ReactionKind?) = reply("react", Unit)
    override suspend fun syncCounts(circleId: String, postId: String) = reply("syncCounts", Unit)
    override suspend fun report(circleId: String, postId: String, reason: String) = reply("report", Unit)
    override suspend fun reportMessage(circleId: String, messageId: String, reason: String) = reply("reportMessage", Unit)
    override suspend fun createPoll(circleId: String, question: String, options: List<String>, multi: Boolean) = reply("createPoll", Unit)
    override suspend fun closePoll(circleId: String, pollId: String) = reply("closePoll", Unit)
    override suspend fun startChain(circleId: String, title: String, postId: String?) = reply("startChain", Unit)
    override suspend fun celebrate(circleId: String, kind: CelebrationKind, text: String, companion: String?, postId: String?): CirclesResult<Unit> {
        lastCelebrate = Triple(kind, companion, postId); return reply("celebrate", Unit)
    }
    var lastCommentAsAuthor: Triple<String, String, String?>? = null
    override suspend fun commentAsAuthor(circleId: String, postId: String, body: String, parentId: String?): CirclesResult<Unit> {
        lastCommentAsAuthor = Triple(postId, body, parentId); return reply("commentAsAuthor", Unit)
    }
    var myCirclesResult: List<String> = emptyList()
    val registered = mutableListOf<String>()
    val unregistered = mutableListOf<String>()
    override suspend fun registerToken(token: String): CirclesResult<Unit> { registered += token; return reply("registerToken", Unit) }
    override suspend fun unregisterToken(token: String): CirclesResult<Unit> { unregistered += token; return reply("unregisterToken", Unit) }
}

class FakeData : CirclesData {
    val circle = MutableStateFlow<DataState<Circle?>>(DataState.Loading)
    val members = MutableStateFlow<DataState<List<Member>>>(DataState.Ready(emptyList()))
    val posts = MutableStateFlow<DataState<List<Post>>>(DataState.Ready(emptyList()))
    val pending = MutableStateFlow<DataState<List<PendingPost>>>(DataState.Ready(emptyList()))
    val messages = MutableStateFlow<DataState<List<ChatMessage>>>(DataState.Ready(emptyList()))
    val reactions = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val polls = MutableStateFlow<Map<String, PollState>>(emptyMap())

    var pendingObserved = 0
    val writes = mutableListOf<String>()
    var sentDrafts = mutableListOf<MessageDraft>()
    var lastVote: List<String>? = null
    var failNextWrite: CirclesFailure? = null

    private fun write(name: String): CirclesResult<Unit> {
        writes += name
        return failNextWrite?.let { f -> failNextWrite = null; CirclesResult.Err(f) } ?: CirclesResult.Ok(Unit)
    }

    override fun observeCircle(circleId: String) = circle
    override fun observeMembers(circleId: String) = members
    override fun observePosts(circleId: String) = posts
    override fun observePending(circleId: String): Flow<DataState<List<PendingPost>>> { pendingObserved++; return pending }
    override fun observeUpdates(circleId: String, postId: String) = flowOf<DataState<List<PostUpdate>>>(DataState.Ready(emptyList()))
    override fun observeComments(circleId: String, postId: String) = flowOf<DataState<List<Comment>>>(DataState.Ready(emptyList()))
    override fun observeMessages(circleId: String, limit: Int) = messages
    override fun observeMessageReactions(circleId: String, messageId: String, myUid: String): Flow<List<MessageReaction>> =
        reactions.map { CirclesParsing.reactionChips(it[messageId].orEmpty(), myUid) }
    override fun observePoll(circleId: String, pollId: String, myUid: String): Flow<DataState<PollState>> =
        polls.map { m -> m[pollId]?.let { DataState.Ready(it) } ?: DataState.Failed(CirclesFailure(FailureKind.NotFound, "gone")) }
    override fun observeChain(circleId: String, chainId: String, myUid: String): Flow<DataState<ChainState>> = flowOf(DataState.Loading)
    override suspend fun hasPrayed(circleId: String, postId: String) = false
    override suspend fun addComment(circleId: String, postId: String, uid: String, name: String, body: String, parentId: String?) = write("addComment")
    override suspend fun deleteComment(circleId: String, postId: String, commentId: String) = write("deleteComment")
    override suspend fun sendMessage(circleId: String, uid: String, name: String, draft: MessageDraft): CirclesResult<Unit> { sentDrafts += draft; return write("sendMessage") }
    override suspend fun editMessage(circleId: String, messageId: String, text: String) = write("editMessage:$text")
    override suspend fun deleteMessage(circleId: String, messageId: String) = write("deleteMessage")
    override suspend fun setMessageReaction(circleId: String, messageId: String, uid: String, emoji: String?) = write("react:$messageId:$emoji")
    override suspend fun vote(circleId: String, pollId: String, uid: String, choices: List<String>): CirclesResult<Unit> { lastVote = choices; return write("vote") }
    override suspend fun claimSlot(circleId: String, chainId: String, uid: String, name: String, hour: Int) = write("claim:$hour")
    override suspend fun releaseSlot(circleId: String, chainId: String, hour: Int) = write("release:$hour")
}

fun testCircle(approval: Boolean = true, types: List<PostType> = PostType.entries.filter { !it.adminOnly }) = Circle(
    "c1", "Tuesday Night", "small_group", "Cell group",
    CircleSettings(allowedTypes = types, prayerApproval = approval), memberCount = 3, ownerUid = "owner"
)

fun repoWith(api: FakeApi = FakeApi(), data: FakeData = FakeData(), auth: FakeAuth = FakeAuth(), store: LocalCircleStore = InMemoryLocalCircleStore(), push: PushRegistrar? = null) =
    CirclesRepository(api, data, auth, store, clock = { 1_700_000_000_000L }, push = push)
