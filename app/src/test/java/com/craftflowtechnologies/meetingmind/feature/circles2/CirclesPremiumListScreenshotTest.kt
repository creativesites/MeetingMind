package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.ui.GoogleSignInButton
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Batch 1 of the Circles premium redesign: the Circles list, its signed-out states, the Google button and create. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi", sdk = [34])
class CirclesPremiumListScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val signedOut = AccountUiState()
    private val signedIn = AccountUiState(linked = true, email = "ann@example.com")
    private val circles = listOf(
        PremiumSamples.faithCircle(),
        PremiumSamples.workCircle(),
        testCircleForShots().copy(id = "c3", name = "Sunday Youth", vocab = "Youth", memberCount = 24, template = "youth_group"),
        testCircleForShots().copy(id = "c4", name = "Book Club", vocab = "Club", memberCount = 7, template = "book_club", space = "general")
    )

    private fun list(state: CirclesListState, account: AccountUiState?) = @Composable {
        CirclesListContent(state, JoinUiState(), false, {}, {}, {}, {}, {}, {}, {}, {}, {}, account = account)
    }

    @Test fun listPopulatedSignedIn() = compose.circlesPremiumShot("list_populated", list(CirclesListState.Ready(circles), signedIn))
    @Test fun listEmptySignedOut() = compose.circlesPremiumShot("list_empty_signed_out", list(CirclesListState.Ready(emptyList()), signedOut))
    @Test fun listEarlierCircles() = compose.circlesPremiumShot("list_earlier_sign_in", list(CirclesListState.Ready(circles.take(1), earlier = 2), signedOut))
    @Test fun listLoading() = compose.circlesPremiumShot("list_loading", list(CirclesListState.Loading, signedOut))

    @Test fun googleButton() = compose.circlesPremiumShot("google_button") {
        Column(
            Modifier.fillMaxWidth().background(MM.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GoogleSignInButton(onClick = {}, modifier = Modifier.fillMaxWidth())
            GoogleSignInButton(onClick = {}, busy = true, modifier = Modifier.fillMaxWidth())
            GoogleSignInButton(onClick = {}, text = "Continue with Google")
            CirclesSignInPrompt(signedOut, "Joining from a new phone? Sign in first and your other circles come with you.", {}, {}, {})
        }
    }

    @Test fun createSignedOut() = compose.circlesPremiumShot("create_signed_out") {
        CreateCircleContent(
            CreateUiState(name = "Tuesday Night", displayName = "Ann"), {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            account = signedOut
        )
    }
}
