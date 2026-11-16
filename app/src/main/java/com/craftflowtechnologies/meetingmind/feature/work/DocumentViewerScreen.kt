package com.craftflowtechnologies.meetingmind.feature.work

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.database.DocumentPageHit
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.documents.DocumentSearch
import com.craftflowtechnologies.meetingmind.core.documents.PageBox
import com.craftflowtechnologies.meetingmind.core.documents.PdfPageRenderer
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** The document opened for viewing: loading, ready to render, or it could not be opened. */
private sealed interface OpenedDocument {
    data object Loading : OpenedDocument
    data class Ready(val renderer: PdfPageRenderer) : OpenedDocument
    data object Failed : OpenedDocument
}

private const val SEARCH_DEBOUNCE_MS = 250L
private const val SEARCH_LIMIT = 200
/** The viewer renders at up to twice the screen width, capped so a page stays a few megabytes. */
private const val VIEW_MIN_EDGE_PX = 800
private const val VIEW_MAX_EDGE_PX = 1800
private const val MAX_ZOOM = 4f

/**
 * Opens a read PDF at a page, with the passage highlighted (spec §R4, screen #8c3). The page is drawn
 * by [PdfPageRenderer]; boxes come from the evidence's `boxJson` (or the page's boxes), normalised to
 * the page. Pinch to zoom, search inside the document through `document_pages_fts`, and go to a page.
 *
 * Chrome follows the MM tokens, so it reads in both Paper (light) and Graphite (dark). The page itself
 * is always paper-white, as a printed page would be.
 *
 * @param initialPage 1-based page to open at.
 * @param highlightPage 1-based page that [highlights] belong to, if any.
 */
@Composable
fun DocumentViewerScreen(
    itemId: String,
    title: String,
    filePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 1,
    highlightPage: Int? = null,
    highlights: List<PageBox> = emptyList()
) {
    val colors = MM.colors

    val opened by produceState<OpenedDocument>(OpenedDocument.Loading, filePath) {
        value = withContext(Dispatchers.IO) {
            try {
                OpenedDocument.Ready(PdfPageRenderer.open(File(filePath)))
            } catch (e: Exception) {
                // Encrypted, corrupt or missing: the screen says so, and nothing is shown half-open.
                OpenedDocument.Failed
            }
        }
    }
    DisposableEffect(opened) {
        onDispose { (opened as? OpenedDocument.Ready)?.renderer?.close() }
    }

    val renderer = (opened as? OpenedDocument.Ready)?.renderer
    val pageCount = renderer?.pageCount ?: 0
    var pageIndex by rememberSaveable { mutableStateOf((initialPage - 1).coerceAtLeast(0)) }
    val current = if (pageCount > 0) pageIndex.coerceIn(0, pageCount - 1) else 0

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var jumpOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        // Top bar: back, the title and where the reader is, and search.
        Row(
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (pageCount > 0) "Page ${current + 1} of $pageCount" else "Opening",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary
                )
            }
            IconButton(onClick = { searchOpen = !searchOpen }, enabled = pageCount > 0) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search inside",
                    tint = if (pageCount > 0) colors.ink else colors.inkFaint
                )
            }
        }

        if (searchOpen) {
            SearchInside(
                itemId = itemId,
                onOpenPage = { page ->
                    pageIndex = page - 1
                    searchOpen = false
                },
                onClose = { searchOpen = false }
            )
        }

        // The page itself. Zoom and pan live here; they reset when the page changes.
        var zoom by remember(current) { mutableFloatStateOf(1f) }
        var pan by remember(current) { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.surfaceSunk)
                .pointerInput(current) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val next = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                        zoom = next
                        pan = if (next > 1f) pan + panChange else Offset.Zero
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                opened is OpenedDocument.Failed -> Text(
                    text = "This document couldn't be opened.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(24.dp)
                )
                renderer == null -> CircularProgressIndicator(color = colors.accent)
                else -> PagePicture(
                    renderer = renderer,
                    pageIndex = current,
                    boxes = if (highlightPage == current + 1) highlights else emptyList(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                )
            }
        }

        // Bottom bar: previous, go to page, next. Previous and next fade out at the ends.
        Row(
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(enabled = current > 0, onClick = { pageIndex = current - 1 }) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    tint = if (current > 0) colors.ink else colors.inkFaint
                )
            }
            TextButton(onClick = { jumpOpen = true }, enabled = pageCount > 0) {
                Text("Go to page", color = colors.accent)
            }
            IconButton(enabled = current < pageCount - 1, onClick = { pageIndex = current + 1 }) {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Next page",
                    tint = if (current < pageCount - 1) colors.ink else colors.inkFaint
                )
            }
        }
    }

    if (jumpOpen) {
        var entry by remember { mutableStateOf((current + 1).toString()) }
        AlertDialog(
            onDismissRequest = { jumpOpen = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = entry,
                    onValueChange = { entry = it.filter { c -> c.isDigit() }.take(5) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("1 to $pageCount") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    entry.toIntOrNull()?.takeIf { it in 1..pageCount }?.let { pageIndex = it - 1 }
                    jumpOpen = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { jumpOpen = false }) { Text("Cancel") }
            }
        )
    }
}

/** Search inside the document: FTS over the document's pages, limited to this item. */
@Composable
private fun SearchInside(
    itemId: String,
    onOpenPage: (Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val colors = MM.colors
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<DocumentPageHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val match = DocumentSearch.matchQuery(query)
        if (match == null) {
            hits = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        hits = withContext(Dispatchers.IO) {
            MeetMindDatabase.getInstance(context.applicationContext)
                .searchDao()
                .documentPages(match, SEARCH_LIMIT)
                .filter { it.itemId == itemId }
        }
        searched = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Find in this document") },
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("Close", color = colors.accent) }
        }
        when {
            query.isBlank() -> Unit
            searched && hits.isEmpty() -> Text(
                text = "No matches",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
            else -> LazyColumn(modifier = Modifier.heightIn(max = 220.dp).padding(top = 8.dp)) {
                items(hits) { hit ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPage(hit.page) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "p. ${hit.page}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accent
                        )
                        Text(
                            text = hit.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * One rendered page, with its highlight boxes drawn over it. The render is sized to the available
 * width (never more than [VIEW_MAX_EDGE_PX] on the longer side), off the main thread.
 */
@Composable
private fun PagePicture(
    renderer: PdfPageRenderer,
    pageIndex: Int,
    boxes: List<PageBox>,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val highlight = colors.accent.copy(alpha = 0.28f)
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val edge = (widthPx * 2).coerceIn(VIEW_MIN_EDGE_PX, VIEW_MAX_EDGE_PX)

        val bitmap by produceState<Bitmap?>(null, renderer, pageIndex, edge) {
            value = null
            value = withContext(Dispatchers.IO) { renderer.render(pageIndex, edge) }
        }
        val picture = bitmap
        if (picture == null) {
            CircularProgressIndicator(color = colors.accent)
        } else {
            val ratio = picture.width.toFloat() / picture.height.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(MaterialTheme.shapes.extraSmall)
            ) {
                Image(
                    bitmap = picture.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize()
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    boxes.forEach { box ->
                        drawRect(
                            color = highlight,
                            topLeft = Offset((box.left * size.width).toFloat(), (box.top * size.height).toFloat()),
                            size = Size((box.width * size.width).toFloat(), (box.height * size.height).toFloat())
                        )
                    }
                }
            }
        }
    }
}
