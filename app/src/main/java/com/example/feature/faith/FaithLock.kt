package com.example.feature.faith

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.datastore.UserPreferencesManager
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary

/**
 * The optional lock on the Faith space and private Faith notes (docs/PLAN_V1.md §6, privacy).
 *
 * It uses the phone's own screen lock — fingerprint, face, PIN or pattern — so there is no second
 * password to forget. An unlock lasts a few minutes, so moving between Faith notes doesn't ask
 * again each time.
 */
object FaithLock {
    private const val UNLOCK_WINDOW_MS = 5 * 60 * 1000L
    @Volatile private var unlockedAt = 0L

    fun isUnlocked(now: Long = System.currentTimeMillis()) = now - unlockedAt < UNLOCK_WINDOW_MS
    fun markUnlocked() { unlockedAt = System.currentTimeMillis() }
}

/**
 * Shows [content] when the Faith lock is off or already unlocked; otherwise asks the phone to
 * confirm it's the owner. [required] lets a caller skip the gate (a note that isn't private).
 */
@Composable
fun FaithLockGate(required: Boolean = true, onCancel: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs by remember { UserPreferencesManager(context).preferencesFlow }.collectAsState(initial = null)
    var unlocked by remember { mutableStateOf(FaithLock.isUnlocked()) }
    val keyguard = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { FaithLock.markUnlocked(); unlocked = true }
    }
    fun ask() {
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent("Faith notes", "Confirm it's you to open your Faith notes")
        if (intent == null) { FaithLock.markUnlocked(); unlocked = true } else launcher.launch(intent)
    }

    val current = prefs ?: return
    if (!required || !current.faithLockEnabled || unlocked) { content(); return }

    LaunchedEffect(Unit) { if (keyguard.isDeviceSecure) ask() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(32.dp)) {
            Surface(shape = RoundedCornerShape(50), color = Accent.copy(alpha = 0.1f), modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Lock, contentDescription = null, tint = Accent) }
            }
            Text("Faith notes are locked", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                if (keyguard.isDeviceSecure) "Unlock with your fingerprint, face or screen lock."
                else "Set a screen lock on your phone to protect them — until then the lock can't keep anyone out.",
                fontSize = 14.sp, color = InkSecondary, textAlign = TextAlign.Center
            )
            if (keyguard.isDeviceSecure) TextButton(onClick = { ask() }) { Text("Unlock") }
            else TextButton(onClick = { FaithLock.markUnlocked(); unlocked = true }) { Text("Open anyway") }
            TextButton(onClick = onCancel) { Text("Go back", color = Color.Gray) }
        }
    }
}
