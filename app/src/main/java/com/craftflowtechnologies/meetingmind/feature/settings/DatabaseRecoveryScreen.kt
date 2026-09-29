package com.craftflowtechnologies.meetingmind.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.database.DatabaseGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown instead of the app when the notes database won't open after an update. Nothing has been
 * deleted; this screen gets the data out and lets the person try again.
 */
@Composable
fun DatabaseRecoveryScreen(error: String, onRetry: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { DatabaseGuard.exportRaw(context, it) } ?: error("Couldn't write the file.")
                }
                "Saved. Keep this file safe until MeetingMind opens normally again."
            }.getOrElse { "Couldn't save: ${it.message}" }
        }
    }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("MeetingMind couldn't open your notes", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your notes are still on this phone. Nothing has been deleted. This update couldn't open " +
                "them, so save a copy now, then try again or install the next update.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = { export.launch("MeetingMind-recovery.zip") }, modifier = Modifier.fillMaxWidth()) { Text("Save a copy of my data") }
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Details: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
