package com.craftflowtechnologies.meetingmind.feature.processing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * The notification question, asked where it makes sense: the first time a recording is being turned into
 * notes, so "tell me when it's ready" has an obvious meaning. Onboarding no longer asks (design 4d). It asks
 * once; "No thanks" is respected and never repeated. Nothing shows below Android 13 or once allowed.
 */
@Composable
internal fun NotifyWhenReadyPrimer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(NotifyPrimer.shouldAsk(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { visible = false }
    if (!visible) return
    MMCard(modifier.fillMaxWidth().testTag("notify_primer")) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.icon))
            Column(Modifier.weight(1f).padding(start = MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                androidx.compose.material3.Text(stringResource(R.string.notify_primer_title), style = MM.type.bodyStrong, color = MM.colors.ink)
                androidx.compose.material3.Text(stringResource(R.string.notify_primer_body), style = MM.type.secondary, color = MM.colors.inkSecondary)
                Row(Modifier.padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton(
                        stringResource(R.string.notify_primer_yes),
                        onClick = {
                            NotifyPrimer.markAsked(context)
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.heightIn(min = MMSize.minTouch).testTag("notify_primer_yes")
                    )
                    TextAction(
                        stringResource(R.string.notify_primer_no),
                        onClick = { NotifyPrimer.markAsked(context); visible = false },
                        modifier = Modifier.testTag("notify_primer_no")
                    )
                }
            }
        }
    }
}

/** Whether the notification question is still open. One shared answer for the whole app. */
internal object NotifyPrimer {
    private const val File = "meetmind_notify_primer"
    private const val Key = "asked"

    fun shouldAsk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return !granted && !context.applicationContext.getSharedPreferences(File, Context.MODE_PRIVATE).getBoolean(Key, false)
    }

    fun markAsked(context: Context) {
        context.applicationContext.getSharedPreferences(File, Context.MODE_PRIVATE).edit().putBoolean(Key, true).apply()
    }
}
