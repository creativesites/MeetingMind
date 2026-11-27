package com.craftflowtechnologies.meetingmind.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.export.ExportContentType
import com.craftflowtechnologies.meetingmind.core.export.ExportFormat

/**
 * Two-step "what to export, then in what format" picker. Exporting creates a persistent file the
 * user chooses where to save (via the system document picker) — a distinct action from Sharing
 * ("send to another app"), never conflated with it in this UI.
 */
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportContentType, ExportFormat) -> Unit,
    onShareAsFile: ((ExportContentType, ExportFormat) -> Unit)? = null
) {
    var selectedContentType by remember { mutableStateOf<ExportContentType?>(null) }
    var shareMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                selectedContentType?.let {
                    if (shareMode) "Share ${it.displayName} as…" else "Export ${it.displayName} as…"
                } ?: "Export"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val contentType = selectedContentType
                if (contentType == null) {
                    ExportContentType.entries.forEach { type ->
                        TextButton(
                            onClick = { selectedContentType = type },
                            modifier = Modifier.fillMaxWidth().testTag("export_content_${type.name}")
                        ) {
                            Text(type.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    ExportFormat.entries.filter { it.supportedFor(contentType) }.forEach { format ->
                        TextButton(
                            onClick = {
                                if (shareMode && onShareAsFile != null) {
                                    onShareAsFile(contentType, format)
                                } else {
                                    onExport(contentType, format)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("export_format_${format.name}")
                        ) {
                            Text(format.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (onShareAsFile != null) {
                        TextButton(
                            onClick = { shareMode = !shareMode },
                            modifier = Modifier.fillMaxWidth().testTag("export_toggle_share_mode")
                        ) {
                            Text(
                                if (shareMode) "Switch to Save to device" else "Switch to Send to app…",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                if (selectedContentType != null) {
                    selectedContentType = null
                    shareMode = false
                } else {
                    onDismiss()
                }
            }) {
                Text(if (selectedContentType != null) "Back" else "Cancel")
            }
        }
    )
}
