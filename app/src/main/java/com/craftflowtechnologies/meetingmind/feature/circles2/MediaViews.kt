package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.MediaKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** A photo, voice note or file read from the device, ready to upload. */
class PickedFile(val bytes: ByteArray, val mime: String, val name: String?, val durationMs: Long? = null)

/**
 * A stored image, fetched through the Worker (members only). Shows a plain block until the bytes arrive,
 * and a plain block if they never do.
 */
@Composable
fun MediaImage(circleId: String, path: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val repo = remember(context) { Circles2.repository(context) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, circleId, path) {
        value = null
        if (path == null) return@produceState
        val bytes = repo.api.downloadMedia(circleId, path).okOrNull ?: return@produceState
        value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MM.colors.surfaceSunk))
    }
}

/** A voice note: play or pause. The bytes are fetched on the first tap and kept in the app cache. */
@Composable
fun VoiceBubble(circleId: String, a: MediaAttachment) {
    val context = LocalContext.current
    val repo = remember(context) { Circles2.repository(context) }
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    DisposableEffect(a.path) { onDispose { player?.release(); player = null } }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        IconButton(
            onClick = {
                val current = player
                if (current != null) {
                    if (playing) current.pause() else current.start()
                    playing = !playing
                    return@IconButton
                }
                if (loading) return@IconButton
                loading = true
                scope.launch {
                    val bytes = repo.api.downloadMedia(circleId, a.path).okOrNull
                    if (bytes != null) {
                        val file = File(context.cacheDir, "voice_${a.path.substringAfterLast('/')}.m4a")
                        file.writeBytes(bytes)
                        player = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnCompletionListener { playing = false }
                            prepare()
                            start()
                        }
                        playing = true
                    }
                    loading = false
                }
            },
            modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MM.colors.accent)
        ) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause voice note" else "Play voice note", tint = MM.colors.surface)
        }
        Text(
            "Voice note · ${formatDuration(a.durationMs)}",
            style = MM.type.caption, color = MM.colors.inkSecondary
        )
    }
}

/** A file: its name and size, and a save action that writes the bytes where the person chooses. */
@Composable
fun FileBubble(circleId: String, a: MediaAttachment) {
    val context = LocalContext.current
    val repo = remember(context) { Circles2.repository(context) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(a.mime)) { uri: Uri? ->
        val bytes = pending
        if (uri != null && bytes != null) context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        pending = null
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Icon(Icons.Rounded.InsertDriveFile, null, tint = MM.colors.inkSecondary)
        Column {
            Text(a.name ?: "File", style = MM.type.bodyStrong, color = MM.colors.ink)
            Text("${formatBytes(a.bytes)} · PDF", style = MM.type.caption, color = MM.colors.inkSecondary)
        }
        TextAction("Save", {
            scope.launch {
                val bytes = repo.api.downloadMedia(circleId, a.path).okOrNull ?: return@launch
                pending = bytes
                saver.launch(a.name ?: "file.pdf")
            }
        })
    }
}

/**
 * Reads a picked document into memory. Every photo is re-encoded as a JPEG, which drops EXIF metadata such as
 * GPS location. Oversized photos are also shrunk. Other files are sent as they are; large ones are refused upstream.
 */
fun readPicked(context: Context, uri: Uri): PickedFile? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: return null
    val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val name = resolver.query(uri, null, null, null, null)?.use { c ->
        val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && index >= 0) c.getString(index) else null
    }
    if (mime.startsWith("image/")) {
        // Refuse a photo we can't decode rather than send it with its metadata intact.
        return reencodeToJpeg(raw)?.let { PickedFile(it, "image/jpeg", name) }
    }
    return PickedFile(raw, mime, name)
}

/**
 * Longest side at most 2048 px, JPEG at 85%. Re-encoding writes only the pixels, so the output carries no EXIF.
 * Null when the picture can't be decoded or still won't fit.
 */
private fun reencodeToJpeg(bytes: ByteArray): ByteArray? {
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val scale = minOf(1f, 2048f / maxOf(bitmap.width, bitmap.height))
    if (scale < 1f) bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray().takeIf { it.size <= MediaKind.Image.maxBytes }
}

/** Records one voice note to the app cache as AAC in an MP4 container. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    /** False when the microphone can't be used right now. */
    fun start(): Boolean {
        val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r; file = f; startedAt = System.currentTimeMillis()
            true
        } catch (e: RuntimeException) {
            r.release(); f.delete(); false
        }
    }

    /** Stops and returns the recording, or null when it was too short to keep. */
    fun stop(): PickedFile? {
        val r = recorder ?: return null
        val f = file
        val durationMs = System.currentTimeMillis() - startedAt
        recorder = null; file = null
        val stopped = try { r.stop(); true } catch (e: RuntimeException) { false }
        r.release()
        if (!stopped || f == null) { f?.delete(); return null }
        val bytes = try { f.readBytes() } catch (e: java.io.IOException) { null }
        f.delete()
        return bytes?.takeIf { it.isNotEmpty() }?.let { PickedFile(it, "audio/mp4", "Voice note", durationMs) }
    }

    fun cancel() {
        recorder?.let { try { it.stop() } catch (e: RuntimeException) { } ; it.release() }
        recorder = null
        file?.delete(); file = null
    }
}

fun formatDuration(ms: Long?): String {
    val s = ((ms ?: 0L) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}
