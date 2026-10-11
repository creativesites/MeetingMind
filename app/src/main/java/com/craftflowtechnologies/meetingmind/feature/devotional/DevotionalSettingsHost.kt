package com.craftflowtechnologies.meetingmind.feature.devotional

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import kotlinx.coroutines.flow.first

/**
 * The devotional settings as a screen of Settings > Faith. It is the same [DevotionalSettingsContent] the tune icon
 * on the devotional opens, saved the same way ([DevotionalViewModel.saveProfile], and a rewrite when asked), so
 * there is one set of devotional controls reached from two places.
 *
 * The content copies the profile it is given when it first appears, so nothing is drawn until the saved one has
 * been read; drawing defaults first and saving would overwrite the person's choices.
 */
@Composable
fun DevotionalSettingsScreen(startPage: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val vm: DevotionalViewModel = viewModel()
    val profile by produceState<DevotionalProfile?>(null) { value = UserPreferencesManager(context).devotionalProfile.first() }
    profile?.let { saved ->
        DevotionalSettingsContent(
            profile = saved,
            onSave = { p, rewrite -> vm.saveProfile(p); if (rewrite) vm.rewrite(); onClose() },
            onDismiss = onClose,
            startPage = startPage
        )
    }
}
