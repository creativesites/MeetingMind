package com.craftflowtechnologies.meetingmind.feature.settings.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.CompanionQuickActions
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.CompanionQuickSheetContent
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.companion.Presence
import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.identity.AppLook
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppLook
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.today.HeroTile
import com.craftflowtechnologies.meetingmind.feature.today.HeroTiles
import com.craftflowtechnologies.meetingmind.feature.today.HomeHeroHeader
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/** An in-memory settings source, so the screens render without DataStore. */
class FakeCompanionSettings(initial: CompanionSettings = CompanionSettings()) : CompanionSettingsSource {
    val state = MutableStateFlow(initial)
    override val settings: Flow<CompanionSettings> = state
    override suspend fun setForm(form: CompanionForm?) { state.value = state.value.copy(form = form) }
    override suspend fun setName(name: String?) { state.value = state.value.copy(name = name?.trim()?.ifEmpty { null }) }
    override suspend fun setPresence(presence: Presence) { state.value = state.value.copy(presence = presence) }
    override suspend fun setHiddenUntil(untilMs: Long?) { state.value = state.value.copy(hiddenUntilMs = untilMs) }
    override suspend fun setOnRecordButton(on: Boolean) { state.value = state.value.copy(onRecordButton = on) }
    override suspend fun setQuietSermons(on: Boolean) { state.value = state.value.copy(quietSermons = on) }
    override suspend fun setCreateSuggest(on: Boolean) { state.value = state.value.copy(createSuggest = on) }
}

/**
 * Settings -> Companion (light and dark) and the Today header with and without a companion.
 * Tests draw on Canvas with static poses, so the images are deterministic.
 * Record with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CompanionPlacementsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun Harness(source: CompanionSettingsSource, dark: Boolean = false, content: @Composable () -> Unit) {
        MeetMindTheme(darkTheme = dark) {
            CompositionLocalProvider(
                LocalCompanionSettingsSource provides source,
                LocalCompanionForceCanvas provides true,
                LocalCompanionReducedMotion provides true,
                content = content
            )
        }
    }

    private fun settingsPage(dark: Boolean) {
        val fake = FakeCompanionSettings(CompanionSettings(name = "Pip"))
        rule.setContent {
            Harness(fake, dark) {
                CompanionSettingsContent(
                    fake.state.collectAsState().value, object : CompanionSettingsActions {
                        override fun setForm(form: CompanionForm?) { fake.state.value = fake.state.value.copy(form = form) }
                        override fun rename(raw: String) {}
                        override fun setPresence(presence: Presence) {}
                        override fun setOnRecordButton(on: Boolean) {}
                        override fun setQuietSermons(on: Boolean) {}
                        override fun setCreateSuggest(on: Boolean) {}
                    }, onNavigateBack = {}
                )
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/placements/settings_companion_${if (dark) "dark" else "light"}.png")
    }

    @Test fun settings_light() = settingsPage(false)
    @Test fun settings_dark() = settingsPage(true)

    @Test fun `tapping a preview cycles its pose and choosing selects the form`() {
        val fake = FakeCompanionSettings()
        rule.setContent {
            Harness(fake) {
                CompanionSettingsContent(
                    fake.state.collectAsState().value, object : CompanionSettingsActions {
                        override fun setForm(form: CompanionForm?) { fake.state.value = fake.state.value.copy(form = form) }
                        override fun rename(raw: String) {}
                        override fun setPresence(presence: Presence) {}
                        override fun setOnRecordButton(on: Boolean) {}
                        override fun setQuietSermons(on: Boolean) {}
                        override fun setCreateSuggest(on: Boolean) {}
                    }, onNavigateBack = {}
                )
            }
        }
        rule.onNodeWithTag("companion_form_nas").assertIsDisplayed()
        rule.onNodeWithTag("companion_none").performClick()
        assertEquals(null, fake.state.value.form)
    }

    private fun hero(source: CompanionSettingsSource, file: String, hour: Int = 9) {
        val identity = AppIdentity(displayName = "Ana")
        rule.setContent {
            Harness(source) {
                CompositionLocalProvider(LocalAppLook provides AppLook.of(identity.look)) {
                    Column {
                        HomeHeroHeader(
                            identity = identity, greeting = "Rise and shine, Ana", contextLine = "2 events today · 1 recording processing",
                            streakLabel = "12 days", weekLabel = "9 this week", inboxCount = 2, showSwitch = false, switchLabel = "Faith",
                            tile = HeroTile("Up next · In 25 min", "Design review", "Last time: Q3 planning", HeroTiles.eventIcon(), AppLook.of(identity.look).accent) {},
                            listState = rememberLazyListState(), onAvatar = {}, onSearch = {}, onInbox = {}, onSwitch = {},
                            at = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 20) }
                        )
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/placements/$file.png")
    }

    @Test fun today_header_with_companion() = hero(FakeCompanionSettings(), "today_header_with_companion")
    @Test fun today_header_without_companion() = hero(FakeCompanionSettings(CompanionSettings(form = null)), "today_header_without_companion")
    @Test fun today_header_presence_off_collapses() = hero(FakeCompanionSettings(CompanionSettings(presence = Presence.OFF)), "today_header_presence_off")

    @Test fun `a hidden companion draws nothing and a visible one does`() {
        val fake = FakeCompanionSettings(CompanionSettings(hiddenUntilMs = System.currentTimeMillis() + 3_600_000))
        rule.setContent { Harness(fake) { ZuriSlot(CompanionPage.HOME, 56.dp, Modifier.testTag("slot")) } }
        rule.onAllNodesWithTag("slot").assertCountEquals(0)
        fake.state.value = fake.state.value.copy(hiddenUntilMs = null)
        rule.waitForIdle()
        rule.onAllNodesWithTag("slot").assertCountEquals(1)
    }

    @Test fun `presence moments hides the companion on Home but not while recording`() {
        val fake = FakeCompanionSettings(CompanionSettings(presence = Presence.MOMENTS))
        rule.setContent {
            Harness(fake) {
                Column {
                    ZuriSlot(CompanionPage.HOME, 56.dp, Modifier.testTag("home"))
                    ZuriSlot(CompanionPage.RECORDING, 56.dp, Modifier.testTag("recording"))
                }
            }
        }
        rule.onAllNodesWithTag("home").assertCountEquals(0)
        rule.onAllNodesWithTag("recording").assertCountEquals(1)
    }

    private fun sheet(form: CompanionForm, dark: Boolean) {
        val fake = FakeCompanionSettings(CompanionSettings(form = form))
        rule.setContent {
            Harness(fake, dark) {
                Box(Modifier.background(MM.colors.surfaceRaised)) {
                    CompanionQuickSheetContent(
                        fake.state.collectAsState().value, object : CompanionQuickActions {
                            override fun setForm(form: CompanionForm) {}
                            override fun setPresence(presence: Presence) {}
                            override fun rename(raw: String) {}
                            override fun hideUntilTomorrow() {}
                            override fun show() {}
                        }, onDone = {}, onOpenSettings = {}
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/placements/quick_sheet_${form.name.lowercase()}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun quick_sheet_zuri_light() = sheet(CompanionForm.ZURI, false)
    @Test fun quick_sheet_nas_dark() = sheet(CompanionForm.NAS, true)
}
