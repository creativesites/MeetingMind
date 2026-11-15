package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.MediaKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

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

    fun getCachedFile(context: Context, path: String, ext: String = "bin"): File? {
        val file = diskFile(context, path, ext)
        return if (file.exists() && file.length() > 0L) file else null
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

/** Decodes a byte array into a Bitmap using inSampleSize so max dimension <= [maxDimension]. */
private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int = 2048): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / sampleSize.toFloat() > maxDimension) {
        sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

/** A profile photo: a centre-cropped square, at most 512 px, as JPEG. Re-encoding drops any EXIF. Null if it can't be read. */
fun avatarJpeg(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    val shortest = minOf(bounds.outWidth, bounds.outHeight)
    while (shortest / sampleSize.toFloat() > 1024f) {
        sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
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
        value = withContext(Dispatchers.Default) {
            decodeSampledBitmap(bytes, 512)?.asImageBitmap()
        }
    }
    Crossfade(targetState = photo, label = "member avatar") { loaded ->
        if (loaded != null) {
            Box(modifier.size(size).clip(MM.radius.pill).clearAndSetSemantics { }) {
                Image(loaded, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        } else {
            InitialsAvatar(name, modifier, size, online)
        }
    }
}

/** Whether a stored attachment is a video. */
val MediaAttachment.isVideo: Boolean get() = mime.startsWith("video/")

/** Whether a stored attachment is a PDF document. */
val MediaAttachment.isPdf: Boolean get() = mime == "application/pdf" || (name?.endsWith(".pdf", ignoreCase = true) == true)

/** A photo, video or PDF on a post. Photos open fullscreen; videos play in a player; PDFs open in document viewer. */
@Composable
fun AttachmentTile(circleId: String, a: MediaAttachment, modifier: Modifier, contentDescription: String) {
    if (a.isVideo) VideoTile(circleId, a, modifier, contentDescription)
    else if (a.isPdf) PdfTile(circleId, a, modifier, contentDescription)
    else MediaImage(circleId = circleId, path = a.path, modifier = modifier, contentDescription = contentDescription, allowFullscreen = true)
}

/** A PDF document on a post, as a tile: filename, badge, and size. */
@Composable
private fun PdfTile(circleId: String, a: MediaAttachment, modifier: Modifier, contentDescription: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(MM.colors.surfaceSunk)
            .clickable(enabled = !loading) {
                loading = true
                scope.launch {
                    val file = CircleMediaCache.fetchToFile(context, circleId, a.path, "pdf")
                    loading = false
                    if (file != null) {
                        runCatching {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open PDF"))
                        }
                    }
                }
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MM.space.xs),
            modifier = Modifier.padding(MM.space.m)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(MM.radius.pill)
                    .background(CirclesPalette.Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = CirclesPalette.Accent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.InsertDriveFile, null, tint = CirclesPalette.Accent, modifier = Modifier.size(26.dp))
                }
            }
            Text(
                a.name ?: "Document.pdf",
                style = MM.type.bodyStrong,
                color = MM.colors.ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                formatBytes(a.bytes),
                style = MM.type.caption,
                color = MM.colors.inkSecondary
            )
        }
    }
}

/** Extracts the initial frame from a local video file. */
private fun extractVideoThumbnail(file: File): Bitmap? {
    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)
        }
    }.getOrNull()
}

/** Coordinated video player ensuring only one inline video plays at a time across feed and chat. */
object CircleVideoCoordinator {
    var activeVideoKey by mutableStateOf<String?>(null)
        private set

    val activeVideoId: String? get() = activeVideoKey

    private var onStopCallback: (() -> Unit)? = null

    fun play(key: String, onStop: () -> Unit) {
        if (activeVideoKey != null && activeVideoKey != key) {
            val prev = onStopCallback
            onStopCallback = null
            runCatching { prev?.invoke() }
        }
        activeVideoKey = key
        onStopCallback = onStop
    }

    fun pauseOrStop(key: String) {
        if (activeVideoKey == key) {
            val prev = onStopCallback
            onStopCallback = null
            activeVideoKey = null
            runCatching { prev?.invoke() }
        }
    }

    fun releaseIfCurrent(key: String) {
        if (activeVideoKey == key) {
            pauseOrStop(key)
        }
    }

