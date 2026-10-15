package com.craftflowtechnologies.meetingmind.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMPreviewFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.SampleAccents
import com.craftflowtechnologies.meetingmind.core.ui.mm.SampleButtons
import com.craftflowtechnologies.meetingmind.core.ui.mm.SampleChoices
import com.craftflowtechnologies.meetingmind.core.ui.mm.SampleFeedback
import com.craftflowtechnologies.meetingmind.core.ui.mm.SampleHeaders
import com.craftflowtechnologies.meetingmind.core.ui.mm.SampleHome
import com.craftflowtechnologies.meetingmind.core.ui.mm.SampleRows
import com.craftflowtechnologies.meetingmind.core.ui.mm.SampleSurfaces
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every design-system component in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class MMComponentScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent { MMPreviewFrame(dark, content) }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/mm/${name}_${if (dark) "dark" else "light"}.png")
    }


    @Test fun headers_light() = shoot("headers", false) { SampleHeaders() }
    @Test fun headers_dark() = shoot("headers", true) { SampleHeaders() }
    @Test fun surfaces_light() = shoot("surfaces", false) { SampleSurfaces() }
    @Test fun surfaces_dark() = shoot("surfaces", true) { SampleSurfaces() }
    @Test fun rows_light() = shoot("rows", false) { SampleRows() }
    @Test fun rows_dark() = shoot("rows", true) { SampleRows() }
    @Test fun buttons_light() = shoot("buttons", false) { SampleButtons() }
    @Test fun buttons_dark() = shoot("buttons", true) { SampleButtons() }
    @Test fun choices_light() = shoot("choices", false) { SampleChoices() }
    @Test fun choices_dark() = shoot("choices", true) { SampleChoices() }
    @Test fun feedback_light() = shoot("feedback", false) { SampleFeedback() }
    @Test fun feedback_dark() = shoot("feedback", true) { SampleFeedback() }
    @Test fun home_light() = shoot("home_scaffold", false) { SampleHome() }
    @Test fun home_dark() = shoot("home_scaffold", true) { SampleHome() }
    @Test fun accents_light() = shoot("accents", false) { SampleAccents() }
    @Test fun accents_dark() = shoot("accents", true) { SampleAccents() }
}
