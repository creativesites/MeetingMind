package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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

import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.widget.VideoView
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A photo, video, voice note or file read from the device, ready to upload. */
class PickedFile(val bytes: ByteArray, val mime: String, val name: String?, val durationMs: Long? = null)

/**
 * Two-tier cache (L1 in-memory LRU up to 24 MB + L2 disk cache under cacheDir/circles_media)
 * so scrolling lazy lists never re-downloads media bytes from the Worker on every recomposition.
 */
object CircleMediaCache {
    private val memory = object : LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size.coerceAtLeast(1)
    }

    private fun diskFile(context: Context, path: String, ext: String = "bin"): File {
        val dir = File(context.cacheDir, "circles_media").apply { mkdirs() }
        val safe = path.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(dir, "$safe.$ext")
    }

    suspend fun fetch(context: Context, circleId: String, path: String): ByteArray? = withContext(Dispatchers.IO) {
        memory.get(path)?.let { return@withContext it }
        val file = diskFile(context, path)
        if (file.exists() && file.length() > 0L) {
            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes != null) {
                if (bytes.size <= 8 * 1024 * 1024) memory.put(path, bytes)
                return@withContext bytes
            }
        }
        val repo = Circles2.repository(context)
        val downloaded = repo.api.downloadMedia(circleId, path).okOrNull ?: return@withContext null
        runCatching { file.writeBytes(downloaded) }
        if (downloaded.size <= 8 * 1024 * 1024) memory.put(path, downloaded)
        downloaded
    }

    suspend fun fetchToFile(context: Context, circleId: String, path: String, ext: String): File? = withContext(Dispatchers.IO) {
        val file = diskFile(context, path, ext)
        if (file.exists() && file.length() > 0L) return@withContext file
        val bytes = fetch(context, circleId, path) ?: return@withContext null
        runCatching { file.writeBytes(bytes) }
        file.takeIf { it.exists() }
    }

    /**
     * A member's profile photo. The version is part of the key, so a new photo is fetched and an old one is never shown:
     * the Worker bumps the version whenever the photo changes or is removed.
     */
    suspend fun fetchAvatar(context: Context, circleId: String, uid: String, version: Long): ByteArray? = withContext(Dispatchers.IO) {
        val key = "avatar/$circleId/$uid/$version"
        memory.get(key)?.let { return@withContext it }
        val file = diskFile(context, key, "jpg")
        if (file.exists() && file.length() > 0L) {
            runCatching { file.readBytes() }.getOrNull()?.let { bytes ->
                memory.put(key, bytes)
                return@withContext bytes
            }
        }
        val bytes = Circles2.repository(context).api.downloadAvatar(circleId, uid).okOrNull ?: return@withContext null
        runCatching { file.writeBytes(bytes) }
        memory.put(key, bytes)
        bytes
    }
}

/** A profile photo: a centre-cropped square, at most 512 px, as JPEG. Re-encoding drops any EXIF. Null if it can't be read. */
fun avatarJpeg(bytes: ByteArray): ByteArray? {
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val side = minOf(source.width, source.height)
    val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val sized = if (side > 512) Bitmap.createScaledBitmap(square, 512, 512, true) else square
    val out = ByteArrayOutputStream()
    sized.compress(Bitmap.CompressFormat.JPEG, 88, out)
    return out.toByteArray().takeIf { it.isNotEmpty() }
}

/**
 * A member's photo, or their initials until it loads or when they have none. Photos are fetched through the Worker
 * for members of [circleId] and cached by version.
 */