    fun stopAll() {
        activeVideoKey?.let { pauseOrStop(it) }
    }

    fun isActive(key: String): Boolean = activeVideoKey == key
}

/**
 * Common inline video player:
 * - Shows extracted thumbnail + dark scrim + duration/size badge + centered Play button initially.
 * - Tapping plays the video inline via VideoView, muted by default.
 * - Tap video toggles pause/play.
 * - Bottom-right Mute/Unmute pill.
 * - Top-right Fullscreen expand pill that pauses inline playback and opens VideoPlayerDialog.
 * - Thin bottom LinearProgressIndicator updating while playing.
 */
@Composable
private fun InlineVideoPlayer(
    circleId: String,
    a: MediaAttachment,
    modifier: Modifier,
    contentDescription: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoKey = remember(circleId, a.path) { "${circleId}_${a.path}" }

    var videoFile by remember {
        mutableStateOf(
            CircleMediaCache.getCachedFile(
                context,
                a.path,
                if (a.mime == "video/webm") "webm" else "mp4"
            )
        )
    }
    var loading by remember { mutableStateOf(false) }
    var isInlinePlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    var showPlayer by remember { mutableStateOf<File?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(a.path) {
        if (videoFile == null) {
            val ext = if (a.mime == "video/webm") "webm" else "mp4"
            val file = CircleMediaCache.fetchToFile(context, circleId, a.path, ext)
            if (file != null) {
                videoFile = file
            }
        }
    }

    val thumbnail by produceState<ImageBitmap?>(initialValue = null, videoFile) {
        val file = videoFile ?: return@produceState
        val bmp = withContext(Dispatchers.IO) {
            extractVideoThumbnail(file)
        }
        value = bmp?.asImageBitmap()
    }

    DisposableEffect(videoKey) {
        onDispose {
            CircleVideoCoordinator.releaseIfCurrent(videoKey)
            runCatching { videoViewRef?.stopPlayback() }
            mediaPlayerRef = null
            videoViewRef = null
        }
    }

    LaunchedEffect(isInlinePlaying, isPaused) {
        if (isInlinePlaying && !isPaused) {
            while (isActive) {
                val vv = videoViewRef
                val dur = vv?.duration ?: 0
                val cur = vv?.currentPosition ?: 0
                if (dur > 0) {
                    playbackProgress = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
                delay(100)
            }
        }
    }

    val startPlayback: () -> Unit = {
        val cur = videoFile
        if (cur != null) {
            isInlinePlaying = true
            isPaused = false
            CircleVideoCoordinator.play(videoKey) {
                runCatching { videoViewRef?.pause() }
                isPaused = true
            }
            videoViewRef?.start()
        } else if (!loading) {
            loading = true
            scope.launch {
                val ext = if (a.mime == "video/webm") "webm" else "mp4"
                val file = CircleMediaCache.fetchToFile(context, circleId, a.path, ext)
                videoFile = file
                loading = false
                if (file != null) {
                    isInlinePlaying = true
                    isPaused = false
                    CircleVideoCoordinator.play(videoKey) {
                        runCatching { videoViewRef?.pause() }
                        isPaused = true
                    }
                }
            }
        }
    }

    val togglePlayPause: () -> Unit = {
        if (isInlinePlaying) {
            if (isPaused) {
                CircleVideoCoordinator.play(videoKey) {
                    runCatching { videoViewRef?.pause() }
                    isPaused = true
                }
                videoViewRef?.start()
                isPaused = false
            } else {
                videoViewRef?.pause()
                isPaused = true
            }
        } else {
            startPlayback()
        }
    }

    Box(
        modifier = modifier
            .clip(MM.radius.card)
            .background(MM.colors.surfaceSunk)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (!isInlinePlaying) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MM.colors.scrim.copy(alpha = 0.45f))
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MM.colors.surfaceSunk),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Videocam,
                    contentDescription = null,
                    tint = MM.colors.inkSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        if (isInlinePlaying && videoFile != null) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(videoFile!!.absolutePath)
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            mp.isLooping = true
                            mp.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
                            if (!isPaused) {
                                start()
                            }
                        }
                        setOnCompletionListener {
                            if (isInlinePlaying && !isPaused) {
                                start()
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            isInlinePlaying = false
                            isPaused = false
                            true
                        }
                    }.also { videoViewRef = it }
                },
                update = { view ->
                    videoViewRef = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tap area to toggle play/pause
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { togglePlayPause() }
        )

        // Center Play / Loading button when not playing or when paused
        if (!isInlinePlaying || isPaused) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(MM.radius.pill)
                    .background(CirclesPalette.Accent)
                    .clickable { togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = FixedWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = if (isPaused) "Resume video" else "Play video",
                        tint = FixedWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Initial duration & size badge at bottom-left
        if (!isInlinePlaying) {
            val pillInfo = listOfNotNull(
                a.durationMs?.takeIf { it > 0 }?.let(::formatDuration),
                formatBytes(a.bytes),
                if (loading) "Loading…" else null
            ).joinToString(" · ")
            if (pillInfo.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(MM.space.s)
                        .clip(MM.radius.pill)
                        .background(MM.colors.scrim.copy(alpha = 0.7f))
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                ) {
                    Text(
                        pillInfo,
                        style = MM.type.caption,
                        color = FixedWhite
                    )
                }
            }
        }

        // Top-right Fullscreen expand pill (pauses inline playback and opens VideoPlayerDialog)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(MM.space.s)
                .clip(MM.radius.pill)
                .background(MM.colors.scrim.copy(alpha = 0.7f))
                .clickable {
                    if (isInlinePlaying && !isPaused) {
                        videoViewRef?.pause()
                        isPaused = true
                    }
                    val cur = videoFile
                    if (cur != null) {
                        showPlayer = cur
                    } else if (!loading) {
                        loading = true
                        scope.launch {
                            val ext = if (a.mime == "video/webm") "webm" else "mp4"
                            val file = CircleMediaCache.fetchToFile(context, circleId, a.path, ext)
                            videoFile = file
                            loading = false
                            if (file != null) {
                                showPlayer = file
                            }
                        }
                    }
                }
                .padding(MM.space.xs),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Fullscreen,
                contentDescription = "Expand video to fullscreen",
                tint = FixedWhite,
                modifier = Modifier.size(MMSize.iconSmall)
            )
        }

        // Bottom-right Mute / Unmute pill (when inline playback is active)
        if (isInlinePlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(MM.space.s)
                    .clip(MM.radius.pill)
                    .background(MM.colors.scrim.copy(alpha = 0.7f))
                    .clickable {
                        val nextMuted = !isMuted
                        isMuted = nextMuted
                        mediaPlayerRef?.setVolume(
                            if (nextMuted) 0f else 1f,
                            if (nextMuted) 0f else 1f
                        )
                        if (!nextMuted) {
                            CircleAudioPlayer.pause()
                        }
                    }
                    .padding(MM.space.xs),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (isMuted) "Unmute video" else "Mute video",
                    tint = FixedWhite,
                    modifier = Modifier.size(MMSize.iconSmall)
                )
            }
        }

        // Thin bottom playback progress bar updating while playing
        if (isInlinePlaying) {
            LinearProgressIndicator(
                progress = { playbackProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
                color = CirclesPalette.Accent,
                trackColor = MM.colors.lineSoft.copy(alpha = 0.4f)
            )
        }
    }

    showPlayer?.let { file ->
        VideoPlayerDialog(file) { showPlayer = null }
    }
}

