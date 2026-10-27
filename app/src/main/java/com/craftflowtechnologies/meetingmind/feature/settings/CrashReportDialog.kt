package com.craftflowtechnologies.meetingmind.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Diagnostics, opened from Settings. It holds the device, the last Java crash, how earlier processes
 * ended and the recent steps, so one copy or share is enough to report a problem. Nothing leaves the
 * phone unless the person shares it.
 */
@Composable
fun CrashReportDialog(report: String, onClear: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Diagnostics") },
        text = {
            Text(
                report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MeetingMind diagnostics", report))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "MeetingMind diagnostics")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(send, "Share diagnostics"))
                }) { Text("Share") }
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    )
}
