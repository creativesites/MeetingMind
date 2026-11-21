package com.craftflowtechnologies.meetingmind.feature.moments

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRhythm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.MilestoneKind
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.companion.Presence
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.RhythmLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.MilestoneCard
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The Proud milestone card and the calm rhythm line (ZURI_EXPERIENCE §3.1, §5.7, §5.9), light then dark, plus the
 * host's once-ever and presence rules. Images: build/outputs/roborazzi/companion_moments_<name>_<light|dark>.png.
 * Record with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CompanionMomentsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var store: MomentLedgerStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "milestones-${System.nanoTime()}.preferences_pb")
        store = MomentLedgerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file })
    }

    private fun ComposeContentTestRule.momentsShot(
        name: String,
        settings: CompanionSettings = CompanionSettings(),
        fontScale: Float? = null,
        content: @Composable () -> Unit
    ) {
        val dark = mutableStateOf(false)
        setContent {
            CompositionLocalProvider(
                LocalCompanionSettingsSource provides FakeCompanionSettings(settings),
                LocalCompanionForceCanvas provides true,
                LocalCompanionReducedMotion provides true
            ) {
                MeetMindTheme(darkTheme = dark.value) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                        Column(
                            Modifier.fillMaxWidth().background(MM.colors.background).padding(MM.space.l),
                            verticalArrangement = Arrangement.spacedBy(MM.space.l)
                        ) { content() }
                    }
                }
            }
        }
        waitForIdle()
        for (d in listOf(false, true)) {
            dark.value = d
            waitForIdle()
            onRoot().captureRoboImage("build/outputs/roborazzi/companion_moments_${name}_${if (d) "dark" else "light"}.png")
        }
    }

    private fun card(kind: MilestoneKind, form: CompanionForm? = CompanionForm.ZURI, share: Boolean = false) = @Composable {
        MilestoneCard(kind = kind, form = form, companionName = "Zuri", onDone = {}, onShare = if (share) ({}) else null)
    }

    @Test fun milestoneTenthNote() = compose.momentsShot("milestone_tenth_note", content = card(MilestoneKind.TENTH_NOTE))

    @Test fun milestoneCardsClearedWithShare() =
        compose.momentsShot("milestone_cards_cleared_share", content = card(MilestoneKind.ALL_CARDS_CLEARED, share = true))

    @Test fun milestonesFaith() = compose.momentsShot("milestone_faith") {
        card(MilestoneKind.SEVEN_DEVOTIONALS_IN_MONTH, CompanionForm.NAS, share = true)()
        card(MilestoneKind.READING_PLAN_DONE, CompanionForm.WREN)()
    }

    @Test fun milestoneNoCompanion() =
        compose.momentsShot("milestone_no_companion", CompanionSettings(form = null), content = card(MilestoneKind.TENTH_NOTE, form = null, share = true))

    @Test fun milestoneLargeText() =
        compose.momentsShot("milestone_large_text", fontScale = 1.3f, content = card(MilestoneKind.SEVEN_DEVOTIONALS_IN_MONTH, share = true))

    @Test fun rhythmLines() = compose.momentsShot("rhythm_lines") {
        RhythmLine(CompanionRhythm.Line(CompanionRhythm.Kind.FAITH_DAYS, 3))
        RhythmLine(CompanionRhythm.Line(CompanionRhythm.Kind.STUDY_DAYS, 4))
        RhythmLine(CompanionRhythm.Line(CompanionRhythm.Kind.NOTES_KEPT, 6))
        RhythmLine(null)
        Text("(a quiet week draws nothing above this line)", style = MM.type.caption, color = MM.colors.inkMuted)
    }

    @Test fun rhythmLinesNoCompanion() = compose.momentsShot("rhythm_lines_no_companion", CompanionSettings(form = null)) {
        RhythmLine(CompanionRhythm.Line(CompanionRhythm.Kind.FAITH_DAYS, 2))
        RhythmLine(CompanionRhythm.Line(CompanionRhythm.Kind.NOTES_KEPT, 12))
    }

    /** The host on a screen: shows the card at the bottom, once ever, and Nice closes it. */
    @Test fun `the host shows a milestone once and Nice closes it`() {
        var entry by mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(
                LocalMomentLedgerStore provides store,
                LocalCompanionSettingsSource provides FakeCompanionSettings(),
                LocalCompanionForceCanvas provides true,
                LocalCompanionReducedMotion provides true
            ) {
                MeetMindTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(MM.colors.background), contentAlignment = Alignment.BottomCenter) {
                        Text("The screen underneath", modifier = Modifier.align(Alignment.TopStart).padding(MM.space.l))
                        // A new key is a new visit to the screen.
                        androidx.compose.runtime.key(entry) { MilestoneHost(MilestoneKind.TENTH_NOTE) }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("milestone_card_TENTH_NOTE").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/companion_moments_host_on_screen_light.png")
        compose.onNodeWithTag("milestone_done").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("milestone_card_TENTH_NOTE").assertCountEquals(0)

        entry++
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        compose.onAllNodesWithTag("milestone_card_TENTH_NOTE").assertCountEquals(0)
    }

    @Test fun `the host shows nothing when the companion is turned off`() {
        compose.setContent {
            CompositionLocalProvider(
                LocalMomentLedgerStore provides store,
                LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings(presence = Presence.OFF)),
                LocalCompanionForceCanvas provides true,
                LocalCompanionReducedMotion provides true
            ) {
                MeetMindTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize()) { MilestoneHost(MilestoneKind.ALL_CARDS_CLEARED) }
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        compose.onAllNodesWithTag("milestone_card_ALL_CARDS_CLEARED").assertCountEquals(0)
    }
}