/** A video on a post, as a tile: thumbnail frame, length and size pill, centered play button, and inline playback. */
@Composable
private fun VideoTile(circleId: String, a: MediaAttachment, modifier: Modifier, contentDescription: String) {
    InlineVideoPlayer(circleId, a, modifier, contentDescription)
}

/** A fullscreen video player with media controls for a file already on disk. */
@Composable
fun VideoPlayerDialog(file: File, onDismiss: () -> Unit) {
    var isPreparing by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MM.colors.scrim.copy(alpha = 0.96f))
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(file.absolutePath)
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { mp ->
                            isPreparing = false
                            playbackError = false
                            mp.isLooping = false
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            isPreparing = false
                            playbackError = true
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().align(Alignment.Center)
            )

            if (isPreparing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = FixedWhite,
                        strokeWidth = 3.dp
                    )
                }
            }

            if (playbackError) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MM.space.s),
                        modifier = Modifier.padding(MM.space.l)
                    ) {
                        Text(
                            "Unable to play video",
                            style = MM.type.bodyStrong,
                            color = FixedWhite
                        )
                        Text(
                            "The video format may not be supported.",
                            style = MM.type.caption,
                            color = FixedWhite.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(MM.space.m)
            ) {
                Icon(Icons.Rounded.Close, "Close video", tint = FixedWhite)
            }
        }
    }
}