@Composable
fun MemberAvatar(
    circleId: String,
    uid: String?,
    name: String,
    avatarVersion: Long?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    online: Boolean = false
) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, circleId, uid, avatarVersion) {
        value = null
        if (uid == null || avatarVersion == null) return@produceState
        val bytes = CircleMediaCache.fetchAvatar(context, circleId, uid, avatarVersion) ?: return@produceState
        value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    Crossfade(targetState = photo, label = "member avatar") { loaded ->
        if (loaded != null) {
            Box(modifier.size(size).clip(CircleShape).clearAndSetSemantics { }) {
                Image(loaded, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        } else {
            InitialsAvatar(name, modifier, size, online)
        }
    }
}

/** Whether a stored attachment is a video. */
val MediaAttachment.isVideo: Boolean get() = mime.startsWith("video/")

/** A photo or video on a post. Photos open fullscreen; videos play in a player when tapped. */
@Composable
fun AttachmentTile(circleId: String, a: MediaAttachment, modifier: Modifier, contentDescription: String) {
    if (a.isVideo) VideoTile(circleId, a, modifier, contentDescription)
    else MediaImage(circleId = circleId, path = a.path, modifier = modifier, contentDescription = contentDescription, allowFullscreen = true)
}

/** A video on a post, as a tile: the length and size, and a play button. */
@Composable
private fun VideoTile(circleId: String, a: MediaAttachment, modifier: Modifier, contentDescription: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    Box(
        modifier
            .background(MM.colors.surfaceSunk)
            .clickable(enabled = !loading) {
                loading = true
                scope.launch {
                    videoFile = CircleMediaCache.fetchToFile(context, circleId, a.path, if (a.mime == "video/webm") "webm" else "mp4")
                    loading = false
                }
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(CirclesPalette.Accent),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = CirclesPalette.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.PlayArrow, null, tint = CirclesPalette.White, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            listOfNotNull(a.durationMs?.takeIf { it > 0 }?.let(::formatDuration), formatBytes(a.bytes)).joinToString(" · "),
            style = MM.type.caption,
            color = MM.colors.inkSecondary,
            modifier = Modifier.align(Alignment.BottomStart).padding(MM.space.s)
        )
    }
    videoFile?.let { file -> VideoPlayerDialog(file) { videoFile = null } }
}

/** A fullscreen video player for a file already on disk. */
@Composable
fun VideoPlayerDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MM.colors.background)) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(file.absolutePath)
                        setOnPreparedListener { mp -> mp.isLooping = false; start() }
                    }
                },
                modifier = Modifier.fillMaxSize().align(Alignment.Center)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(MM.space.m)) {
                Icon(Icons.Rounded.Close, "Close video", tint = MM.colors.ink)
            }
        }
    }
}

/**
 * A stored image, fetched through the Worker (members only) and cached in L1/L2.
 * Tapping opens a fullscreen preview dialog when [allowFullscreen] is true.
 */
@Composable
fun MediaImage(
    circleId: String,
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    allowFullscreen: Boolean = false
) {
    val context = LocalContext.current
    var showFull by remember { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, circleId, path) {
        value = null
        if (path == null) return@produceState
        val bytes = CircleMediaCache.fetch(context, circleId, path) ?: return@produceState
        value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    val loaded = bitmap
    if (loaded != null) {
        val imgMod = if (allowFullscreen) modifier.clickable { showFull = true } else modifier
        Image(loaded, contentDescription, imgMod, contentScale = ContentScale.Crop)
        if (showFull) {
            Dialog(onDismissRequest = { showFull = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().background(MM.colors.background.copy(alpha = 0.96f)).clickable { showFull = false }) {
                    Image(loaded, contentDescription, Modifier.fillMaxSize().padding(MM.space.m), contentScale = ContentScale.Fit)
                    IconButton(onClick = { showFull = false }, modifier = Modifier.align(Alignment.TopEnd).padding(MM.space.m)) {
                        Icon(Icons.Rounded.Close, "Close preview", tint = MM.colors.ink)
                    }
                }
            }
        }
    } else {
        Box(modifier.background(MM.colors.surfaceSunk))
    }
}

/** A voice note: play or pause with a 12-bar audio waveform (`circle_chat.html`). */
@Composable
fun VoiceBubble(circleId: String, a: MediaAttachment, tint: androidx.compose.ui.graphics.Color = CirclesPalette.Accent) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    DisposableEffect(a.path) { onDispose { player?.release(); player = null } }
    val bars = remember { listOf(6, 12, 18, 10, 22, 14, 16, 8, 20, 12, 8, 14) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
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
                    val file = CircleMediaCache.fetchToFile(context, circleId, a.path, "m4a")
                    if (file != null) {
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
            modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint)
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pause voice note" else "Play voice note",
                tint = CirclesPalette.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.height(24.dp)
        ) {
            bars.forEachIndexed { idx, h ->
                val active = playing || idx < 5
                Box(
                    Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        .background(if (active) tint else MM.colors.line)
                )
            }
        }
        Text(
            formatDuration(a.durationMs),
            style = MM.type.caption,
            color = MM.colors.inkSecondary
        )
    }
}

