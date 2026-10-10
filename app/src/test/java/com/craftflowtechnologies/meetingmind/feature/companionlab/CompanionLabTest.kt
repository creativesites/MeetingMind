package com.craftflowtechnologies.meetingmind.feature.companionlab

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Companion Lab (Z-6) exists in debug builds only, and renders. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CompanionLabTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `the lab is not in the main or release source sets`() {
        val main = File("src/main")
        val offenders = main.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { it.readText().contains("CompanionLab") || it.readText().contains("companion_lab") }.toList()
        assertTrue("main must not reference the lab: $offenders", offenders.isEmpty())
        assertFalse(File("src/release").walkTopDown().any { it.isFile && it.readText().contains("CompanionLab") })
        assertTrue(File("src/debug/AndroidManifest.xml").readText().contains(".companionlab.CompanionLabActivity"))
    }

    @Test fun `the lab renders`() {
        rule.mainClock.autoAdvance = false
        rule.setContent { CompanionLabScreen() }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Companion Lab").assertExists()
        rule.onNodeWithText("Renderer: CANVAS (NO_ASSET)").assertExists()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/companion/lab.png")
    }
}
