package com.craftflowtechnologies.meetingmind.feature.onboarding

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicDevotionals
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalPlans
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSource
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.GZIPInputStream

/** "Choose how you read": every plan with a preview, the recommended one first and chosen, skippable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi", sdk = [34])
class PlanStepTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val choices: List<PlanChoice> = DevotionalPlans.available.mapIndexed { i, p ->
        PlanChoice.of(File("src/main/assets/${p.asset}").inputStream().use { ClassicDevotionals.parse(GZIPInputStream(it), p) }, recommended = i == 0)
    } + PlanChoice.ai()

    private fun shot(name: String, dark: Boolean, selected: String = DevotionalPlans.DEFAULT_ID) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                var pick by androidx.compose.runtime.remember { mutableStateOf(selected) }
                Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.Navy, Brand.NavyLift, Brand.Navy))).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    PlanStep(choices, pick) { pick = it }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_plan_$name.png")
    }

    @Test fun `plan step light`() = shot("light", dark = false)
    @Test fun `plan step dark`() = shot("dark", dark = true)

    @Test fun `each plan shows a preview of its first entry, the recommended first, then the AI row`() {
        assertEquals(DevotionalPlans.available.map { it.id } + PlanChoice.AI, choices.map { it.id })
        assertTrue(choices.first().recommended)
        assertEquals(DevotionalPlans.DEFAULT_ID, choices.first().id)
        assertTrue(choices.dropLast(1).all { !it.previewText.isNullOrBlank() })
        assertTrue(choices.last().previewText == null)
        assertEquals("Write one with AI, any time", choices.last().title)
        assertTrue(choices.first().previewText!!.startsWith("“They did eat of the fruit"))
    }

    @Test fun `Morning and Evening is chosen until the person picks, and skipping writes nothing`() {
        val vm = OnboardingViewModel(app)
        assertEquals(DevotionalPlans.DEFAULT_ID, vm.reading.value)
        assertFalse(vm.readingTouched.value)
        // A fresh install already reads Morning and Evening.
        assertEquals(DevotionalProfile(), DevotionalProfile.fromJson(null))
    }

    @Test fun `choosing a plan or AI changes only the source and plan`() {
        val base = DevotionalProfile(enabled = true, minutes = 7)
        val checkbook = commitReading(base, DevotionalPlans.FAITHS_CHECKBOOK)
        assertEquals(DevotionalSource.CLASSIC, checkbook.source)
        assertEquals(DevotionalPlans.FAITHS_CHECKBOOK, checkbook.planId)
        assertEquals(base.copy(source = DevotionalSource.CLASSIC, planId = DevotionalPlans.FAITHS_CHECKBOOK), checkbook)
        val ai = commitReading(base, PlanChoice.AI)
        assertEquals(DevotionalSource.AI, ai.source)
        assertEquals("morning-and-evening", ai.planId)
    }

    @Test fun `tapping a row selects it`() {
        var picked: String? = null
        compose.setContent { MeetMindTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PlanStep(choices, DevotionalPlans.DEFAULT_ID) { picked = it } } } }
        compose.onNodeWithTag("onboarding_plan_${DevotionalPlans.FAITHS_CHECKBOOK}").performClick()
        assertEquals(DevotionalPlans.FAITHS_CHECKBOOK, picked)
    }
}
