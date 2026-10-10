package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.flow.Flow

/** What the composer on the Chat tab sends. Time and author come from the server rules, not from here. */
sealed interface MessageDraft {
    data class Text(val text: String) : MessageDraft
    data class Reply(val text: String, val replyTo: String) : MessageDraft
    data class Card(val card: CardPayload) : MessageDraft
}

/**
 * Live reads and the client-side writes the rules allow (comments, chat, votes, slot claims).
 * Everything else goes through [CirclesApi]. Implemented over Firestore (offline cache comes from the
 * SDK) and faked in tests.
 */
interface CirclesData {
    fun observeCircle(circleId: String): Flow<DataState<Circle?>>
    fun observeMembers(circleId: String): Flow<DataState<List<Member>>>
    fun observePosts(circleId: String): Flow<DataState<List<Post>>>
    fun observePending(circleId: String): Flow<DataState<List<PendingPost>>>
    fun observeUpdates(circleId: String, postId: String): Flow<DataState<List<PostUpdate>>>
    fun observeComments(circleId: String, postId: String): Flow<DataState<List<Comment>>>
    fun observeMessages(circleId: String, limit: Int = 100): Flow<DataState<List<ChatMessage>>>
    fun observeMessageReactions(circleId: String, messageId: String, myUid: String): Flow<List<MessageReaction>>
    fun observePoll(circleId: String, pollId: String, myUid: String): Flow<DataState<PollState>>
    fun observeChain(circleId: String, chainId: String, myUid: String): Flow<DataState<ChainState>>

    /** My own "I prayed" marker (the only one rules let me read). False when unknown. */
    suspend fun hasPrayed(circleId: String, postId: String): Boolean

    suspend fun addComment(circleId: String, postId: String, uid: String, name: String, body: String, parentId: String?): CirclesResult<Unit>
    suspend fun deleteComment(circleId: String, postId: String, commentId: String): CirclesResult<Unit>
    suspend fun sendMessage(circleId: String, uid: String, name: String, draft: MessageDraft): CirclesResult<Unit>
    suspend fun editMessage(circleId: String, messageId: String, text: String): CirclesResult<Unit>
    suspend fun deleteMessage(circleId: String, messageId: String): CirclesResult<Unit>
    /** [emoji] null removes my reaction. */
    suspend fun setMessageReaction(circleId: String, messageId: String, uid: String, emoji: String?): CirclesResult<Unit>
    /** Empty [choices] removes my vote. */
    suspend fun vote(circleId: String, pollId: String, uid: String, choices: List<String>): CirclesResult<Unit>
    suspend fun claimSlot(circleId: String, chainId: String, uid: String, name: String, hour: Int): CirclesResult<Unit>
    suspend fun releaseSlot(circleId: String, chainId: String, hour: Int): CirclesResult<Unit>
}
