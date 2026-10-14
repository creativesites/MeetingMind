package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.circles2.*
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light and dark screenshots of the Circles screens, on sample data that never leaves the tests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi", sdk = [34])
class CirclesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        val dark = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                MeetMindTheme(darkTheme = dark.value) { content() }
            }
        }
        for (d in listOf(false, true)) {
            dark.value = d
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/circles_${name}_${if (d) "dark" else "light"}.png")
        }
    }

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    // ---------- list ----------

    private val noJoin = JoinUiState()
    private fun list(state: CirclesListState) = @Composable {
        CirclesListContent(state, noJoin, false, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun listNotConnected() = shot("list_not_connected", list(CirclesListState.NotConnected))
    @Test fun listEmpty() = shot("list_empty", list(CirclesListState.Ready(emptyList())))
    @Test fun listPopulated() = shot("list_populated", list(CirclesListState.Ready(listOf(
        testCircle(), testCircle().copy(id = "c2", name = "Sunday Youth", vocab = "Youth", memberCount = 24),
        testCircle().copy(id = "c3", name = "The Okafors", vocab = "Family", memberCount = 1)
    ))))
    @Test fun listOffline() = shot("list_error", list(CirclesListState.Ready(listOf(testCircle()), CirclesFailure.offline)))

    // ---------- create ----------

    @Test fun create() = shot("create", {
        CreateCircleContent(
            CreateUiState(name = "Tuesday Night", displayName = "Ann"), {}, {}, {}, {}, {}, {}, {}, {}, {}
        )
    })
    @Test fun createError() = shot("create_error", {
        CreateCircleContent(CreateUiState(name = "", error = "Give your cell group a name."), {}, {}, {}, {}, {}, {}, {}, {}, {})
    })

    // ---------- circle home ----------

    private val members = listOf(
        Member("owner", "Olive Adeyemi", Role.Owner), Member("me", "Ann Mensah", Role.Admin),
        Member("u3", "Ben Carter", Role.Member), Member("u4", "Chidi Okafor", Role.Member, muted = true)
    )
    private fun post(id: String, type: PostType, body: String, name: String? = "Ben Carter", anonymous: Boolean = false, prayed: Int = 0, comments: Int = 0, answered: Boolean = false, verse: String? = null, ageMin: Long = 30, reactions: Map<ReactionKind, Int> = emptyMap(), updates: Int = 0) =
        Post(id, type, body, verse, anonymous, answered, authorUid = if (anonymous) null else "u3", authorName = if (anonymous) null else name,
            counts = PostCounts(prayed, comments, updates, reactions), createdAt = now - ageMin * minute)

    private val feedPosts = listOf(
        FeedPost(post("1", PostType.Prayer, "Please pray for my mum's surgery on Thursday. She is nervous and so am I.", anonymous = true, prayed = 7, comments = 3, verse = "Psalm 46:1"), mine = false, prayedByMe = true),
        FeedPost(post("2", PostType.Prayer, "Thank you all. The surgery went well and she is home resting.", name = "Ann Mensah", prayed = 12, answered = true, ageMin = 600, updates = 2), mine = true, prayedByMe = false),
        FeedPost(post("3", PostType.Testimony, "I got the job after eight months of searching. God was faithful the whole way.", reactions = mapOf(ReactionKind.Celebrate to 5, ReactionKind.Amen to 2), ageMin = 1500), mine = false, prayedByMe = false),
        FeedPost(post("4", PostType.Achievement, "Finished the 30-day devotional plan today.", name = "Chidi Okafor", ageMin = 2900, reactions = mapOf(ReactionKind.Heart to 3)), mine = false, prayedByMe = false),
        FeedPost(post("5", PostType.Study, "Romans 8: where do you see 'no condemnation' showing up in your week?", comments = 4, ageMin = 4000), mine = false, prayedByMe = false),
        FeedPost(post("6", PostType.Encouragement, "You are doing better than you think. Keep going.", name = "Olive Adeyemi", ageMin = 5000), mine = false, prayedByMe = false)
    )

    private fun homeState(admin: Boolean = true, posts: List<FeedPost> = feedPosts, pending: List<PendingPost> = emptyList(), myPending: Int = 0) = CircleHomeUiState(
        loading = false, circle = testCircle(), me = if (admin) members[1] else members[2], members = members, posts = posts, pending = pending, myPendingCount = myPending
    )

    private fun home(state: CircleHomeUiState, chat: ChatUiState = ChatUiState(loading = false, myUid = "me", myName = "Ann Mensah"), tab: HomeTab = HomeTab.Feed, chatActions: ChatActions = ChatActions.None) = @Composable {
        CircleHomeContent(state, chat, "", tab, {}, now, FeedActions.None, chatActions, MemberActions.None, SnackbarHostState(), {}, {}, {}, {})
    }

    @Test fun feed() = shot("feed", home(homeState()))
    @Test fun feedMember() = shot("feed_member_waiting", home(homeState(admin = false, myPending = 1)))
    @Test fun feedEmpty() = shot("feed_empty", home(homeState(posts = emptyList())))
    @Test fun feedAdminPending() = shot("feed_admin_pending", home(homeState(posts = feedPosts.take(2), pending = listOf(
        PendingPost("pp1", PostType.Prayer, "Pray for my marriage. We are going through a hard season.", "Ecclesiastes 4:9", true, now - 5 * minute),
        PendingPost("pp2", PostType.Prayer, "Safe travel for the mission team next week.", null, false, now - 90 * minute)
    ))))
    @Test fun members() = shot("members", home(homeState(), tab = HomeTab.Members))
    @Test fun gone() = shot("gone", { CircleHomeContent(CircleHomeUiState(loading = false, gone = true), ChatUiState(), "", HomeTab.Feed, {}, now, FeedActions.None, ChatActions.None, MemberActions.None, SnackbarHostState(), {}, {}, {}, {}) })

    // ---------- chat ----------

    private val poll = Poll("pl1", "Which night for Bible study?", listOf(PollOption("o0", "Tuesday"), PollOption("o1", "Thursday"), PollOption("o2", "Saturday morning")), multi = false, closed = false, createdBy = "u3")
    private val pollState = PollState(poll, mapOf("u3" to listOf("o0"), "owner" to listOf("o1"), "u4" to listOf("o1"), "me" to listOf("o1")), "me")
    private val chain = Chain("ch1", "For Sam's surgery tomorrow", "1", startsAt = now - 5 * 3_600_000L, endsAt = now + 19 * 3_600_000L, hours = 24, createdByName = "Olive")
    private val chainState = ChainState(chain, (0..23).filter { it in listOf(0, 1, 2, 5, 6, 9, 12, 13, 14, 20) }.map { Slot(it, if (it == 5) "me" else "u$it", if (it == 5) "Ann" else "Ben") }, "me", now)

    private val chatMessages = listOf(
        ChatMessage("m1", "owner", "Olive Adeyemi", MessageKind.Text, "Good evening everyone. Are we still on for this week?", createdAt = now - 120 * minute),
        ChatMessage("m2", "me", "Ann Mensah", MessageKind.Reply, "Yes! I'll bring snacks.", replyTo = "m1", createdAt = now - 110 * minute),
        ChatMessage("m3", "u3", "Ben Carter", MessageKind.Poll, "Which night for Bible study?", pollId = "pl1", createdAt = now - 100 * minute),
        ChatMessage("m4", "u4", "Chidi Okafor", MessageKind.Celebration, "Thirty years young!", celebration = "birthday", companion = "zuri", createdAt = now - 60 * minute),
        ChatMessage("m5", "owner", "Olive Adeyemi", MessageKind.Chain, "For Sam's surgery tomorrow", chainId = "ch1", createdAt = now - 40 * minute),
        ChatMessage("m6", "u3", "Ben Carter", MessageKind.Card, "", card = CardPayload("gold", "Be still, and know that I am God.", "calm"), createdAt = now - 20 * minute),
        ChatMessage("m7", "u4", "Chidi Okafor", MessageKind.Text, "", createdAt = now - 10 * minute, deleted = true),
        ChatMessage("m8", "me", "Ann Mensah", MessageKind.Text, "Praying for Sam tonight 🙏", createdAt = now - 2 * minute)
    )

    private val funActions = ChatActions(
        pollFor = { flowOf(DataState.Ready(pollState)) },
        chainFor = { flowOf(DataState.Ready(chainState)) },
        reactionsFor = { id -> flowOf(if (id == "m1") listOf(MessageReaction("🙏", 3, true), MessageReaction("❤️", 1, false)) else emptyList()) }
    )

    @Test fun chatEmpty() = shot("chat_empty", home(homeState(), ChatUiState(loading = false, myUid = "me"), HomeTab.Chat))
    @Test fun chat() = shot("chat_poll_celebration", home(homeState(), ChatUiState(loading = false, messages = chatMessages, myUid = "me", myName = "Ann Mensah"), HomeTab.Chat, funActions))
    @Test fun chatReplying() = shot("chat_replying", home(homeState(), ChatUiState(loading = false, messages = chatMessages.take(2), myUid = "me", replyTo = chatMessages[0]), HomeTab.Chat, funActions))

    // ---------- settings ----------

    @Test fun settingsAdmin() = shot("settings_admin", { CircleSettingsContent(testCircle(), members[1], {}, { _, _, _ -> }, {}, {}) })
    @Test fun settingsMember() = shot("settings_member", { CircleSettingsContent(testCircle(), members[2], {}, { _, _, _ -> }, {}, {}) })
}
