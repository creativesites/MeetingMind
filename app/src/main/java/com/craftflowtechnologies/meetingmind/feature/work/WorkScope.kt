package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes

/**
 * The [WorkViewModel] for a Work screen. Its owner is the back-stack entry of the Work route when
 * that is on the stack, so the Work screens opened from it share one instance and it is cleared when
 * Work is left. Reached any other way (from Home, a deep link), the screen's own entry owns it and
 * it goes when that screen does. Never the activity, whose view-models live as long as the app.
 */
@Composable
fun workViewModel(navController: NavController): WorkViewModel {
    val self = checkNotNull(LocalViewModelStoreOwner.current) { "Work screens live in a navigation entry" }
    val owner = remember(self) { runCatching { navController.getBackStackEntry(Routes.WORK) }.getOrNull() ?: self }
    return viewModel(viewModelStoreOwner = owner)
}
