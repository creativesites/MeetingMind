package com.craftflowtechnologies.meetingmind.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme

/**
 * Wraps a Study screen the way every Learn test needs: the app theme, a fake companion store, and the companion's
 * frame clock held still (agents/TESTING.md gotcha 2), so Compose can go idle.
 */
@Composable
internal fun LearnHarness(dark: Boolean = false, content: @Composable () -> Unit) {
    MeetMindTheme(darkTheme = dark) {
        CompositionLocalProvider(
            LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings(sounds = false)),
            LocalCompanionForceCanvas provides true,
            LocalCompanionReducedMotion provides true
        ) {
            content()
        }
    }
}
