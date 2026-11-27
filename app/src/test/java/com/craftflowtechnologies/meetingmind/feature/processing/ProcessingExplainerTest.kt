package com.craftflowtechnologies.meetingmind.feature.processing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "What's happening?" on the processing wait: one sentence per running stage, and where it runs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ProcessingExplainerTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun everyRunningStageHasASentenceAndEndStatesHaveNone() {
        val ended = setOf(ProcessingStage.COMPLETED, ProcessingStage.FAILED, ProcessingStage.CANCELLED)
        ProcessingStage.entries.forEach { stage ->
            if (stage in ended) assertNull(stage.name, ProcessingExplainer.stageSentence(stage))
            else assertNotNull(stage.name, ProcessingExplainer.stageSentence(stage))
        }
        assertNotNull(ProcessingExplainer.stageSentence(null))
    }

    @Test fun whereItRunsFollowsTheProfile() {
        assertEquals(R.string.processing_explain_where_device, ProcessingExplainer.whereSentence(ProcessingProfile.OFFLINE))
        assertEquals(R.string.processing_explain_where_cloud, ProcessingExplainer.whereSentence(ProcessingProfile.INTERNET))
    }

    @Test fun closedByDefaultAndOneTapOpensTheStageSentence() {
        compose.setContent { MeetMindTheme { WhatsHappening(ProcessingStage.TRANSCRIBING, ProcessingProfile.OFFLINE) } }
        compose.onNodeWithTag("processing_explainer_stage").assertDoesNotExist()
        compose.onNodeWithTag("processing_explainer_toggle").performClick()
        compose.onNodeWithTag("processing_explainer_stage")
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.processing_explain_transcribing))
    }

    @Test fun nothingShowsOnceTheWorkHasEnded() {
        compose.setContent { MeetMindTheme { WhatsHappening(ProcessingStage.COMPLETED, ProcessingProfile.INTERNET) } }
        compose.onNodeWithTag("processing_explainer").assertDoesNotExist()
    }
}