private sealed interface ImageLoadState {
    data object Loading : ImageLoadState
    data object Error : ImageLoadState
    data class Success(val bitmap: ImageBitmap, val bytes: ByteArray) : ImageLoadState
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
    var retryKey by remember { mutableStateOf(0) }

    val loadState by produceState<ImageLoadState>(initialValue = ImageLoadState.Loading, circleId, path, retryKey) {
        value = ImageLoadState.Loading
        if (path.isNullOrBlank()) {
            value = ImageLoadState.Error
            return@produceState
        }
        val bytes = CircleMediaCache.fetch(context, circleId, path)
        if (bytes == null) {
            value = ImageLoadState.Error
            return@produceState
        }
        val bmp = withContext(Dispatchers.Default) {
            decodeSampledBitmap(bytes, 2048)?.asImageBitmap()
        }
        value = if (bmp != null) ImageLoadState.Success(bmp, bytes) else ImageLoadState.Error
    }

    when (val state = loadState) {
        is ImageLoadState.Loading -> {
            Box(
                modifier = modifier.background(MM.colors.surfaceSunk),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MM.colors.inkSecondary,
                    strokeWidth = 2.dp
                )
            }
        }
        is ImageLoadState.Error -> {
            Box(
                modifier = modifier
                    .background(MM.colors.surfaceSunk)
                    .clickable { retryKey++ },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs),
                    modifier = Modifier.padding(MM.space.s)
                ) {
                    Icon(
                        Icons.Rounded.Sync,
                        contentDescription = "Retry loading image",
                        tint = MM.colors.inkSecondary,
                        modifier = Modifier.size(MMSize.iconSmall)
                    )
                    Text(
                        "Tap to retry",
                        style = MM.type.caption,
                        color = MM.colors.inkSecondary
                    )
                }
            }
        }
        is ImageLoadState.Success -> {
            val imgMod = if (allowFullscreen) modifier.clickable { showFull = true } else modifier
            Image(
                state.bitmap,
                contentDescription = contentDescription,
                modifier = imgMod,
                contentScale = ContentScale.Crop
            )
            if (showFull) {
                FullscreenImageDialog(
                    bitmap = state.bitmap,
                    rawBytes = state.bytes,
                    contentDescription = contentDescription,
                    onDismiss = { showFull = false }
                )
            }
        }
    }
}

@Composable
private fun FullscreenImageDialog(
    bitmap: ImageBitmap,
    rawBytes: ByteArray,
    contentDescription: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(rawBytes) }
            }
        }
    }

    val onShare: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "circles_media").apply { mkdirs() }
            val shareFile = File(cacheDir, "share_${System.currentTimeMillis()}.jpg")
            runCatching {
                shareFile.writeBytes(rawBytes)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share photo"))
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MM.colors.scrim.copy(alpha = 0.94f))
                .clickable(enabled = scale <= 1.05f) { onDismiss() }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(MM.space.m)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(MM.space.m),
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, "Share photo", tint = FixedWhite)
                }
                IconButton(onClick = { saver.launch("photo_${System.currentTimeMillis()}.jpg") }) {
                    Icon(Icons.Rounded.Download, "Save photo", tint = FixedWhite)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close preview", tint = FixedWhite)
                }
            }
        }
    }
}

/** Coordinated audio player tracking active voice note playback so only one plays at a time. */
internal object CircleAudioPlayer {
    private var activePlayer: MediaPlayer? = null
    private var activePath: String? = null
    private var onStopCallback: (() -> Unit)? = null

