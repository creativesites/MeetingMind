package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.MessageKind
import com.craftflowtechnologies.meetingmind.core.circles2.MessageReaction
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.craftflowtechnologies.meetingmind.feature.circles2.PremiumSamples.MINUTE
import com.craftflowtechnologies.meetingmind.feature.circles2.PremiumSamples.NOW
import com.craftflowtechnologies.meetingmind.feature.circles2.PremiumSamples.message

/** Batch 3: chat. Ink bubbles instead of blue, and photos and videos with no bubble or backdrop behind them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi", sdk = [34])
class CirclesPremiumChatScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val members = PremiumSamples.members
    private val photo = "circles/c1/chat/m3/table.jpg"
    private val photo2 = "circles/c1/chat/m4/view.jpg"
    private val video = "circles/c1/chat/m6/clip.mp4"

    private val conversation = listOf(
        message("m1", "owner", "Olive Adeyemi", "Good evening everyone. Are we still on for Tuesday?", 180),
        ChatMessage("m2", "me", "Ann Mensah", MessageKind.Reply, "Yes! I'll bring the soup.", replyTo = "m1", createdAt = NOW - 170 * MINUTE),
        message("m3", "u3", "Ben Carter", "@Ann Mensah you're a star. I'll do bread.", 160),
        message("m4", "u3", "Ben Carter", "And I can give two people a lift from town.", 159),
        message("m5", "me", "Ann Mensah", "Praying for Sam tonight 🙏", 20),
        message("m6", "me", "Ann Mensah", "See you all at seven.", 19)
    )

    private val media = listOf(
        message("m1", "owner", "Olive Adeyemi", "Last week's table, for the memories.", 120),
        ChatMessage("m3", "me", "Ann Mensah", MessageKind.Image, "", createdAt = NOW - 100 * MINUTE, attachment = MediaAttachment(photo, "image/jpeg", 140_000)),
        ChatMessage("m4", "u3", "Ben Carter", MessageKind.Image, "The view from the hill on the way home", createdAt = NOW - 60 * MINUTE, attachment = MediaAttachment(photo2, "image/jpeg", 120_000)),
        ChatMessage("m6", "me", "Ann Mensah", MessageKind.Video, "", createdAt = NOW - 10 * MINUTE, attachment = MediaAttachment(video, "video/mp4", 2_400_000, durationMs = 14_000)),
        message("m7", "u5", "Grace Phiri", "Beautiful, thank you for hosting.", 5)
    )

    private val reactions = ChatActions(
        reactionsFor = { id -> flowOf(if (id == "m1") listOf(MessageReaction("🙏", 3, true), MessageReaction("❤️", 1, false)) else emptyList()) }
    )

    private fun chat(messages: List<ChatMessage>, replyTo: ChatMessage? = null) = @Composable {
        CircleHomeContent(
            CircleHomeUiState(loading = false, circle = PremiumSamples.faithCircle(), me = members[1], members = members),
            ChatUiState(loading = false, messages = messages, myUid = "me", myName = "Ann Mensah", members = members, replyTo = replyTo, circleId = "c1"),
            "", CircleTab.Chat, {}, NOW, FeedActions.None, reactions, MemberActions.None, SnackbarHostState(), {}, {}, {}, {}, {},
            onCompose = {}
        )
    }

    @Test fun chatConversation() = compose.circlesPremiumShot("chat_conversation", chat(conversation))
    @Test fun chatReplying() = compose.circlesPremiumShot("chat_replying", chat(conversation.take(4), replyTo = conversation[0]))

    @Test fun chatMedia() {
        seedCirclesMedia(photo)
        seedCirclesMedia(photo2, warm = false)
        seedCirclesMedia(video, ext = "mp4")
        compose.circlesPremiumShot("chat_media", chat(media))
    }
}
