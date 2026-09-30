package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import com.github.takahirom.roborazzi.captureRoboImage
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The top of the Work space and its list icons, in both themes, so the look can be reviewed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class WorkHeaderScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the header and the list icons render in both themes`() {
        val dark = mutableStateOf(true)
        compose.setContent {
            MeetMindTheme(darkTheme = dark.value) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column {
                        WorkMasthead(now = 1_790_000_000_000L, status = statusLine(3, 1, 2, "x"), onBack = {}, onSearch = {}, onSettings = {})
                        IntroCard(WorkProfile.CLIENT_WORK, onTry = {}, onRecord = {}, onDismiss = {})
                        listOf(
                            RecordingType.MEETING to "Client kickoff", RecordingType.CLIENT_CALL to "Acme review",
                            RecordingType.ONE_ON_ONE to "Weekly 1:1", RecordingType.GENERAL to "Action items · Work Notes",
                            RecordingType.GENERAL to "Summary · Work Notes", RecordingType.DECISION_RECORD to "Launch date"
                        ).forEach { (type, title) ->
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                WorkTypeTile(type, title)
                                Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 14.dp))
                            }
                        }
                    }
                }
            }
        }
        for (d in listOf(true, false)) {
            dark.value = d
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/work_header_${if (d) "dark" else "light"}.png")
        }
    }

    @Test
    fun `the status line says what is live, or falls back`() {
        assertEquals("3 tasks · 1 waiting on · 2 to review", statusLine(3, 1, 2, "fallback"))
        assertEquals("1 task", statusLine(1, 0, 0, "fallback"))
        assertEquals("fallback", statusLine(0, 0, 0, "fallback"))
    }
}