    fun play(
        path: String,
        file: File,
        onPrepared: (MediaPlayer) -> Unit,
        onError: () -> Unit,
        onCompletion: () -> Unit,
        onStop: () -> Unit
    ) {
        stop()
        activePath = path
        onStopCallback = onStop
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    if (activePath == path) {
                        mp.start()
                        onPrepared(mp)
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (activePath == path) {
                        stop()
                        onError()
                    }
                    true
                }
                setOnCompletionListener {
                    if (activePath == path) {
                        stop()
                        onCompletion()
                    }
                }
                prepareAsync()
            }
            activePlayer = player
        } catch (e: Exception) {
            stop()
            onError()
        }
    }

    fun pause() {
        runCatching { activePlayer?.pause() }
    }

    fun resume() {
        runCatching { activePlayer?.start() }
    }

    fun getPlayer(path: String): MediaPlayer? {
        return if (activePath == path) activePlayer else null
    }

    fun stop() {
        val cb = onStopCallback
        onStopCallback = null
        activePath = null
        val player = activePlayer
        activePlayer = null
        runCatching {
            if (player != null) {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        }
        cb?.invoke()
    }

    fun releaseIfCurrent(path: String) {
        if (activePath == path) {
            stop()
        }
    }
}

/** A voice note: play or pause with a 24-bar audio waveform, speed toggle pill, and coordinated playback. */
@Composable
fun VoiceBubble(
    circleId: String,
    a: MediaAttachment,
    tint: androidx.compose.ui.graphics.Color = CirclesPalette.Accent,
    onAccent: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(a.durationMs ?: 0L) }
    var speed by remember { mutableStateOf(1f) }

    DisposableEffect(a.path) {
        onDispose {
            CircleAudioPlayer.releaseIfCurrent(a.path)
        }
    }

    LaunchedEffect(a.path) {
        if (durationMs <= 0L) {
            val cached = CircleMediaCache.getCachedFile(context, a.path, "m4a")
            if (cached != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        MediaMetadataRetriever().use { r ->
                            r.setDataSource(cached.absolutePath)
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        }
                    }.getOrNull()?.let { dur ->
                        if (dur > 0) durationMs = dur
                    }
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val mp = CircleAudioPlayer.getPlayer(a.path)
                if (mp != null && mp.isPlaying) {
                    currentPositionMs = mp.currentPosition.toLong()
                    val dur = mp.duration.toLong()
                    if (dur > 0) durationMs = dur
                }
                delay(150)
            }
        }
    }

    val bars = remember {
        listOf(
            6, 10, 16, 12, 20, 14, 18, 24, 14, 18, 22, 12,
            16, 20, 14, 24, 18, 14, 20, 16, 12, 16, 10, 6
        )
    }
    val total = if (durationMs > 0) durationMs else (a.durationMs ?: 0L)
    val progress = if (total > 0) (currentPositionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val buttonBg = if (onAccent) MM.colors.onAccent else tint
    val buttonFg = if (onAccent) tint else FixedWhite
    val inactiveBarColor = if (onAccent) MM.colors.onAccent.copy(alpha = 0.35f) else MM.colors.line
    val activeBarColor = if (onAccent) MM.colors.onAccent else MM.colors.accent
    val textColor = if (onAccent) MM.colors.onAccent.copy(alpha = 0.85f) else MM.colors.inkSecondary

    val speedLabel = when (speed) {
        1.5f -> "1.5x"
        2f -> "2x"
        else -> "1x"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
        modifier = Modifier.padding(vertical = MM.space.xs)
    ) {
        IconButton(
            onClick = {
                if (isLoading) return@IconButton
                val mp = CircleAudioPlayer.getPlayer(a.path)
                if (mp != null) {
                    if (isPlaying) {
                        CircleAudioPlayer.pause()
                        isPlaying = false
                    } else {
                        CircleAudioPlayer.resume()
                        isPlaying = true
                    }
                    return@IconButton
                }
                isLoading = true
                CircleVideoCoordinator.pauseOrStop(CircleVideoCoordinator.activeVideoKey ?: "")
                scope.launch {
                    val file = CircleMediaCache.fetchToFile(context, circleId, a.path, "m4a")
                    if (file != null) {
                        CircleAudioPlayer.play(
                            path = a.path,
                            file = file,
                            onPrepared = { player ->
                                isLoading = false
                                val dur = player.duration.toLong()
                                if (dur > 0) durationMs = dur
                                runCatching {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && speed != 1f) {
                                        player.playbackParams = player.playbackParams.setSpeed(speed)
                                    }
                                }
                                isPlaying = true
                            },
                            onError = {
                                isLoading = false
                                isPlaying = false
                                currentPositionMs = 0L
                            },
                            onCompletion = {
                                isLoading = false
                                isPlaying = false
                                currentPositionMs = 0L
                            },
                            onStop = {
                                isLoading = false
                                isPlaying = false
                                currentPositionMs = 0L
                            }
                        )
                    } else {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .size(36.dp)
                .clip(MM.radius.pill)
                .background(buttonBg)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = buttonFg,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                    tint = buttonFg,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 24-bar waveform with smooth played/unplayed color split
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            modifier = Modifier.height(24.dp)
        ) {
            bars.forEachIndexed { idx, h ->
                val active = (isPlaying || currentPositionMs > 0L) && ((idx + 1).toFloat() / bars.size <= progress)
                val barColor by animateColorAsState(
                    targetValue = if (active) activeBarColor else inactiveBarColor,
                    animationSpec = MM.motion.quick(),
                    label = "waveform_bar_$idx"
                )
                Box(
                    Modifier
                        .width(2.5.dp)
                        .height(h.dp)
                        .clip(MM.radius.pill)
                        .background(barColor)
                )
            }
        }

        val timeText = "${formatDuration(currentPositionMs)} / ${formatDuration(total)}"

        Text(
            timeText,
            style = MM.type.caption,
            color = textColor
        )

        // Playback speed toggle pill (1x -> 1.5x -> 2x)
        Box(
            modifier = Modifier
                .clip(MM.radius.pill)
                .background(if (onAccent) MM.colors.onAccent.copy(alpha = 0.2f) else MM.colors.surfaceSunk)
                .clickable {
                    val nextSpeed = when (speed) {
                        1f -> 1.5f
                        1.5f -> 2f
                        else -> 1f
                    }
                    speed = nextSpeed
                    val mp = CircleAudioPlayer.getPlayer(a.path)
                    if (mp != null) {
                        runCatching {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                val wasPlaying = mp.isPlaying
                                mp.playbackParams = mp.playbackParams.setSpeed(nextSpeed)
                                if (!wasPlaying) {
                                    mp.pause()
                                }
                            }
                        }
                    }
                }
                .padding(horizontal = MM.space.xs, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                speedLabel,
                style = MM.type.caption,
                color = textColor
            )
        }
    }
}

/** A video attachment: styled for chat with inline playback, mute toggle, and fullscreen expand. */
@Composable
fun VideoBubble(circleId: String, a: MediaAttachment) {
    InlineVideoPlayer(
        circleId = circleId,
        a = a,
        modifier = Modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .height(172.dp)
            .clip(MM.radius.card),
        contentDescription = a.name ?: "Video preview"
    )
}

/** A file: its name and size, clickable to open in PDF viewer (fallback to save), plus a save action. */
@Composable
fun FileBubble(circleId: String, a: MediaAttachment) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(a.mime.ifBlank { "application/pdf" })) { uri: Uri? ->
        val bytes = pendingSaveBytes
        if (uri != null && bytes != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        }
        pendingSaveBytes = null
    }

    val openFile: () -> Unit = {
        if (!loading) {
            loading = true
            scope.launch {
                val ext = if (a.isPdf || a.mime == "application/pdf") "pdf" else "bin"
                val file = CircleMediaCache.fetchToFile(context, circleId, a.path, ext)
                loading = false
                if (file != null) {
                    val opened = runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, if (a.isPdf) "application/pdf" else a.mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open file"))
                        true
                    }.getOrDefault(false)

                    if (!opened) {
                        val bytes = runCatching { file.readBytes() }.getOrNull()
                        if (bytes != null) {
                            pendingSaveBytes = bytes
                            saver.launch(a.name ?: "document.$ext")
                        }
                    }
                }
            }
        }
    }

    val onSave: () -> Unit = {
        if (!loading) {
            scope.launch {
                val bytes = CircleMediaCache.fetch(context, circleId, a.path) ?: return@launch
                pendingSaveBytes = bytes
                saver.launch(a.name ?: "document.pdf")
            }
        }
    }

    Row(
        modifier = Modifier
            .clip(MM.radius.card)
            .clickable(enabled = !loading, onClick = openFile)
            .padding(MM.space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MM.radius.pill)
                .background(MM.colors.surfaceSunk),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MM.colors.accent,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = MM.colors.accent,
                    modifier = Modifier.size(MMSize.iconSmall)
                )
            }
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                a.name ?: "File",
                style = MM.type.bodyStrong,
                color = MM.colors.ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            val subtext = listOfNotNull(
                formatBytes(a.bytes),
                if (a.isPdf) "PDF" else null,
                if (loading) "Opening…" else null
            ).joinToString(" · ")
            Text(
                subtext,
                style = MM.type.caption,
                color = MM.colors.inkSecondary
            )
        }
        TextAction("Save", onSave)
    }
}