/** A video attachment: shows duration and size, and opens a fullscreen player when tapped. */
@Composable
fun VideoBubble(circleId: String, a: MediaAttachment) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        IconButton(
            onClick = {
                if (loading) return@IconButton
                loading = true
                scope.launch {
                    val ext = if (a.mime == "video/webm") "webm" else "mp4"
                    videoFile = CircleMediaCache.fetchToFile(context, circleId, a.path, ext)
                    loading = false
                }
            },
            modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MM.colors.accent)
        ) {
            Icon(Icons.Rounded.Videocam, "Play video", tint = MM.colors.surface)
        }
        Column {
            Text(a.name ?: "Video", style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(
                listOfNotNull(a.durationMs?.takeIf { it > 0 }?.let(::formatDuration), formatBytes(a.bytes), if (loading) "Loading…" else null).joinToString(" · "),
                style = MM.type.caption, color = MM.colors.inkSecondary
            )
        }
    }
    videoFile?.let { file -> VideoPlayerDialog(file) { videoFile = null } }
}

/** A file: its name and size, and a save action that writes the bytes where the person chooses. */
@Composable
fun FileBubble(circleId: String, a: MediaAttachment) {
    val context = LocalContext.current
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
                val bytes = CircleMediaCache.fetch(context, circleId, a.path) ?: return@launch
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
    val name = resolver.query(uri, null, null, null, null)?.use { c ->
        val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && index >= 0) c.getString(index) else null
    }
    var mime = resolver.getType(uri)
    if (mime == null || mime == "application/octet-stream") {
        val ext = (name?.substringAfterLast('.', "") ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())).lowercase()
        if (ext.isNotBlank()) {
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
    }
    if (mime == "application/x-pdf") {
        mime = "application/pdf"
    }

    // A video is checked by size before it is read, so a long clip can't exhaust memory.
    if (mime?.startsWith("video/") == true) {
        val size = runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
        if (size > MediaKind.Video.maxBytes) return null
    }

    val raw = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null

    // If MIME is still unresolved or generic, sniff magic bytes
    if (mime == null || mime == "application/octet-stream") {
        mime = when {
            raw.size >= 3 && (raw[0].toInt() and 0xFF) == 0xFF && (raw[1].toInt() and 0xFF) == 0xD8 && (raw[2].toInt() and 0xFF) == 0xFF -> "image/jpeg"
            raw.size >= 8 && (raw[0].toInt() and 0xFF) == 0x89 && (raw[1].toInt() and 0xFF) == 0x50 && (raw[2].toInt() and 0xFF) == 0x4E -> "image/png"
            raw.size >= 5 && raw.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-" -> "application/pdf"
            raw.size >= 8 && raw.take(4).toByteArray().toString(Charsets.US_ASCII) == "RIFF" && raw.drop(8).take(4).toByteArray().toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
            raw.size >= 8 && raw.drop(4).take(4).toByteArray().toString(Charsets.US_ASCII) == "ftyp" -> "video/mp4"
            else -> mime ?: return null
        }
    }

    if (mime.startsWith("image/")) {
        // Refuse a photo we can't decode rather than send it with its metadata intact.
        return reencodeToJpeg(raw)?.let { PickedFile(it, "image/jpeg", name) }
    }
    if (mime.startsWith("video/")) {
        if (raw.size > MediaKind.Video.maxBytes) return null
        val durationMs = runCatching {
            android.media.MediaMetadataRetriever().use { r ->
                r.setDataSource(context, uri)
                r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }
        }.getOrNull()
        val normalizedMime = if (raw.size >= 8 && raw.drop(4).take(4).toByteArray().toString(Charsets.US_ASCII) == "ftyp") "video/mp4" else mime
        return PickedFile(raw, normalizedMime, name, durationMs)
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
