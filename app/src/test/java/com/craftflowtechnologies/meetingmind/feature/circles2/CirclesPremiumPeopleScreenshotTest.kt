package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.craftflowtechnologies.meetingmind.core.circles2.JoinRequest
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.craftflowtechnologies.meetingmind.feature.circles2.PremiumSamples.MINUTE
import com.craftflowtechnologies.meetingmind.feature.circles2.PremiumSamples.NOW

/** Batch 4: People and circle settings, for an admin and for a member, with the signed-out account section. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi", sdk = [34])
class CirclesPremiumPeopleScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val members = PremiumSamples.members
    private val posts = listOf("u3", "u3", "owner", "me", "u5").mapIndexed { i, uid ->
        FeedPost(Post("p$i", PostType.Note, "A post", authorUid = uid, authorName = uid, createdAt = NOW - i * MINUTE), mine = uid == "me", prayedByMe = false)
    }

    private fun people(admin: Boolean, work: Boolean = false) = @Composable {
        CircleHomeContent(
            CircleHomeUiState(
                loading = false, circle = if (work) PremiumSamples.workCircle() else PremiumSamples.faithCircle(),
                me = if (admin) members[1] else members[2], members = members, posts = posts,
                joinRequests = if (admin) listOf(JoinRequest("j1", "Tendai Moyo", NOW - 30 * MINUTE)) else emptyList()
            ),
            ChatUiState(loading = false, myUid = if (admin) "me" else "u3"), "", CircleTab.People, {}, NOW,
            FeedActions.None, ChatActions.None, MemberActions.None, SnackbarHostState(), {}, {}, {}, {}, {},
            onCompose = {}
        )
    }

    @Test fun peopleAdmin() = compose.circlesPremiumShot("people_admin", people(admin = true))
    @Test fun peopleMemberWork() = compose.circlesPremiumShot("people_member_work", people(admin = false, work = true))

    @Test fun settingsAdmin() = compose.circlesPremiumShot("settings_admin") {
        CircleSettingsContent(PremiumSamples.faithCircle(), members[1], {}, { _, _, _, _ -> }, {}, {}, account = AccountUiState(linked = true, email = "ann@example.com"))
    }

    @Test fun settingsMemberSignedOut() = compose.circlesPremiumShot("settings_member_signed_out") {
        CircleSettingsContent(PremiumSamples.faithCircle(), members[2], {}, { _, _, _, _ -> }, {}, {}, account = AccountUiState())
    }
}