/**
 * Asynchronously reads a picked document into memory on Dispatchers.IO.
 */
suspend fun readPickedAsync(context: Context, uri: Uri): PickedFile? = withContext(Dispatchers.IO) {
    readPicked(context, uri)
}

/**
 * Reads a picked document into memory. Every photo is re-encoded as a JPEG, which drops EXIF metadata such as
 * GPS location. Oversized photos are shrunk with inSampleSize so the longest side is <= 2048 px.
 * Videos stream with a bounded check to avoid allocating huge arrays.
 */
fun readPicked(context: Context, uri: Uri): PickedFile? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: return null

    val name = resolver.query(uri, null, null, null, null)?.use { c ->
        val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && index >= 0) c.getString(index) else null
    }

    if (mime.startsWith("image/")) {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        } ?: return null

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var sampleSize = 1
        val longest = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        while (longest / sampleSize.toFloat() > 2048f) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val decoded = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null

        val finalBitmap = if (maxOf(decoded.width, decoded.height) > 2048) {
            val scale = 2048f / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val jpegBytes = out.toByteArray()
        if (jpegBytes.isEmpty() || jpegBytes.size > MediaKind.Image.maxBytes) return null
        return PickedFile(jpegBytes, "image/jpeg", name)
    }

    if (mime.startsWith("video/")) {
        val size = runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
        if (size > MediaKind.Video.maxBytes) return null

        val raw = resolver.openInputStream(uri)?.use { stream ->
            readStreamBounded(stream, MediaKind.Video.maxBytes)
        } ?: return null

        val durationMs = runCatching {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(context, uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }
        }.getOrNull()
        return PickedFile(raw, mime, name, durationMs)
    }

    val raw = resolver.openInputStream(uri)?.use { stream ->
        readStreamBounded(stream, MediaKind.File.maxBytes)
    } ?: return null
    return PickedFile(raw, mime, name)
}

