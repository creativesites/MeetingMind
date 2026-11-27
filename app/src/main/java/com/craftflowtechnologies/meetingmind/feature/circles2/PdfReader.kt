package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** "Page 3 of 12". The current page is clamped to 1..total. Empty when there are no pages. */
internal fun pdfPageIndicator(current: Int, total: Int): String {
    if (total <= 0) return ""
    return "Page ${current.coerceIn(1, total)} of $total"
}

/** The rendered height in pixels for a page drawn [targetWidthPx] wide, keeping the page's aspect ratio. */
internal fun pdfRenderHeightPx(pageWidth: Int, pageHeight: Int, targetWidthPx: Int): Int {
    if (targetWidthPx <= 0) return 1
    if (pageWidth <= 0 || pageHeight <= 0) return targetWidthPx
    return (targetWidthPx.toLong() * pageHeight / pageWidth).toInt().coerceAtLeast(1)
}

/** Tallest bitmap we ask the renderer for. Taller pages are drawn smaller so a long scan can't exhaust memory. */
internal const val PDF_MAX_RENDER_HEIGHT_PX = 6000

/** The (width, height) in pixels to render a page at. Keeps the aspect ratio and caps the height. */
internal fun pdfRenderSizePx(pageWidth: Int, pageHeight: Int, targetWidthPx: Int): Pair<Int, Int> {
    val height = pdfRenderHeightPx(pageWidth, pageHeight, targetWidthPx)
    if (height <= PDF_MAX_RENDER_HEIGHT_PX || pageWidth <= 0 || pageHeight <= 0) {
        return Pair(targetWidthPx.coerceAtLeast(1), height)
    }
    val width = (PDF_MAX_RENDER_HEIGHT_PX.toLong() * pageWidth / pageHeight).toInt().coerceAtLeast(1)
    return Pair(width, PDF_MAX_RENDER_HEIGHT_PX)
}

/**
 * Owns one PdfRenderer and its file descriptor. PdfRenderer is not thread-safe, so every call goes through one mutex.
 * Renders run on Dispatchers.IO (callers switch). [close] is safe to call more than once and closes any renderer already open.
 */
internal class PdfPageSource {
    private val lock = Mutex()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var closed = false

    /**
     * Opens [file] and returns its page count. Throws SecurityException for a password-protected PDF,
     * and an IOException or similar for a file that isn't a readable PDF.
     */
    suspend fun open(file: File): Int = lock.withLock {
        check(!closed) { "PDF source is closed" }
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val opened = PdfRenderer(pfd)
            descriptor = pfd
            renderer = opened
            opened.pageCount
        } catch (e: Throwable) {
            pfd.close()
            throw e
        }
    }

    /** Renders page [index] to a bitmap [widthPx] wide on white. Null when closed or the page is out of range. */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        val pdf = renderer ?: return@withLock null
        if (index < 0 || index >= pdf.pageCount || widthPx <= 0) return@withLock null
        val page = pdf.openPage(index)
        try {
            val (width, height) = pdfRenderSizePx(page.width, page.height, widthPx)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }

    suspend fun close() = lock.withLock {
        closed = true
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }
}

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data object Failed : PdfLoad
    data class Protected(val file: File) : PdfLoad
    data class Ready(val file: File, val pageCount: Int) : PdfLoad
}

/** Downloads (or reads from cache) the PDF and opens it. Runs on Dispatchers.IO. */
private suspend fun loadPdf(context: Context, circleId: String, path: String, source: PdfPageSource): PdfLoad {
    val file = CircleMediaCache.fetchToFile(context, circleId, path, "pdf") ?: return PdfLoad.Failed
    return try {
        PdfLoad.Ready(file, source.open(file))
    } catch (e: SecurityException) {
        PdfLoad.Protected(file)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PdfLoad.Failed
    }
}

/** Hands the PDF to another app (the chooser), as PdfTile did before the in-app reader existed. */
internal fun openPdfExternally(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    }
}

