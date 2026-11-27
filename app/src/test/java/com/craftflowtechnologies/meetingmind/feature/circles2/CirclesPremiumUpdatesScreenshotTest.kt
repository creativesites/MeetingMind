package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.ChecklistItem
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.DecisionStatus
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostCounts
import com.craftflowtechnologies.meetingmind.core.circles2.PostMeta
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.WorkPriority
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.craftflowtechnologies.meetingmind.feature.circles2.PremiumSamples.MINUTE
import com.craftflowtechnologies.meetingmind.feature.circles2.PremiumSamples.NOW

/** Batch 2: the circle page (header, dock, Home), Updates, the post card and Create post. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi", sdk = [34])
class CirclesPremiumUpdatesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val members = PremiumSamples.members
    private val photoPath = "circles/c1/posts/p7/sunset.jpg"

    private fun post(
        id: String, type: PostType, body: String, author: String? = "Ben Carter", uid: String? = "u3", ago: Long = 30,
        prayed: Int = 0, comments: Int = 0, reactions: Map<ReactionKind, Int> = emptyMap(), answered: Boolean = false,
        verse: String? = null, meta: PostMeta? = null, attachments: List<MediaAttachment> = emptyList(), anonymous: Boolean = false, status: String? = null
    ) = Post(
        id, type, body, verse, anonymous, answered, authorUid = if (anonymous) null else uid, authorName = if (anonymous) null else author,
        counts = PostCounts(prayed, comments, 0, reactions), createdAt = NOW - ago * MINUTE, meta = meta, attachments = attachments,
        status = status ?: if (answered) "answered" else "open"
    )

    private val faithPosts = listOf(
        FeedPost(post("1", PostType.Prayer, "Please pray for my mum's surgery on Thursday. She is nervous and so am I.", anonymous = true, prayed = 7, comments = 3), mine = false, prayedByMe = true),
        FeedPost(post("2", PostType.Testimony, "I got the job after eight months of searching. God was faithful the whole way.", reactions = mapOf(ReactionKind.Celebrate to 5, ReactionKind.Heart to 2), comments = 4, ago = 600), mine = false, prayedByMe = false, myReaction = ReactionKind.Celebrate),
        FeedPost(post("3", PostType.Note, "Supper this week is at the Okafors'. Bring a dish if you can, and a friend.", author = "Olive Adeyemi", uid = "owner", ago = 1500,
            meta = PostMeta(tags = listOf("gathering", "supper"), checklist = listOf(ChecklistItem("a", "Rice", true), ChecklistItem("b", "Salad"), ChecklistItem("c", "Drinks")))), mine = false, prayedByMe = false, saved = true),
        FeedPost(post("4", PostType.Encouragement, "You are doing better than you think. Keep going.", author = "Ann Mensah", uid = "me", ago = 3000, verse = "Galatians 6:9"), mine = true, prayedByMe = false)
    )

    private val workPosts = listOf(
        FeedPost(post("w1", PostType.Decision, "We launch on the 1st with the Android app first; iOS follows in two weeks.", author = "Olive Adeyemi", uid = "owner",
            meta = PostMeta(decisionOwner = "Olive Adeyemi", decisionStatus = DecisionStatus.Confirmed), reactions = mapOf(ReactionKind.Ack to 4)), mine = false, prayedByMe = false),
        FeedPost(post("w2", PostType.Blocker, "Waiting on the payment provider's production keys.", author = "Grace Phiri", uid = "u5", ago = 90,
            meta = PostMeta(blockedOn = "Payments team", priority = WorkPriority.Urgent)), mine = false, prayedByMe = false),
        FeedPost(post("w3", PostType.Task, "Write the store listing copy and screenshots.", author = "Ann Mensah", uid = "me", ago = 240,
            meta = PostMeta(assigneeUid = "me", assigneeName = "Ann Mensah", dueText = "Friday")), mine = true, prayedByMe = false)
    )

    private fun state(circle: Circle, posts: List<FeedPost>, pinned: String? = null) = CircleHomeUiState(
        loading = false, circle = circle.copy(pinnedPostId = pinned), me = members[1], members = members, posts = posts, pinnedPostId = pinned
    )

    private fun page(state: CircleHomeUiState, tab: CircleTab) = @Composable {
        CircleHomeContent(
            state, ChatUiState(loading = false, myUid = "me", myName = "Ann Mensah", members = members), "", tab, {}, NOW,
            FeedActions.None, ChatActions.None, MemberActions.None, SnackbarHostState(), {}, {}, {}, {}, {},
            onCompose = {}
        )
    }

    @Test fun updatesFaith() = compose.circlesPremiumShot("updates_faith", page(state(PremiumSamples.faithCircle(), faithPosts, pinned = "3"), CircleTab.Updates))
    @Test fun updatesWork() = compose.circlesPremiumShot("updates_work", page(state(PremiumSamples.workCircle(), workPosts), CircleTab.Updates))
    @Test fun circleHomeFaith() = compose.circlesPremiumShot("circle_home_faith", page(state(PremiumSamples.faithCircle(), faithPosts), CircleTab.Home))
    @Test fun circleHomeWork() = compose.circlesPremiumShot("circle_home_work", page(state(PremiumSamples.workCircle(), workPosts), CircleTab.Home))

    @Test fun postCardWithPhoto() {
        seedCirclesMedia(photoPath)
        val withPhoto = FeedPost(
            post("7", PostType.Achievement, "Finished the 30-day reading plan today. Sunset walk to celebrate.", author = "Chidi Okafor", uid = "u4", ago = 45,
                reactions = mapOf(ReactionKind.Heart to 3, ReactionKind.Celebrate to 2), comments = 2,
                attachments = listOf(MediaAttachment(photoPath, "image/jpeg", 140_000))),
            mine = false, prayedByMe = false
        )
        compose.circlesPremiumShot("post_card_photo") {
            Column(Modifier.fillMaxWidth().background(MM.colors.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PostCard(withPhoto, PremiumSamples.faithCircle(), isAdmin = true, now = NOW, actions = FeedActions.None, members = members)
                PostCard(faithPosts[0], PremiumSamples.faithCircle(), isAdmin = true, now = NOW, actions = FeedActions.None, members = members)
            }
        }
    }

    @Test fun composerPrayer() = compose.circlesPremiumShot("composer_prayer") {
        ComposerSheet(
            PremiumSamples.faithCircle(),
            ComposerState(type = PostType.Prayer, body = "Please pray for wisdom as I decide about the new role."),
            {}, {}, {}, {}, {}, members = members, inSheet = false
        )
    }

    @Test fun composerWorkTask() = compose.circlesPremiumShot("composer_work_task") {
        ComposerSheet(
            PremiumSamples.workCircle(),
            ComposerState(type = PostType.Task, body = ""),
            {}, {}, {}, {}, {}, members = members, inSheet = false,
            meta = PostMeta(assigneeUid = "me", assigneeName = "Ann Mensah", dueText = "Friday", priority = WorkPriority.High)
        )
    }
}
