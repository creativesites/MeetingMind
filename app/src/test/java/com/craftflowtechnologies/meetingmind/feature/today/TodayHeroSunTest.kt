package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode
import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.identity.AppLook
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppLook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.timeline.TimeOfDaySky
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * The Today hero's sun or moon must never sit under its greeting, its context line or its bottom
 * banner, at 360 dp and at 1.3x font scale. The sun's bounds come from the same placement the
 * drawing uses. Record screenshots with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class TodayHeroSunTest {
    @get:Rule val compose = createComposeRule()

    private val greeting = "Coffee, then conquer, Winston?"
    private val contextLine = "2 events today · 1 recording processing"
    private val identity = AppIdentity(spaces = setOf(NotebookSpace.WORK), displayName = "Winston")

    private fun hero(hour: Int, fontScale: Float, dark: Boolean, quote: Boolean) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MeetMindTheme(darkTheme = dark) {
                    CompositionLocalProvider(
                        LocalCompanionForceCanvas provides true,
                        LocalCompanionReducedMotion provides true,
                        LocalAppLook provides AppLook.of(identity.look)
                    ) {
                        Box(Modifier.width(360.dp).testTag("hero_test_frame")) {
                            Column {
                                HomeHeroHeader(
                                    identity = identity, greeting = greeting, contextLine = contextLine,
                                    streakLabel = "12 days", weekLabel = "9 this week", inboxCount = 2, showSwitch = identity.showsFaith,
                                    switchLabel = "Faith",
                                    tile = HeroTile("Up next · In 25 min", "Design review", "Last time: Q3 planning", HeroTiles.eventIcon(), AppLook.of(identity.look).accent) {},
                                    listState = rememberLazyListState(), onAvatar = {}, onSearch = {}, onInbox = {}, onSwitch = {},
                                    displayMode = if (quote) HomeHeroDisplayMode.DAILY_QUOTE else HomeHeroDisplayMode.DAILY_SCRIPTURE,
                                    scriptureReference = "Psalm 46:10",
                                    scriptureText = "Be still, and know that I am God.",
                                    quoteText = "Grace is not part of the way; grace is all of the way.",
                                    quoteAuthor = "C.S. Lewis",
                                    at = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 20) }
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Asserts the sun's body and ring clear every text block in the card, then records the frame. */
    private fun assertSunClearOfText(hour: Int, fontScale: Float, dark: Boolean = false, quote: Boolean = false, shot: String? = null) {
        hero(hour, fontScale, dark, quote)
        val card = compose.onNodeWithTag("home_hero").fetchSemanticsNode().boundsInRoot
        val sky = TimeOfDaySky.at(hour, 20, warm = false)
        val sun = skyBodyPlacementFor(card, sky)

        val texts = linkedMapOf(
            "greeting" to compose.onNodeWithText(greeting).fetchSemanticsNode().boundsInRoot,
            "context line" to compose.onNodeWithText(contextLine).fetchSemanticsNode().boundsInRoot,
            "banner" to compose.onNodeWithTag(if (quote) "hero_quote_banner" else "hero_scripture_banner").fetchSemanticsNode().boundsInRoot
        )
        texts.forEach { (name, rect) ->
            assertFalse("the $name $rect runs under the sun $sun at ${hour}h, font ${fontScale}x", rect.overlaps(sun))
        }
        assertTrue("the sun $sun sits on the card $card", card.contains(sun.center))
        shot?.let { compose.onNodeWithTag("hero_test_frame").captureRoboImage("build/outputs/roborazzi/$it.png") }
    }

    /** The sun's placement mapped from card-local pixels to the window's pixels. */
    private fun skyBodyPlacementFor(card: Rect, sky: TimeOfDaySky): Rect =
        skyBodyPlacement(Size(card.width, card.height), sky, GreetingWidthWithCompanion).bounds.translate(card.left, card.top)

    @Test fun noonAt360AndDefaultFont() = assertSunClearOfText(hour = 12, fontScale = 1f, shot = "today_hero_sun_360_light")
    @Test fun noonAt360AndDefaultFontDark() = assertSunClearOfText(hour = 12, fontScale = 1f, dark = true, shot = "today_hero_sun_360_dark")
    @Test fun noonAt13xFont() = assertSunClearOfText(hour = 12, fontScale = 1.3f, shot = "today_hero_sun_360_13x_light")
    @Test fun sunsetAt13xFontQuote() = assertSunClearOfText(hour = 18, fontScale = 1.3f, quote = true)
    @Test fun dawnAt13xFont() = assertSunClearOfText(hour = 6, fontScale = 1.3f)
    @Test fun moonAt13xFontQuote() = assertSunClearOfText(hour = 23, fontScale = 1.3f, quote = true, shot = "today_hero_moon_360_13x_light")
}
