package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionAssets
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionRenderer
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionRiveContract
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.RendererChoice
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.RendererReason
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.RendererInputs
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionRendererSelector
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.CompanionRiveBudget
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive.LocalCompanionRiveBudget
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Companion shell in Robolectric: renderer choice, clock pausing, semantics, export. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CompanionRendererTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `all four riv files are bundled, so a 96 dp animated slot is Rive-ready`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (f in CompanionForm.entries) {
            assertTrue("$f .riv bundled", CompanionAssets.isBundled(ctx, f))
            val inputs = RendererInputs(
                sizeDp = 96f, durationScale = 1f, assetBundled = CompanionAssets.isBundled(ctx, f), hasBudgetSlot = true
            )
            assertEquals(RendererChoice(CompanionRenderer.RIVE, RendererReason.RIVE_READY), CompanionRendererSelector.select(inputs))
            // Slot < 48 dp, or animations off, still falls back to Canvas.
            assertEquals(RendererReason.TOO_SMALL, CompanionRendererSelector.select(inputs.copy(sizeDp = 47f)).reason)
            assertEquals(RendererReason.REDUCED_MOTION, CompanionRendererSelector.select(inputs.copy(durationScale = 0f)).reason)
        }
    }

    @Test fun `the shell wants Rive for a bundled form, and only the budget keeps the JVM off the natives`() {
        // A zero budget means Companion() reaches the Rive decision (asset found, 96 dp, animations on)
        // and then stops at OVER_BUDGET, so no Rive native library is ever loaded on the JVM.
        var choice: RendererChoice? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MeetMindTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalCompanionReducedMotion provides false,
                    LocalCompanionRiveBudget provides CompanionRiveBudget(max = 0)
                ) {
                    Companion(CompanionForm.ZURI, CompanionState.IDLE, 96.dp, onRenderer = { choice = it })
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(RendererChoice(CompanionRenderer.CANVAS, RendererReason.OVER_BUDGET), choice)
    }

    @Test fun `small, reduced-motion and forced slots say why they are Canvas`() {
        val seen = mutableMapOf<String, RendererReason>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MeetMindTheme {
                CompositionLocalProvider(LocalCompanionReducedMotion provides false) {
                    Companion(CompanionForm.NAS, CompanionState.IDLE, 24.dp, onRenderer = { seen["small"] = it.reason })
                }
                CompositionLocalProvider(LocalCompanionReducedMotion provides true) {
                    Companion(CompanionForm.NAS, CompanionState.IDLE, 96.dp, onRenderer = { seen["reduced"] = it.reason })
                }
                CompositionLocalProvider(LocalCompanionForceCanvas provides true) {
                    Companion(CompanionForm.NAS, CompanionState.IDLE, 96.dp, onRenderer = { seen["forced"] = it.reason })
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(RendererReason.TOO_SMALL, seen["small"])
        assertEquals(RendererReason.REDUCED_MOTION, seen["reduced"])
        assertEquals(RendererReason.FORCED_CANVAS, seen["forced"])
    }

    @Test fun `the clock advances only while running`() {
        var running by mutableStateOf(false)
        var t = 0f
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val time = rememberCompanionTime(running = running, restartKey = Unit)
            t = time.value
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("paused clock does not tick", 0f, t, 0f)
        running = true
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue("running clock ticks: $t", t > 0.5f)
        running = false
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(100)
        val held = t
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("paused again", held, t, 0f)
    }

    @Test fun `one-shots stop the clock when done`() {
        var finished = false
        var t = 0f
        rule.mainClock.autoAdvance = false
        rule.setContent {
            t = rememberCompanionTime(running = true, restartKey = Unit, runMs = 600, onFinished = { finished = true }).value
        }
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue(finished)
        assertEquals(0.6f, t, 1e-3f)
    }

    @Test fun `decorative by default, labelled when asked`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MeetMindTheme {
                Companion(CompanionForm.WREN, CompanionState.IDLE, 48.dp, Modifier.testTag("decorative"))
                Companion(CompanionForm.PAGE, CompanionState.LISTENING, 48.dp, contentDescription = "Page is listening")
            }
        }
        rule.onNodeWithTag("decorative").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        rule.onNodeWithContentDescription("Page is listening").assertExists()
    }

    @Test fun `card export draws the static pose into a bitmap`() {
        for (form in CompanionForm.entries) {
            val bmp = renderCompanionBitmap(form, CreateMode.PEACEFUL, companionPalette(MMAccent.Gold, false), 192)
            val px = bmp.toPixelMap()
            assertTrue("$form draws its body at the centre", px[96, 120].alpha > 0.9f)
            assertEquals("$form leaves the corner clear", 0f, px[2, 2].alpha, 0f)
        }
    }

    @Test fun `contract names`() {
        assertEquals("Companion", CompanionRiveContract.StateMachine)
        assertEquals(listOf("Zuri", "Nas", "Wren", "Page"), CompanionForm.entries.map(CompanionRiveContract::artboard))
        assertEquals("companion/page.riv", CompanionRiveContract.assetPath(CompanionForm.PAGE))
    }
}
