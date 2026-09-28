package com.example.feature.settings

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.backup.AutoBackupState
import com.example.core.backup.BackupFormat
import com.example.core.backup.BackupManager
import com.example.core.backup.BackupManifest
import com.example.core.backup.BackupOptions
import com.example.core.backup.BackupRejected
import com.example.core.backup.BackupSchedule
import com.example.core.backup.BackupScheduler
import com.example.core.backup.MarkdownVaultExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.Date

/**
 * Settings → Data & backup (docs/PRD_M0.md §4.3–4.5): back up now, restore, automatic backups,
 * export everything as Markdown, and the Trash.
 */
@Composable
fun DataBackupScreen(onNavigateBack: () -> Unit, onOpenTrash: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf(BackupOptions()) }
    var auto by remember { mutableStateOf(BackupScheduler.state(context)) }
    var pendingRestore by remember { mutableStateOf<Pair<Uri, BackupManifest>?>(null) }

    fun run(label: String, block: suspend () -> String) {
        busy = label
        scope.launch {
            message = runCatching { block() }.getOrElse { e -> (e as? BackupRejected)?.message ?: "Something went wrong: ${e.message}" }
            busy = null
        }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        run("Backing up…") {
            val manifest = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { BackupManager(context).write(it, options) } }
                ?: error("Couldn't write the file.")
            "Backed up ${manifest.counts["notes"] ?: 0} notes. Keep the file somewhere safe, like Google Drive."
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        run("Reading the backup…") {
            val manifest = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { BackupManager(context).inspect(it) } }
                ?: error("Couldn't open the file.")
            pendingRestore = uri to manifest
            ""
        }
    }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        run("Exporting…") {
            val count = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { MarkdownVaultExporter(context).write(it) } }
                ?: error("Couldn't write the file.")
            "Exported $count notes as Markdown."
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val next = auto.copy(folder = uri, schedule = if (auto.schedule == BackupSchedule.OFF) BackupSchedule.DAILY else auto.schedule)
        BackupScheduler.update(context, next)
        auto = BackupScheduler.state(context)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(start = 6.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Data & backup", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Your notes live on this phone. A backup is one file with every note, notebook, picture, highlight and setting; restore it on any phone.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
        )

        busy?.let {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp).height(20.dp), strokeWidth = 2.dp)
                Text(it)
            }
        }
        message?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
        }

        Section("Back up")
        Row_("Back up now", "Save a backup file wherever you like") {
            saveBackup.launch(BackupFormat.fileName(LocalDateTime.now()))
        }
        Toggle("Include recordings", "Audio can be large; notes and transcripts are always included", options.includeRecordings) { options = options.copy(includeRecordings = it) }
        Toggle("Include devotional audio", "The voice recordings of your devotionals", options.includeDevotionalAudio) { options = options.copy(includeDevotionalAudio = it) }
        Toggle("Include my Gemini API key", "Off keeps the key out of the file; you'd add it again after restoring", options.includeApiKey) { options = options.copy(includeApiKey = it) }

        Section("Automatic backups")
        Row_(
            "Folder",
            auto.folder?.let { Uri.decode(it.lastPathSegment ?: it.toString()).substringAfterLast(':').ifBlank { "Chosen folder" } } ?: "Choose a folder: Google Drive, Downloads, anywhere"
        ) { pickFolder.launch(null) }
        if (auto.folder != null) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupSchedule.entries.forEach { s ->
                    TextButton(onClick = { BackupScheduler.update(context, auto.copy(schedule = s)); auto = BackupScheduler.state(context) }) {
                        Text(s.label, fontWeight = if (auto.schedule == s) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Toggle("Include recordings", "In automatic backups too", auto.includeRecordings) {
                BackupScheduler.update(context, auto.copy(includeRecordings = it)); auto = BackupScheduler.state(context)
            }
            Text(
                "Keeps the newest ${auto.keep}. " + lastRun(context, auto),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )
            Row_("Back up to this folder now", "Runs the automatic backup once") {
                val folder = auto.folder ?: return@Row_
                run("Backing up…") {
                    val size = withContext(Dispatchers.IO) { BackupScheduler.backUpTo(context, folder, auto.keep, BackupOptions(includeRecordings = auto.includeRecordings)) }
                    BackupScheduler.recordResult(context, size, null); auto = BackupScheduler.state(context)
                    "Backed up (${Formatter.formatShortFileSize(context, size)})."
                }
            }
        }

        Section("Restore")
        Row_("Restore from a backup", "Replaces everything here with the backup. What's here now is saved first.") { pickRestore.launch(arrayOf("*/*")) }

        Section("Export")
        Row_("Export all notes as Markdown", "A folder per notebook; opens in Obsidian and other Markdown apps") { exportMarkdown.launch("MeetingMind notes.zip") }

        Section("Deleted notes")
        Row_("Trash", "Deleted notes and notebooks, kept for 30 days") { onOpenTrash() }
    }

    pendingRestore?.let { (uri, manifest) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Column {
                    Text(describe(manifest))
                    Spacer(Modifier.height(10.dp))
                    Text("Everything in MeetingMind on this phone is replaced. A copy of what's here now is kept first. The app restarts when it's done.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    run("Restoring…") {
                        withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { BackupManager(context).restore(it) } } ?: error("Couldn't open the file.")
                        BackupManager(context).restartApp()
                        ""
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }
}

private fun describe(m: BackupManifest): String {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(m.createdAt))
    val parts = listOfNotNull(
        m.counts["notes"]?.let { "$it notes" },
        m.counts["notebooks"]?.let { "$it notebooks" },
        m.counts["recordings"]?.let { "$it recordings" + if (m.options.includeRecordings) "" else " (audio not included)" }
    )
    return "Backed up $date on ${m.device.ifBlank { "a phone" }} (MeetingMind ${m.appVersion}).\n" + parts.joinToString(", ")
}

private fun lastRun(context: android.content.Context, s: AutoBackupState): String = when {
    s.lastError != null -> "The last one didn't finish: ${s.lastError}"
    s.lastAt != null -> "Last: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastAt)) +
        (s.lastBytes?.let { " · " + Formatter.formatShortFileSize(context, it) } ?: "")
    else -> "None yet."
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 18.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun Row_(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
