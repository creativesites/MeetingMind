package com.craftflowtechnologies.meetingmind.feature.applock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.applock.AppLockAvailability
import com.craftflowtechnologies.meetingmind.core.applock.AppLockState
import com.craftflowtechnologies.meetingmind.core.applock.AppLockViewModel
import com.craftflowtechnologies.meetingmind.core.applock.AuthPrompt
import com.craftflowtechnologies.meetingmind.core.applock.AuthResult
import com.craftflowtechnologies.meetingmind.core.applock.BiometricPromptAuthenticator
import com.craftflowtechnologies.meetingmind.core.applock.unlockWith
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single place App Lock takes effect. While the lock is engaged [content] is **not composed
 * at all** — not merely covered — so no note, transcript, dialog or sheet can draw over the lock
 * screen (dialogs are separate windows and would otherwise sit on top of any overlay).
 *
 * Nothing that lives above this gate is lost by locking: the NavController and every ViewModel are
 * held higher up, so the person returns to where they were. Deep links wait in `DeepLinks.pending`
 * and are consumed by [content] only after it is composed again, i.e. after unlocking.
 */
@Composable
fun AppLockGate(viewModel: AppLockViewModel, content: @Composable () -> Unit) {
    val ready by viewModel.ready.collectAsState()
    val state by viewModel.state.collectAsState()
    AppLockGateSwitch(ready, state, lockScreen = { AppLockScreen(viewModel) }, content = content)
}

/** The gate's whole decision, separate from the ViewModel so tests can drive it directly. */
@Composable
internal fun AppLockGateSwitch(
    ready: Boolean,
    state: AppLockState,
    lockScreen: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        // Preference not read yet: show nothing rather than guess.
        !ready -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        state.isContentHidden -> lockScreen()
        else -> content()
    }
}

@Composable
private fun AppLockScreen(viewModel: AppLockViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val authenticator = remember(activity) { activity?.let { BiometricPromptAuthenticator(it) } }
    val keyguard = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = viewModel.controller

    var availability by remember { mutableStateOf(authenticator?.availability() ?: AppLockAvailability.Unsupported) }
    var lastResult by remember { mutableStateOf<AuthResult?>(null) }
    // Reset per lock episode so returning to the app offers the prompt again without being trapped.
    var autoPrompted by remember { mutableStateOf(false) }
    val prompt = remember { viewModel.unlockPrompt() }

    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            controller.onUnlockSucceeded()
        } else {
            controller.onUnlockFailed()
        }
    }

    fun attemptDeviceCredential() {
        @Suppress("DEPRECATION")
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            context.getString(R.string.app_lock_title),
            context.getString(R.string.app_lock_prompt_unlock_subtitle)
        )
        if (intent != null) {
            controller.beginUnlock()
            credentialLauncher.launch(intent)
        }
    }

    suspend fun attempt() {
        if (authenticator == null) return
        availability = authenticator.availability()
        lastResult = controller.unlockWith(authenticator, prompt)
    }

    LaunchedEffect(authenticator) {
        if (autoPrompted || authenticator == null) return@LaunchedEffect
        // Wait until fully visible; deliberately not cancelled if we pause again, because the
        // screen-lock fallback can briefly take the foreground on some Android versions.
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        attempt()
        autoPrompted = true
    }

    AppLockContent(
        availability = availability,
        lastResult = lastResult,
        onUnlock = { scope.launch { attempt() } },
        onOpenSecuritySettings = { context.openSecuritySettings() },
        onTurnOff = { viewModel.turnOffWithoutAuthentication(availability) },
        onUnlockWithDeviceCredential = if (keyguard?.isDeviceSecure == true) { { attemptDeviceCredential() } } else null
    )
}

/** Stateless lock screen — previewable and testable without a BiometricPrompt. */
@Composable
fun AppLockContent(
    availability: AppLockAvailability,
    lastResult: AuthResult?,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onTurnOff: () -> Unit,
    onUnlockWithDeviceCredential: (() -> Unit)? = null
) {
    val noCredential = availability.hasNoOwnerCredential
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Brand.Navy, Brand.NavyLift, Brand.Navy)))
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Decorative: the heading below already names the app.
            Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(width = 96.dp, height = 82.dp))
            Text(
                stringResource(R.string.app_lock_title),
                color = OnNavy, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp).semantics { heading() }
            )
            Text(
                stringResource(R.string.app_lock_body),
                color = Color.White.copy(alpha = 0.78f), fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )

            val notice = noticeFor(availability, lastResult)
            if (notice != null) {
                Text(
                    stringResource(notice),
                    color = com.craftflowtechnologies.meetingmind.ui.theme.Warning, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("app_lock_notice")
                )
            }

            if (!noCredential) {
                UnlockButton(onUnlock)
                if (onUnlockWithDeviceCredential != null && lastResult != null) {
                    TextButton(
                        onClick = onUnlockWithDeviceCredential,
                        modifier = Modifier.padding(top = 10.dp).heightIn(min = 48.dp).testTag("app_lock_unlock_pin")
                    ) {
                        Text(stringResource(R.string.app_lock_unlock_with_pin), color = Brand.Cyan, fontSize = 15.sp)
                    }
                }
            } else {
                // The phone can no longer verify its owner (screen lock removed), so App Lock cannot
                // protect anything and must not trap the person out of their own notes.
                TextButton(onClick = onOpenSecuritySettings, modifier = Modifier.padding(top = 22.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.app_lock_open_security_settings), color = Brand.Cyan, fontSize = 15.sp)
                }
                TextButton(onClick = onTurnOff, modifier = Modifier.heightIn(min = 48.dp).testTag("app_lock_turn_off")) {
                    Text(stringResource(R.string.app_lock_turn_off), color = OnNavy, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun UnlockButton(onClick: () -> Unit) {
    val label = stringResource(R.string.app_lock_unlock)
    Row(
        Modifier.padding(top = 28.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Brand.Cyan, Brand.Indigo, Brand.Violet)))
            .clickableWithRole(onClick, label)
            .testTag("app_lock_unlock")
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = OnNavy, modifier = Modifier.size(22.dp))
        Box(Modifier.width(10.dp))
        Text(label, color = OnNavy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun noticeFor(availability: AppLockAvailability, result: AuthResult?): Int? = when {
    availability.hasNoOwnerCredential -> R.string.app_lock_notice_no_credential
    availability == AppLockAvailability.HardwareUnavailable -> R.string.app_lock_notice_hardware_unavailable
    availability == AppLockAvailability.SecurityUpdateRequired -> R.string.app_lock_notice_update_required
    availability == AppLockAvailability.Unsupported -> R.string.app_lock_notice_unsupported
    result is AuthResult.LockedOut -> R.string.app_lock_notice_locked_out
    result is AuthResult.Error -> R.string.app_lock_notice_error
    else -> null
}

internal fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Takes the person to where a screen lock or biometric can be set up. */
fun Context.openSecuritySettings() {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= 30) add(Intent(Settings.ACTION_BIOMETRIC_ENROLL))
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in intents) {
        if (runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

/** Text on the lock screen's navy: white whatever the app theme. */
private val OnNavy = Color.White.copy(alpha = 1f)
