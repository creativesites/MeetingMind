package com.craftflowtechnologies.meetingmind.feature.circles2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.craftflowtechnologies.meetingmind.core.circles2.SharedPrefsPushTokenStore

/**
 * Asks for notification permission in context: right after someone joins or creates their first circle, when
 * "tell me when something happens here" makes sense. Returns a function that runs `next` once the question is
 * settled (immediately when there is nothing to ask). It asks once; "No" is respected.
 */
@Composable
fun rememberNotificationAsk(): (next: () -> Unit) -> Unit {
    val context = LocalContext.current
    val store = remember { SharedPrefsPushTokenStore(context) }
    val waiting = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        waiting.value?.invoke()
        waiting.value = null
    }
    return { next ->
        val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !store.askedPermission
        if (needs) {
            store.askedPermission = true
            waiting.value = next
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else next()
    }
}