private fun readStreamBounded(stream: InputStream, maxBytes: Long): ByteArray? {
    val buffer = ByteArray(16384)
    val out = ByteArrayOutputStream()
    var total = 0L
    while (true) {
        val n = stream.read(buffer)
        if (n == -1) break
        total += n
        if (total > maxBytes) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/**
 * Longest side at most 2048 px, JPEG at 85%. Re-encoding writes only the pixels, so the output carries no EXIF.
 * Null when the picture can't be decoded or still won't fit.
 */
private fun reencodeToJpeg(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / sampleSize.toFloat() > 2048f) {
        sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
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

    /** Safely calls recorder?.maxAmplitude and returns normalized (amp / 32767f).coerceIn(0f, 1f), or 0f if null/error. */
    fun amplitude(): Float {
        return runCatching {
            val amp = recorder?.maxAmplitude ?: 0
            (amp / 32767f).coerceIn(0f, 1f)
        }.getOrDefault(0f)
    }

    /** Returns elapsed recording time in milliseconds, or 0L when not recording. */
    fun elapsedMs(): Long = if (startedAt > 0L) System.currentTimeMillis() - startedAt else 0L

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
            recorder = r
            file = f
            startedAt = System.currentTimeMillis()
            true
        } catch (e: RuntimeException) {
            r.release()
            f.delete()
            startedAt = 0L
            false
        }
    }

    /** Stops and returns the recording, or null when it was too short to keep. */
    fun stop(): PickedFile? {
        val r = recorder ?: return null
        val f = file
        val durationMs = if (startedAt > 0L) System.currentTimeMillis() - startedAt else 0L
        recorder = null
        file = null
        startedAt = 0L
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
        file?.delete()
        file = null
        startedAt = 0L
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