/**
 * A full-screen in-app PDF reader. Fetches the file through the same cache as the rest of Circles media,
 * renders each page off the main thread at screen width, and closes the renderer when the dialog leaves.
 */
@Composable
fun PdfReaderDialog(circleId: String, attachment: MediaAttachment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val source = remember(circleId, attachment.path) { PdfPageSource() }
    DisposableEffect(source) {
        onDispose { CoroutineScope(Dispatchers.IO).launch { source.close() } }
    }
    val load by produceState<PdfLoad>(PdfLoad.Loading, circleId, attachment.path) {
        value = withContext(Dispatchers.IO) { loadPdf(context, circleId, attachment.path, source) }
    }
    val listState = rememberLazyListState()
    val file: File? = when (val l = load) {
        is PdfLoad.Ready -> l.file
        is PdfLoad.Protected -> l.file
        else -> null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MM.colors.surfaceSunk)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MM.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = MM.colors.ink)
                }
                Text(
                    attachment.name ?: "Document.pdf",
                    style = MM.type.bodyStrong,
                    color = MM.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = MM.space.xs)
                )
                if (file != null) {
                    TextAction("Open in…", onClick = { openPdfExternally(context, file) })
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                    when (val l = load) {
                        PdfLoad.Loading -> Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = MM.colors.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.height(MM.space.s))
                            Text("Opening PDF…", style = MM.type.caption, color = MM.colors.inkSecondary)
                        }
                        PdfLoad.Failed -> CenteredMessage(
                            "This PDF couldn't be opened",
                            "Check your connection and try again from the post."
                        )
                        is PdfLoad.Protected -> CenteredMessage(
                            "This PDF is password-protected",
                            "It can't be shown here. Open it in another app instead.",
                            action = { SecondaryButton("Open in…", { openPdfExternally(context, l.file) }) }
                        )
                        is PdfLoad.Ready -> if (l.pageCount == 0) {
                            CenteredMessage("This PDF has no pages", "")
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(MM.space.s),
                                contentPadding = PaddingValues(bottom = 56.dp)
                            ) {
                                items(l.pageCount) { index ->
                                    PdfPage(source, index, widthPx)
                                }
                            }
                        }
                    }
                }
                val ready = load as? PdfLoad.Ready
                if (ready != null && ready.pageCount > 0) {
                    PdfPageIndicator(
                        listState = listState,
                        pageCount = ready.pageCount,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = MM.space.m)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(MM.space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MM.type.bodyStrong, color = MM.colors.ink)
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(MM.space.xs))
            Text(detail, style = MM.type.caption, color = MM.colors.inkSecondary)
        }
        if (action != null) {
            Spacer(Modifier.height(MM.space.m))
            action()
        }
    }
}

@Composable
private fun PdfPage(source: PdfPageSource, index: Int, widthPx: Int) {
    var failed by remember(source, index, widthPx) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, source, index, widthPx) {
        // A damaged page, or a bitmap too big for the phone's memory, shows a line instead of crashing the app.
        value = withContext(Dispatchers.IO) {
            try {
                source.renderPage(index, widthPx)?.asImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }
        if (value == null) failed = true
    }
    val loaded = bitmap
    if (loaded == null && failed) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7071f), contentAlignment = Alignment.Center) {
            Text("Page ${index + 1} couldn't be shown", style = MM.type.caption, color = MM.colors.inkSecondary)
        }
    } else if (loaded != null) {
        Image(
            loaded,
            contentDescription = "Page ${index + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7071f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = MM.colors.accent, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun PdfPageIndicator(listState: LazyListState, pageCount: Int, modifier: Modifier = Modifier) {
    val current by remember(pageCount) { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    Surface(
        shape = MM.radius.pill,
        color = MM.colors.surface,
        border = BorderStroke(MMSize.hairline, MM.colors.line),
        modifier = modifier
    ) {
        Text(
            pdfPageIndicator(current, pageCount),
            style = MM.type.caption,
            color = MM.colors.ink,
            modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
        )
    }
}
