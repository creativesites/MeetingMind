package com.example.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.core.identity.AppIdentity
import com.example.core.identity.AppLook
import com.example.core.identity.LocalAppLook
import com.example.core.identity.LookAndFeel
import com.example.core.model.NotebookSpace
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Home hero in its two faces. Record with -Proborazzi.test.record=true. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class TodayScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun hero(identity: AppIdentity, greeting: String, file: String, hour: Int? = null) {
        compose.setContent {
            MeetMindTheme {
                CompositionLocalProvider(LocalAppLook provides AppLook.of(identity.look)) {
                    Column {
                        HomeHeroHeader(
                            identity = identity, greeting = greeting, contextLine = "2 events today · 1 recording processing",
                            streakLabel = "12 days", weekLabel = "9 this week", inboxCount = 2, showSwitch = identity.showsFaith,
                            switchLabel = "Faith",
                            tile = HeroTile("Up next · In 25 min", "Design review", "Last time: Q3 planning", HeroTiles.eventIcon(), AppLook.of(identity.look).accent) {},
                            listState = rememberLazyListState(), onAvatar = {}, onSearch = {}, onInbox = {}, onSwitch = {},
                            at = hour?.let { h -> java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, 20) } }
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$file.png")
    }

    @Test fun professional() = hero(AppIdentity(spaces = setOf(NotebookSpace.WORK), displayName = "Ana Banda"), "Coffee, then conquer, Ana?", "today_hero_professional")
    @Test fun morning() = hero(AppIdentity(displayName = "Ana"), "Rise and shine, Ana ☀️", "today_hero_morning", hour = 9)
    @Test fun dawn() = hero(AppIdentity(displayName = "Ana"), "Up with the sun, Ana ☀️", "today_hero_dawn", hour = 6)
    @Test fun golden() = hero(AppIdentity(displayName = "Ana"), "Golden hour, Ana ✨", "today_hero_golden", hour = 17)
    @Test fun sanctuary() = hero(AppIdentity(spaces = setOf(NotebookSpace.FAITH, NotebookSpace.PERSONAL), look = LookAndFeel.SANCTUARY, displayName = "Ana"), "This is the day the Lord has made, Ana", "today_hero_sanctuary")
}
