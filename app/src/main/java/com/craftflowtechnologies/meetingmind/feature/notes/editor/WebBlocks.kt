package com.craftflowtechnologies.meetingmind.feature.notes.editor

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.notes.InlineStyle
import com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.notes.YouTube
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

/** Code, shown as written: monospace, scrolls sideways, one tap to copy, tap the code to edit. */
@Composable
internal fun CodeBlock(block: NoteBlock, onTap: (() -> Unit)? = null, onEdit: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf(false) }
    val language = block.payload[NoteBlock.PAYLOAD_LANGUAGE].orEmpty()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceSunk)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(language.ifBlank { "Code" }, fontSize = 12.sp, color = InkMuted, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(block.content.text)) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = InkSecondary)
                Spacer(Modifier.width(6.dp))
                Text("Copy", fontSize = 12.sp, color = InkSecondary)
            }
        }
        Text(
            block.content.text.ifEmpty { " " },
            fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, color = Ink, softWrap = false,
            modifier = Modifier.fillMaxWidth().clickable { if (onTap != null) onTap() else editing = true }.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
        )
    }
    if (editing) {
        var text by remember { mutableStateOf(block.content.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit code") },
            text = {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            },
            confirmButton = { TextButton(onClick = { onEdit(text); editing = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
}

/** A table: a header row and cells with their bold, links and code, scrolling sideways when wide. */
@Composable
internal fun TableBlock(block: NoteBlock) {
    val table = remember(block.payload[NoteBlock.PAYLOAD_TABLE]) { MarkdownImport.Table.fromJson(block.payload[NoteBlock.PAYLOAD_TABLE]) }
    if (table == null) { Text(block.content.text, color = InkSecondary, modifier = Modifier.padding(vertical = 8.dp)); return }
    val width = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    val colors = LocalMMColors.current
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            (listOf(table.header) + table.rows).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(if (r == 0) SurfaceSunk else colors.surface)) {
                    for (c in 0 until width) {
                        val cell = remember(row, c) { MarkdownImport.inline(row.getOrElse(c) { "" }) }
                        Text(
                            styled(cell, colors),
                            fontSize = 14.sp, lineHeight = 20.sp, color = Ink,
                            fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.widthIn(min = 72.dp, max = 240.dp).weight(1f, fill = true).padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                        if (c < width - 1) Box(Modifier.width(1.dp).fillMaxSize().background(Line))
                    }
                }
                if (r < table.rows.size) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
    }
}

internal fun styled(content: RichText, colors: com.craftflowtechnologies.meetingmind.ui.theme.MMColors): AnnotatedString = buildAnnotatedString {
    append(content.text)
    for (span in content.spans) {
        val style = when (span.style) {
            InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            InlineStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            InlineStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            InlineStyle.HIGHLIGHT -> SpanStyle(background = colors.accentWash)
            InlineStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceSunk)
            InlineStyle.LINK -> SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)
        }
        addStyle(style, span.start.coerceIn(0, content.text.length), span.end.coerceIn(0, content.text.length))
    }
}

/** Something from the web: a picture by its address, a YouTube video that plays right here, or a link. */
@Composable
internal fun EmbedBlock(block: NoteBlock) {
    val url = block.payload[NoteBlock.PAYLOAD_URL] ?: return
    val alt = block.payload[NoteBlock.PAYLOAD_ALT].orEmpty()
    when (block.payload[NoteBlock.PAYLOAD_EMBED_KIND]) {
        NoteBlock.EMBED_IMAGE -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            AsyncImage(
                model = url, contentDescription = alt.ifBlank { "Picture" }, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceSunk)
            )
            if (alt.isNotBlank()) Text(alt, fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        }
        NoteBlock.EMBED_YOUTUBE -> YouTubeBlock(url, alt)
        else -> LinkCard(url, alt)
    }
}

@Composable
private fun YouTubeBlock(url: String, title: String) {
    val id = YouTube.idOf(url) ?: return LinkCard(url, title)
    val context = LocalContext.current
    var playing by remember(id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(SurfaceSunk)) {
            if (playing) YouTubePlayer(id, YouTube.startSeconds(url) ?: 0)
            else {
                AsyncImage(model = YouTube.thumbnail(id), contentDescription = title.ifBlank { "YouTube video" }, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable { playing = true })
                Box(Modifier.align(Alignment.Center).size(58.dp).clip(CircleShape).background(Accent).clickable { playing = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = LocalMMColors.current.onAccent, modifier = Modifier.size(32.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.ifBlank { "YouTube" }, fontSize = 13.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(Modifier.clickable { context.openUrl(url) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Open in YouTube", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Accent, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(id: String, start: Int) {
    val html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style></head>
        <body><iframe src="https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1&rel=0&start=$start"
        allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>
    """.trimIndent()
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                // A real https origin, so YouTube accepts the embed.
                loadDataWithBaseURL("https://${ctx.packageName}", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}

@Composable
private fun LinkCard(url: String, title: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceSunk).clickable { context.openUrl(url) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Link, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (title.isNotBlank()) Text(title, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun android.content.Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
